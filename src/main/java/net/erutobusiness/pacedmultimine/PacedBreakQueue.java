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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 一括破壊を「少しずつ」に変えるキュー。プレイヤー1人につき1つ。
 *
 * <p>破壊は {@code ServerPlayerGameMode.destroyBlock} を通す。これは**プレイヤーが手で掘る経路**
 * そのものなので、ドロップ・経験値・道具の消耗・保護MODの判定・他MODのイベントが全部いつも通りに走る
 * （FTB Ultimine も Ore Excavation も同じ考え方をしている）。
 */
public final class PacedBreakQueue {

  private static final Logger LOG = LoggerFactory.getLogger("pacedmultimine");
  private static final Map<UUID, Job> JOBS = new HashMap<>();
  /** 自分が壊している最中かどうか。⚠ これが無いと、破壊するたびに power が再び発火して無限に増える。 */
  private static final Set<UUID> BUSY = new HashSet<>();

  private PacedBreakQueue() {
  }

  /** @return 横取りしてキューに積んだら true（呼び出し元は空の一覧を返す） */
  public static boolean enqueue(Player player, BlockPos origin, List<BlockPos> positions) {

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
    Job job = new Job(serverPlayer, origin, positions);
    LOG.info("[paced] 横取り {} 個（{}）1回 {} 個 / {} tick ごと", positions.size(),
        serverPlayer.getGameProfile().getName(), job.batch, job.interval);
    JOBS.put(serverPlayer.getUUID(), job);
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

    private Job(ServerPlayer player, BlockPos origin, List<BlockPos> positions) {
      this.playerId = player.getUUID();
      this.dimension = player.level().dimension();
      this.tool = player.getMainHandItem().getItem();
      this.remaining = orderFromOrigin(origin, positions);
      this.interval = PacedMultiMine.Config.INTERVAL_TICKS.get();
      this.batch = batchSize(positions.size(), this.interval);
      int range = PacedMultiMine.Config.MAX_DISTANCE.get();
      this.maxDistanceSq = (double) range * range;
      this.cooldown = this.interval;
    }

    /**
     * 壊す順番を決める。**掘った位置から繋がりを辿った順**（幅優先）。
     *
     * <p>⚠ 上流が返すのは**順序を持たない集合**なので、そのまま壊すとバラバラの順になる。
     *
     * <p>⚠ 2026-08-04 まで「掘った位置からの直線距離順」で並べていた。まっすぐな幹なら同じ結果に
     * なるが、**曲がった鉱脈では破壊の先頭が飛ぶ**。C の字に曲がった鉱脈だと、掘った所から
     * 直線では近いが繋がりでは遠い端が**宙に浮いたまま先に消える**（試験場の値では、
     * 掘った位置から数えて 36 個中 8 番目に消えていた。繋がり順なら 32 番目）。
     * 幅優先なら**壊す位置は必ず、直前までに壊した所と隣り合っている**。
     *
     * <p>繋がりの向きは上流の探索と揃える必要が無い（上流より広い26方向で辿るだけなので、
     * 上流が集めた集合は必ず辿り切れる）。それでも届かないものが残ったら、
     * 取りこぼさないように末尾へ直線距離順で付ける。
     */
    private static Deque<BlockPos> orderFromOrigin(BlockPos origin, List<BlockPos> positions) {
      Set<BlockPos> pool = new HashSet<>(positions);
      Deque<BlockPos> ordered = new ArrayDeque<>(positions.size());
      Deque<BlockPos> frontier = new ArrayDeque<>();
      // 掘った位置そのものはプレイヤーが既に壊している。ここは辿る起点としてだけ使う
      pool.remove(origin);
      frontier.add(origin);

      while (!frontier.isEmpty()) {
        BlockPos cur = frontier.poll();

        for (int dx = -1; dx <= 1; dx++) {
          for (int dy = -1; dy <= 1; dy++) {
            for (int dz = -1; dz <= 1; dz++) {

              if (dx == 0 && dy == 0 && dz == 0) {
                continue;
              }
              BlockPos next = cur.offset(dx, dy, dz);

              if (pool.remove(next)) {
                ordered.add(next);
                frontier.add(next);
              }
            }
          }
        }
      }

      if (!pool.isEmpty()) {
        // 繋がっていなかったぶん（上流の探索が斜めをまたいだ等）。掘った所に近い順で最後に付ける
        List<BlockPos> rest = new ArrayList<>(pool);
        rest.sort(java.util.Comparator.comparingDouble(p -> p.distSqr(origin)));
        ordered.addAll(rest);
      }
      return ordered;
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
            LOG.info("[paced] 壊せなかった {}（保護MODか採掘条件を疑う）", pos);
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
