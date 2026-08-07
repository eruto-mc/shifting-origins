package net.erutobusiness.shiftingorigins;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * クライアント側の「いま開いている選択画面はやめられるか」の印。
 *
 * <p>この印が立っているときだけ、{@code ChooseOriginScreen} は Esc で閉じ、× ボタンを出す。
 * 立てるのはサーバー（珠を使った直後）だけ。
 *
 * <p>⚠ **既定で立てない**のが肝。{@code /origin gui} など珠以外で開いた画面まで閉じられると、
 * 層が空のまま残って {@code hasAllOrigins()} が偽＝**無敵の状態で詰む**。
 *
 * <p>⚠ このクラスはクライアントでしか読み込まれない（{@code Dist.CLIENT}）。
 * サーバー側から名前で触らないこと。{@link Net} は {@code DistExecutor} 越しに呼んでいる。
 */
@Mod.EventBusSubscriber(modid = ShiftingOrigins.MOD_ID, value = Dist.CLIENT,
    bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CancelClient {

  private static boolean cancelable;

  private CancelClient() {
  }

  public static void set(boolean value) {
    cancelable = value;
  }

  public static boolean isCancelable() {
    return cancelable;
  }

  /** やめると伝えて、印を下ろす。 */
  public static void requestCancel() {
    cancelable = false;
    Net.toServer(new Net.CancelRequest());
  }

  /** 選び終えた・画面が別のものに変わった等。伝えずに印だけ下ろす。 */
  public static void clear() {
    cancelable = false;
  }

  /**
   * 接続が切れたら印を下ろす。
   *
   * <p>⚠ 残したままだと、入り直したあとに {@code /origin gui} で開いた画面まで
   * Esc で閉じられてしまう。そのときサーバーには控えが無い（退場時に捨てている）ので
   * **層が空のまま閉じる**＝無敵で詰む。両側で同時に捨てることでこれを防ぐ。
   */
  @SubscribeEvent
  public static void onLoggingOut(final ClientPlayerNetworkEvent.LoggingOut event) {
    cancelable = false;
  }
}
