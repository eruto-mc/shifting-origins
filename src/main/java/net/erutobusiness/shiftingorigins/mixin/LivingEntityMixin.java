package net.erutobusiness.shiftingorigins.mixin;

import net.erutobusiness.shiftingorigins.CookMark;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 出口④: <b>手に持っている物と入れ替える</b>。
 *
 * <p>バケツ・水筒・ボウルのように「使ったら中身入りに変わる」台がこの形。
 * ⚠ 当部だと Tough as Nails の水筒、Let's Do の鍋・ボウル系がここを通る。
 *
 * <p>⚠ <b>「直前に触った台のそば」だけ</b>に絞る。そうしないと、
 * 持ち替えただけで印が付く。
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

  @Inject(method = "setItemInHand", at = @At("HEAD"))
  private void shiftingorigins$markInHand(InteractionHand hand, ItemStack stack, CallbackInfo ci) {
    if (!((Object) this instanceof ServerPlayer sp) || stack.isEmpty() || !stack.isEdible()) {
      return;
    }
    if (!CookMark.nearLastUse(sp, sp.getX(), sp.getY(), sp.getZ())) {
      return;
    }
    CookMark.stamp(sp, stack);
  }
}
