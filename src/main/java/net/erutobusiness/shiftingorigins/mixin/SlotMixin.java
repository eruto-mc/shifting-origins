package net.erutobusiness.shiftingorigins.mixin;

import net.erutobusiness.shiftingorigins.CookMark;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 出口①: <b>画面のある台から取り出す</b>。
 *
 * <p>⚠ <b>{@code Slot} を対象にすると、すべての画面のすべての枠に当たる。</b>
 * 上流のように台ごとに当てる必要が無くなる代わりに、
 * <b>チェストから出した食べ物にも当たってしまう</b>ので、
 * 「<b>物を置けない枠</b>」（{@code mayPlace} が偽）に絞る——それが産物の枠の印。
 *
 * <p>⚠ {@code SlotItemHandler}（Forge の capability 用）も {@code Slot} の子なので、
 * ここ1本で拾える。
 */
@Mixin(Slot.class)
public abstract class SlotMixin {

  @Inject(method = "onTake", at = @At("HEAD"))
  private void shiftingorigins$markCooked(Player player, ItemStack stack, CallbackInfo ci) {
    if (!(player instanceof ServerPlayer sp) || stack.isEmpty()) {
      return;
    }
    // ⚠ 置ける枠＝ふつうの収納。産物の枠は置けない
    if (((Slot) (Object) this).mayPlace(stack)) {
      return;
    }
    CookMark.stamp(sp, stack);
  }
}
