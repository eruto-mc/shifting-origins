package net.erutobusiness.shiftingorigins;

import io.github.edwinmindcraft.origins.api.capabilities.IOriginContainer;
import io.github.edwinmindcraft.origins.api.origin.Origin;
import io.github.edwinmindcraft.origins.api.origin.OriginLayer;
import io.github.edwinmindcraft.origins.common.OriginsCommon;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 珠（Orb of Origin）で開いた選択画面を**やめられる**ようにする、サーバー側の受け口。
 *
 * <p>なぜ要るか（2026-08-07 部長要望）: 珠は使った時点で消え、選択画面は
 * {@code ChooseOriginScreen.shouldCloseOnEsc()} が {@code false} 固定なので閉じられない。
 * 「解禁済みの種族の能力を見比べたいだけ」でも珠が1つ失われる。
 *
 * <p>どうやるか:
 *
 * <pre>
 *   珠を使う  ─ Mixin(HEAD) ─→ remember(): いまの種族一覧と珠を控える
 *              ─ 上流の処理 ─→ 層を空にして選択画面を開かせる
 *              ─ Mixin(RETURN) → announce(): クライアントへ「やめられる」と伝える
 *   Esc / ×   ────────────→ CancelRequest ─→ cancel(): 控えた種族へ戻し、珠を返す
 * </pre>
 *
 * <p>⚠ **控えは記憶の上だけ**（サーバーを落とすと消える）。落ちたあとに入り直すと
 * 選択画面はまた開くが、やめられない＝選ぶしかない。珠を消したまま何も選べない状態には
 * ならないので、これで安全側に倒れている。
 *
 * <p>⚠ **戻す前に「もう選び終えていないか」を見る。** 選び終えていれば層は
 * {@code origins:empty} ではないので、そこには触らないし珠も返さない。
 * 便りが二重に届いても種族が巻き戻らない。
 */
public final class OriginChangeCancel {

  private static final Logger LOG = LoggerFactory.getLogger("shiftingorigins");

  /** 「その層は未選択」を表す種族。珠はこれを書き込んで層を空にする。 */
  private static final ResourceLocation EMPTY_ORIGIN = new ResourceLocation("origins", "empty");

  private static final Map<UUID, Pending> PENDING = new ConcurrentHashMap<>();

  private record Pending(Map<ResourceKey<OriginLayer>, ResourceKey<Origin>> before,
                         ItemStack orb, boolean consumed) {
  }

  private OriginChangeCancel() {
  }

  /** 珠を使う**直前**に呼ぶ（層が空にされる前でないと意味が無い）。 */
  public static void remember(ServerPlayer player, ItemStack orb, boolean consumed) {
    ItemStack one = orb.copy();
    one.setCount(1);
    IOriginContainer.get(player).ifPresent(container -> PENDING.put(
        player.getUUID(),
        new Pending(new HashMap<>(container.getOrigins()), one, consumed)));
  }

  /** 選択画面を開かせた**あと**に呼ぶ。クライアントの Esc と × を有効にする。 */
  public static void announce(ServerPlayer player) {
    if (PENDING.containsKey(player.getUUID())) {
      Net.toPlayer(player, new Net.CancelAllowed(true));
    }
  }

  /** 「やめる」が届いたとき。控えた種族へ戻し、珠を返す。 */
  public static void cancel(ServerPlayer player) {
    Pending pending = PENDING.remove(player.getUUID());
    if (pending == null) {
      // 控えが無い＝珠で開いた画面ではない。ここで層をいじると詰むので何もしない。
      return;
    }
    IOriginContainer.get(player).ifPresent(container -> {
      int restored = 0;
      for (Map.Entry<ResourceKey<OriginLayer>, ResourceKey<Origin>> e : pending.before().entrySet()) {
        ResourceKey<Origin> before = e.getValue();
        ResourceKey<Origin> now = container.getOrigin(e.getKey());
        if (before == null || now == null || !EMPTY_ORIGIN.equals(now.location())) {
          // 空になっていない層＝この珠が触っていないか、もう選び終えている
          continue;
        }
        container.setOrigin(e.getKey(), before);
        restored++;
      }
      if (restored == 0) {
        return;
      }
      // 上流の珠と同じ順で伝える（同期の便り → 容器の同期）
      OriginsCommon.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
          container.getSynchronizationPacket());
      container.synchronize();
      if (pending.consumed()) {
        giveBack(player, pending.orb());
      }
      LOG.info("[shiftingorigins] {} が種族の選び直しをやめた（{} 層を戻した）",
          player.getGameProfile().getName(), restored);
    });
  }

  private static void giveBack(ServerPlayer player, ItemStack orb) {
    if (!player.getInventory().add(orb)) {
      player.drop(orb, false);
    }
  }

  /**
   * 退場したら控えを捨てる。
   *
   * <p>⚠ 捨てないと、入り直したあとに残った控えで**古い種族へ戻せてしまう**。
   * クライアント側の「やめられる」印も接続ごとに消えるので、両側で揃う。
   */
  @SubscribeEvent
  public static void onLoggedOut(final PlayerEvent.PlayerLoggedOutEvent event) {
    PENDING.remove(event.getEntity().getUUID());
  }
}
