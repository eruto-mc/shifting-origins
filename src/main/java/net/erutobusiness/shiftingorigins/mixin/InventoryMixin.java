package net.erutobusiness.shiftingorigins.mixin;

import net.erutobusiness.shiftingorigins.CookMark;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 出口②③: <b>持ち物へ入れる</b>／<b>枠へ直に代入する</b>。
 *
 * <p>⚠ {@code Player#addItem} と Forge の {@code ItemHandlerHelper.giveItemToPlayer} は
 * どちらも {@code Inventory#add} を呼ぶので、ここ1本で両方を拾える
 * （2026-08-09 に Sushi Go Crafting を追ったときに分かった。
 * 名前だけで数えると、間に1枚挟まっただけで見失う）。
 *
 * <p>⚠ <b>「直前に触った台のそばに居るとき」だけ印を押す。</b>
 * そうしないと、チェストから出した食べ物にも付いてしまう。
 */
@Mixin(Inventory.class)
public abstract class InventoryMixin {

  @Shadow
  public Player player;

  @Inject(method = "add(Lnet/minecraft/world/item/ItemStack;)Z", at = @At("HEAD"))
  private void shiftingorigins$markAdded(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
    mark(stack);
  }

  @Inject(method = "setItem", at = @At("HEAD"))
  private void shiftingorigins$markSet(int slot, ItemStack stack, CallbackInfo ci) {
    mark(stack);
  }

  private void mark(ItemStack stack) {
    if (!(this.player instanceof ServerPlayer sp) || stack.isEmpty() || !stack.isEdible()) {
      return;
    }
    if (!CookMark.nearLastUse(sp, sp.getX(), sp.getY(), sp.getZ())) {
      return;
    }
    CookMark.stamp(sp, stack);
  }
}
