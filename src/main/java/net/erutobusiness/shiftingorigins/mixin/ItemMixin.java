package net.erutobusiness.shiftingorigins.mixin;

import net.erutobusiness.shiftingorigins.LavaBottle;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * {@code alexsmobs:lava_bottle} を<b>本物の飲み物にする</b>（ブレイズボーンだけ）。
 *
 * <h2>なぜ Mixin なのか</h2>
 *
 * ⚠ 最初は {@code PlayerInteractEvent.RightClickItem} で拾っていたが、
 * <b>啜るモーションが出なかった</b>。構えが出る条件は
 * {@code Item.getUseAnimation} が {@code DRINK} を返し、
 * {@code getUseDuration} が 0 より大きいことで、
 * ⚠ <b>あの瓶は {@code FoodProperties} を持たない素の {@code Item}</b> なので
 * どちらも既定のまま（{@code NONE} と 0）だった。
 *
 * <p>⚠ <b>相手が {@code Item} のインスタンスそのもの</b>（専用のクラスを持たない）ので、
 * 特定のクラスを狙えない。だから<b>全アイテムの親に差し込んで id で弾く</b>。
 *
 * <h2>⚠ 全アイテムに触れることへの手当て</h2>
 *
 * 4か所とも<b>最初に {@link LavaBottle#is} を通す</b>。
 * 中身は「持ち物が空か」と「registry の id が一致するか」だけなので、
 * 他のアイテムはそこで抜ける。
 *
 * <h2>差し込む4か所</h2>
 *
 * <table><caption></caption>
 *   <tr><td>{@code getUseAnimation}</td><td>{@code DRINK}（構えが出る）</td></tr>
 *   <tr><td>{@code getUseDuration}</td><td>32（バニラの飲み物と同じ）</td></tr>
 *   <tr><td>{@code use}</td><td>⚠ <b>ブレイズボーンのときだけ</b>飲み始める</td></tr>
 *   <tr><td>{@code finishUsingItem}</td><td>飲み終わりに渇きを回復し、ガラス瓶を返す</td></tr>
 * </table>
 *
 * <p>⚠ 姿と長さ（前の2つ）は<b>誰に対しても返している</b>。
 * それだけでは何も起きない——{@code use} が飲み始めなければ構えに入らないため。
 * ⚠ こうしておくと、飲んでいる最中に種族が変わっても長さが 0 に化けない。
 */
@Mixin(Item.class)
public abstract class ItemMixin {

  @Inject(method = "getUseAnimation", at = @At("HEAD"), cancellable = true)
  private void shiftingorigins$drinkAnim(ItemStack stack, CallbackInfoReturnable<UseAnim> cir) {
    if (LavaBottle.is(stack)) {
      cir.setReturnValue(UseAnim.DRINK);
    }
  }

  @Inject(method = "getUseDuration", at = @At("HEAD"), cancellable = true)
  private void shiftingorigins$drinkTime(ItemStack stack, CallbackInfoReturnable<Integer> cir) {
    if (LavaBottle.is(stack)) {
      cir.setReturnValue(LavaBottle.USE_DURATION);
    }
  }

  @Inject(method = "use", at = @At("HEAD"), cancellable = true)
  private void shiftingorigins$startDrink(Level level, Player player, InteractionHand hand,
                                          CallbackInfoReturnable<InteractionResultHolder<ItemStack>> cir) {
    ItemStack stack = player.getItemInHand(hand);
    if (LavaBottle.is(stack) && LavaBottle.canDrink(player)) {
      cir.setReturnValue(ItemUtils.startUsingInstantly(level, player, hand));
    }
  }

  /**
   * 飲み終わり。
   *
   * <p>⚠ <b>返し方はバニラのポーションに合わせてある</b>——
   * 減らして空になったらガラス瓶そのものを返し、まだ残っていれば
   * 持ち物へ足す（入らなければ足元へ落とす）。
   */
  @Inject(method = "finishUsingItem", at = @At("HEAD"), cancellable = true)
  private void shiftingorigins$finishDrink(ItemStack stack, Level level, LivingEntity entity,
                                           CallbackInfoReturnable<ItemStack> cir) {
    if (!LavaBottle.is(stack) || !(entity instanceof Player player)) {
      return;
    }
    if (!level.isClientSide()) {
      LavaBottle.drink(player);
    }
    if (!player.getAbilities().instabuild) {
      stack.shrink(1);
    }
    ItemStack bottle = new ItemStack(Items.GLASS_BOTTLE);
    if (stack.isEmpty()) {
      cir.setReturnValue(bottle);
      return;
    }
    if (!player.getInventory().add(bottle)) {
      player.drop(bottle, false);
    }
    cir.setReturnValue(stack);
  }
}
