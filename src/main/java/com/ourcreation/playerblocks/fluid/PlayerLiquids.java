package com.ourcreation.playerblocks.fluid;

import com.ourcreation.playerblocks.PlayerBlocks;
import com.ourcreation.playerblocks.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.SoundActions;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.fluids.capability.wrappers.FluidBucketWrapper;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class PlayerLiquids {

    private static final DeferredRegister<FluidType> TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, PlayerBlocks.MODID);

    private static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(Registries.FLUID, PlayerBlocks.MODID);

    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Registries.BLOCK, PlayerBlocks.MODID);

    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, PlayerBlocks.MODID);

    private static final DeferredRegister<BlockEntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, PlayerBlocks.MODID);

    public static final DeferredHolder<FluidType, FluidType> TYPE = TYPES.register("player_liquid", () -> new FluidType(
            FluidType.Properties.create().descriptionId("fluid.player_blocks.player_liquid").fallDistanceModifier(0F)
                    .canExtinguish(true).canHydrate(true).canConvertToSource(true).supportsBoating(true)
                    .sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL)
                    .sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY)));

    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> SOURCE =
            FLUIDS.register("player_liquid", () -> new BaseFlowingFluid.Source(properties()) {

                @Override
                public boolean canConvertToSource(FluidState state, Level level, BlockPos pos) {
                    return level.getGameRules().getBoolean(GameRules.RULE_WATER_SOURCE_CONVERSION);
                }

                @Override
                protected void spreadTo(LevelAccessor level, BlockPos pos, BlockState state, Direction direction,
                                        FluidState fluid) {
                    var owner = PlayerLiquidEntity.flowOwner(level, pos, direction);
                    super.spreadTo(level, pos, state, direction, fluid);
                    PlayerLiquidEntity.linkAt(level, pos, owner);
                }
            });

    public static final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> FLOWING =
            FLUIDS.register("flowing_player_liquid", () -> new BaseFlowingFluid.Flowing(properties()) {

                @Override
                public boolean canConvertToSource(FluidState state, Level level, BlockPos pos) {
                    return level.getGameRules().getBoolean(GameRules.RULE_WATER_SOURCE_CONVERSION);
                }

                @Override
                protected void spreadTo(LevelAccessor level, BlockPos pos, BlockState state, Direction direction,
                                        FluidState fluid) {
                    var owner = PlayerLiquidEntity.flowOwner(level, pos, direction);
                    super.spreadTo(level, pos, state, direction, fluid);
                    PlayerLiquidEntity.linkAt(level, pos, owner);
                }
            });

    public static final DeferredHolder<Block, PlayerLiquidBlock> BLOCK =
            BLOCKS.register("player_liquid", () -> new PlayerLiquidBlock());

    public static final DeferredHolder<Item, BucketItem> BUCKET = ITEMS.register("player_liquid_bucket",
            () -> new BucketItem(SOURCE.get(), new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1)) {

                @Override
                public void checkExtraContent(Player player, Level level, ItemStack stack, BlockPos pos) {
                    if (player != null) {
                        PlayerLiquidEntity.linkAt(level, pos, player.getUUID());
                    }
                }
            });

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PlayerLiquidEntity>> ENTITY =
            ENTITIES.register("player_liquid",
                    () -> BlockEntityType.Builder.of(PlayerLiquidEntity::new, BLOCK.get()).build(null));

    private static BaseFlowingFluid.Properties properties() {
        return new BaseFlowingFluid.Properties(TYPE, SOURCE, FLOWING).block(BLOCK).bucket(BUCKET).slopeFindDistance(4)
                .levelDecreasePerBlock(1).tickRate(5).explosionResistance(100);
    }

    public static void register(IEventBus bus) {
        bus.addListener((FMLCommonSetupEvent event) -> event.enqueueWork(
                () -> DispenserBlock.registerBehavior(BUCKET.get(),
                        DispenserBlock.DISPENSER_REGISTRY.get(Items.WATER_BUCKET))));
        TYPES.register(bus);
        FLUIDS.register(bus);
        BLOCKS.register(bus);
        ITEMS.register(bus);
        ENTITIES.register(bus);
        bus.addListener((RegisterCapabilitiesEvent event) -> event.registerItem(Capabilities.FluidHandler.ITEM,
                (stack, context) -> new FluidBucketWrapper(stack), BUCKET.get()));
        bus.addListener((BuildCreativeModeTabContentsEvent event) -> {
            if (event.getTab() == ModContent.TAB.get() || event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
                event.accept(BUCKET.get());
            }
        });
    }
}
