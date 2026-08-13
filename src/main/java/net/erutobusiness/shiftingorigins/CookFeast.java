package net.erutobusiness.shiftingorigins;

import dev.limonblaze.originsclasses.util.CommonUtils;
import io.github.edwinmindcraft.apoli.api.ApoliAPI;
import io.github.edwinmindcraft.apoli.api.power.configuration.ConfiguredPower;
import io.github.edwinmindcraft.apoli.common.power.ModifyFoodPower;
import io.github.edwinmindcraft.apoli.common.power.configuration.ModifyFoodConfiguration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.MappedRegistry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 印の付いた料理を食べたときの<b>効果</b>（{@code entity_action}）をここで出す。
 *
 * <p>⚠⚠ <b>なぜ apoli に任せないのか</b>（2026-08-09 の調査。ここを読まずに戻さないこと）:
 *
 * <p>apoli は {@code FoodData#eat} の中、<b>{@code add}（満腹度を足す）の直後</b>で
 * {@code entity_action} を走らせる。ところが<b>他のMODがその手前で {@code eat} を取り消す</b>:
 *
 * <ul>
 *   <li><b>Salt</b> … 塩を振った料理を食べたとき（{@code add} の直前で取り消す）</li>
 *   <li><b>Alex's Caves</b> … 生肉タグ＋原始の防具を着ているとき（入口で取り消す）</li>
 * </ul>
 *
 * <p>どちらも「自分で満腹度を足して {@code ci.cancel()}」という<b>相手にとっては正しい実装</b>で、
 * 取り消しは<b>そこで {@code eat} から抜ける</b>ので、注入の順番を変えても届かない。
 * ⚠ <b>エラーもログも出ない。効かなくなるだけ。</b>
 * 誰が取り消しうるかは作者環境の検査（{@code check_eat_path}・この repo には無い）が数える。
 *
 * <p>⚠ 取り消されても<b>満腹度 +25% は生きている</b>——相手は取り消す前に
 * {@code getFoodProperties} を呼び直しており、そこに apoli の coremod が差し込まれているため。
 * <b>失われるのは {@code entity_action} だけ</b>だった。
 *
 * <p><b>そこで出す場所を変えた。</b> {@code LivingEntityUseItemEvent.Finish} は
 * {@code LivingEntity#completeUsingItem} で発火し、<b>{@code FoodData#eat} へ入る前</b>に通る。
 * 誰が {@code eat} を取り消しても関係なく<b>1回だけ</b>呼ばれる。
 *
 * <p>⚠ <b>中身は自作しない。</b> 印の読み取りも効果の実行も上流のまま
 * （{@code ModifyFoodPower.getValidPowers} → {@code execute}）。
 * 当部が決めているのは<b>いつ呼ぶか</b>だけ。
 *
 * <p>⚠⚠ <b>取り消されなかった料理では、apoli と当部の両方が走る</b>（＝2回走る）。
 * これは承知のうえ。{@code apoli:apply_effect} は<b>同じ効果を同じ長さで入れ直すだけ</b>なので
 * 結果は変わらない（バニラの {@code addEffect} が上書きする）。
 * ⚠ <b>だから datapack の {@code entity_action} には
 * 「2回走っても結果が変わらない動作」しか書かないこと。</b>
 * ダメージ・経験値・アイテム付与のような<b>積み上がるもの</b>を書くと2倍になる。
 *
 * <p>⚠ 「片方だけ走らせる」形にはできない。取り消されるかどうかは
 * <b>{@code eat} へ入ってみるまで分からない</b>ので、入る前に居るこちらからは判定できない。
 */
@Mod.EventBusSubscriber(modid = ShiftingOrigins.MOD_ID)
public final class CookFeast {

  private CookFeast() {
  }

  @SubscribeEvent
  public static void onFinishUsing(final LivingEntityUseItemEvent.Finish event) {
    if (!(event.getEntity() instanceof ServerPlayer player)) {
      return;
    }
    ItemStack stack = event.getItem();
    if (stack.isEmpty()) {
      return;
    }
    // ⚠ **印の付いた物だけを見る。** Finish は水入り瓶や道具の使用でも発火する
    Set<String> marked = markedPowerIds(stack);
    if (marked.isEmpty()) {
      return;
    }
    // ⚠ **印から足された分だけを走らせる。** getValidPowers は
    //    「プレイヤーが自分で持っている modify_food」も返すので、そちらは apoli に任せる
    //    （両方走らせると、将来そういう power を足したときに二重になる）
    //
    // ⚠ **power から ID を引かない。** `ConfiguredPower#getRegistryName` を呼ぶと
    //    calio の `DynamicRegistryListener` までコンパイル時に要求される（依存が1本増える）。
    //    **ID から power を引く向きにすれば、レジストリの参照だけで済む。**
    //    印が足す power は Origins Classes 側も同じレジストリから引いているので、
    //    同じ実体が返る＝ `contains` で照合できる。
    MappedRegistry<ConfiguredPower<?, ?>> registry = ApoliAPI.getPowers();
    Set<ConfiguredPower<?, ?>> markedPowers = new HashSet<>();
    for (String id : marked) {
      ConfiguredPower<?, ?> power = registry.get(new ResourceLocation(id));
      if (power != null) {
        markedPowers.add(power);
      }
    }
    if (markedPowers.isEmpty()) {
      return;
    }
    List<ConfiguredPower<ModifyFoodConfiguration, ModifyFoodPower>> fromMark = new ArrayList<>();
    for (ConfiguredPower<ModifyFoodConfiguration, ModifyFoodPower> power
        : ModifyFoodPower.getValidPowers(player, player.level(), stack)) {
      if (markedPowers.contains(power)) {
        fromMark.add(power);
      }
    }
    if (fromMark.isEmpty()) {
      return;
    }
    // ⚠ item_condition の照合は execute の中で上流がやる
    ModifyFoodPower.execute(fromMark, player, player.level(), stack);
  }

  /** 印（{@code OriginsClasses.ModifyFoodPowers}）に並んでいる power の ID */
  private static Set<String> markedPowerIds(ItemStack stack) {
    CompoundTag tag = stack.getTag();
    if (tag == null) {
      return Set.of();
    }
    CompoundTag ours = CommonUtils.getOriginsClassesTag(tag);
    if (!ours.contains(CommonUtils.MODIFY_FOOD_POWERS, Tag.TAG_LIST)) {
      return Set.of();
    }
    ListTag ids = ours.getList(CommonUtils.MODIFY_FOOD_POWERS, Tag.TAG_STRING);
    Set<String> out = new HashSet<>();
    for (int i = 0; i < ids.size(); i++) {
      out.add(ids.getString(i));
    }
    return out;
  }
}
