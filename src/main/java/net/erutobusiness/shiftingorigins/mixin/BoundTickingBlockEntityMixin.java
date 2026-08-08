package net.erutobusiness.shiftingorigins.mixin;

import net.erutobusiness.shiftingorigins.CookSpeed;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 料理人が近くに居る調理台を<b>2倍で動かす</b>。
 *
 * <p>⚠ <b>ここは世界のすべてのブロックエンティティが通る1か所</b>なので、
 * 台ごとの進行フィールドを知らなくても速くできる。代わりに、
 * <b>誰を速くするかの判定を間違えると世界中が壊れる</b>。
 * 判定は {@link CookSpeed}（許可リスト方式）に一本化してある。
 *
 * <p>⚠ <b>TAIL に差し込んで ticker をもう1回呼ぶ。</b> 元の {@code tick()} を
 * 再帰で呼ばない——除去済み・チャンク未読込などの見張りを二重に通すと、
 * 条件が変わった隙に例外へ落ちる。
 */
@Mixin(targets = "net.minecraft.world.level.chunk.LevelChunk$BoundTickingBlockEntity")
public abstract class BoundTickingBlockEntityMixin<T extends BlockEntity> {

  @Shadow
  @Final
  private T blockEntity;

  @Shadow
  @Final
  private BlockEntityTicker<T> ticker;

  @Inject(method = "tick", at = @At("TAIL"))
  private void shiftingorigins$cookSpeed(CallbackInfo ci) {
    if (!CookSpeed.shouldSpeedUp(this.blockEntity)) {
      return;
    }
    if (this.blockEntity.isRemoved() || !this.blockEntity.hasLevel()) {
      return;
    }
    this.ticker.tick(this.blockEntity.getLevel(), this.blockEntity.getBlockPos(),
        this.blockEntity.getBlockState(), this.blockEntity);
  }
}
