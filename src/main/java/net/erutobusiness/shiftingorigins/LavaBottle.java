package net.erutobusiness.shiftingorigins;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import toughasnails.api.thirst.IThirst;
import toughasnails.api.thirst.ThirstHelper;

/**
 * ブレイズボーンが <b>溶岩入りの瓶を飲む</b>ための判定と効果。
 *
 * <p>差し込む場所は {@link net.erutobusiness.shiftingorigins.mixin.ItemMixin}。
 * ここは<b>判定と効果だけ</b>を持ち、いつ呼ばれるかは知らない。
 *
 * <h2>なぜ要るか</h2>
 *
 * ブレイズボーンは {@code world3:damage_from_canteens}（「水は毒」）で
 * ⚠ <b>水筒の水を飲むと 2 痛む</b>。一方 {@code world3:heat_tolerance} が面倒を見るのは
 * <b>体温だけ</b>で、⚠ <b>喉の渇きは普通にある</b>。
 * つまり「渇くのに、水を飲むと痛む」種族だった。
 *
 * <p>Alex's Mobs の {@code alexsmobs:lava_bottle} は、
 * <b>ガラス瓶を持って溶岩の源を右クリック</b>すると手に入る（そのとき火が付くが、
 * ⚠ <b>ブレイズボーンは火に完全耐性なのでこの代償だけ無料</b>）。
 *
 * <h2>⚠ タグでは飲めるようにならない</h2>
 *
 * Tough As Nails の飲み物はアイテムタグで決まるが、
 * ⚠ <b>タグは「どれだけ回復するか」を決めているだけ。</b>
 * {@code thirst/*_thirst_drinks} に入っているのは牛乳バケツ・ポーション・
 * TaN 自身の瓶と水筒で、<b>全部もともと飲める物</b>だった。
 * ⚠ {@code alexsmobs:lava_bottle} は {@code FoodProperties} を持たない素の {@code Item}。
 *
 * <h2>回復量</h2>
 *
 * ⚠ <b>上流の「浄水の瓶」と同じ値</b>（tag を読んで採った・自分で決めた数字ではない）:
 * {@code thirst/5_thirst_drinks} と {@code hydration/80_hydration_drinks} に
 * {@code toughasnails:purified_water_bottle} が入っている。
 */
public final class LavaBottle {

  private static final ResourceLocation ID = new ResourceLocation("alexsmobs", "lava_bottle");
  private static final String BLAZEBORN = "origins:blazeborn";

  /** バニラの飲み物と同じ長さ（{@code Item.EAT_DURATION} が 32）。 */
  public static final int USE_DURATION = 32;

  private LavaBottle() {
  }

  /** その持ち物が溶岩入りの瓶か。⚠ <b>全アイテムの親に差し込むので、ここが最初の関門</b>。 */
  public static boolean is(ItemStack stack) {
    if (stack.isEmpty()) {
      return false;
    }
    ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
    return ID.equals(id);
  }

  /**
   * いまこの人が飲めるか。
   *
   * <p>⚠ <b>ブレイズボーン以外は false</b>——他の種族には今までどおり何も起きない。
   * ⚠ 満タンのときも false（瓶を無駄にしない）。
   */
  public static boolean canDrink(Player player) {
    if (!ShiftingOrigins.Config.LAVA_BOTTLE_DRINK.get()) {
      return false;
    }
    if (!BLAZEBORN.equals(OriginNames.raceId(player))) {
      return false;
    }
    if (!ThirstHelper.isThirstEnabled()) {
      return false;
    }
    return ThirstHelper.canDrink(player, false);
  }

  /** 飲み終わったときの効果。⚠ <b>サーバー側でだけ呼ぶこと</b>。 */
  public static void drink(Player player) {
    IThirst thirst = ThirstHelper.getThirst(player);
    thirst.addThirst(ShiftingOrigins.Config.LAVA_BOTTLE_THIRST.get());
    thirst.addHydration(ShiftingOrigins.Config.LAVA_BOTTLE_HYDRATION.get().floatValue());
  }
}
