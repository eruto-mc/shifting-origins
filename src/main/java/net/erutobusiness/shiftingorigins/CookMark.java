package net.erutobusiness.shiftingorigins;

import dev.limonblaze.originsclasses.common.apoli.power.ModifyCraftedFoodPower;
import dev.limonblaze.originsclasses.common.event.ModifyCraftResultEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 「料理人が作った食べ物」に印を押す。
 *
 * <p>⚠ <b>印の形式も、食べたときの読み取りも、上流のまま使う。</b>
 * 押すのは {@code ModifyCraftedFoodPower#modify}（NBT `OriginsClasses.ModifyFoodPowers` に
 * power の ID を並べる）、読むのは {@code ModifyFoodPowerMixin}。
 * 当部が足すのは<b>どこから押すか</b>だけ。⚠ 同じ役割の物を二重に作らない。
 *
 * <p>⚠ <b>なぜ「出口」を押さえるのか（2026-08-09 の調査）</b>:
 * 上流は調理台ごとに mixin を当てている（バニラの {@code ResultSlot} と
 * {@code FurnaceResultSlot}、Farmer's Delight、Tetra）。
 * ⚠ <b>当部は Farmer's Delight も Tetra も入れていない</b>ので、
 * 料理MOD 12種・67 のレシピ型のうち<b>届いていたのは作業台とかまど系の5型だけ</b>だった。
 *
 * <p>台ごとに当てる形は、MOD を足すたびに増えるうえ、相手の版が上がると黙って外れる。
 * そこで<b>食べ物がプレイヤーへ渡る出口</b>を押さえる形にした。
 * ⚠ 出口は数えたら<b>5か所</b>だった（`verify/check_cook_paths.py` が毎回数える）:
 *
 * <pre>
 *   Slot#onTake            画面から取り出す（SlotItemHandler も Slot の子）
 *   Inventory#add          持ち物へ入れる（Player#addItem と giveItemToPlayer はここへ落ちる）
 *   Inventory#setItem      枠へ直に代入する
 *   LivingEntity#setItemInHand 手の物と入れ替える
 *   Level#addFreshEntity   地面へ出す（popResource と dropItemStack はここへ落ちる）
 * </pre>
 *
 * <p>⚠ <b>「作った」と「拾った」を分ける必要がある。</b> 出口を押さえるだけでは、
 * チェストから出した食べ物にも印が付いてしまう。判定は2つ:
 *
 * <ol>
 *   <li><b>置けない枠から取った</b>（{@code Slot#mayPlace} が偽）＝ 産物の枠。
 *       画面のある台はこれで確実に分かる</li>
 *   <li><b>直前に自分で触った台の近くから出た</b>。画面を持たない台
 *       （焚き火・地面へ吐く台）はこれで拾う</li>
 * </ol>
 */
public final class CookMark {

  /** 料理人が最後に触った台の位置と時刻。**手で受け取る側**（持ち物・手）の判定に使う */
  private static final Map<ServerPlayer, long[]> LAST_USE = new WeakHashMap<>();

  /**
   * <b>台ごとに「誰が仕込んだか」</b>。地面へ吐く台の判定に使う。
   *
   * <p>⚠⚠ <b>プレイヤーごとに1つ覚える形では焚き火が取れない</b>（2026-08-10 に作り直した）。
   * 理由は2つあって、どちらも実測ではなく**数で決まる**:
   *
   * <ul>
   *   <li><b>時間</b>: 焚き火の `campfire_cooking` は <b>600 tick</b>（バニラのレシピ JSON の
   *       `cookingtime`）。仕込んでから焼き上がるまでに、旧 100 tick の窓は<b>6倍過ぎている</b></li>
   *   <li><b>数</b>: 焚き火は1つに<b>4つ</b>まで入るし、複数台を並べて仕込むのが普通。
   *       <b>プレイヤーごとに1か所しか覚えないと、最後に触った台以外は全部忘れる</b></li>
   * </ul>
   *
   * <p>→ <b>台の位置で覚える。</b> 産物は台の位置に湧くので、湧いた場所のブロックを引けばよい。
   */
  private static final Map<BlockPos, LoadedBy> LOADED = new HashMap<>();

  /** 台から出たと見なす距離（ブロック）。⚠ 体感で決める調整値 */
  private static final double NEAR = 6.0D;
  /**
   * <b>手で受け取る</b>とき、台に触ってから何 tick まで「その台の産物」と見なすか。
   * ⚠ 体感で決める調整値。⚠ <b>長くしない</b>——長いと、台のそばで拾った食べ物にまで印が付く。
   */
  private static final long WINDOW_TAKE = 100L;
  /**
   * <b>地面へ吐く台</b>を仕込んでから、その台の産物と見なす上限。
   *
   * <p>⚠⚠ <b>これは体感で決める値ではない。データで決まる。</b>
   * 入れてあるMODの<b>全レシピの調理時間の最大</b>を超えていないと、その台は取りこぼす。
   *
   * <p>2026-08-10 に全 jar のレシピ JSON を数えた結果:
   *
   * <pre>
   *   7200 tick（6分） … yeastnfeast:keg の brew_time / yeastnfeast:cheese_press の pressTime
   *   3600 tick（3分） … yeastnfeast:keg の一部
   *    600 tick（30秒）… バニラの campfire_cooking
   * </pre>
   *
   * <p>最大 7200 に余裕をみて <b>9600</b>（8分）。
   * ⚠ <b>最初は 2400（2分）と書いた。焚き火の 600 しか見ておらず、
   * チーズ搾りと樽の 7200 を取りこぼす値だった。</b>
   *
   * <p>⚠ <b>MODを足したら数え直す。</b>
   * {@code py verify/check_cook_window.py} が数えて、この値を超えていたら止める。
   */
  private static final long WINDOW_COOK = 9600L;

  private static final Logger LOG = LoggerFactory.getLogger("shiftingorigins");

  /** どの料理人が、いつ仕込んだか */
  private record LoadedBy(UUID cook, long at) {
  }

  private CookMark() {
  }

  /** 料理人が台を触ったことを控える（{@code PlayerInteractEvent.RightClickBlock} から呼ぶ） */
  public static void remember(ServerPlayer player, BlockPos pos) {
    long now = player.serverLevel().getGameTime();
    LAST_USE.put(player, new long[]{pos.getX(), pos.getY(), pos.getZ(), now});
    LOADED.put(pos.immutable(), new LoadedBy(player.getUUID(), now));
    // ⚠ 触るたびに古い分を捨てる。⚠ **溜めっぱなしにしない**（台の数だけ増え続ける）
    LOADED.entrySet().removeIf(e -> now - e.getValue().at() > WINDOW_COOK);
    LOG.info("[cook] 台を触った: {} @ {}（控えている台 {} 個）",
        player.getGameProfile().getName(), pos, LOADED.size());
  }

  /**
   * その座標が「直前に触った台のそば」か。
   * ⚠ <b>手で受け取る側の判定</b>（持ち物へ入る・手の物と入れ替わる）にだけ使う。
   */
  public static boolean nearLastUse(ServerPlayer player, double x, double y, double z) {
    long[] v = LAST_USE.get(player);
    if (v == null) {
      return false;
    }
    if (player.serverLevel().getGameTime() - v[3] > WINDOW_TAKE) {
      return false;
    }
    double dx = x - v[0];
    double dy = y - v[1];
    double dz = z - v[2];
    return dx * dx + dy * dy + dz * dz <= NEAR * NEAR;
  }

  /**
   * その場所へ吐かれた物を仕込んだ料理人を返す（居なければ {@code null}）。
   *
   * <p>⚠ <b>湧いた場所のブロックだけを見るのではなく、近く（{@code NEAR}）の台を探す。</b>
   * 台によっては産物が少しずれた位置に湧く（焚き火は火の上、粉砕機は正面など）。
   *
   * <p>⚠ <b>仕込んだ人が居なくなっていたら諦める。</b> 印を押すには
   * その人が持っている power を引く必要があるので、離線していると押せない。
   */
  public static ServerPlayer cookWhoLoaded(ServerLevel level, double x, double y, double z) {
    long now = level.getGameTime();
    for (Map.Entry<BlockPos, LoadedBy> e : LOADED.entrySet()) {
      LoadedBy v = e.getValue();
      if (now - v.at() > WINDOW_COOK) {
        continue;
      }
      BlockPos p = e.getKey();
      double dx = x - p.getX();
      double dy = y - p.getY();
      double dz = z - p.getZ();
      if (dx * dx + dy * dy + dz * dz > NEAR * NEAR) {
        continue;
      }
      ServerPlayer sp = level.getServer().getPlayerList().getPlayer(v.cook());
      if (sp != null) {
        return sp;
      }
    }
    return null;
  }

  /**
   * 印を押す。⚠ <b>食べ物でなければ何もしない</b>（判定は上流の power の item_condition）。
   *
   * <p>⚠ <b>種別は {@code CRAFTING} を渡す。</b> 当部は「どの台か」で出し分けないので、
   * 上流が持つ5種のうち一番基本のものへ寄せる。
   * ⚠ datapack 側の {@code crafting_result_type} がこれを含んでいないと発火しない。
   */
  public static void stamp(ServerPlayer player, ItemStack stack) {
    if (stack.isEmpty() || !stack.isEdible()) {
      return;
    }
    ModifyCraftedFoodPower.modify(player, stack,
        ModifyCraftResultEvent.CraftingResultType.CRAFTING);
    LOG.info("[cook] 印を押した: {} -> {} / tag={}", player.getGameProfile().getName(),
        stack, stack.getTag());
  }
}
