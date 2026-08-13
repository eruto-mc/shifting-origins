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
    // ⚠⚠ **押し直しの猶予を食い潰さないように、先に「勢い」を1つ戻す**（2026-08-13 追加）。
    //
    // 実測で分かったこと（台本 cook-noui）: `farm_and_charm` の混ぜ鉢・ミンサーは
    //   use  … 勢い（stirring / crank）を 10 に立てる。勢い > 6 の間は押しても無視
    //   tick … 勢い > 0 なら累計 +1、勢い -1。**勢いが 0 になると累計が 0 に戻る**
    // という作りなので、もう1回 tick を回すと**累計も勢いも2倍で動く**。
    // 結果、押し直しの窓が 4〜9 → **2〜4 ティック**に狭まり、
    // **「速くなる」はずの料理人が、画面の無い台では一番不利**になっていた
    // （待ち8で対照は stirred=50 に到達、料理人は 0 のまま。待ち4でも 40 まで行って切れた）。
    //
    // だから**余分な tick の前に勢いを1つ戻す**。こうすると
    //   累計 … 1ゲームtickに 2 進む（狙いどおり速い）
    //   勢い … 1ゲームtickに 1 減る（＝素と同じ。窓が狭まらない）
    // ⚠ 上限に張り付いているときは戻せないので、そのぶんは素と同じ速さになる（害は無い）。
    CookSpeed.keepMomentum(this.blockEntity);
    this.ticker.tick(this.blockEntity.getLevel(), this.blockEntity.getBlockPos(),
        this.blockEntity.getBlockState(), this.blockEntity);
  }
}
