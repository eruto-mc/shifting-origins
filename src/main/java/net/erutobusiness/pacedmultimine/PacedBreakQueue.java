package net.erutobusiness.pacedmultimine;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 一括破壊を「少しずつ」に変えるキュー。プレイヤー1人につき1つ。
 *
 * <p>破壊は {@code ServerPlayerGameMode.destroyBlock} を通す。これは**プレイヤーが手で掘る経路**
 * そのものなので、ドロップ・経験値・道具の消耗・保護MODの判定・他MODのイベントが全部いつも通りに走る
 * （FTB Ultimine も Ore Excavation も同じ考え方をしている）。
 */
public final class PacedBreakQueue {

  private static final Map<UUID, Job> JOBS = new HashMap<>();
  /** 自分が壊している最中かどうか。⚠ これが無いと、破壊するたびに power が再び発火して無限に増える。 */
  private static final Set<UUID> BUSY = new HashSet<>();

  private PacedBreakQueue() {
  }

  /** @return 横取りしてキューに積んだら true（呼び出し元は空の一覧を返す） */
  public static boolean enqueue(Player player, List<BlockPos> positions) {

    if (!(player instanceof ServerPlayer serverPlayer) || positions.isEmpty()) {
      return false;
    }

    if (BUSY.contains(serverPlayer.getUUID())) {
      // 自分の破壊が引き起こした発火。ここで積むと無限に増えるので、何も壊させない
      return true;
    }

    if (PacedMultiMine.Config.INTERVAL_TICKS.get() <= 0) {
      return false;   // 0 なら横取りしない＝上流どおり同じ tick で全部壊れる
    }
    JOBS.put(serverPlayer.getUUID(), new Job(serverPlayer, positions));
    return true;
  }

  @SubscribeEvent
  public static void onLevelTick(final TickEvent.LevelTickEvent event) {

    if (event.phase != TickEvent.Phase.END || event.level.isClientSide() || JOBS.isEmpty()) {
      return;
    }

    if (!(event.level instanceof ServerLevel level)) {
      return;
    }
    Iterator<Map.Entry<UUID, Job>> it = JOBS.entrySet().iterator();

    while (it.hasNext()) {
      Job job = it.next().getValue();

      if (!job.dimension.equals(level.dimension())) {
        continue;
      }

      if (job.tick(level)) {
        it.remove();
      }
    }
  }

  private static final class Job {

    private final UUID playerId;
    private final ResourceKey<Level> dimension;
    private final Item tool;
    private final Deque<BlockPos> remaining;
    private final int interval;
    private final int batch;
    private final double maxDistanceSq;
    private int cooldown;

    private Job(ServerPlayer player, List<BlockPos> positions) {
      this.playerId = player.getUUID();
      this.dimension = player.level().dimension();
      this.tool = player.getMainHandItem().getItem();
      this.remaining = new ArrayDeque<>(new ArrayList<>(positions));
      this.interval = PacedMultiMine.Config.INTERVAL_TICKS.get();
      this.batch = batchSize(positions.size(), this.interval);
      int range = PacedMultiMine.Config.MAX_DISTANCE.get();
      this.maxDistanceSq = (double) range * range;
      this.cooldown = this.interval;
    }

    /** 全体が maxTotalTicks を超えそうなら、1回あたりの個数を上げる。 */
    private static int batchSize(int total, int interval) {
      int perBatch = PacedMultiMine.Config.BLOCKS_PER_BATCH.get();
      int budget = PacedMultiMine.Config.MAX_TOTAL_TICKS.get();

      if (budget <= 0 || interval <= 0) {
        return perBatch;
      }
      int batches = Math.max(1, budget / interval);
      return Math.max(perBatch, (total + batches - 1) / batches);
    }

    /** @return 終わった（一覧から外す）なら true */
    private boolean tick(ServerLevel level) {

      if (--cooldown > 0) {
        return false;
      }
      cooldown = interval;
      ServerPlayer player = level.getServer().getPlayerList().getPlayer(playerId);

      // 続けられない条件は Ore Excavation と同じ並び:
      // 居ない／退場した／死んだ／別の次元へ移った／メインハンドの道具が変わった
      if (player == null || player.isRemoved() || player.isDeadOrDying()
          || !player.level().dimension().equals(dimension)
          || player.getMainHandItem().getItem() != tool) {
        return true;
      }
      BUSY.add(playerId);

      try {

        for (int i = 0; i < batch && !remaining.isEmpty(); i++) {
          BlockPos pos = remaining.poll();

          if (level.isEmptyBlock(pos)) {
            continue;
          }

          // 掘っている間に離れたら、その先へは進まない
          if (player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D)
              > maxDistanceSq) {
            continue;
          }

          if (!player.gameMode.destroyBlock(pos)) {
            // 保護MODなどに止められた。そこだけ飛ばす
            continue;
          }
        }
      } finally {
        BUSY.remove(playerId);
      }
      return remaining.isEmpty();
    }
  }
}
