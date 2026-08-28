package net.erutobusiness.shiftingorigins;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/**
 * Origins Classes の一括破壊（木こりの伐採・datapack で足した鉱脈掘り）を、
 * 同じ tick に全部ではなく少しずつ壊すようにする。
 *
 * <p>なぜ要るか: Origins Classes は最大255ブロックを1tickで壊す。小さい木なら気にならないが、
 * 巨木や大鉱脈は一瞬で消えて手応えが無く、負荷も1tickに集中する。
 *
 * <p>やり方: {@code MultiMinePower.apply} が返すブロック一覧を横取りして**空の一覧を返し**、
 * 実際の破壊はこちらのキューが数tickかけて行う。破壊は
 * {@code ServerPlayerGameMode.destroyBlock} を通すので、ドロップ・経験値・道具の消耗・
 * 他MODのイベントは手掘りとまったく同じに走る。
 */
@Mod(ShiftingOrigins.MOD_ID)
public final class ShiftingOrigins {

  public static final String MOD_ID = "shiftingorigins";

  /**
   * 鉱脈用の一括破壊 power。Origins Classes の `MultiMinePower` を**そのまま使い、
   * 探索だけ差し替える**（`apply` は `instanceof MultiMinePower` で工場を拾うので、
   * こちらの工場も同じ経路で動く）。木こりは上流のまま触らない。
   */
  public static final net.minecraftforge.registries.DeferredRegister<
      io.github.edwinmindcraft.apoli.api.power.factory.PowerFactory<?>> POWER_FACTORIES =
      net.minecraftforge.registries.DeferredRegister.create(
          io.github.edwinmindcraft.apoli.api.registry.ApoliRegistries.POWER_FACTORY_KEY, MOD_ID);

  public static final net.minecraftforge.registries.RegistryObject<
      dev.limonblaze.originsclasses.common.apoli.power.MultiMinePower> ORE_VEIN =
      POWER_FACTORIES.register("vein_mine",
          () -> new dev.limonblaze.originsclasses.common.apoli.power.MultiMinePower(
              OreVeinRange::find));

  public ShiftingOrigins() {
    POWER_FACTORIES.register(FMLJavaModLoadingContext.get().getModEventBus());
    FMLJavaModLoadingContext.get().getModEventBus().addListener(ShiftingOrigins::onConfigLoad);
    net.minecraftforge.fml.ModLoadingContext.get()
        .registerConfig(ModConfig.Type.SERVER, Config.SPEC);
    Net.register();
    net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(PacedBreakQueue.class);
    net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(ClassPowers.class);
    net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(PotionSharing.class);
    net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(OriginChangeCancel.class);
    net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(OriginLayerGuard.class);
    net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(SkeletonShy.class);
    // ⚠ `[種族・職業]` を Tab とサイドバーにだけ出す。名札・チャット・死亡メッセージには出さない。
    //   ⚠ 止め方は config の nameLabels.enabled（datapack 側が数秒で元へ戻る）。理由は NameLabels。
    net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(NameLabels.class);
    // ⚠ 精気吸収がアンデッドから吸えていた（上流の説明は「効かない」と書いているのに実装が無い）
    net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(SiphonGuard.class);
    // ⚠ 溶岩入りの瓶（ブレイズボーンの水）は **Mixin 側**で拾う（`mixin/ItemMixin`）。
    //   ⚠ イベントで拾うと**啜るモーションが出ない**ので、ここには登録しない。
    // ⚠ 能力名の横に自動で付く印（A / T / R）を、画面へ出る前に外す。
    //   ⚠ datapack では消せない（消しても内蔵の絵で足される）。理由は AutoBadgeStrip の説明。
    net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(AutoBadgeStrip.class);
  }

  private static void onConfigLoad(final ModConfigEvent event) {
    // 値は都度 get するので、ここでは何もしない（読み込み順の事故を避ける）
  }

