package net.erutobusiness.pacedmultimine;

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
@Mod(PacedMultiMine.MOD_ID)
public final class PacedMultiMine {

  public static final String MOD_ID = "pacedmultimine";

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

  public PacedMultiMine() {
    POWER_FACTORIES.register(FMLJavaModLoadingContext.get().getModEventBus());
    FMLJavaModLoadingContext.get().getModEventBus().addListener(PacedMultiMine::onConfigLoad);
    net.minecraftforge.fml.ModLoadingContext.get()
        .registerConfig(ModConfig.Type.SERVER, Config.SPEC);
    net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(PacedBreakQueue.class);
    net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(ClassPowers.class);
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
      SPEC = b.build();
    }

    private Config() {
    }
  }
}
