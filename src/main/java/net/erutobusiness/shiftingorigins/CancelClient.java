package net.erutobusiness.shiftingorigins;

import io.github.apace100.origins.screen.OriginSelectionCancel;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * クライアント側の「いま開いている選択画面はやめられるか」を、origins 本体へ渡す口。
 *
 * <p>⚠⚠ <b>2026-09-01 に、実装を origins 本体へ移した</b>（依頼者の意向
 * 「⚠ 複数の MOD や datapack に散らばってしまったものを
 * ⚠⚠ <b>1つの MOD に1つの実装として</b>まとめれば管理もしやすくバグもなくなる」）。
 *
 * <p>それまでは {@code ChooseOriginScreenMixin} が
 * {@code io.github.apace100.origins.screen.ChooseOriginScreen} を<b>外から書き換えて</b>いた。
 * ⚠ 1つの画面に実装が2つあり、⚠⚠ <b>両方を開かないと何が起きるか分からない</b>状態だった。
 *
 * <p>いまは向きが逆で、<b>origins が印と口を持ち、ここが中身を登録する</b>
 * （{@link OriginSelectionCancel}）。⚠ 依存は片道なので循環しない。
 *
 * <p>⚠ このクラスはクライアントでしか読み込まれない（{@code Dist.CLIENT}）。
 * サーバー側から名前で触らないこと。{@link Net} は {@code DistExecutor} 越しに呼んでいる。
 */
@Mod.EventBusSubscriber(modid = ShiftingOrigins.MOD_ID, value = Dist.CLIENT,
    bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CancelClient {

  private CancelClient() {
  }

  /**
   * origins 本体へ「やめると伝える中身」を渡す。
   *
   * <p>⚠ 呼ばれなければ origins 側は<b>何もしない口</b>のままなので、
   * ⚠⚠ <b>この MOD を外した構成でも画面は壊れない</b>（Esc も × も出ないだけ）。
   */
  public static void init() {
    OriginSelectionCancel.setSender(() -> Net.toServer(new Net.CancelRequest()));
  }

  public static void set(boolean value) {
    OriginSelectionCancel.set(value);
  }

  public static boolean isCancelable() {
    return OriginSelectionCancel.isCancelable();
  }

  /** やめると伝えて、印を下ろす。 */
  public static void requestCancel() {
    OriginSelectionCancel.requestCancel();
  }

  /** 選び終えた・画面が別のものに変わった等。伝えずに印だけ下ろす。 */
  public static void clear() {
    OriginSelectionCancel.clear();
  }

  /**
   * 接続が切れたら印を下ろす。
   *
   * <p>⚠ 残したままだと、入り直したあとに {@code /origin gui} で開いた画面まで
   * Esc で閉じられてしまう。そのときサーバーには控えが無い（退場時に捨てている）ので
   * <b>層が空のまま閉じる</b>＝無敵で詰む。両側で同時に捨てることでこれを防ぐ。
   */
  @SubscribeEvent
  public static void onLoggingOut(final ClientPlayerNetworkEvent.LoggingOut event) {
    OriginSelectionCancel.clear();
  }
}
