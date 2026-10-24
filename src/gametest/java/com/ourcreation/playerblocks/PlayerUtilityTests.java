package com.ourcreation.playerblocks;

import com.ourcreation.playerblocks.block.PlayerConcreteButton;
import com.ourcreation.playerblocks.block.PlayerConcreteDoor;
import com.ourcreation.playerblocks.block.PlayerConcreteSlab;
import com.ourcreation.playerblocks.block.PlayerConcreteStairs;
import com.ourcreation.playerblocks.block.entity.PlayerColoredEntity;
import com.ourcreation.playerblocks.color.PlayerColors;
import com.ourcreation.playerblocks.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.block.state.properties.StairsShape;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(PlayerBlocks.MODID)
@PrefixGameTestTemplate(false)
public final class PlayerUtilityTests {

    @GameTest(template = "empty")
    public static void regularDoorOpensByHandAndIronDoorNeedsRedstone(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = TestPlayers.create(helper, GameType.CREATIVE);
        PlayerColors.get(level.getServer()).set(player.getUUID(), 0x52A8DD);
        var doors = new PlayerConcreteDoor[]{ModContent.DOOR.get(), ModContent.IRON_DOOR.get()};
        var positions =
                new BlockPos[]{helper.absolutePos(new BlockPos(1, 1, 2)), helper.absolutePos(new BlockPos(3, 1, 2))};
        for (int i = 0; i < doors.length; i++) {
            var pos = positions[i];
            level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 3);
            var stack = new ItemStack(doors[i]);
            var context = new BlockPlaceContext(player, InteractionHand.MAIN_HAND, stack,
                    new BlockHitResult(Vec3.atCenterOf(pos.below()), Direction.UP, pos.below(), false));
            helper.assertTrue(((BlockItem) stack.getItem()).place(context).consumesAction(),
                    "Door item places both halves");
            level.getBlockState(pos).useWithoutItem(level, player,
                    new BlockHitResult(Vec3.atCenterOf(pos), Direction.NORTH, pos, false));
            helper.assertTrue(level.getBlockState(pos).getValue(BlockStateProperties.OPEN) == (i == 0),
                    "Only the regular concrete door opens by hand");
            for (var half : new BlockPos[]{pos, pos.above()}) {
                var entity = (PlayerColoredEntity) level.getBlockEntity(half);
                helper.assertTrue(player.getUUID().equals(entity.owner()) && entity.color() == 0x52A8DD,
                        "Both halves retain the player's color for either door type");
            }
        }
        var iron = positions[1];
        level.setBlock(iron.east(), Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);
        helper.assertTrue(level.getBlockState(iron).getValue(BlockStateProperties.OPEN) &&
                        level.getBlockState(iron.above()).getValue(BlockStateProperties.OPEN),
                "Redstone opens both iron-style door halves");
        level.removeBlock(iron.east(), false);
        helper.assertTrue(!level.getBlockState(iron).getValue(BlockStateProperties.OPEN),
                "Iron-style door closes when power is removed");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void patternedShapePlacementMergingAndStairCorners(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = TestPlayers.create(helper, GameType.CREATIVE);
        PlayerColors.get(level.getServer()).set(player.getUUID(), 0xCE7921);
        var slabs = new PlayerConcreteSlab[]{ModContent.CHISELED_SLAB.get(), ModContent.TILES_SLAB.get()};
        for (int i = 0; i < slabs.length; i++) {
            var pos = helper.absolutePos(new BlockPos(i == 0 ? 1 : 3, 1, 1));
            level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 3);
            var stack = new ItemStack(slabs[i], 2);
            var lower = new BlockPlaceContext(player, InteractionHand.MAIN_HAND, stack,
                    new BlockHitResult(Vec3.atCenterOf(pos.below()), Direction.UP, pos.below(), false));
            helper.assertTrue(((BlockItem) stack.getItem()).place(lower).consumesAction(),
                    "Patterned slab places as its own variant");
            var upper = new BlockPlaceContext(player, InteractionHand.MAIN_HAND, stack,
                    new BlockHitResult(Vec3.atCenterOf(pos).add(0, 0.5, 0), Direction.UP, pos, false));
            helper.assertTrue(((BlockItem) stack.getItem()).place(upper).consumesAction(),
                    "Matching patterned slabs merge");
            helper.assertTrue(level.getBlockState(pos).is(slabs[i]) &&
                            level.getBlockState(pos).getValue(BlockStateProperties.SLAB_TYPE) == SlabType.DOUBLE,
                    "Double slab retains its patterned block type");
            helper.assertTrue(((PlayerColoredEntity) level.getBlockEntity(pos)).color() == 0xCE7921,
                    "Slab merging preserves the owner's tint");
        }
        var stairs = new PlayerConcreteStairs[]{ModContent.CHISELED_STAIRS.get(), ModContent.TILES_STAIRS.get()};
        for (int i = 0; i < stairs.length; i++) {
            var pos = helper.absolutePos(new BlockPos(i == 0 ? 1 : 3, 1, 3));
            level.setBlock(pos,
                    stairs[i].defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH), 3);
            level.setBlock(pos.north(),
                    stairs[i].defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST), 3);
            helper.assertTrue(
                    level.getBlockState(pos).getValue(BlockStateProperties.STAIRS_SHAPE) != StairsShape.STRAIGHT,
                    "Patterned stairs form vanilla corner shapes");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void decorativeItemsPlaceTheirOwnPatternedBlocks(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = TestPlayers.create(helper, GameType.CREATIVE);
        PlayerColors.get(level.getServer()).set(player.getUUID(), 0xCE7921);
        var blocks = new Block[]{ModContent.PILLAR.get(), ModContent.CHISELED.get(), ModContent.TILES.get()};
        var positions = new BlockPos[]{new BlockPos(1, 1, 1), new BlockPos(3, 1, 1), new BlockPos(1, 1, 3)};
        for (int i = 0; i < blocks.length; i++) {
            var target = helper.absolutePos(positions[i]);
            var support = target.below();
            level.setBlock(support, Blocks.STONE.defaultBlockState(), 3);
            var stack = new ItemStack(blocks[i]);
            var context = new BlockPlaceContext(player, InteractionHand.MAIN_HAND, stack,
                    new BlockHitResult(Vec3.atCenterOf(support), Direction.UP, support, false));
            var result = ((BlockItem) stack.getItem()).place(context);
            helper.assertTrue(result.consumesAction() && level.getBlockState(target).is(blocks[i]),
                    "Item placement retains the patterned block instead of replacing it with ordinary concrete: " +
                            blocks[i]);
            var entity = (PlayerColoredEntity) level.getBlockEntity(target);
            helper.assertTrue(player.getUUID().equals(entity.owner()) && entity.color() == 0xCE7921,
                    "Actual placed pattern retains its player's color");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void buttonsUseStoneAndWoodTimingAndArrowActivation(GameTestHelper helper) {
        var level = helper.getLevel();
        var buttons = new PlayerConcreteButton[]{ModContent.BUTTON.get(), ModContent.WOODEN_BUTTON.get()};
        var positions =
                new BlockPos[]{helper.absolutePos(new BlockPos(1, 2, 2)), helper.absolutePos(new BlockPos(3, 2, 2))};
        for (int i = 0; i < buttons.length; i++) {
            var pos = positions[i];
            level.setBlock(pos.south(), Blocks.STONE.defaultBlockState(), 3);
            level.setBlock(pos, buttons[i].defaultBlockState(), 2);
            var bounds = level.getBlockState(pos).getShape(level, pos).bounds().move(pos);
            var arrow = EntityType.ARROW.create(level);
            arrow.setPos(bounds.getCenter());
            level.addFreshEntity(arrow);
            level.getBlockState(pos).entityInside(level, pos, arrow);
            helper.assertTrue(level.getBlockState(pos).getValue(BlockStateProperties.POWERED) == (i == 1),
                    "Only wooden buttons are activated by arrows");
            arrow.discard();
            if (i == 0) {
                buttons[i].press(level.getBlockState(pos), level, pos, null);
            }
        }
        helper.runAfterDelay(21, () -> {
            helper.assertTrue(!level.getBlockState(positions[0]).getValue(BlockStateProperties.POWERED),
                    "Stone button releases after 20 ticks");
            helper.assertTrue(level.getBlockState(positions[1]).getValue(BlockStateProperties.POWERED),
                    "Wooden button remains active for 30 ticks");
        });
        helper.runAfterDelay(32, () -> {
            helper.assertTrue(!level.getBlockState(positions[1]).getValue(BlockStateProperties.POWERED),
                    "Wooden button releases after 30 ticks");
            helper.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void platesUseVanillaActivationAndWeightedSignals(GameTestHelper helper) {
        var level = helper.getLevel();
        var plates =
                new Block[]{ModContent.PRESSURE_PLATE.get(), ModContent.LIGHT_WEIGHTED_PLATE.get(), ModContent.HEAVY_WEIGHTED_PLATE.get()};
        var positions = new BlockPos[]{new BlockPos(1, 1, 1), new BlockPos(3, 1, 1), new BlockPos(1, 1, 3)};
        for (int i = 0; i < plates.length; i++) {
            var pos = helper.absolutePos(positions[i]);
            level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 3);
            level.setBlock(pos, plates[i].defaultBlockState(), 2);
            ItemEntity item = null;
            for (int n = 0; n < 3; n++) {
                item = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.05, pos.getZ() + 0.5,
                        new ItemStack(Items.DIAMOND));
                level.addFreshEntity(item);
            }
            level.getBlockState(pos).entityInside(level, pos, item);
            var state = level.getBlockState(pos);
            if (i == 0) {
                helper.assertTrue(!state.getValue(BlockStateProperties.POWERED), "Stone plates ignore dropped items");
            } else {
                helper.assertTrue(state.getValue(BlockStateProperties.POWER) == (i == 1 ? 3 : 1),
                        "Light/heavy weighted plates produce vanilla proportional signals");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void pillarOrientationMatchesPlacement(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = TestPlayers.create(helper, GameType.CREATIVE);
        var support = helper.absolutePos(new BlockPos(2, 2, 2));
        level.setBlock(support, Blocks.STONE.defaultBlockState(), 3);
        for (Direction face : Direction.values()) {
            var context =
                    new BlockPlaceContext(player, InteractionHand.MAIN_HAND, new ItemStack(ModContent.PILLAR.get()),
                            new BlockHitResult(Vec3.atCenterOf(support), face, support, false));
            var state = ModContent.PILLAR.get().getStateForPlacement(context);
            helper.assertTrue(state != null && state.getValue(RotatedPillarBlock.AXIS) == face.getAxis(),
                    "Pillar aligns to each clicked face");
        }
        helper.succeed();
    }
}
