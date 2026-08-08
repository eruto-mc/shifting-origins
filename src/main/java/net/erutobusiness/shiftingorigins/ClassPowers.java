package net.erutobusiness.shiftingorigins;

import io.github.edwinmindcraft.apoli.api.component.IPowerContainer;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 職業ごとの能力を**このMODから直接配る**（2026-08-04）。
 *
 * <p>なぜ datapack でやらないか: 当部は `origins_unlock` datapack で
 * `data/origins-classes/origins/<職業>.json` を上書きして能力を差し替えていたが、
 * **実測したら1つも効いていなかった**。datapack 自体は有効
 * （`/datapack list enabled` に `file/origins_unlock…（ワールド）` が出る）なのに、
 * 職業の中身だけ上流の定義が使われる。
 *
 * <pre>
 *   鉱夫   期待: 上流2つ ＋ world3:vein_mining   実際: 上流2つだけ
 *   探索者 期待: world3:keen_eye ＋ 疾走         実際: explorer_kit ＋ 疾走
 * </pre>
 *
 * <p>⚠ 探索者の `explorer_kit`（開始装備）は当部が**意図的に外したはずのもの**。
 * 職業を付け替えるたびに地図が9枚増えるため、常時効果へ差し替える決定を
 * 2026-08-02 にしていた。それが効いていなかった。
 *
 * <p>ここでは**上流の定義を書き換えず、足す/外すだけ**を行う。上流が職業の中身を変えても、
 * こちらは「その職業のときに何を足すか」しか持たないので黙って壊れにくい。
 */
public final class ClassPowers {

  private static final Logger LOG = LoggerFactory.getLogger("shiftingorigins");
  /** 誰が配ったかの印。これを source にしておくと、後から自分のぶんだけ外せる。 */
  private static final ResourceLocation SOURCE =
      new ResourceLocation(ShiftingOrigins.MOD_ID, "class_powers");

  /**
   * (種族・職業, 足す能力)
   *
   * <p>⚠ **「外す」は 2026-08-04 に廃止した。** `removePower` は毎秒呼べていて source も
   * 正しかったのに `power has` が真のままだった＝**上流が付け直していて勝てない**
   * （台本 shifting-origins の逆向き判定3本が2回とも NG）。
   * いまは**上流の power の定義そのものを無害な内容で上書きする**
   * （`data/origins/powers/*.json` に `loading_priority: 100`）。
   * これは Origins の公式ドキュメントが案内している正規のやり方:
   * 「Higher numbers mean it's loaded later, which means it will override those with
   * lower loading priorities which share the same ID」（既定MODの値は 0）。
   */
  private record Rule(String origin, List<String> add) {
  }

  private static final List<Rule> RULES = List.of(
      new Rule("origins-classes:miner",
          List.of("shiftingorigins:vein_mine")),
      new Rule("origins-classes:explorer",
          List.of("shiftingorigins:keen_eye", "shiftingorigins:tireless")),
      new Rule("origins-classes:cleric",
          List.of("shiftingorigins:potion_sharing")));

  private ClassPowers() {
  }

  @SubscribeEvent
  public static void onPlayerTick(final TickEvent.PlayerTickEvent event) {

    // 1秒に1回だけ見る（毎tickだと無駄。職業の変更は稀）
    if (event.phase != TickEvent.Phase.END
        || !(event.player instanceof ServerPlayer player)
        || player.tickCount % 20 != 0) {
      return;
    }
    IPowerContainer.get(player).ifPresent(container -> apply(player, container));
  }

  private static void apply(ServerPlayer player, IPowerContainer container) {

    for (Rule rule : RULES) {
      boolean isThisClass = hasOrigin(player, rule.origin());

      for (String id : rule.add()) {
        ResourceLocation power = new ResourceLocation(id);
        boolean has = container.hasPower(power, SOURCE);

        if (isThisClass && !has) {
          container.addPower(power, SOURCE);
          LOG.info("[paced] {} に {} を配った（{}）", player.getGameProfile().getName(), id,
              rule.origin());
        } else if (!isThisClass && has) {
          // 自分が配ったぶん（source=SOURCE）は自分で外せる。上流が配ったものとは別。
          container.removePower(power, SOURCE);
        }
      }
    }
  }

  /**
   * そのプレイヤーが料理人か。
   *
   * <p>⚠ **判定を2か所に書かない。** 種族・職業の読み出しは下の {@code hasOrigin} が正で、
   * ここはその呼び出し口にすぎない（2026-08-09 追加）。
   */
  public static boolean isCook(ServerPlayer player) {
    return hasOrigin(player, "origins-classes:cook");
  }

  /** そのプレイヤーが指定の種族／職業を選んでいるか（層は問わない）。 */
  private static boolean hasOrigin(ServerPlayer player, String originId) {
    return io.github.edwinmindcraft.origins.api.capabilities.IOriginContainer.get(player)
        .map(c -> c.getOrigins().values().stream()
            .anyMatch(key -> key != null && key.location().toString().equals(originId)))
        .orElse(false);
  }
}
