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
      SPEC = b.build();
    }

    private Config() {
    }
  }
}
