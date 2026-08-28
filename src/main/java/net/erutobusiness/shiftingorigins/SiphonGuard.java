package net.erutobusiness.shiftingorigins;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.MobType;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * アンデッド（種族）の <b>精気吸収がアンデッドから吸えてしまう</b>のを止める。
 *
 * <h2>何が壊れていたか</h2>
 *
 * 上流（Medieval Origins Revival）の説明はこう書いている:
 *
 * <pre>"This affects all creatures for better or worse, except summoned dead."</pre>
 *
 * ⚠ <b>実装に除外が1つも無い。</b> 3か所を開いて確かめた:
 *
 * <table><caption></caption>
 *   <tr><td>{@code revenant/siphon.json}（7,538バイト）</td>
 *       <td>⚠ 対象を絞る条件が無い。{@code bientity_condition} は無く、
 *           唯一の {@code apoli:in_tag} は<b>ダメージの種類</b>に掛かっていて、
 *           自分の吸収ダメージで再発火しないためのもの</td></tr>
 *   <tr><td>{@code SummonedSkeleton.class}</td>
 *       <td>⚠ {@code siphon} の参照 0 件・{@code isInvulnerableTo} の上書き無し</td></tr>
 *   <tr><td>{@code SpellDamageAction.class}</td>
 *       <td>⚠ 召喚の判定 0 件（{@code instanceof} は {@code LivingEntity} だけ）</td></tr>
 * </table>
 *
 * <p>実害は<b>回復し放題</b>——自分で呼んだゾンビを攻撃し続ければ体力が戻り続ける。
 *
 * <h2>なぜ datapack でやらないか</h2>
 *
 * ⚠ 条件そのものは data 側にも在る（{@code apoli:entity_group}）。
 * ただし当てるには<b>上流の power を丸ごと写して置き直す</b>ことになり、
 * 7,538バイトを当部が抱えて上流の変更に追随できなくなる。
 * 当部の方針は「<b>上流の定義は書き換えない（足す／外すだけ）</b>」なので、
 * ⚠ <b>ここでは damage を止める側に置いた</b>（2026-08-28・あなたの判断）。
 *
 * <h2>やり方</h2>
 *
 * {@code LivingAttackEvent}（<b>ダメージが入る前</b>）で、
 * ダメージの種類が {@code medievalorigins:siphon} かつ相手が
 * {@code MobType.UNDEAD} なら取り消す。
 *
 * <p>⚠ <b>召喚した分だけでなくアンデッド全体</b>を対象にしている（あなたの判断）。
 * 死んでいるものから生命力は吸えない、という筋のほう。
 *
 * <p>⚠ <b>回復も一緒に止まる。</b> 上流は「吸収ダメージが通ったか」を見てから
 * 回復させる形（{@code if_else_list} の中で {@code medievalorigins:spell_damage} を判定）なので、
 * ダメージが入らなければ回復の枝へ進まない。⚠ <b>実機で確かめること</b>——
 * ここは bytecode の読みからの見立てで、実測ではない。
 */
public final class SiphonGuard {

  /** ⚠ 上流の damage_type（`data/medievalorigins/damage_type/siphon.json`）。 */
  private static final ResourceKey<DamageType> SIPHON = ResourceKey.create(
      Registries.DAMAGE_TYPE, new ResourceLocation("medievalorigins", "siphon"));

  private SiphonGuard() {
  }

  @SubscribeEvent
  public static void onAttack(LivingAttackEvent event) {
    if (!ShiftingOrigins.Config.SIPHON_SKIP_UNDEAD.get()) {
      return;
    }
    if (!event.getSource().is(SIPHON)) {
      return;
    }
    if (event.getEntity().getMobType() == MobType.UNDEAD) {
      event.setCanceled(true);
    }
  }
}
