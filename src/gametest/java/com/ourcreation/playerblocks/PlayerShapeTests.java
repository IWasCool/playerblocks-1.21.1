package com.ourcreation.playerblocks;

import com.ourcreation.playerblocks.block.PlayerConcreteBlock;
import com.ourcreation.playerblocks.block.PlayerShapeBlock;
import com.ourcreation.playerblocks.block.PlayerShulkerBox;
import com.ourcreation.playerblocks.block.entity.PlayerChestEntity;
import com.ourcreation.playerblocks.block.entity.PlayerColoredEntity;
import com.ourcreation.playerblocks.block.entity.PlayerConcreteEntity;
import com.ourcreation.playerblocks.color.PlayerColorRefresh;
import com.ourcreation.playerblocks.color.PlayerColors;
import com.ourcreation.playerblocks.item.PlayerBlockItem;
import com.ourcreation.playerblocks.registry.ModContent;

import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(PlayerBlocks.MODID)
@PrefixGameTestTemplate(false)
public final class PlayerShapeTests {

    @GameTest(template = "empty")
    public static void passiveRefreshIsDeduplicatedAndStopsAfterRemoval(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(2, 2, 2));
        var state = ModContent.SLAB.get().defaultBlockState();
        level.setBlock(pos, state, 2);
        level.removeBlockEntity(pos);
        var entity = new CountingColorEntity(pos, state);
        level.setBlockEntity(entity);
        for (int index = 0; index < 2000; index++) {
            PlayerColorRefresh.track(entity);
        }
        helper.runAfterDelay(41, () -> {
            helper.assertTrue(entity.refreshes >= 2 && entity.refreshes <= 3,
                    "Repeated registrations still refresh only once per twenty ticks");
            int previous = entity.refreshes;
            level.removeBlockEntity(pos);
            helper.runAfterDelay(25, () -> {
                helper.assertTrue(entity.refreshes == previous, "Removed entities stop receiving refresh callbacks");
                helper.succeed();
            });
        });
    }

    private static final class CountingColorEntity extends BlockEntity implements PlayerColoredEntity {

        private final UUID owner = UUID.randomUUID();

        private int refreshes;

        CountingColorEntity(BlockPos pos, BlockState state) {
            super(ModContent.ENTITY.get(), pos, state);
        }

        @Override
        public UUID owner() {
            return owner;
        }

        @Override
        public int color() {
            return 0xFFFFFF;
        }

        @Override
        public void link(UUID owner) {
        }

        @Override
        public void refresh() {
            refreshes++;
        }
    }

    @GameTest(template = "empty")
    public static void inventoryColorsReuseComponentsAndPreserveShapeProperties(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = TestPlayers.create(helper, GameType.CREATIVE);
        var other = TestPlayers.create(helper, GameType.SURVIVAL);
        var colors = PlayerColors.get(level.getServer());
        var first = new ItemStack(ModContent.SLAB.get());
        var second = new ItemStack(ModContent.STAIRS.get());
        PlayerBlockItem.prepare(first, level, player);
        PlayerBlockItem.prepare(second, level, player);
        var componentType = DataComponents.BLOCK_STATE;
        var shared = first.get(componentType);
        helper.assertTrue(shared == second.get(componentType),
                "Plain colored stacks share an immutable slot component");
        for (int index = 0; index < 2000; index++) {
            PlayerBlockItem.prepare(first, level, player);
        }
        helper.assertTrue(first.get(componentType) == shared, "Inventory updates leave an unchanged component intact");
        PlayerBlockItem.prepare(first, level, other);
        helper.assertTrue(first.get(componentType) != shared && second.get(componentType) == shared,
                "Changing one stack's owner leaves shared components on other stacks intact");
        helper.assertTrue(
                PlayerBlockItem.material(first).getValue(PlayerConcreteBlock.COLOR_ID) == colors.slot(other.getUUID()),
                "A transferred item follows its new holder's slot");
        var shaped = new ItemStack(ModContent.SLAB.get());
        shaped.set(componentType, new BlockItemStateProperties(Map.of("type", "top", "waterlogged", "true", "color_id",
                Integer.toString(colors.slot(other.getUUID())))));
        PlayerBlockItem.prepare(shaped, level, player);
        var state = shaped.get(componentType).apply(ModContent.SLAB.get().defaultBlockState());
        helper.assertTrue(
                state.getValue(SlabBlock.TYPE) == SlabType.TOP && state.getValue(BlockStateProperties.WATERLOGGED),
                "Preparing color preserves unrelated shape and waterlogging properties");
        helper.assertTrue(shaped.get(componentType).properties().get("color_id")
                        .equals(Integer.toString(colors.slot(player.getUUID()))),
                "Shape properties do not prevent rebinding the color slot");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void allShapesPersistOwnershipAndFollowColorChanges(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = TestPlayers.create(helper, GameType.CREATIVE);
        var colors = PlayerColors.get(level.getServer());
        colors.set(player.getUUID(), 0x27B6D9);
        var pos = helper.absolutePos(new BlockPos(2, 2, 2));
        level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(pos.south(), Blocks.STONE.defaultBlockState(), 3);
        for (Block block : ModContent.allBlocks()) {
            level.setBlock(pos, block.defaultBlockState(), 2);
            var entity = (PlayerColoredEntity) level.getBlockEntity(pos);
            helper.assertTrue(entity != null, "Every shape supplies a color entity: " + block);
            var ticker = ((EntityBlock) block).getTicker(level, level.getBlockState(pos),
                    level.getBlockEntity(pos).getType());
            helper.assertTrue((ticker != null) == (block instanceof PlayerShulkerBox),
                    "Only native shulker animation needs a server block entity ticker: " + block);
            block.setPlacedBy(level, pos, level.getBlockState(pos), player, new ItemStack(block));
            helper.assertTrue(player.getUUID().equals(entity.owner()) && entity.color() == 0x27B6D9,
                    "Placement links exact RGB for " + block);
            var saved = level.getBlockEntity(pos).saveWithFullMetadata(level.registryAccess());
            var restored = ((EntityBlock) block).newBlockEntity(pos, level.getBlockState(pos));
            restored.loadWithComponents(saved, level.registryAccess());
            helper.assertTrue(player.getUUID().equals(((PlayerColoredEntity) restored).owner()) &&
                            ((PlayerColoredEntity) restored).color() == 0x27B6D9,
                    "Ownership and color survive reload for " + block);
            var stack = new ItemStack(block);
            PlayerBlockItem.prepare(stack, level, player);
            helper.assertTrue(PlayerBlockItem.material(stack).getValue(PlayerConcreteBlock.COLOR_ID) ==
                    colors.slot(player.getUUID()), "Shape inventory preview stores the owner's color slot");
        }
        // Leave a slab in the world and exercise the shared server refresh queue.
        level.setBlock(pos, ModContent.SLAB.get().defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP), 2);
        PlayerShapeBlock.link(level, pos, player);
        colors.set(player.getUUID(), 0xF012DE);
        helper.runAfterDelay(25, () -> {
            var entity = (PlayerConcreteEntity) level.getBlockEntity(pos);
            helper.assertTrue(entity.getUpdateTag(level.registryAccess()).getInt("color") == 0xF012DE,
                    "Shared refresh queue updates the client-visible shape color after a color change");
            helper.assertTrue(level.getBlockState(pos).getValue(SlabBlock.TYPE) == SlabType.TOP,
                    "Color changes preserve shape properties");
            helper.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void passiveShapeRefreshSurvivesReloadAndReplacement(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = TestPlayers.create(helper, GameType.CREATIVE);
        var colors = PlayerColors.get(level.getServer());
        colors.set(player.getUUID(), 0x204060);
        var pos = helper.absolutePos(new BlockPos(2, 2, 2));
        var shape = ModContent.CHEST.get().defaultBlockState().setValue(ChestBlock.FACING, Direction.EAST);
        level.setBlock(pos, shape, 2);
        var original = (PlayerChestEntity) level.getBlockEntity(pos);
        original.link(player.getUUID());
        var saved = original.saveWithFullMetadata(level.registryAccess());
        var restored = BlockEntity.loadStatic(pos, shape, saved, level.registryAccess());
        level.removeBlockEntity(pos);
        level.setBlockEntity(restored);
        colors.set(player.getUUID(), 0x60A0E0);
        helper.runAfterDelay(25, () -> {
            helper.assertTrue(original.isRemoved(), "Replacing a shape retires the previous entity");
            helper.assertTrue(restored.getUpdateTag(level.registryAccess()).getInt("color") == 0x60A0E0,
                    "A restored shape resumes refreshing without a block entity ticker");
            helper.assertTrue(level.getBlockState(pos).getValue(ChestBlock.FACING) == Direction.EAST,
                    "Reload and color refresh retain shape state");
            helper.assertTrue(original.getUpdateTag(level.registryAccess()).getInt("color") == 0x204060,
                    "Removed entities receive no more scheduled color refreshes");
            // State-only edits can restore an old palette block's slot to zero.
            level.setBlock(pos, ModContent.CONCRETE.get().defaultBlockState(), 2);
            var concrete = (PlayerConcreteEntity) level.getBlockEntity(pos);
            concrete.link(player.getUUID());
            level.setBlock(pos, level.getBlockState(pos).setValue(PlayerConcreteBlock.COLOR_ID, 0), 2);
            helper.runAfterDelay(25, () -> {
                helper.assertTrue(level.getBlockState(pos).getValue(PlayerConcreteBlock.COLOR_ID) ==
                                colors.slot(player.getUUID()),
                        "State-only legacy slot edits automatically migrate through the refresh queue");
                helper.succeed();
            });
        });
    }

    @GameTest(template = "empty")
    public static void bothDoorHalvesShareOwnershipAndColor(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = TestPlayers.create(helper, GameType.CREATIVE);
        var pos = helper.absolutePos(new BlockPos(2, 1, 2));
        var door = ModContent.DOOR.get();
        level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(pos, door.defaultBlockState(), 2);
        level.setBlock(pos.above(), door.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), 2);
        door.setPlacedBy(level, pos, level.getBlockState(pos), player, new ItemStack(door));
        helper.assertTrue(player.getUUID().equals(((PlayerConcreteEntity) level.getBlockEntity(pos.above())).owner()),
                "Normal placement links the upper half");
        var claimant = TestPlayers.create(helper, GameType.SURVIVAL);
        PlayerShapeBlock.link(level, pos.above(), claimant);
        helper.assertTrue(claimant.getUUID().equals(((PlayerConcreteEntity) level.getBlockEntity(pos)).owner()),
                "Linking the upper half also links the lower half");
        PlayerShapeBlock.link(level, pos, player);
        PlayerColors.get(level.getServer()).set(player.getUUID(), 0x123456);
        helper.runAfterDelay(25, () -> {
            for (BlockPos half : new BlockPos[]{pos, pos.above()}) {
                var entity = (PlayerConcreteEntity) level.getBlockEntity(half);
                helper.assertTrue(player.getUUID().equals(entity.owner()) && entity.color() == 0x123456,
                        "Both door halves retain the same owner and update their color");
            }
            helper.succeed();
        });
    }
}