  public static final class Config {

    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.IntValue INTERVAL_TICKS;
    public static final ForgeConfigSpec.IntValue BLOCKS_PER_BATCH;
    public static final ForgeConfigSpec.IntValue MAX_TOTAL_TICKS;
    public static final ForgeConfigSpec.IntValue MAX_DISTANCE;
    public static final ForgeConfigSpec.IntValue ORE_VEIN_MAX;
    public static final ForgeConfigSpec.DoubleValue SHARE_RADIUS_H;
    public static final ForgeConfigSpec.DoubleValue SHARE_RADIUS_V;
    public static final ForgeConfigSpec.ConfigValue<java.util.List<? extends String>> SHARE_CATEGORIES;
    public static final ForgeConfigSpec.BooleanValue GUARD_ENABLED;
    public static final ForgeConfigSpec.IntValue GUARD_GRACE_TICKS;
    public static final ForgeConfigSpec.BooleanValue SKELETON_SHY;
    public static final ForgeConfigSpec.DoubleValue SKELETON_SHY_DISTANCE;
    public static final ForgeConfigSpec.BooleanValue LABELS_ENABLED;
    public static final ForgeConfigSpec.BooleanValue LABELS_TAB;
    public static final ForgeConfigSpec.BooleanValue LABELS_SIDEBAR;
    public static final ForgeConfigSpec.BooleanValue SIPHON_SKIP_UNDEAD;
    public static final ForgeConfigSpec.BooleanValue LAVA_BOTTLE_DRINK;
    public static final ForgeConfigSpec.IntValue LAVA_BOTTLE_THIRST;
    public static final ForgeConfigSpec.DoubleValue LAVA_BOTTLE_HYDRATION;
    public static final ForgeConfigSpec.BooleanValue VERBOSE_LOGS;

