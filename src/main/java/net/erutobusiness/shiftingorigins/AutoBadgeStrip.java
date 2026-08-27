package net.erutobusiness.shiftingorigins;

import io.github.apace100.origins.badge.Badge;
import io.github.edwinmindcraft.origins.api.event.AutoBadgeEvent;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Origins が能力名の横へ自動で足す印（A / T / R）を、画面へ出る前に取り除く。
 *
 * <p>⚠⚠ <b>なぜ要るか（2026-08-27・あなたの指摘）</b>: あの 9×9 の絵は、
 * 中の文字が何の頭文字かを画面のどこにも書いていない（A=active ／ T=toggle ／
 * R=recipe）。部員に「A や T が分からない」と言われたので、当部は代わりに
 * <b>押すキーそのものを描いた印（G＝主 ／ H＝副）</b>を power ごとに足した。
 * ⚠ <b>自動の印はその隣に並んだままで、読めない絵が2つ並ぶ形</b>になっていた。
 *
 * <p>⚠⚠ <b>datapack では消せない。</b>{@code BadgeManager.createAutoBadges} を
 * 逆アセンブルして確かめた実物の形:
 *
 * <pre>
 * if (発動する能力) {
 *     if (REGISTRY.containsId(ACTIVE/TOGGLE)) badges.add(datapack の badge);
 *     else                                    badges.add(内蔵の絵の KeybindBadge);
 * } else if (レシピの能力) {
 *     badges.add(new CraftingRecipeBadge(RECIPE_BADGE_SPRITE, …));
 * }
 * </pre>
 *
 * <p>⚠ <b>datapack の JSON を消すと「内蔵の絵」に落ちるだけ</b>で、印は必ず足される。
 * ⚠ レシピの印には datapack の口がそもそも無い。
 * ⚠ 当部は 2026-08-27 に <b>絵を透明にして見えなくした</b>が、
 * <b>枠のぶんの隙間は残っていた</b>（消えていない）。
 *
 * <p><b>だからイベントで外す。</b>{@code AutoBadgeEvent} が持つ一覧は書き換えられ、
 * ⚠ <b>格納（{@code putPowerBadge}）は全部の post が済んだあと</b>なので、
 * ⚠ <b>いちばん後ろの順位（LOWEST）で消せば間に合う</b>（同じく逆アセンブルで確認）。
 *
 * <p>⚠ <b>絵で見分ける。</b>種類（クラス）では分けられない——内蔵の A / T は
 * 当部の G / H と同じ {@code KeybindBadge} だから。
 * ⚠ 外すのは下の4つだけなので、<b>当部の G / H（key_g / key_h）も、
 * MOD が power ごとに付けている印（ピクシーの1件）も残る</b>。
 *
 * <p>⚠ 文（ツールチップ）ごと消える点は承知のうえ。押せることは当部の G / H の印と、
 * 説明文の「主のキー（G）で発動」で伝わる。
 */
public final class AutoBadgeStrip {

  private AutoBadgeStrip() {
  }

  /**
   * 外す印の絵。⚠ <b>自動で足されるものだけ</b>を名指しする。
   *
   * <ul>
   *   <li>{@code origins:…/active.png} {@code toggle.png} … datapack を置かないときに内蔵から出る絵
   *   <li>{@code origins:…/recipe.png} … レシピの印（datapack の口が無い）
   *   <li>{@code world3:…/blank.png} … 当部が 2026-08-27 に透明へ差し替えた絵
   *       （`origins_setup` の `blank_auto_badges()`）。⚠ <b>差し替えをやめても
   *       上の origins: 側で拾える</b>ので、どちらの道でも消える
   * </ul>
   */
  private static final Set<ResourceLocation> DROP = Set.of(
      new ResourceLocation("origins", "textures/gui/badge/active.png"),
      new ResourceLocation("origins", "textures/gui/badge/toggle.png"),
      new ResourceLocation("origins", "textures/gui/badge/recipe.png"),
      new ResourceLocation("world3", "textures/gui/badge/blank.png"));

  /** ⚠ 順位は LOWEST。Origins 自身が HIGH と LOW で足すので、その後ろで消す。 */
  @SubscribeEvent(priority = EventPriority.LOWEST)
  public static void strip(AutoBadgeEvent event) {
    event.getBadges().removeIf(AutoBadgeStrip::isAuto);
  }

  private static boolean isAuto(Badge badge) {
    if (badge == null) {
      return true;
    }
    ResourceLocation sprite = badge.spriteId();
    return sprite != null && DROP.contains(sprite);
  }
}
