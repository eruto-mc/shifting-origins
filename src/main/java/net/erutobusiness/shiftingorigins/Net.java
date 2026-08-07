package net.erutobusiness.shiftingorigins;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * このMOD専用の通信路。**選択画面をやめる**ためだけに使う。
 *
 * <p>なぜ要るか: Esc とボタンはクライアントの出来事だが、種族を戻すのも珠を返すのも
 * サーバーの仕事なので、間を繋ぐものが要る。Origins 側の通信路には
 * 「やめる」に当たる便りが無い（{@code C2SChooseOrigin} / {@code C2SChooseRandomOrigin} /
 * {@code C2SAcknowledgeOrigins} の3つだけ）。
 *
 * <p>⚠ **このMODは client と server の両方に置く**（mods.toml の side は BOTH）。
 * 片側にしか無いと Forge の握手で弾かれる。
 */
public final class Net {

  private static final String VERSION = "1";

  public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
      new ResourceLocation(ShiftingOrigins.MOD_ID, "main"),
      () -> VERSION, VERSION::equals, VERSION::equals);

  /** サーバー → クライアント。「いま開いている選択画面はやめられる」。 */
  public record CancelAllowed(boolean allowed) {
  }

  /** クライアント → サーバー。「やめる」。中身は無い。 */
  public record CancelRequest() {
  }

  private Net() {
  }

  static void register() {
    CHANNEL.registerMessage(0, CancelAllowed.class,
        (msg, buf) -> buf.writeBoolean(msg.allowed()),
        buf -> new CancelAllowed(buf.readBoolean()),
        Net::onCancelAllowed);
    CHANNEL.registerMessage(1, CancelRequest.class,
        (msg, buf) -> {
        },
        buf -> new CancelRequest(),
        Net::onCancelRequest);
  }

  public static void toPlayer(ServerPlayer player, Object message) {
    CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), message);
  }

  public static void toServer(Object message) {
    CHANNEL.send(PacketDistributor.SERVER.noArg(), message);
  }

  private static void onCancelAllowed(CancelAllowed msg, java.util.function.Supplier<NetworkEvent.Context> ctx) {
    NetworkEvent.Context context = ctx.get();
    // ⚠ クライアント専用のクラスは**ラムダの中でしか触らない**。専用サーバーでは
    //    そのラムダが走らないので、クラス読み込みごと起きない。
    context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
        Dist.CLIENT, () -> () -> CancelClient.set(msg.allowed())));
    context.setPacketHandled(true);
  }

  private static void onCancelRequest(CancelRequest msg, java.util.function.Supplier<NetworkEvent.Context> ctx) {
    NetworkEvent.Context context = ctx.get();
    context.enqueueWork(() -> {
      ServerPlayer sender = context.getSender();
      if (sender != null) {
        OriginChangeCancel.cancel(sender);
      }
    });
    context.setPacketHandled(true);
  }
}
