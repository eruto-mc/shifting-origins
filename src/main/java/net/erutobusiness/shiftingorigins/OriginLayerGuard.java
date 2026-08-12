package net.erutobusiness.shiftingorigins;

import io.github.edwinmindcraft.origins.api.capabilities.IOriginContainer;
import io.github.edwinmindcraft.origins.common.OriginsCommon;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 層が空のまま取り残されたプレイヤーを、既定の種族へ戻す安全網。
 *
 * <h2>なぜ要るか（2026-08-13 実測・部長判断）</h2>
 *
 * <p>層が空（{@code origins:empty}）のあいだ、Origins はそのプレイヤーを
 * <b>全ダメージ無効</b>にする（{@code hasAllOrigins()} が偽のとき無敵）。
 * 選択画面が出ていれば「選ぶまでの保護」だが、<b>画面が出ないまま空で残ると詰む</b>。
 *
 * <p>実在する経路が1つある——{@code /origin gui}。
 * {@code OriginCommand.openLayerScreen} は
 * {@code setOrigin(層, EMPTY)} →{@code synchronize()} →
 * <b>{@code checkAutoChoosingLayers(false)}</b> →{@code S2COpenOriginScreen(false)} と進むが、
 *
 * <ul>
 *   <li>既定の種族を入れる枝は<b>第1引数の boolean で塞がれている</b>ので、この経路では入らない
 *   <li>画面はクライアントの<b>描画tick</b>で組まれる（便りは {@code AWAITING_DISPLAY} を
 *       立てるだけ）。この経路では実測で11秒待っても出なかった
 * </ul>
 *
 * <p>結果、{@code /origin gui} 1回で「層が空・画面も出ない・無敵」が揃う。
 * 台本 {@code shifting-origins} の {@code cancel-negative-state} が
 * {@code generic / magic / starve / fall} すべて {@code 無敵=true} を記録している。
 *
 * <h2>どうやるか</h2>
 *
 * <pre>
 *   毎秒 ─→ 層は埋まっている？ ── はい ─→ 何もしない（＋珠の控えを捨てる）
 *            └ いいえ ─→ 珠で選択中（控えが在る）？ ── はい ─→ 触らない
 *                         └ いいえ ─→ 空が graceTicks 続いたら
 *                                     checkAutoChoosingLayers(true) で既定を入れる
 * </pre>
 *
 * <p><b>既定の種族を自分で引き直さない。</b>上流の入室処理
 * （{@code OriginsEventHandler.onLogin}）と<b>同じ呼び出しと同じ順</b>を使う。
 * 層の {@code default_origin} を読む場所を二重に持つと、片方だけ腐る。
 *
 * <h2>⚠ なぜ「初参加の選択中」を気にしなくてよいか</h2>
 *
 * <p>当部は<b>初参加の選択画面を無くしてある</b>（必ず珠から選ぶ）。両方の層に既定を書いてあり
 * （種族＝{@code origins:human} / 職業＝{@code origins-classes:nitwit}）、
 * 入室処理は {@code checkAutoChoosingLayers(<b>true</b>)} を呼ぶので、
 * <b>入った瞬間に既定が入る</b>。つまり層が空になる正当な場面は
 * <b>珠を使った直後の選択中だけ</b>で、そこは {@link OriginChangeCancel} の控えで見分けられる。
 *
 * <p>⚠ <b>猶予（graceTicks）を 0 にしない。</b>層を一瞬だけ空にして入れ直す実装
 * （他MODの「種族を振り直す」処理など）と競合すると、こちらが先に既定を入れてしまう。
 *
 * <p>⚠ 埋められなかったときは<b>その接続では二度と試さない</b>（{@code GAVE_UP}）。
 * 猶予ごとに永久に再試行すると、ログが同じ行で埋まって本物の失敗が埋もれる。
 */
public final class OriginLayerGuard {

  private static final Logger LOG = LoggerFactory.getLogger("shiftingorigins");

