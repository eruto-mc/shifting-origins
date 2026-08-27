package net.erutobusiness.shiftingorigins;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderNameTagEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 人の頭に乗っているあいだ、**乗られている相手の名前札を自分の画面から消す**。
 *
 * <p>なぜ要るか: ピクシーは `medievalorigins:pixie/mount`（両手を空にして人を右クリック）で
 * 相手に乗れる。乗ると視点が相手の頭のすぐ上に来るので、
 * **相手の名前札が目の前に貼り付いて前が見えない**。
 *
 * <p>⚠ **消えるのは乗っている本人の画面だけ。** 他の人からは今までどおり名前札が見える。
 * サーバー側は何も変えていない（チームの `nametagVisibility` を使うと**全員から**消えてしまう）。
 *
 * <p>やり方: Forge が `EntityRenderer.render` に差し込んでいる
 * {@code RenderNameTagEvent} を DENY にする。DENY は `renderNameTag` の呼び出しごと飛ばすので、
 * 名前の下に出る順位表の点数も一緒に消える。
 *
 * <p>⚠ 条件を「乗り物が**人**のとき」に絞ってある。馬・ボート・トロッコに乗っても効かない
 * （人に乗れるのはピクシーだけなので、種族を見に行かなくても同じことになる）。
 *
 * <p>⚠ このクラスはクライアントでしか読み込まれない（{@code Dist.CLIENT}）。
 * サーバー側から名前で触らないこと。
 */
@Mod.EventBusSubscriber(modid = ShiftingOrigins.MOD_ID, value = Dist.CLIENT,
    bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MountNameTagClient {

  private MountNameTagClient() {
  }

  /** 自分がいま乗っている人の名前札だけを描かせない。 */
  @SubscribeEvent
  public static void onRenderNameTag(RenderNameTagEvent event) {
    LocalPlayer self = Minecraft.getInstance().player;
    if (self == null) {
      return;
    }
    Entity vehicle = self.getVehicle();
    if (vehicle instanceof Player && event.getEntity() == vehicle) {
      event.setResult(Event.Result.DENY);
    }
  }
}
