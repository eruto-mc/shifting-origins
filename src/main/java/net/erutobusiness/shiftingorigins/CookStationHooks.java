package net.erutobusiness.shiftingorigins;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 出口②〜⑤のうち、<b>イベントで足りるもの</b>をここで拾う。
 *
 * <ul>
 *   <li>台に触ったことを控える（{@code RightClickBlock}）——
 *       画面を持たない台の産物を「誰の物か」判定する材料</li>
 *   <li>地面へ出た食べ物（{@code EntityJoinLevelEvent} の {@code ItemEntity}）——
 *       {@code Block#popResource} も {@code Containers.dropItemStack} も
 *       最後は {@code addFreshEntity} を通るので、ここ1か所で拾える</li>
 * </ul>
 *
 * <p>⚠ <b>持ち物へ直に入る形</b>（{@code Inventory#add} / {@code setItemInHand}）は
 * イベントが無いので mixin 側で拾う。
 */
@Mod.EventBusSubscriber(modid = ShiftingOrigins.MOD_ID)
public final class CookStationHooks {

  private CookStationHooks() {
  }

  /**
   * ⚠ <b>取り消されたかどうかを見ない。</b> 台を開けなかった場合も「触った」と控えるが、
   * 控えるだけなら害は無い（近くから食べ物が出なければ何も起きない）。
   */
  @SubscribeEvent(priority = EventPriority.LOWEST)
  public static void onRightClickBlock(final PlayerInteractEvent.RightClickBlock event) {
    if (event.getEntity() instanceof ServerPlayer sp) {
      CookMark.remember(sp, event.getPos());
    }
  }

  /**
   * 地面へ出た食べ物に印を押す。
   *
   * <p>⚠ <b>近くに居る料理人のうち、直前にその台を触った人だけ</b>を見る。
   * 誰も触っていなければ何もしないので、落ちている食べ物を拾っただけでは印は付かない。
   */
  @SubscribeEvent
  public static void onItemSpawn(final EntityJoinLevelEvent event) {
    if (!(event.getEntity() instanceof ItemEntity item)) {
      return;
    }
    if (!(event.getLevel() instanceof ServerLevel level)) {
      return;
    }
    ItemStack stack = item.getItem();
    if (stack.isEmpty() || !stack.isEdible()) {
      return;
    }
    double x = item.getX();
    double y = item.getY();
    double z = item.getZ();
    for (ServerPlayer sp : level.players()) {
      if (CookMark.nearLastUse(sp, x, y, z)) {
        CookMark.stamp(sp, stack);
        return;
      }
    }
  }
}
