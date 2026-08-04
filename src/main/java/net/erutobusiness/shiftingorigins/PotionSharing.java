package net.erutobusiness.shiftingorigins;

import io.github.edwinmindcraft.apoli.api.component.IPowerContainer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 聖職者が飲んだポーションの効果を、近くのプレイヤーにも配る（2026-08-04）。
 *
 * <p><b>なぜ自作なのか</b>: apoli の実体アクションには {@code area_of_effect} も
 * {@code apply_effect} もあるが、{@code apply_effect} は<b>効果を JSON に固定で書く</b>型なので、
 * 「いま飲んだポーションの効果」を動的に写せない。だから JSON だけでは書けない。
 *
 * <p><b>なぜ聖職者なのか</b>: 当部は 2026-08-04 に聖職者をポーション専業にした
 * （{@code better_enchanting} は EnchantingInfuser に効かず死んでいたので外した）。
 * 残る {@code potion_bonus} は「水入り大釜で祝福したポーションは誰が飲んでも
 * 持続2倍／即効性はレベル+1」という<b>作った物に焼き付く</b>型なので、
 * こちらは「その場に居る人に効く」方向で重ならないようにしてある。
 *
 * <p><b>範囲はバニラを参照した</b>: スプラッシュポーションは
 * {@code AABB.inflate(4.0, 2.0, 4.0)}（{@code ThrownPotion.applySplash} を 1.20.1 の jar で確認）。
 * その倍を既定にしてある＝「投げるより広く配れる」。⚠ スプラッシュにある<b>距離減衰は付けない</b>。
 * 自分が中心なので、減衰があると立ち位置を気にするだけの手間になる。
 */
public final class PotionSharing {

  private static final Logger LOG = LoggerFactory.getLogger("shiftingorigins");

  /** この power を持っている人が配る。{@link ClassPowers} が聖職者に配る。 */
  public static final ResourceLocation POWER =
      new ResourceLocation(ShiftingOrigins.MOD_ID, "potion_sharing");

  private PotionSharing() {
  }

  @SubscribeEvent
  public static void onFinishUsing(final LivingEntityUseItemEvent.Finish event) {

    if (!(event.getEntity() instanceof ServerPlayer drinker)) {
      return;
    }
    ItemStack stack = event.getItem();
    // ⚠ 飲むポーションだけ。スプラッシュ・残留・矢はもともと範囲攻撃なので二重になる。
    //    PotionItem は「飲む瓶」だけで、SplashPotionItem / LingeringPotionItem は別クラス。
    if (!(stack.getItem() instanceof PotionItem)
        || stack.getItem() instanceof net.minecraft.world.item.SplashPotionItem
        || stack.getItem() instanceof net.minecraft.world.item.LingeringPotionItem) {
      return;
    }
    // ⚠ static の hasPower(Entity, PowerFactory) は工場を取る。ここは power の ID で見たいので、
    //    ClassPowers と同じく容器を取り出してから ID で問い合わせる。
    boolean has = IPowerContainer.get(drinker).map(c -> c.hasPower(POWER)).orElse(false);
    if (!has) {
      return;
    }

    // ⚠ **getMobEffects でなければならない**。1.20.1 には ItemStack を取る経路が2つあり、
    //    getMobEffects → getAllEffects(CompoundTag)、getCustomEffects → getCustomEffects(CompoundTag)。
    //    Origins Classes の PotionUtilMixin が刺さっているのは **getAllEffects(CompoundTag)** のほうなので、
    //    こちらを通せば「聖職者が祝福したポーション（持続2倍）」の値がそのまま配られる。
    //    （joined-1.20.1-srg.jar を逆アセンブルして呼び先を確認した）
    List<MobEffectInstance> share = new ArrayList<>();
    for (MobEffectInstance e : PotionUtils.getMobEffects(stack)) {
      if (isShared(e)) {
        share.add(new MobEffectInstance(e));
      }
    }
    if (share.isEmpty()) {
      return;
    }

    double h = ShiftingOrigins.Config.SHARE_RADIUS_H.get();
    double v = ShiftingOrigins.Config.SHARE_RADIUS_V.get();
    AABB box = drinker.getBoundingBox().inflate(h, v, h);
    int n = 0;
    for (Player other : drinker.level().getEntitiesOfClass(Player.class, box)) {
      if (other == drinker || other.isSpectator()) {
        continue;                       // 本人はバニラの経路で既に効いている
      }
      for (MobEffectInstance e : share) {
        other.addEffect(new MobEffectInstance(e), drinker);
      }
      n++;
    }
    if (n > 0) {
      LOG.info("[shifting] {} のポーション効果 {} 件を、周りの {} 人へ配った",
          drinker.getGameProfile().getName(), share.size(), n);
    }
  }

  /**
   * 配る対象か。カテゴリで決める（config）。
   *
   * <p>⚠ 既定を「有益＋無害」にしてあるのは<b>事故を防ぐため</b>——自分を強化するつもりで
   * 毒を撒くと、味方を巻き込む。意図して毒を配りたいときは config に HARMFUL を足す。
   */
  private static boolean isShared(final MobEffectInstance e) {
    String cat = e.getEffect().getCategory().name();      // BENEFICIAL / NEUTRAL / HARMFUL
    return ShiftingOrigins.Config.SHARE_CATEGORIES.get().contains(cat);
  }
}
