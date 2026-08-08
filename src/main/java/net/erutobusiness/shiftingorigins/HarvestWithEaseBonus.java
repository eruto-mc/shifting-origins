package net.erutobusiness.shiftingorigins;

import dev.limonblaze.originsclasses.common.registry.OriginsClassesPowers;
import io.github.edwinmindcraft.apoli.api.component.IPowerContainer;
import io.github.edwinmindcraft.apoli.common.power.ModifyValueBlockPower;
import it.crystalnest.harvest_with_ease.api.event.HarvestEvents;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 農家の「収穫が増える」を、Harvest with Ease の**右クリック収穫**にも効かせる。
 *
 * <p>⚠ <b>なぜ要るか（2026-08-09 に逆アセンブルで判明）</b>:
 * Origins: Classes の農家 power {@code more_crop_drops} は
 * {@code BlockMixin.originsClasses$additionalDrop} が
 * {@code Block#playerDestroy}（SRG {@code m_6240_}）へ注入して働いている。
 * ところが Harvest with Ease の {@code HarvestHandler} は
 * <b>{@code Block#getDrops}（SRG {@code m_49874_}）を直接呼ぶ</b>ので、
 * {@code playerDestroy} を通らない。
 * つまり<b>右クリックで収穫するかぎり、農家の能力は一度も発動しない</b>。
 * 当部は Harvest with Ease を入れているので、<b>部員の自然な遊び方だと死んでいた</b>。
 *
 * <p>⚠ <b>倍率を自分で書かない。</b> 上流と同じ経路
 * （{@code IPowerContainer.modify} → {@code rollInt}）で値を取るので、
 * datapack で倍率を変えたらこちらも自動で追随する。
 * 数字を2か所に書くと、必ず片方が腐る。
 *
 * <p>⚠ <b>上流の抽選と同じ形にする。</b> base 1.0 に power の修正を掛け、
 * 小数部を確率として切り上げる（1.3 なら 30% で 2）。
 * 得られた n に対して<b>ドロップ一式を n 回ぶんにする</b>——
 * 上流は {@code dropResources} を n-1 回追加で呼ぶが、
 * こちらはイベントが持つドロップ一覧を書き換える形になる。
 */
@Mod.EventBusSubscriber(modid = ShiftingOrigins.MOD_ID)
public final class HarvestWithEaseBonus {

  private static final Logger LOG = LoggerFactory.getLogger("shiftingorigins");

  private HarvestWithEaseBonus() {
  }

  @SubscribeEvent
  public static void onHarvestDrops(final HarvestEvents.HarvestDropsEvent event) {
    ServerPlayer player = event.getEntity();
    if (player == null) {
      return;
    }
    ServerLevel level = event.getLevel();
    BlockPos pos = event.getPos();
    BlockState crop = event.getCrop();

    int copies = extraCopies(player, level, pos, crop);
    if (copies <= 1) {
      return;
    }
    List<ItemStack> drops = new ArrayList<>(event.getDrops());
    List<ItemStack> out = new ArrayList<>(drops.size() * copies);
    for (int i = 0; i < copies; i++) {
      for (ItemStack stack : drops) {
        out.add(stack.copy());
      }
    }
    event.setDrops(out);
    LOG.info("[shiftingorigins] 右クリック収穫の取り分を {} 倍にした（{}）",
        copies, player.getGameProfile().getName());
  }

  /**
   * 上流の {@code additionalDrop} と同じ計算で「一式を何回ぶん落とすか」を出す。
   *
   * <p>⚠ <b>ここで乱数を引く。</b> 上流と同じく、値の小数部が当たりの確率。
   *
   * <p>⚠ <b>ブロックの条件は見ていない</b>（{@code modify} の3引数版を使っている）。
   * 上流は {@code harvestable_crops}（{@code instanceof CropBlock} かつ最大成長）で
   * 絞っているが、<b>このイベント自体が「熟した作物を右クリックした」ときしか飛ばない</b>ので
   * 条件は既に満たされている。
   * ⚠ 条件を見るには {@code ConfiguredBlockCondition} が要り、
   * それは Calio の型を引き込む（依存がもう1つ増える）ので採らなかった。
   * ⚠ <b>もし将来 {@code modify_block_loot} を「作物以外」に配ったら、ここは誤爆する。</b>
   * そのときはこの注記ごと見直すこと。
   */
  private static int extraCopies(ServerPlayer player, ServerLevel level, BlockPos pos,
                                 BlockState crop) {
    ModifyValueBlockPower factory =
        (ModifyValueBlockPower) OriginsClassesPowers.MODIFY_BLOCK_LOOT.get();
    float value = IPowerContainer.modify(player, factory, 1.0F);
    int whole = (int) value;
    return level.random.nextDouble() < value - whole ? whole + 1 : whole;
  }
}
