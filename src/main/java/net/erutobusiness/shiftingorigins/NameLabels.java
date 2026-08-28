package net.erutobusiness.shiftingorigins;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Score;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.server.ServerLifecycleHooks;

/**
 * `[種族・職業]` を <b>Tab の一覧とサイドバーにだけ</b> 出す。
 *
 * <h2>⚠⚠ なぜチームの prefix をやめたのか</h2>
 *
 * それまでは datapack（`club_stats`）がチームの prefix に飾りを入れていた。prefix を読むのは
 * {@code PlayerTeam.formatNameForTeam} で、これを呼ぶのは <b>サイドバー・頭上の名札・Tab・
 * チャット/死亡メッセージの4か所</b>。1つの値を4か所が共有しているので、
 * <b>出し分けができない</b>。⚠ Forge の {@code PlayerEvent.NameFormat} でも消せない——
 * patched な {@code Player.getDisplayName()} は、あのイベントの<b>後で</b>
 * {@code formatNameForTeam} を呼ぶ（bytecode で確認）。
 *
 * <h2>面ごとに道が違う（1.20.1 の bytecode で確認）</h2>
 *
 * <table><caption></caption>
 *   <tr><td><b>Tab</b></td><td>{@code PlayerTabOverlay.getNameForDisplay} は
 *       <b>tab の表示名が入っていればそれを使って返す</b>——
 *       {@code formatNameForTeam} を通らない。だから
 *       {@code PlayerEvent.TabListNameFormat} で入れれば、そこだけに出せる</td></tr>
 *   <tr><td><b>サイドバー</b></td><td>左の列は {@code formatNameForTeam} を通る。
 *       サーバー側から出し分ける手は無い。⚠ そこで<b>飾りを「点の持ち主の名前」そのものにする</b>
 *       ——サイドバーが並べているのは持ち主の文字列で、実在のプレイヤー名である必要が無い。
 *       {@code Scoreboard.getOrCreatePlayerScore} に長さの検査は無く
 *       （{@code computeIfAbsent} が2回だけ）、{@code ClientboundSetScorePacket} も
 *       {@code writeUtf} の既定（32767）なので、長い名前で困らない</td></tr>
 * </table>
 *
 * <h2>持ち主の名前でやると、オフラインの人も並ぶ</h2>
 *
 * 点は {@code scoreboard.dat} に残るので、<b>ログインしていない人の行も消えない</b>。
 * ⚠ これが目的そのもの（「オフライン中の他プレイヤーを見たい」）。
 * ⚠ ただし<b>一度もログインしていない人には飾りが付かない</b>——
 * 種族はオンラインのときにしか読めないため。入れば次の秒で付く。
 *
 * <h2>⚠ 止め方（配布の当日に効くように）</h2>
 *
 * config の {@code nameLabels.enabled} を false にすると、
 * <b>表示枠を元の項目へ戻し、旗を降ろす</b>。datapack 側は旗が 1 でないときだけ
 * prefix を書くので、<b>数秒で元の見え方に戻る</b>。⚠ 配布物の作り直しは要らない。
 */
public final class NameLabels {

  /** datapack が作る項目の頭。 */
  private static final String SRC_PREFIX = "club.";
  /** こちらが作る「飾り付きの持ち主」を入れる項目の頭。 */
  private static final String MIRROR_PREFIX = "view.";

  /** 表示枠の番号。1 が sidebar、3 + 色ID が sidebar.team.&lt;色&gt;。 */
  private static final int SLOT_SIDEBAR = 1;
  private static final int SLOT_TEAM_BASE = 3;
  private static final int SLOT_TEAM_COUNT = 16;

  /**
   * datapack 側へ「MOD が飾りを持っている」と伝える旗。
   *
   * <p>⚠ <b>持ち主名を `#` で始める</b>とサイドバーに出ない（バニラの決まり）。
   * ⚠ 置き場に {@code club.const} を使うのは、あれが datapack の作る dummy 項目で、
   * 定数（`#d100` 等）が既に入っているため。新しい項目を増やさずに済む。
   */
  private static final String FLAG_OWNER = "#labels";
  private static final String FLAG_OBJECTIVE = "club.const";

  /** 1秒ごとに動かす。 */
  private static final int PERIOD_TICKS = 20;

  /** 直前に貼った Tab 名の飾り。変わったときだけ貼り直す（毎秒配ると無駄）。 */
  private static final Map<UUID, String> lastLabel = new HashMap<>();

  private static int ticks;
  /** prefix の消し込みは起動後1回でよい。 */
  private static boolean prefixesCleared;

  private NameLabels() {
  }

  // ---------------------------------------------------------------- Tab