    static {
      ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
      INTERVAL_TICKS = b
          .comment("How many ticks to wait between batches. 0 restores the vanilla Origins Classes",
              "behaviour of breaking everything in the same tick.",
              "1 tick per block (20 blocks a second) is the club default; maxTotalTicks raises the",
              "batch size on top of that so a large vein still finishes in a few seconds.")
          .defineInRange("intervalTicks", 1, 0, 200);
      BLOCKS_PER_BATCH = b
          .comment("How many blocks to break per batch.")
          .defineInRange("blocksPerBatch", 1, 1, 1000);
      MAX_TOTAL_TICKS = b
          .comment("Upper bound, in ticks, for finishing one vein. If the configured pace would",
              "take longer, the batch size is raised so that a huge vein does not keep the player",
              "waiting. 0 disables the bound.")
          .defineInRange("maxTotalTicks", 100, 0, 12000);
      MAX_DISTANCE = b
          .comment("Blocks further than this from the player are skipped. Stops the vein from",
              "continuing after the player walks away.")
          .defineInRange("maxDistanceFromPlayer", 48, 4, 256);
      ORE_VEIN_MAX = b
          .comment("Upper bound for the ore vein power added by this mod. The tree felling power",
              "from Origins Classes has its own hard-coded limit of 255 and is not affected.")
          .defineInRange("oreVeinMaxBlocks", 160, 1, 4096);
      b.comment("Cleric potion sharing. A cleric's drunk potion also reaches nearby players.",
              "The vanilla anchor is the splash potion, which uses inflate(4.0, 2.0, 4.0) in",
              "ThrownPotion.applySplash. The defaults here are twice that: a cleric reaches",
              "further by drinking than anyone reaches by throwing.")
          .push("potionSharing");
      SHARE_RADIUS_H = b
          .comment("Horizontal reach in blocks. Vanilla splash is 4.0.")
          .defineInRange("radiusHorizontal", 8.0D, 0.0D, 64.0D);
      SHARE_RADIUS_V = b
          .comment("Vertical reach in blocks. Vanilla splash is 2.0.")
          .defineInRange("radiusVertical", 4.0D, 0.0D, 64.0D);
      SHARE_CATEGORIES = b
          .comment("Which effect categories get shared: BENEFICIAL, NEUTRAL, HARMFUL.",
              "HARMFUL is left out by default so that a cleric buffing themselves does not",
              "poison the people standing next to them. Add it if you want that on purpose.")
          .defineList("categories", java.util.List.of("BENEFICIAL", "NEUTRAL"),
              o -> o instanceof String s2
                  && (s2.equals("BENEFICIAL") || s2.equals("NEUTRAL") || s2.equals("HARMFUL")));
      b.pop();
      b.comment("Safety net for players left with an empty origin layer. While a layer is empty",
              "Origins makes the player invulnerable to every damage type, which is meant as",
              "protection while the choose-origin screen is up. /origin gui empties the layer",
              "without ever opening that screen, so the player is stuck invulnerable instead.",
              "This puts the layer's default origin back, using the same call the login handler",
              "uses. A selection started from an orb is left alone.")
          .push("originGuard");
      GUARD_ENABLED = b
          .comment("Whether to put the default origin back.")
          .define("enabled", true);
      GUARD_GRACE_TICKS = b
          .comment("How long a layer has to stay empty before the default origin goes back in.",
              "Do not set this to 0: another mod may empty a layer for a moment before writing",
              "the new origin, and this net would win that race.")
          .defineInRange("graceTicks", 60, 20, 12000);
      b.pop();
      b.comment("Skeletons back away from the club's beastfolk_dog origin, the way vanilla",
              "skeletons back away from wolves. Vanilla hard-codes Wolf.class in",
              "AbstractSkeleton.registerGoals, so a datapack cannot put a player in that slot.",
              "The numbers below are vanilla's own: 6 blocks, walk 1.0, sprint 1.2, priority 3.")
          .push("skeletonShy");
      SKELETON_SHY = b
          .comment("Whether skeletons avoid the dog beastfolk.")
          .define("enabled", true);
      SKELETON_SHY_DISTANCE = b
          .comment("How far away a skeleton starts backing off, in blocks. Vanilla uses 6.0 for",
              "wolves. Raising this makes skeleton-heavy areas much easier for the dog.")
          .defineInRange("distance", 6.0D, 1.0D, 32.0D);
      b.pop();
      b.comment("Show [origin/class] next to player names in the tab list and the scoreboard",
              "sidebar ONLY -- not above the head, not in chat, not in death messages.",
              "The club datapack used to do this with a team prefix, but a team prefix is read by",
              "all four of those places at once, so it could not be limited to two of them.",
              "Turning this off puts the sidebar display slots back to the club.* objectives and",
              "lowers the flag the datapack watches, so the old look returns within a few seconds",
              "without rebuilding anything.")
          .push("nameLabels");
      LABELS_ENABLED = b
          .comment("Whether this mod owns the origin/class labels.")
          .define("enabled", true);
      LABELS_TAB = b
          .comment("Put the label in the tab list (PlayerEvent.TabListNameFormat).")
          .define("tabList", true);
      LABELS_SIDEBAR = b
          .comment("Put the label in the sidebar, by mirroring each club.* objective into a",
              "view.* one whose score holders are named '[origin/class] player'. Offline players",
              "keep their row, which is the whole point of showing this in the sidebar.")
          .define("sidebar", true);
      b.pop();
      b.comment("The revenant's Essence Extraction (medievalorigins:revenant/siphon).",
              "Upstream's own description says it affects everything 'except summoned dead',",
              "but there is no such exclusion anywhere in the implementation: the power JSON",
              "carries no bientity_condition, SummonedSkeleton never mentions siphon, and",
              "SpellDamageAction has no summon check. So a revenant can summon a zombie and",
              "heal off it forever. This cancels the siphon damage against ALL undead, which",
              "also stops the heal (upstream only heals after the damage lands).")
          .push("siphon");
      SIPHON_SKIP_UNDEAD = b
          .comment("Whether siphon damage against undead mobs is cancelled.")
          .define("skipUndead", true);
      b.pop();
      b.comment("Blazeborn drink Alex's Mobs lava bottles.",
              "Blazeborn take 2 damage from canteen water (world3:damage_from_canteens) but",
              "still get thirsty -- heat_tolerance only covers body temperature. A lava bottle",
              "is made by right-clicking a lava source with a glass bottle, which sets the",
              "player on fire; blazeborn are immune to that, so it is free for them alone.",
              "The tag route does not work: Tough As Nails' thirst tags only say how much a",
              "drink restores, and alexsmobs:lava_bottle is a plain Item with no use action.",
              "The defaults match toughasnails:purified_water_bottle, read from its tags.")
          .push("lavaBottle");
      LAVA_BOTTLE_DRINK = b
          .comment("Whether blazeborn can drink alexsmobs:lava_bottle.")
          .define("enabled", true);
      LAVA_BOTTLE_THIRST = b
          .comment("Thirst restored. Purified water bottle is 5.")
          .defineInRange("thirst", 5, 0, 20);
      LAVA_BOTTLE_HYDRATION = b
          .comment("Hydration restored. Purified water bottle is 80.")
          .defineInRange("hydration", 80.0D, 0.0D, 100.0D);
      b.pop();

      // ⚠⚠ **調査用の出力を既定で黙らせる**（2026-08-29・本番で分かった）。
      //    2026-08-28 の開始日、⚠ **サーバのログの 90%（3時間で 11,900 行）が
      //    この MOD の出力**だった（右クリックごと・拾得ごとに1行）。
      //    ⚠ 遅れの原因ではなかったが、**部員の座標と行動が全部残り**、
      //    ⚠ **本当に見たい行が埋まる**（実際、原因を調べるとき掻き分ける羽目になった）。
      //    ⚠ 消さずに残すのは、どれも切り分けに要った出力だから。
      VERBOSE_LOGS = b
          .comment("Log every cook-station touch, item pickup and paced-break batch.",
              "Off by default: on a live server this is thousands of lines an hour and",
              "buries the lines you actually want. Turn on only while investigating.")
          .define("verboseLogs", false);

      SPEC = b.build();
    }

    /** ⚠ 調査用の出力を出すか。⚠ **既定は false**（上の理由）。 */
    public static boolean verbose() {
      return VERBOSE_LOGS != null && VERBOSE_LOGS.get();
    }

    private Config() {
    }
  }
}
