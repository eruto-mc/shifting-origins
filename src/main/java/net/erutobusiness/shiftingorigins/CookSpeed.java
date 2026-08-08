package net.erutobusiness.shiftingorigins;

import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SmokerBlockEntity;

/**
 * 料理人が近くに居る調理台を<b>2倍で動かす</b>かどうかを決める。
 *
 * <p>⚠ <b>なぜ「許可リスト」なのか（2026-08-09・あなたの判断）</b>:
 * 速さは {@code LevelChunk$BoundTickingBlockEntity#tick}（すべてのブロックエンティティが
 * 通る1か所）で ticker をもう1回呼ぶ形で作る。だからどの台にも効く代わりに、
 * <b>無関係なものまで速くなる</b>。
 *
 * <p>最初は「危ないものを外す」形（禁止リスト）で考えたが、実際に数えたら
 * <b>ブロックエンティティは 811 件あり、名前で危ないと分類できたのは 34 件だけ</b>だった。
 * しかも分類は両方向に外れた（KawaiiDishes のコーヒーメーカーは「機械」に出るが
 * 実際は速くしたい調理台／名前が素直でない台は出ない）。
 * <b>811 件から選び出す形では必ずどれか見落とす。</b>
 *
 * <p>→ <b>許可リストにした。</b> 漏れの壊れ方は「その台だけ速くならない」であって、
 * 世界の他の仕掛けは一切触らない。⚠ <b>失敗しても被害が出ない側へ倒す。</b>
 *
 * <p>⚠ <b>この一覧は手で書いていない。</b>
 * {@code py verify/list_cooking_stations.py} が
 * 「食べ物を登録している jar の、{@code RecipeManager#getRecipeFor} を呼ぶ
 * ブロックエンティティ」を機械で選んだもの（2026-08-09 時点で 31 種 / 12 MOD）。
 * <b>MOD を足したら回し直して貼り替える。</b>
 *
 * <p>⚠ <b>漏れているのが分かっているMOD</b>: Sushi Go Crafting（Titanium で自前に組む）、
 * Butchery（BE が 215 件あるがレシピを引かない）、Coffee Craft、Chocolatiers ほか。
 * 遅い台を見つけたら個別に足す。
 *
 * <p>⚠ <b>かまどと溶鉱炉は入れない</b>（あなたの判断）。料理人は燻製器の経験値ボーナスを
 * 既に持っているので、バニラは<b>燻製器だけ</b>を速くする。
 */
public final class CookSpeed {

  /** 料理人からこの距離までの台を速くする。⚠ 体感で決める調整値 */
  private static final double RANGE = 8.0D;

  /**
   * ⚠ <b>`py verify/list_cooking_stations.py` の出力を貼ったもの。</b>
   * 手で足すときは、なぜ足したかを行末に書く。
   */
  private static final Set<String> STATIONS = Set.of(
      "ApplePressBlockEntity",
      "BlenderBlockEntity",
      "CheeseFormBlockEntity",
      "CheesePressBlockEntity",
      "CoffeeMachineBlockEntity",
      "CoffeePressBlockEntity",
      "CookingCauldronBlockEntity",
      "CookingPanBlockEntity",
      "CookingPotBlockEntity",
      "CraftingBowlBlockEntity",
      "CuttingBoardBlockEntity",
      "FermentationBarrelBlockEntity",
      "FermentationBoxBlockEntity",
      "FryingPanBlockEntity",
      "GlassDrinkCupBlockEntity",
      "GrillBlockEntity",
      "IceCreamMachineBlockEntity",
      "KegBlockEntity",
      "MincerBlockEntity",
      "MiniFridgeBlockEntity",
      "NuclearFurnaceBlockEntity",
      "OvenBlockEntity",
      "PalmBarBlockEntity",
      "PizzaFlatbreadBlockEntity",
      "ProcessingContainerBlockEntity",
      "RoasterBlockEntity",
      "StoneKilnBlockEntity",
      "StoveBlockEntity",
      "TeaKettleBlockEntity",
      "ToasterBlockEntity",
      "WaterPurifierBlockEntity");

  private CookSpeed() {
  }

  /**
   * その台をもう1回動かすか。
   *
   * <p>⚠ <b>判定の順は「安い順」。</b> 毎tick・全ブロックエンティティで呼ばれるので、
   * クラス名の照合（ハッシュ1回）を先に置き、プレイヤーの走査は最後にする。
   */
  public static boolean shouldSpeedUp(BlockEntity be) {
    if (be == null || be.getLevel() == null || be.getLevel().isClientSide) {
      return false;
    }
    boolean isStation = be instanceof SmokerBlockEntity
        || STATIONS.contains(be.getClass().getSimpleName());
    if (!isStation) {
      return false;
    }
    ServerLevel level = (ServerLevel) be.getLevel();
    double x = be.getBlockPos().getX() + 0.5D;
    double y = be.getBlockPos().getY() + 0.5D;
    double z = be.getBlockPos().getZ() + 0.5D;
    for (ServerPlayer sp : level.players()) {
      if (sp.distanceToSqr(x, y, z) <= RANGE * RANGE && ClassPowers.isCook(sp)) {
        return true;
      }
    }
    return false;
  }
}
