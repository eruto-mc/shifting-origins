package net.erutobusiness.pacedmultimine.mixin;

import java.util.List;
import net.erutobusiness.pacedmultimine.PacedBreakQueue;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Origins Classes の一括破壊 power が「壊すブロックの一覧」を返すところを横取りする。
 *
 * <p>相手（{@code ServerPlayerGameModeMixin}）はこの戻り値をそのまま回して**同じ tick で全部壊す**。
 * こちらが空の一覧を返せば相手は何もしない。実際の破壊は
 * {@link PacedBreakQueue} が数tickかけて行う。
 *
 * <p>⚠ クラスは名前（文字列）で指定する。Origins Classes の jar をコンパイル時に見なくてよく、
 * 相手の版が上がってもクラス名が変わらない限り当たり続ける。
 * ⚠ {@code remap = false} が要る。{@code apply} は相手のMODが持つ名前で、
 * バニラのメソッド名の置き換え表（SRG）には載っていない。
 */
@Mixin(targets = "dev.limonblaze.originsclasses.common.apoli.power.MultiMinePower", remap = false)
public abstract class MultiMinePowerMixin {

  @Inject(method = "apply", at = @At("RETURN"), cancellable = true, remap = false)
  private static void pacedmultimine$queueInsteadOfBreakingNow(
      Player player, BlockPos pos, BlockState state,
      CallbackInfoReturnable<List<BlockPos>> cir) {
    List<BlockPos> positions = cir.getReturnValue();

    if (positions == null || positions.isEmpty()) {
      return;
    }

    if (PacedBreakQueue.enqueue(player, positions)) {
      cir.setReturnValue(List.of());
    }
  }
}
