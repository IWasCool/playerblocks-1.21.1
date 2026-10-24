package com.ourcreation.playerblocks;

import com.ourcreation.playerblocks.fluid.PlayerLiquids;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(PlayerBlocks.MODID)
@PrefixGameTestTemplate(false)
public final class PlayerLiquidDispenserTests {

    @GameTest(template = "empty")
    public static void stackedBucketsKeepCustomFluidAndVanillaWaterBehavior(
            GameTestHelper helper) throws ReflectiveOperationException {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(3, 2, 2));
        var state = Blocks.DISPENSER.defaultBlockState().setValue(DispenserBlock.FACING, Direction.WEST);
        level.setBlock(pos, state, 3);
        var dispenser = (DispenserBlockEntity) level.getBlockEntity(pos);
        var source = new BlockSource(level, pos, state, dispenser);
        var target = pos.west();
        for (Direction side : Direction.values()) {
            if (side != Direction.EAST) {
                level.setBlock(target.relative(side), Blocks.STONE.defaultBlockState(), 3);
            }
        }
        var behavior = DispenserBlock.DISPENSER_REGISTRY.get(Items.BUCKET);
        level.setBlock(target, PlayerLiquids.BLOCK.get().defaultBlockState(), 3);
        var remaining = behavior.dispense(source, new ItemStack(Items.BUCKET, 2));
        helper.assertTrue(remaining.is(Items.BUCKET) && remaining.getCount() == 1,
                "Picking up custom liquid consumes exactly one empty bucket");
        helper.assertTrue(level.getFluidState(target).isEmpty(), "Custom source is removed after pickup");
        int filled = 0;
        for (int slot = 0; slot < dispenser.getContainerSize(); slot++) {
            var stack = dispenser.getItem(slot);
            helper.assertTrue(stack.isEmpty() || stack.is(PlayerLiquids.BUCKET.get()),
                    "Custom liquid never inserts a vanilla water bucket");
            filled += stack.getCount();
        }
        helper.assertTrue(filled == 1, "The filled custom bucket is inserted exactly once into the dispenser");
        level.setBlock(target, Blocks.WATER.defaultBlockState(), 3);
        var purity =
                ModList.get().isLoaded("thirst") ? Class.forName("cn.mlus.thirst.content.purity.WaterPurity") : null;
        Integer expectedPurity =
                purity != null && (boolean) purity.getMethod("isEnabled").invoke(null) ? (Integer) purity.getMethod(
                        "getBlockPurity", Level.class, BlockPos.class).invoke(null, level, target) : null;
        var water = behavior.dispense(source, new ItemStack(Items.BUCKET));
        helper.assertTrue(water.is(Items.WATER_BUCKET) && water.getCount() == 1,
                "Vanilla source water retains its normal bucket behavior");
        helper.assertTrue(level.getFluidState(target).isEmpty(), "Vanilla water source is still consumed");
        if (expectedPurity != null) {
            helper.assertTrue((boolean) purity.getMethod("hasPurity", ItemStack.class).invoke(null, water) &&
                            expectedPurity.equals(purity.getMethod("getPurity", ItemStack.class).invoke(null, water)),
                    "Thirst still adds the source's water purity to vanilla water buckets");
        }
        helper.succeed();
    }
}
