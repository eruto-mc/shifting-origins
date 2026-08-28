package net.erutobusiness.shiftingorigins;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import io.github.edwinmindcraft.origins.api.capabilities.IOriginContainer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;

/**
 * 種族・職業の **id → 和名** の対応表と、そこから作る `[種族・職業] ` の飾り。
 *
 * <h2>⚠ なぜ jar に和名を焼くのか</h2>
 *
 * サイドバーへ出す文字列は「点の持ち主の名前」＝<b>ただの文字列</b>なので、
 * {@code Component.translatable} でクライアントに訳させる手が使えない。
 * ⚠ 専用サーバの {@code Language} は jar の en_us しか持たないため、
 * <b>和名をサーバ側が持っているしかない</b>。
 *
 * <h2>⚠⚠ この表を手で書かない</h2>
 *
 * 中身は {@code worlds/world-3/dev/work/export_origin_names.py} が書き出す。
 * 出どころは部員へ配る早見表と<b>同じ窓口</b>
 * （{@code handouts/build_origins_sheet.py} → {@code dev/work/ja_lang.py}）で、
 * ⚠ 別の読み方をすると<b>紙と画面で名前が食い違う</b>。
 * 種族・職業を足したり和名を直したら、あの script を回して jar を作り直す。
 */
public final class OriginNames {

  /** ⚠ datapack 側の path と合わせてある（`data/shiftingorigins/origin_names.json`）。 */
  private static final String RESOURCE = "/data/shiftingorigins/origin_names.json";

  private static Map<String, String> race = Collections.emptyMap();
  private static Map<String, String> clazz = Collections.emptyMap();
  private static String raceLayer = "origins:origin";
  private static String classLayer = "origins-classes:class";
  private static boolean loaded;

  private OriginNames() {
  }

  /**
   * 表を読む。⚠ <b>読めなくても落とさない</b>——飾りが付かないだけで、
   * 世界は今までどおり動くほうが害が小さい。
   */
  private static synchronized void load() {
    if (loaded) {
      return;
    }
    loaded = true;
    try (var in = OriginNames.class.getResourceAsStream(RESOURCE)) {
      if (in == null) {
        return;
      }
      JsonObject root = JsonParser
          .parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
      if (root.has("layers")) {
        JsonObject layers = root.getAsJsonObject("layers");
        if (layers.has("race")) {
          raceLayer = layers.get("race").getAsString();
        }
        if (layers.has("class")) {
          classLayer = layers.get("class").getAsString();
        }
      }
      race = read(root, "race");
      clazz = read(root, "class");
    } catch (Exception e) {
      race = Collections.emptyMap();
      clazz = Collections.emptyMap();
    }
  }

  private static Map<String, String> read(JsonObject root, String key) {
    Map<String, String> out = new HashMap<>();
    if (!root.has(key)) {
      return out;
    }
    for (var e : root.getAsJsonObject(key).entrySet()) {
      out.put(e.getKey(), e.getValue().getAsString());
    }
    return out;
  }

  /**
   * その人の飾り。付けるものが無ければ<b>空文字</b>を返す（null は返さない）。
   *
   * <p>形は {@code "[種族・職業] "}。職業だけ未選択なら {@code "[種族] "}、
   * 種族が読めなければ空。⚠ <b>末尾の空白まで含めて返す</b>——
   * 呼ぶ側で足し忘れると名前とくっつく。
   *
   * <p>⚠ 層の id は {@code getOrigins()} の鍵の {@code location()} で見る。
   * こうすると Origins の registry key の定数を持たずに済み、
   * 上流の作りが変わっても壊れにくい。
   */
  public static String label(Player player) {
    load();
    if (race.isEmpty()) {
      return "";
    }
    IOriginContainer container = IOriginContainer.get(player).resolve().orElse(null);
    if (container == null) {
      return "";
    }
    String raceName = null;
    String className = null;
    for (Map.Entry<ResourceKey<io.github.edwinmindcraft.origins.api.origin.OriginLayer>,
        ResourceKey<io.github.edwinmindcraft.origins.api.origin.Origin>> e
        : container.getOrigins().entrySet()) {
      String layer = e.getKey().location().toString();
      String origin = e.getValue().location().toString();
      if (layer.equals(raceLayer)) {
        raceName = race.get(origin);
      } else if (layer.equals(classLayer)) {
        className = clazz.get(origin);
      }
    }
    if (raceName == null || raceName.isEmpty()) {
      return "";
    }
    if (className == null || className.isEmpty()) {
      return "[" + raceName + "] ";
    }
    return "[" + raceName + "・" + className + "] ";
  }

  /**
   * その人の<b>種族の id</b>（`origins:blazeborn` など）。読めなければ空文字。
   *
   * <p>⚠ 層の id は表（`origin_names.json`）が持っている値で見る。
   * こうすると Origins の registry key の定数を持たずに済む。
   */
  public static String raceId(Player player) {
    load();
    IOriginContainer container = IOriginContainer.get(player).resolve().orElse(null);
    if (container == null) {
      return "";
    }
    for (Map.Entry<ResourceKey<io.github.edwinmindcraft.origins.api.origin.OriginLayer>,
        ResourceKey<io.github.edwinmindcraft.origins.api.origin.Origin>> e
        : container.getOrigins().entrySet()) {
      if (e.getKey().location().toString().equals(raceLayer)) {
        return e.getValue().location().toString();
      }
    }
    return "";
  }

  /** 表が読めているか（検査と記録用）。 */
  public static int size() {
    load();
    return race.size() + clazz.size();
  }
}
