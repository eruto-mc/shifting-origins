package net.erutobusiness.shiftingorigins;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 鉱脈の探索。Origins Classes の木こり用の探索（{@code CommonUtils.lumberjackMultiMineRange}）は
 * **木にしか使えない**ので、鉱石用にこちらを用意する。
 *
 * <p>木こり用と何が違うか（上流の bytecode を読んで確かめた・2026-08-04）:
 *
 * <table>
 *   <tr><th></th><th>木こり（上流）</th><th>鉱脈（これ）</th></tr>
 *   <tr><td>広がる向き</td><td>**上と横だけ**（dy は 0〜+1）</td><td>上下横すべて（dy は -1〜+1）</td></tr>
 *   <tr><td>葉</td><td>**自然に生えた葉が近くに要る**（無ければ結果を捨てる）</td><td>見ない</td></tr>
 *   <tr><td>上限</td><td>255 固定</td><td>config（既定 160）</td></tr>
 * </table>
 *
 * <p>「自然の葉が要る」は上流の安全装置で、**丸太で組んだ建築や、手で置いた葉の飾りを
 * 切り倒さない**ためのもの。鉱石にはそれに当たるものが無いので、代わりに上限で抑える。
 */
public final class OreVeinRange {

  private OreVeinRange() {
  }

  /** {@code MultiMinePower.Range} と同じ形（Player, BlockState, BlockPos → 壊す位置の一覧）。 */
  public static List<BlockPos> find(Player player, BlockState source, BlockPos origin) {
    Level level = player.level();
    Set<BlockPos> found = new HashSet<>();
    Queue<BlockPos> queue = new LinkedList<>();
    queue.add(origin);
    int max = ShiftingOrigins.Config.ORE_VEIN_MAX.get();

    while (!queue.isEmpty()) {
      BlockPos cur = queue.remove();

      for (int dx = -1; dx <= 1; dx++) {
        for (int dy = -1; dy <= 1; dy++) {
          for (int dz = -1; dz <= 1; dz++) {

            if (dx == 0 && dy == 0 && dz == 0) {
              continue;
            }
            BlockPos pos = cur.offset(dx, dy, dz);

            if (found.contains(pos) || !level.getBlockState(pos).is(source.getBlock())) {
              continue;
            }
            found.add(pos);
            queue.add(pos);

            if (found.size() >= max) {
              return new ArrayList<>(found);
            }
          }
        }
      }
    }
    return new ArrayList<>(found);
  }
}