  /**
   * Tab の一覧の表示名。
   *
   * <p>⚠ <b>飾りが無いときは何も入れない</b>。入れないと Forge は null のままにし、
   * バニラが今までどおりチーム書式で描く（＝素の名前）。
   */
  @SubscribeEvent
  public static void onTabListName(PlayerEvent.TabListNameFormat event) {
    if (!ShiftingOrigins.Config.LABELS_ENABLED.get()
        || !ShiftingOrigins.Config.LABELS_TAB.get()) {
      return;
    }
    String label = OriginNames.label(event.getEntity());
    if (label.isEmpty()) {
      return;
    }
    event.setDisplayName(Component.literal(label + event.getEntity().getGameProfile().getName()));
  }

  // ---------------------------------------------------------------- 毎秒

  @SubscribeEvent
  public static void onServerTick(TickEvent.ServerTickEvent event) {
    if (event.phase != TickEvent.Phase.END) {
      return;
    }
    if (++ticks < PERIOD_TICKS) {
      return;
    }
    ticks = 0;
    MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
    if (server == null) {
      return;
    }
    Scoreboard sb = server.getScoreboard();
    if (!ShiftingOrigins.Config.LABELS_ENABLED.get()) {
      standDown(sb);
      return;
    }
    raiseFlag(sb, 1);
    clearTeamPrefixesOnce(sb);
    refreshTabNames(server);
    if (ShiftingOrigins.Config.LABELS_SIDEBAR.get()) {
      mirrorSidebar(server, sb);
    }
  }

  /** 止めるとき: 表示枠を元へ戻し、旗を降ろす。⚠ 鏡の項目は消さない（点を捨てないため）。 */
  private static void standDown(Scoreboard sb) {
    raiseFlag(sb, 0);
    prefixesCleared = false;
    lastLabel.clear();
    for (int slot : slots()) {
      Objective cur = sb.getDisplayObjective(slot);
      if (cur == null || !cur.getName().startsWith(MIRROR_PREFIX)) {
        continue;
      }
      Objective src = sb.getObjective(SRC_PREFIX + cur.getName().substring(MIRROR_PREFIX.length()));
      if (src != null) {
        sb.setDisplayObjective(slot, src);
      }
    }
  }

  private static void raiseFlag(Scoreboard sb, int value) {
    Objective flag = sb.getObjective(FLAG_OBJECTIVE);
    if (flag == null) {
      return;                                   // datapack がまだ読み込まれていない
    }
    Score s = sb.getOrCreatePlayerScore(FLAG_OWNER, flag);
    if (s.getScore() != value) {
      s.setScore(value);
    }
  }

  /**
   * 残っている prefix を1回だけ消す。
   *
   * <p>⚠ datapack 側は旗が立っている間 prefix を書かないが、
   * <b>既に書かれた prefix は消えない</b>（世界の scoreboard.dat に残る）。
   */
  private static void clearTeamPrefixesOnce(Scoreboard sb) {
    if (prefixesCleared) {
      return;
    }
    prefixesCleared = true;
    for (PlayerTeam team : sb.getPlayerTeams()) {
      if (team.getName().startsWith("club_p")) {
        team.setPlayerPrefix(Component.empty());
      }
    }
  }

  private static void refreshTabNames(MinecraftServer server) {
    if (!ShiftingOrigins.Config.LABELS_TAB.get()) {
      return;
    }
    for (ServerPlayer player : server.getPlayerList().getPlayers()) {
      String label = OriginNames.label(player);
      String prev = lastLabel.put(player.getUUID(), label);
      if (!label.equals(prev)) {
        // ⚠ 名前が変わったときだけ全員へ配る（Forge 側が Objects.equals で見ている）
        player.refreshTabListName();
      }
    }
  }

  // ---------------------------------------------------------------- サイドバー

  /**
   * 表示枠に出ている `club.*` を、飾り付きの持ち主を並べた `view.*` に置き換える。
   *
   * <p>⚠ <b>どの色にどの項目を出すかは datapack が決めている</b>。ここでは
   * 表示枠を読んで<b>そこに入っている項目から学ぶ</b>ので、対応表を二重に持たない。
   */
  private static void mirrorSidebar(MinecraftServer server, Scoreboard sb) {
    Set<String> sources = new HashSet<>();
    for (int slot : slots()) {
      Objective cur = sb.getDisplayObjective(slot);
      if (cur == null) {
        continue;
      }
      String name = cur.getName();
      if (name.startsWith(MIRROR_PREFIX)) {
        sources.add(SRC_PREFIX + name.substring(MIRROR_PREFIX.length()));
      } else if (name.startsWith(SRC_PREFIX)) {
        Objective mirror = ensureMirror(sb, cur);
        sb.setDisplayObjective(slot, mirror);
        sources.add(name);
      }
    }
    for (String src : sources) {
      Objective source = sb.getObjective(src);
      if (source != null) {
        mirrorOne(server, sb, source, ensureMirror(sb, source));
      }
    }
    dropOrphansEverywhere(sb);
  }

