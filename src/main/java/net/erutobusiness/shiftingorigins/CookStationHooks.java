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
   * ⚠⚠ <b>{@code receiveCanceled = true} が要る。</b>
   * Forge のイベントは<b>誰かが取り消すと、以後の購読者へ届かない</b>。
   * 当部は Open Parties and Claims のような保護MODを入れているので、
   * {@code RightClickBlock} は取り消されうる。
   *
   * <p>2026-08-09 に実際に踏んだ: 診断ログを入れたら
   * 「台を触った」が<b>1行も出ない</b>のに「持ち物へ入る」は毎回出ていた
   * ——つまり<b>購読が呼ばれていなかった</b>。
   * ⚠ Web を1回引いて分かった（検索語:
   * 「Minecraft Forge event handler not called canceled event receiveCanceled RightClickBlock」）。
   *
   * <p>⚠ <b>取り消された操作でも「触った」と控えてよい。</b> 控えるだけなら害は無い
   * （近くから食べ物が出なければ何も起きない）。
   */
  @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
  public static void onRightClickBlock(final PlayerInteractEvent.RightClickBlock event) {
    if (event.getEntity() instanceof ServerPlayer sp) {
      CookMark.remember(sp, event.getPos());
    }
  }

  /**
   * 地面へ出た食べ物に印を押す。
   *
   * <p>⚠ <b>台の位置で引く</b>（2026-08-10 に作り直した）。産物は台の位置に湧くので、
   * 「その場所の台を誰が仕込んだか」を見れば、<b>仕込んだ人がその場に居なくても</b>印を押せる。
   *
   * <p>⚠⚠ <b>作り直した理由は焚き火。</b> 旧実装は「近くに居る料理人のうち、直前に
   * その台を触った人」を 100 tick の窓で探していた。焚き火の調理は <b>600 tick</b> なので、
   * <b>焼き上がるころには窓が6倍過ぎている</b>。さらに旧実装は
   * <b>プレイヤーごとに1か所しか覚えない</b>ので、焚き火を並べて仕込むと最後の1つ以外は忘れた。
   * ⚠ **UIの無い台が「料理でない」わけではない。焚き火はバニラの調理台。**
   *
   * <p>⚠ 落ちている食べ物を拾っただけでは印は付かない（誰も仕込んでいない場所には台の記憶が無い）。
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
    ServerPlayer cook = CookMark.cookWhoLoaded(level, item.getX(), item.getY(), item.getZ());
    if (cook != null) {
      CookMark.stamp(cook, stack);
    }
  }
}
