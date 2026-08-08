package net.erutobusiness.shiftingorigins;

import dev.limonblaze.originsclasses.common.apoli.power.ModifyCraftedFoodPower;
import dev.limonblaze.originsclasses.common.event.ModifyCraftResultEvent;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

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

  /** 料理人が最後に触った台の位置と時刻。⚠ プレイヤーごとに1つだけ持つ */
  private static final Map<ServerPlayer, long[]> LAST_USE = new WeakHashMap<>();
  /** 台から出たと見なす距離（ブロック）。⚠ 体感で決める調整値 */
  private static final double NEAR = 6.0D;
  /** 台に触ってから何 tick まで「その台の産物」と見なすか。⚠ 同上 */
  private static final long WINDOW = 100L;

  private CookMark() {
  }

  /** 料理人が台を触ったことを控える（{@code PlayerInteractEvent.RightClickBlock} から呼ぶ） */
  public static void remember(ServerPlayer player, BlockPos pos) {
    LAST_USE.put(player, new long[]{pos.getX(), pos.getY(), pos.getZ(),
        player.serverLevel().getGameTime()});
  }

  /** その座標が「直前に触った台のそば」か */
  public static boolean nearLastUse(ServerPlayer player, double x, double y, double z) {
    long[] v = LAST_USE.get(player);
    if (v == null) {
      return false;
    }
    if (player.serverLevel().getGameTime() - v[3] > WINDOW) {
      return false;
    }
    double dx = x - v[0];
    double dy = y - v[1];
    double dz = z - v[2];
    return dx * dx + dy * dy + dz * dz <= NEAR * NEAR;
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
  }
}
