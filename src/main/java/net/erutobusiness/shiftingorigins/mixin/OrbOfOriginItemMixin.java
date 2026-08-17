package net.erutobusiness.shiftingorigins.mixin;

import io.github.apace100.origins.content.OrbOfOriginItem;
import io.github.edwinmindcraft.origins.api.origin.Origin;
import io.github.edwinmindcraft.origins.api.origin.OriginLayer;
import java.util.List;
import java.util.Map;
import net.erutobusiness.shiftingorigins.OriginChangeCancel;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 珠（Orb of Origin）に手を入れる3件。
 *
 * <h2>1. 説明欄の「」を直す</h2>
 *
 * <p>上流の {@code appendHoverText} は、珠が指す種族が {@code Origin.EMPTY} と
 * <b>同一オブジェクトなら</b>「新しい〜を選び直せる」、違えば「〜を『(名前)』にする」と出す。
 * 当部の珠は NBT に {@code Origin:"origins:empty"} を持ち、これはレジストリから引いた
 * <b>別のオブジェクト</b>なので後者に落ちる。その名前は
 * {@code Component.literal("")}（{@code Origin} クラスの静的初期化で空文字を渡している）
 * なので、<b>「種族を『』にする。」</b>と表示されていた。
 *
 * <p>⚠ NBT から {@code Origin} を外せば直る、ではない。{@code getTargets} は
 * {@code Origin} を持たない項目を<b>捨てる</b>ので、対象0件＝「全部の層を選び直す」に化ける。
 * 名前が空のときに「選び直せる」側の文言を出す、が正しい直し方。
 *
 * <h2>2. やめられるようにするための控え</h2>
 *
 * <p>詳細は {@link OriginChangeCancel}。ここは「層が空にされる前に控える」ことだけを担う。
 *
 * <h2>3. 重ねられるようにする（2026-08-17 追加）</h2>
 *
 * <p>上流はコンストラクタで {@code new Item.Properties().stacksTo(1)} と<b>焼き込んで</b>いる
 * （bytecode: {@code iconst_1} → {@code m_41487_}）。当部は珠を実績の褒美として配るので、
 * 1個ずつ枠を潰されると持ち物が埋まる。
 *
 * <p>⚠ <b>上流の値を書き換えるのではなく、Forge の拡張を上書きする。</b>
 * バニラの {@code Item#getMaxStackSize()} は <b>{@code final}</b> で、実体の
 * {@code Item.f_41370_} も {@code private final} なので、どちらも触れない。
 * ところが<b>パッチ後の</b> {@code ItemStack#getMaxStackSize()} は
 * {@code Item.getMaxStackSize(ItemStack)}（Forge が足した既定メソッド）を呼んでいる
 * ——だから<b>そちらを上書きすれば効く</b>。
 * 確認: {@code forge-1.20.1-47.4.22-server.jar} の {@code ItemStack.m_41741_} が
 * {@code invokevirtual Item.getMaxStackSize:(Lnet/minecraft/world/item/ItemStack;)I}。
 *
 * <p>⚠ <b>使ったときに溶けないことを先に確かめてある。</b> 上流の {@code use} は
 * {@code isCreative()} でなければ {@code shrink(1)} を呼ぶ（bytecode: {@code iconst_1} →
 * {@code m_41774_}）ので、<b>重なっていても1個ずつ減る</b>。
 * {@link OriginChangeCancel} の返却も {@code setCount(1)} なので噛み合う。
 *
 * <p>⚠⚠ <b>NBT が違う珠は互いに重ならない</b>（バニラの決まり）。当部の珠は
 * {@code Targets} に層を持つので、<b>同じ層を指す珠同士だけが重なる</b>——
 * 「種族の珠」×64 と「職業の珠」×64 は別の山になる。これは仕様で、直せない。
 *
 * <p>⚠ <b>メソッドは難読化後の名前で指定して {@code remap = false} にする。</b>
 * {@code m_7203_}＝{@code use} / {@code m_7373_}＝{@code appendHoverText}。
 * 出荷済みの origins jar は既に SRG 名なので、名前の付け替えを挟まないほうが確実に当たる。
 */
@Mixin(OrbOfOriginItem.class)
public abstract class OrbOfOriginItemMixin {

  /** 珠を重ねられる上限。⚠ 変えるときは上の「3.」の注意を読む。 */
  private static final int STACK_LIMIT = 64;

  @Invoker(value = "getTargets", remap = false)
  abstract Map<OriginLayer, Origin> shiftingorigins$targets(ItemStack stack);

  /**
   * Forge の {@code IForgeItem#getMaxStackSize(ItemStack)} を上書きする。
   *
   * <p>⚠ <b>名前を {@code shiftingorigins$} で飾ってはいけない。</b>
   * 上書きが成り立つのは名前が一致しているからで、飾ると「ただの新しいメソッド」になり
   * <b>誰も呼ばないまま静かに効かなくなる</b>。
   *
   * <p>⚠ 上流の {@code OrbOfOriginItem} はこのメソッドを持っていないので、衝突しない
   * （{@code javap} で全メソッドを確認済み: {@code <init>} / {@code m_7203_} /
   * {@code m_7373_} / {@code getTargets} と lambda 2本だけ）。
   */
  public int getMaxStackSize(ItemStack stack) {
    return STACK_LIMIT;
  }

  @Inject(method = "m_7373_", at = @At("HEAD"), cancellable = true, remap = false)
  private void shiftingorigins$readableTooltip(ItemStack stack, Level level,
      List<Component> lines, TooltipFlag flag, CallbackInfo ci) {

    Map<OriginLayer, Origin> targets = shiftingorigins$targets(stack);
    if (targets.isEmpty()
        || targets.values().stream().noneMatch(OrbOfOriginItemMixin::shiftingorigins$isBlank)) {
      // 素の珠（対象なし）と、具体的な種族を指す珠は上流の文言のままでよい
      return;
    }
    targets.forEach((layer, origin) -> lines.add(
        (shiftingorigins$isBlank(origin)
            ? Component.translatable("item.origins.orb_of_origin.layer_generic", layer.name())
            : Component.translatable("item.origins.orb_of_origin.layer_specific",
                layer.name(), origin.getName()))
            .withStyle(ChatFormatting.GRAY)));
    ci.cancel();
  }

  @Inject(method = "m_7203_", at = @At("HEAD"), remap = false)
  private void shiftingorigins$rememberBeforeUse(Level level, Player player, InteractionHand hand,
      CallbackInfoReturnable<InteractionResultHolder<ItemStack>> cir) {

    if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
      OriginChangeCancel.remember(serverPlayer, serverPlayer.getItemInHand(hand),
          !serverPlayer.getAbilities().instabuild);
    }
  }

  @Inject(method = "m_7203_", at = @At("RETURN"), remap = false)
  private void shiftingorigins$allowCancelAfterUse(Level level, Player player, InteractionHand hand,
      CallbackInfoReturnable<InteractionResultHolder<ItemStack>> cir) {

    if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
      OriginChangeCancel.announce(serverPlayer);
    }
  }

  /** 名前を持たない種族＝「未選択に戻す」ための指定。上流の {@code Origin.EMPTY} も含む。 */
  private static boolean shiftingorigins$isBlank(Origin origin) {
    return origin == Origin.EMPTY || origin.getName().getString().isEmpty();
  }
}
