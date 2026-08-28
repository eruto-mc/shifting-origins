package net.erutobusiness.shiftingorigins;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

import toughasnails.api.thirst.IThirst;
import toughasnails.api.thirst.ThirstHelper;

/**
 * ブレイズボーンが <b>溶岩入りの瓶を飲めるようにする</b>。
 *
 * <h2>なぜ要るか</h2>
 *
 * ブレイズボーンは {@code world3:damage_from_canteens}（「水は毒」）を持っていて、
 * ⚠ <b>水筒の水を飲むと 2 痛む</b>。一方 {@code world3:heat_tolerance} が面倒を見るのは
 * <b>体温だけ</b>で、⚠ <b>喉の渇きは普通にある</b>。
 * つまり「渇くのに、水を飲むと痛む」種族だった。
 *
 * <p>Alex's Mobs の {@code alexsmobs:lava_bottle} は、
 * <b>ガラス瓶を持って溶岩の源を右クリック</b>すると手に入る（そのとき火が付くが、
 * ⚠ <b>ブレイズボーンは火に完全耐性なのでこの代償だけ無料</b>）。
 * そこで<b>これを彼らの水にする</b>。
 *
 * <h2>⚠ なぜ datapack でやらないか</h2>
 *
 * Tough As Nails の飲み物はアイテムタグで決まるが、
 * ⚠ <b>タグは「どれだけ回復するか」を決めているだけで、飲めるようにはしない。</b>
 * 実際 {@code thirst/*_thirst_drinks} に入っているのは
 * 牛乳バケツ・ポーション・TaN 自身の瓶と水筒で、<b>全部もともと飲める物</b>だった。
 * ⚠ {@code alexsmobs:lava_bottle} は {@code FoodProperties} を持たない素の {@code Item} なので、
 * タグに足しても右クリックで何も起きない。だから Java 側で拾う。
 *
 * <h2>回復量</h2>
 *
 * ⚠ <b>上流の「浄水の瓶」と同じ値</b>にしてある（tag を読んで採った。
 * {@code thirst/5_thirst_drinks} と {@code hydration/80_hydration_drinks} に
 * {@code toughasnails:purified_water_bottle} が入っている）。
 * ⚠ <b>自分で決めた数字ではない。</b>
 */
public final class LavaBottleDrink {

  private static final ResourceLocation LAVA_BOTTLE =
      new ResourceLocation("alexsmobs", "lava_bottle");
  private static final String BLAZEBORN = "origins:blazeborn";

  private LavaBottleDrink() {
  }

  @SubscribeEvent
  public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
    if (!ShiftingOrigins.Config.LAVA_BOTTLE_DRINK.get()) {
      return;
    }
    Player player = event.getEntity();
    ItemStack stack = event.getItemStack();
    ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
    if (id == null || !LAVA_BOTTLE.equals(id)) {
      return;
    }
    if (!BLAZEBORN.equals(OriginNames.raceId(player))) {
      return;                                   // ⚠ 他の種族はこれまでどおり（何も起きない）
    }
    if (!ThirstHelper.isThirstEnabled()) {
      return;
    }
    // ⚠ 満タンのときは飲ませない（瓶を無駄にしない）。第2引数は「常に飲めるか」
    if (!ThirstHelper.canDrink(player, false)) {
      return;
    }

    // ⚠ 効くのはサーバー側だけ。クライアントでは印だけ返して音を鳴らす
    if (!event.getLevel().isClientSide()) {
      IThirst thirst = ThirstHelper.getThirst(player);
      thirst.addThirst(ShiftingOrigins.Config.LAVA_BOTTLE_THIRST.get());
      thirst.addHydration(ShiftingOrigins.Config.LAVA_BOTTLE_HYDRATION.get().floatValue());
      if (!player.isCreative()) {
        stack.shrink(1);
        // ⚠ 瓶は返す（バニラの飲み物と同じ作法）。持ち物が満杯なら足元へ落とす
        if (!player.getInventory().add(new ItemStack(Items.GLASS_BOTTLE))) {
          player.drop(new ItemStack(Items.GLASS_BOTTLE), false);
        }
      }
    }
    player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
        SoundEvents.GENERIC_DRINK, SoundSource.PLAYERS, 1.0F, 1.0F);
    event.setCanceled(true);
    event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide()));
  }
}
