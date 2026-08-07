package net.erutobusiness.shiftingorigins.mixin.client;

import io.github.apace100.origins.screen.ChooseOriginScreen;
import io.github.apace100.origins.screen.OriginDisplayScreen;
import net.erutobusiness.shiftingorigins.CancelClient;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 珠で開いた種族・職業の選択画面を <b>Esc と × でやめられる</b>ようにする。
 *
 * <p>上流は {@code shouldCloseOnEsc()} が {@code false} 固定で、閉じる手段がまったく無い。
 * 理由はある——層を空にしたまま閉じると {@code hasAllOrigins()} が偽のまま残り、
 * {@code SelectionInvulnerabilityMixin} が全ダメージを無効にする＝<b>無敵で詰む</b>。
 *
 * <p>そこで<b>やめてよい場面だけ</b>開ける。判断はサーバーが持ち（珠を使った直後だけ）、
 * クライアントは {@link CancelClient} の印を見るだけにしてある。
 * 閉じるときは必ずサーバーへ「やめる」を送り、サーバーが種族を戻して珠を返す。
 *
 * <p>閉じ方は2つとも {@code onClose()} に集まる:
 *
 * <pre>
 *   Esc  → Screen.keyPressed → shouldCloseOnEsc()==true → onClose()
 *   ×    → ボタンの処理 ────────────────────────────────→ onClose()
 * </pre>
 *
 * <p>⚠ <b>「選ぶ」を押した経路はここを通らない。</b> あちらは
 * {@code openNextLayerScreen()} → {@code Minecraft.setScreen(…)} で画面を差し替えるので
 * {@code onClose()} は呼ばれない。それでも印は下ろす（下の inject）——
 * 残したままだと、次に {@code /origin gui} 等で開いた画面まで閉じられてしまう。
 *
 * <p>⚠ 上流は {@code onClose()} を持たない（{@code ChooseOriginScreen} にも
 * {@code OriginDisplayScreen} にも無い）ので、ここで足したものが唯一の上書きになる。
 * 中身は {@code Screen.onClose()} と同じ1行を書き写してある（super を呼ばないため）。
 */
@Mixin(ChooseOriginScreen.class)
public abstract class ChooseOriginScreenMixin extends OriginDisplayScreen {

  private ChooseOriginScreenMixin(Component title, boolean showDirtBackground) {
    super(title, showDirtBackground);
  }

  /**
   * × ボタンを窓の右上に置く。
   *
   * <p>位置は上流の「＞」ボタンと同じ列（{@code guiLeft + 176 + 20}）の一番上。
   * {@code guiLeft} / {@code guiTop} は {@code OriginDisplayScreen} の protected フィールドで、
   * init の最後には計算済み。
   */
  @Inject(method = "m_7856_", at = @At("TAIL"), remap = false)
  private void shiftingorigins$addCloseButton(CallbackInfo ci) {
    if (!CancelClient.isCancelable()) {
      return;
    }
    this.addRenderableWidget(
        Button.builder(Component.literal("×"), button -> this.onClose())
            .bounds(this.guiLeft + 176 + 20, this.guiTop, 20, 20)
            .build());
  }

  @Inject(method = "m_6913_", at = @At("HEAD"), cancellable = true, remap = false)
  private void shiftingorigins$allowEsc(CallbackInfoReturnable<Boolean> cir) {
    if (CancelClient.isCancelable()) {
      cir.setReturnValue(true);
    }
  }

  /** 「選ぶ」を押した＝もうやめる話ではない。 */
  @Inject(method = "openNextLayerScreen", at = @At("HEAD"), remap = false)
  private void shiftingorigins$forgetOnChosen(CallbackInfo ci) {
    CancelClient.clear();
  }

  @Override
  public void onClose() {
    if (CancelClient.isCancelable()) {
      CancelClient.requestCancel();
    }
    this.minecraft.setScreen(null);
  }
}