  /** 見に行く間隔。層が空になるのは稀なので毎tickは要らない。 */
  private static final int CHECK_INTERVAL = 20;

  /** そのプレイヤーの層が空だった累計tick（猶予の計測用）。 */
  private static final Map<UUID, Integer> EMPTY_FOR = new HashMap<>();

  /** 既定を入れようとして失敗した人。同じ失敗を毎秒記録しないため。 */
  private static final Set<UUID> GAVE_UP = new HashSet<>();

  private OriginLayerGuard() {
  }

  @SubscribeEvent
  public static void onPlayerTick(final TickEvent.PlayerTickEvent event) {

    if (event.phase != TickEvent.Phase.END
        || !(event.player instanceof ServerPlayer player)
        || player.tickCount % CHECK_INTERVAL != 0
        || !ShiftingOrigins.Config.GUARD_ENABLED.get()) {
      return;
    }
    IOriginContainer.get(player).ifPresent(container -> check(player, container));
  }

  private static void check(ServerPlayer player, IOriginContainer container) {

    UUID id = player.getUUID();

    if (container.hasAllOrigins()) {
      EMPTY_FOR.remove(id);
      GAVE_UP.remove(id);
      if (OriginChangeCancel.isChoosing(player)) {
        // 選び終えた＝「やめる」の窓は閉じた。控えを残したままだと、次に層が空になったとき
        // （管理コマンド等）この安全網が「まだ選択中」と読んで働かない。
        OriginChangeCancel.forget(player);
      }
      return;
    }

    if (OriginChangeCancel.isChoosing(player)) {
      // 珠で開いた選択の途中。ここで既定を入れると「やめる」も「選ぶ」も奪う。
      EMPTY_FOR.remove(id);
      return;
    }

    if (GAVE_UP.contains(id)) {
      return;
    }

    int waited = EMPTY_FOR.merge(id, CHECK_INTERVAL, Integer::sum);
    if (waited < ShiftingOrigins.Config.GUARD_GRACE_TICKS.get()) {
      return;
    }
    EMPTY_FOR.remove(id);
    restore(player, container, waited);
  }

  private static void restore(ServerPlayer player, IOriginContainer container, int waited) {

    // 入室時（OriginsEventHandler.onLogin）と同じ呼び出し・同じ順。
    container.checkAutoChoosingLayers(true);
    OriginsCommon.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player),
        container.getSynchronizationPacket());
    container.synchronize();

    if (!container.hasAllOrigins()) {
      GAVE_UP.add(player.getUUID());
      LOG.error("[shiftingorigins] {} の層が空のまま埋められなかった（{} tick 待った）。"
              + "層の default_origin を確かめる。以後この接続では試さない",
          player.getGameProfile().getName(), waited);
      return;
    }

    // ⚠ **true を渡す。**「新しく湧いたのではない」の意味。false だと
    //    origins:modify_player_spawn を持つ種族で**湧き場所へ飛ばされる**
    //    （ModifyPlayerSpawnPowerMixin.onChosen は `if (!b) teleportToModifiedSpawn(...)`）。
    //    入室処理が false を渡すのは、あちらが本当に「初めて湧く」場面だから。
    container.onChosen(true);

    LOG.warn("[shiftingorigins] {} の層が空のまま {} tick 残っていたので既定の種族を入れた"
            + "（空のあいだは全ダメージ無効＝詰むため）",
        player.getGameProfile().getName(), waited);
    player.sendSystemMessage(Component.literal(
            "[部] 種族が未選択のままだったので既定に戻しました。選び直すには珠を使ってください。")
        .withStyle(ChatFormatting.GRAY));
  }

  /** 退場したら控えを捨てる（入り直した人に前回の猶予や諦めを持ち込まない）。 */
  @SubscribeEvent
  public static void onLoggedOut(final PlayerEvent.PlayerLoggedOutEvent event) {
    EMPTY_FOR.remove(event.getEntity().getUUID());
    GAVE_UP.remove(event.getEntity().getUUID());
  }
}
