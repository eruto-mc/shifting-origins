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
 * 珠（Orb of Origin）に手を入れる2件。
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
 * <p>⚠ <b>メソッドは難読化後の名前で指定して {@code remap = false} にする。</b>
 * {@code m_7203_}＝{@code use} / {@code m_7373_}＝{@code appendHoverText}。
 * 出荷済みの origins jar は既に SRG 名なので、名前の付け替えを挟まないほうが確実に当たる。
 */
@Mixin(OrbOfOriginItem.class)
public abstract class OrbOfOriginItemMixin {

  @Invoker(value = "getTargets", remap = false)
  abstract Map<OriginLayer, Origin> shiftingorigins$targets(ItemStack stack);

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
