package com.ourcreation.playerblocks.registry;

import com.ourcreation.playerblocks.PlayerBlocks;
import com.ourcreation.playerblocks.block.PlayerBanner;
import com.ourcreation.playerblocks.block.PlayerBed;
import com.ourcreation.playerblocks.block.PlayerCandle;
import com.ourcreation.playerblocks.block.PlayerChest;
import com.ourcreation.playerblocks.block.PlayerConcreteBlock;
import com.ourcreation.playerblocks.block.PlayerConcreteButton;
import com.ourcreation.playerblocks.block.PlayerConcreteCarpet;
import com.ourcreation.playerblocks.block.PlayerConcreteDoor;
import com.ourcreation.playerblocks.block.PlayerConcreteFence;
import com.ourcreation.playerblocks.block.PlayerConcreteFenceGate;
import com.ourcreation.playerblocks.block.PlayerConcreteGlass;
import com.ourcreation.playerblocks.block.PlayerConcretePane;
import com.ourcreation.playerblocks.block.PlayerConcretePillar;
import com.ourcreation.playerblocks.block.PlayerConcretePressurePlate;
import com.ourcreation.playerblocks.block.PlayerConcreteSlab;
import com.ourcreation.playerblocks.block.PlayerConcreteStairs;
import com.ourcreation.playerblocks.block.PlayerConcreteTrapdoor;
import com.ourcreation.playerblocks.block.PlayerConcreteWall;
import com.ourcreation.playerblocks.block.PlayerGlazedTerracotta;
import com.ourcreation.playerblocks.block.PlayerPatternBlock;
import com.ourcreation.playerblocks.block.PlayerShulkerBox;
import com.ourcreation.playerblocks.block.PlayerWallBanner;
import com.ourcreation.playerblocks.block.PlayerWeightedPressurePlate;
import com.ourcreation.playerblocks.block.entity.PlayerBannerEntity;
import com.ourcreation.playerblocks.block.entity.PlayerBedEntity;
import com.ourcreation.playerblocks.block.entity.PlayerChestEntity;
import com.ourcreation.playerblocks.block.entity.PlayerConcreteEntity;
import com.ourcreation.playerblocks.block.entity.PlayerShulkerEntity;
import com.ourcreation.playerblocks.item.PlayerBannerItem;
import com.ourcreation.playerblocks.item.PlayerBlockItem;
import com.ourcreation.playerblocks.recipe.PlayerShulkerRecipe;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModContent {

    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Registries.BLOCK, PlayerBlocks.MODID);

    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, PlayerBlocks.MODID);

    private static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, PlayerBlocks.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("player_blocks",
            () -> CreativeModeTab.builder().title(Component.translatable("itemGroup.player_blocks"))
                    .icon(() -> new ItemStack(ModContent.ITEM.get())).displayItems((parameters, output) -> {
                        for (Item item : allItems()) {
                            output.accept(item);
                        }
                    }).build());

    private static final DeferredRegister<BlockEntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, PlayerBlocks.MODID);

    private static final DeferredRegister<RecipeSerializer<?>> RECIPES =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, PlayerBlocks.MODID);

    public static final DeferredHolder<RecipeSerializer<?>, SimpleCraftingRecipeSerializer<PlayerShulkerRecipe>>
            SHULKER_RECIPE = RECIPES.register("player_shulker_conversion",
            () -> new SimpleCraftingRecipeSerializer<>(PlayerShulkerRecipe::new));

    public static final DeferredHolder<Block, PlayerConcreteBlock> CONCRETE = BLOCKS.register("player_concrete",
            () -> new PlayerConcreteBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.WHITE_CONCRETE)));

    public static final DeferredHolder<Item, BlockItem> ITEM =
            ITEMS.register("player_concrete", () -> new PlayerBlockItem(new Item.Properties()));

    public static final DeferredHolder<Item, Item> DYE =
            ITEMS.register("player_dye", () -> new Item(new Item.Properties()));

    private static final List<DeferredHolder<Block, ? extends Block>> SHAPES = new ArrayList<>();

    private static final List<DeferredHolder<Item, BlockItem>> SHAPE_ITEMS = new ArrayList<>();

    public static final DeferredHolder<Block, PlayerConcreteSlab> SLAB =
            shape("player_concrete_slab", () -> new PlayerConcreteSlab(shapeProperties(Blocks.SMOOTH_STONE_SLAB)));

    public static final DeferredHolder<Block, PlayerConcreteStairs> STAIRS =
            shape("player_concrete_stairs", () -> new PlayerConcreteStairs(shapeProperties(Blocks.BRICK_STAIRS)));

    public static final DeferredHolder<Block, PlayerConcreteWall> WALL =
            shape("player_concrete_wall", () -> new PlayerConcreteWall(shapeProperties(Blocks.COBBLESTONE_WALL)));

    public static final DeferredHolder<Block, PlayerConcreteFence> FENCE =
            shape("player_concrete_fence", () -> new PlayerConcreteFence(shapeProperties(Blocks.OAK_FENCE)));

    public static final DeferredHolder<Block, PlayerConcreteFenceGate> FENCE_GATE = shape("player_concrete_fence_gate",
            () -> new PlayerConcreteFenceGate(shapeProperties(Blocks.OAK_FENCE_GATE)));

    public static final DeferredHolder<Block, PlayerConcreteDoor> DOOR =
            shape("player_concrete_door", () -> new PlayerConcreteDoor(shapeProperties(Blocks.OAK_DOOR)));

    public static final DeferredHolder<Block, PlayerConcreteDoor> IRON_DOOR = shape("player_concrete_iron_door",
            () -> new PlayerConcreteDoor(BlockSetType.IRON, shapeProperties(Blocks.IRON_DOOR)));

    public static final DeferredHolder<Block, PlayerConcreteTrapdoor> TRAPDOOR =
            shape("player_concrete_trapdoor", () -> new PlayerConcreteTrapdoor(shapeProperties(Blocks.OAK_TRAPDOOR)));

    public static final DeferredHolder<Block, PlayerConcretePane> PANE =
            shape("player_concrete_pane", () -> new PlayerConcretePane(shapeProperties(Blocks.GLASS_PANE)));

    public static final DeferredHolder<Block, PlayerConcretePillar> PILLAR =
            shape("player_concrete_pillar", () -> new PlayerConcretePillar(shapeProperties(Blocks.QUARTZ_PILLAR)));

    public static final DeferredHolder<Block, PlayerConcreteButton> BUTTON =
            shape("player_concrete_button", () -> new PlayerConcreteButton(shapeProperties(Blocks.STONE_BUTTON)));

    public static final DeferredHolder<Block, PlayerConcreteButton> WOODEN_BUTTON =
            shape("player_concrete_wooden_button",
                    () -> new PlayerConcreteButton(BlockSetType.OAK, 30, shapeProperties(Blocks.OAK_BUTTON)));

    public static final DeferredHolder<Block, PlayerConcretePressurePlate> PRESSURE_PLATE =
            shape("player_concrete_pressure_plate",
                    () -> new PlayerConcretePressurePlate(shapeProperties(Blocks.STONE_PRESSURE_PLATE)));

    public static final DeferredHolder<Block, PlayerConcreteCarpet> CARPET =
            shape("player_concrete_carpet", () -> new PlayerConcreteCarpet(shapeProperties(Blocks.WHITE_CARPET)));

    public static final DeferredHolder<Block, PlayerConcreteGlass> GLASS = shape("player_concrete_glass",
            () -> new PlayerConcreteGlass(BlockBehaviour.Properties.ofFullCopy(Blocks.GLASS).noOcclusion()));

    public static final DeferredHolder<Block, PlayerConcreteBlock> CHISELED = shape("player_concrete_chiseled",
            () -> new PlayerConcreteBlock(shapeProperties(Blocks.CHISELED_QUARTZ_BLOCK)));

    public static final DeferredHolder<Block, PlayerConcreteBlock> TILES =
            shape("player_concrete_tiles", () -> new PlayerConcreteBlock(shapeProperties(Blocks.QUARTZ_BRICKS)));

    public static final DeferredHolder<Block, PlayerConcreteSlab> CHISELED_SLAB = shape("player_concrete_chiseled_slab",
            () -> new PlayerConcreteSlab(shapeProperties(Blocks.SMOOTH_STONE_SLAB)));

    public static final DeferredHolder<Block, PlayerConcreteStairs> CHISELED_STAIRS =
            shape("player_concrete_chiseled_stairs", () -> new PlayerConcreteStairs(CHISELED.get().defaultBlockState(),
                    shapeProperties(Blocks.BRICK_STAIRS)));

    public static final DeferredHolder<Block, PlayerConcreteWall> CHISELED_WALL = shape("player_concrete_chiseled_wall",
            () -> new PlayerConcreteWall(shapeProperties(Blocks.COBBLESTONE_WALL)));

    public static final DeferredHolder<Block, PlayerConcreteSlab> TILES_SLAB = shape("player_concrete_tiles_slab",
            () -> new PlayerConcreteSlab(shapeProperties(Blocks.SMOOTH_STONE_SLAB)));

    public static final DeferredHolder<Block, PlayerConcreteStairs> TILES_STAIRS = shape("player_concrete_tiles_stairs",
            () -> new PlayerConcreteStairs(TILES.get().defaultBlockState(), shapeProperties(Blocks.BRICK_STAIRS)));

    public static final DeferredHolder<Block, PlayerConcreteWall> TILES_WALL =
            shape("player_concrete_tiles_wall", () -> new PlayerConcreteWall(shapeProperties(Blocks.COBBLESTONE_WALL)));

    public static final DeferredHolder<Block, PlayerWeightedPressurePlate> LIGHT_WEIGHTED_PLATE =
            shape("player_concrete_light_weighted_pressure_plate",
                    () -> new PlayerWeightedPressurePlate(15, BlockSetType.GOLD,
                            shapeProperties(Blocks.LIGHT_WEIGHTED_PRESSURE_PLATE)));

    public static final DeferredHolder<Block, PlayerWeightedPressurePlate> HEAVY_WEIGHTED_PLATE =
            shape("player_concrete_heavy_weighted_pressure_plate",
                    () -> new PlayerWeightedPressurePlate(150, BlockSetType.IRON,
                            shapeProperties(Blocks.HEAVY_WEIGHTED_PRESSURE_PLATE)));

    public static final DeferredHolder<Block, PlayerPatternBlock> WOOL = patterned("player_diorite", Blocks.WHITE_WOOL);

    public static final DeferredHolder<Block, PlayerPatternBlock> TERRACOTTA =
            patterned("player_terracotta", Blocks.TERRACOTTA);

    public static final DeferredHolder<Block, PlayerPatternBlock> PURPUR =
            patterned("player_purpur_block", Blocks.PURPUR_BLOCK);

    public static final DeferredHolder<Block, PlayerConcretePillar> PURPUR_PILLAR = shape("player_purpur_pillar",
            () -> new PlayerConcretePillar(BlockBehaviour.Properties.ofFullCopy(Blocks.PURPUR_PILLAR)));

    public static final DeferredHolder<Block, PlayerPatternBlock> STONE_BRICKS =
            patterned("player_stone_bricks", Blocks.STONE_BRICKS);

    public static final DeferredHolder<Block, PlayerBed> BED =
            shape("player_bed", () -> new PlayerBed(BlockBehaviour.Properties.ofFullCopy(Blocks.WHITE_BED)));

    public static final DeferredHolder<Block, PlayerChest> CHEST =
            shape("player_concrete_chest", () -> new PlayerChest(BlockBehaviour.Properties.ofFullCopy(Blocks.CHEST)));

    public static final DeferredHolder<Block, PlayerShulkerBox> SHULKER = shape("player_shulker_box",
            () -> new PlayerShulkerBox(BlockBehaviour.Properties.ofFullCopy(Blocks.WHITE_SHULKER_BOX)),
            block -> new PlayerBlockItem(block, new Item.Properties().stacksTo(1)));

    public static final DeferredHolder<Block, PlayerCandle> CANDLE =
            shape("player_candle", () -> new PlayerCandle(BlockBehaviour.Properties.ofFullCopy(Blocks.WHITE_CANDLE)));

    public static final DeferredHolder<Block, PlayerWallBanner> WALL_BANNER = itemlessShape("player_wall_banner",
            () -> new PlayerWallBanner(BlockBehaviour.Properties.ofFullCopy(Blocks.WHITE_WALL_BANNER)));

    public static final DeferredHolder<Block, PlayerBanner> BANNER =
            shape("player_banner", () -> new PlayerBanner(BlockBehaviour.Properties.ofFullCopy(Blocks.WHITE_BANNER)),
                    block -> new PlayerBannerItem(new Item.Properties().stacksTo(16)));

    public static final List<DeferredHolder<Block, PlayerGlazedTerracotta>> GLAZED_TERRACOTTA =
            Arrays.stream(DyeColor.values()).map(color -> {
                String name = color.getName() + "_glazed_terracotta";
                var original = BuiltInRegistries.BLOCK.get(ResourceLocation.withDefaultNamespace(name));
                return shape("player_" + name,
                        () -> new PlayerGlazedTerracotta(BlockBehaviour.Properties.ofFullCopy(original)));
            }).toList();

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PlayerConcreteEntity>> ENTITY =
            ENTITIES.register("player_concrete", () -> BlockEntityType.Builder.of(PlayerConcreteEntity::new,
                    Arrays.stream(allBlocks())
                            .filter(block -> !(block instanceof PlayerBed || block instanceof PlayerBanner ||
                                    block instanceof PlayerWallBanner || block instanceof PlayerChest ||
                                    block instanceof PlayerShulkerBox)).toArray(Block[]::new)).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PlayerBedEntity>> BED_ENTITY =
            ENTITIES.register("player_bed",
                    () -> BlockEntityType.Builder.of(PlayerBedEntity::new, BED.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PlayerBannerEntity>> BANNER_ENTITY =
            ENTITIES.register("player_banner",
                    () -> BlockEntityType.Builder.of(PlayerBannerEntity::new, BANNER.get(), WALL_BANNER.get())
                            .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PlayerChestEntity>> CHEST_ENTITY =
            ENTITIES.register("player_chest",
                    () -> BlockEntityType.Builder.of(PlayerChestEntity::new, CHEST.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PlayerShulkerEntity>> SHULKER_ENTITY =
            ENTITIES.register("player_shulker_box",
                    () -> BlockEntityType.Builder.of(PlayerShulkerEntity::new, SHULKER.get()).build(null));

    private ModContent() {
    }

    private static DeferredHolder<Block, PlayerPatternBlock> patterned(String name, Block original) {
        return shape(name, () -> new PlayerPatternBlock(BlockBehaviour.Properties.ofFullCopy(original)));
    }

    private static BlockBehaviour.Properties shapeProperties(Block original) {
        return BlockBehaviour.Properties.ofFullCopy(original).strength(1.8F).sound(SoundType.STONE);
    }

    private static <T extends Block> DeferredHolder<Block, T> shape(String name, Supplier<T> factory) {
        return shape(name, factory, block -> new PlayerBlockItem(block, new Item.Properties()));
    }

    private static <T extends Block> DeferredHolder<Block, T> itemlessShape(String name, Supplier<T> factory) {
        var block = BLOCKS.register(name, factory);
        SHAPES.add(block);
        return block;
    }

    private static <T extends Block> DeferredHolder<Block, T> shape(String name, Supplier<T> factory,
                                                                    Function<Block, BlockItem> itemFactory) {
        var block = itemlessShape(name, factory);
        SHAPE_ITEMS.add(ITEMS.register(name, () -> itemFactory.apply(block.get())));
        return block;
    }

    public static Block[] allBlocks() {
        return Stream.concat(Stream.of(CONCRETE.get()), SHAPES.stream().map(DeferredHolder::get)).toArray(Block[]::new);
    }

    public static Item[] allItems() {
        return Stream.concat(Stream.of(ITEM.get(), DYE.get()), SHAPE_ITEMS.stream().map(DeferredHolder::get))
                .toArray(Item[]::new);
    }

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        ENTITIES.register(bus);
        RECIPES.register(bus);
        TABS.register(bus);
        bus.addListener(ModContent::creative);
    }

    private static void creative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS) {
            for (Item item : allItems()) {
                event.accept(item);
            }
        }
    }
}