  /**
   * 点の元が消えた人の行を、<b>出していない項目からも</b>落とす。
   *
   * <p>⚠⚠ {@link #mirrorOne} の始末は<b>いま居る人のぶんだけ</b>で、しかも
   * <b>表示枠に出ている項目にしか回らない</b>。だから<b>居ない間に点を消された人</b>の行は
   * 誰にも消せず永久に残る。2026-08-28 に実際に残った——世界を差し替えて
   * {@code playerdata} を消したとき、その人はオフラインだったので
   * {@code [ヴァルキリー・ニート] エルト} の行が 8 項目に残り、
   * <b>入り直して種族を変えても、その行だけ古い種族のまま並ぶ</b>状態になっていた。
   *
   * <p>⚠ 落とすのは「元の {@code club.*} に点が無い人」だけ。
   * <b>オフラインでも点が在る人の行は残す</b>（それがこの鏡の目的なので）。
   */
  private static void dropOrphansEverywhere(Scoreboard sb) {
    for (Objective mirror : new ArrayList<>(sb.getObjectives())) {
      String name = mirror.getName();
      if (!name.startsWith(MIRROR_PREFIX)) {
        continue;
      }
      Objective source = sb.getObjective(SRC_PREFIX + name.substring(MIRROR_PREFIX.length()));
      if (source == null) {
        continue;
      }
      Set<String> alive = new HashSet<>();
      for (Score s : sb.getPlayerScores(source)) {
        String owner = s.getOwner();
        if (owner != null && !owner.startsWith("#")) {
          alive.add(owner);
        }
      }
      for (Score s : new ArrayList<>(sb.getPlayerScores(mirror))) {
        String holder = s.getOwner();
        if (holder != null && !alive.contains(plainName(holder))) {
          sb.resetPlayerScore(holder, mirror);
        }
      }
    }
  }

  private static Objective ensureMirror(Scoreboard sb, Objective source) {
    String name = MIRROR_PREFIX + source.getName().substring(SRC_PREFIX.length());
    Objective mirror = sb.getObjective(name);
    if (mirror == null) {
      mirror = sb.addObjective(name, ObjectiveCriteria.DUMMY, source.getDisplayName(),
          ObjectiveCriteria.RenderType.INTEGER);
    } else if (!mirror.getDisplayName().equals(source.getDisplayName())) {
      mirror.setDisplayName(source.getDisplayName());
    }
    return mirror;
  }

  /**
   * 1つの項目を写す。
   *
   * <p>⚠ <b>オフラインの人の行は触らない</b>。あちらの点は増えないし、飾りも変わらない。
   * ⚠ <b>いま居る人の古い行だけ消す</b>（種族を変えた直後に前の飾りが残るのを防ぐ）。
   */
  private static void mirrorOne(MinecraftServer server, Scoreboard sb,
                                Objective source, Objective mirror) {
    Map<String, Integer> values = new HashMap<>();
    for (Score s : sb.getPlayerScores(source)) {
      String owner = s.getOwner();
      if (owner != null && !owner.startsWith("#")) {
        values.put(owner, s.getScore());
      }
    }

    Map<String, String> wanted = new HashMap<>();
    for (ServerPlayer player : server.getPlayerList().getPlayers()) {
      String plain = player.getScoreboardName();
      wanted.put(plain, OriginNames.label(player) + plain);
    }

    // 古い行の始末。⚠ **いま居る人のぶんだけ**（オフラインの行は残すのが狙い）
    for (Score s : new ArrayList<>(sb.getPlayerScores(mirror))) {
      String holder = s.getOwner();
      if (holder == null) {
        continue;
      }
      String want = wanted.get(plainName(holder));
      if (want != null && !want.equals(holder)) {
        sb.resetPlayerScore(holder, mirror);
      }
    }

    for (Map.Entry<String, String> e : wanted.entrySet()) {
      Integer value = values.get(e.getKey());
      if (value == null) {
        continue;                               // その項目にまだ点が無い人
      }
      Score dst = sb.getOrCreatePlayerScore(e.getValue(), mirror);
      if (dst.getScore() != value) {
        dst.setScore(value);                    // ⚠ 変わったときだけ（点が動くと全員へ配られる）
      }
    }
  }

  /** `[種族・職業] エルト` → `エルト`。飾りが無ければそのまま返す。 */
  private static String plainName(String holder) {
    if (!holder.startsWith("[")) {
      return holder;
    }
    int end = holder.indexOf("] ");
    return end < 0 ? holder : holder.substring(end + 2);
  }

  private static int[] slots() {
    int[] out = new int[SLOT_TEAM_COUNT + 1];
    out[0] = SLOT_SIDEBAR;
    for (int i = 0; i < SLOT_TEAM_COUNT; i++) {
      out[i + 1] = SLOT_TEAM_BASE + i;
    }
    return out;
  }
}
