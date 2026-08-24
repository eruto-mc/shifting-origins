package net.erutobusiness.shiftingorigins;

import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * スケルトンがイヌ獣人から逃げるようにする（2026-08-24）。
 *
 * <p><b>なぜ Java が要るか</b>: バニラのスケルトンは
 * {@code AbstractSkeleton.registerGoals()} で
 * {@code new AvoidEntityGoal<>(this, Wolf.class, 6.0F, 1.0, 1.2)} を持っている。
 * ⚠ <b>避ける相手が {@code Wolf.class} というクラスで固定</b>されているので、
 * プレイヤーをそこへ入れる手が datapack 側に無い。
 *
 * <p>⚠ 探した範囲: apoli に登録されている power の型を全部（100種超）見たが、
 * 他のモブを逃がす型は1つも無い。Origins の {@code origins:scare_creepers} は在るが、
 * datapack の中身は {@code {"type": "origins:scare_creepers"}} の1行だけで、
 * 実体は {@code ScareCreepersMixin} という <b>Java 専用の型</b>。
 * ⚠ <b>1つのモブのために Java を書く必要があったこと自体が、data 側からは無理だという証拠。</b>
 *
 * <p><b>やり方</b>: スケルトンが世界に入った瞬間に、
 * <b>バニラがオオカミへ使っているのと同じ目標を、同じ数字で</b>足す。
 * 避ける相手の条件に「イヌ獣人を選んでいるプレイヤーか」を入れる。
 *
 * <pre>
 *   優先度 3 ／ 距離 6.0 ／ 歩き 1.0 ／ 走り 1.2   ← すべてバニラのオオカミ用と同じ
 * </pre>
 *
 * <p>⚠ <b>数字を自分で決めていない。</b> バニラの関係をそのまま写すのがこの種族の狙いなので、
 * 独自の値を入れると「バニラ由来に見せる」という前提から外れる。
 *
 * <p>⚠ 対象は {@link AbstractSkeleton} なので<b>スケルトン・ストレイ・ウィザースケルトン</b>の3種。
 * イヌ獣人が与える傷を増やす側（{@code world3:skeleton_hunter}）が
 * {@code #minecraft:skeletons} タグを使っており、<b>そのタグの中身と同じ3種</b>になる。
 */
public final class SkeletonShy {

  private SkeletonShy() {
  }

  /** イヌ獣人の id。⚠ datapack 側（{@code origins_setup/build.py}）と揃える。 */
  private static final String DOG_ORIGIN = "world3:beastfolk_dog";

  @SubscribeEvent
  public static void onEntityJoin(final EntityJoinLevelEvent event) {

    if (!ShiftingOrigins.Config.SKELETON_SHY.get()
        || !(event.getEntity() instanceof AbstractSkeleton skeleton)) {
      return;
    }
    // ⚠ サーバ側だけで足す。クライアントの複製に目標を積んでも意味が無く、二重になる。
    if (skeleton.level().isClientSide()) {
      return;
    }
    final float distance = ShiftingOrigins.Config.SKELETON_SHY_DISTANCE.get().floatValue();
    skeleton.goalSelector.addGoal(3,
        new AvoidEntityGoal<>(skeleton, Player.class, distance, 1.0D, 1.2D,
            living -> living instanceof ServerPlayer player && isDog(player)));
  }

  /**
   * そのプレイヤーがイヌ獣人か。
   *
   * <p>⚠ 種族の読み出しは {@link ClassPowers} と<b>同じ経路</b>を通す
   * （判定を2か所に書かない。あちらの {@code hasOrigin} と同じ形）。
   */
  private static boolean isDog(final ServerPlayer player) {
    return io.github.edwinmindcraft.origins.api.capabilities.IOriginContainer.get(player)
        .map(c -> c.getOrigins().values().stream()
            .anyMatch(key -> key != null && key.location().toString().equals(DOG_ORIGIN)))
        .orElse(false);
  }
}
