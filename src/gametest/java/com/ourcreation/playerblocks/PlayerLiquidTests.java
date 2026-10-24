package com.ourcreation.playerblocks;

import com.ourcreation.playerblocks.color.PlayerColors;
import com.ourcreation.playerblocks.fluid.PlayerLiquidEntity;
import com.ourcreation.playerblocks.fluid.PlayerLiquids;
import com.ourcreation.playerblocks.registry.ModContent;

import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.ticks.LevelChunkTicks;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(PlayerBlocks.MODID)
@PrefixGameTestTemplate(false)
public class PlayerLiquidTests {

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void scheduledLiquidRecolorsWithoutEntityTicker(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(2, 2, 2));
        encloseLiquid(helper, pos);
        var state = level.getBlockState(pos);
        helper.assertTrue(state.getTicker(level, PlayerLiquids.ENTITY.get()) == null,
                "Liquid has no per-tick block entity callback");
        helper.assertTrue(!level.getBlockTicks().hasScheduledTick(pos, PlayerLiquids.BLOCK.get()),
                "Unowned liquid needs no color refresh tick");
        UUID owner = UUID.randomUUID();
        var colors = PlayerColors.get(level.getServer());
        colors.set(owner, 0x2468AC);
        var entity = (PlayerLiquidEntity) level.getBlockEntity(pos);
        entity.link(owner);
        entity.refresh();
        entity.onLoad();
        helper.assertTrue(refreshTickCount(helper, pos) == 1, "Link, refresh and load hooks share one pending refresh");
        colors.set(owner, 0xBADA55);
        helper.runAfterDelay(21, () -> {
            helper.assertTrue(entity.color() == 0xBADA55,
                    "Scheduled refresh propagates owner color changes within twenty ticks");
            helper.assertTrue(refreshTickCount(helper, pos) == 1, "Owned liquid keeps a single next refresh");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void restoredLiquidSchedulesMissingRefresh(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(2, 2, 2));
        encloseLiquid(helper, pos);
        UUID owner = UUID.randomUUID();
        var saved = level.getBlockEntity(pos).saveWithoutMetadata(level.registryAccess());
        saved.putUUID("owner", owner);
        saved.putInt("color", 0x102030);
        PlayerColors.get(level.getServer()).set(owner, 0xC0FFEE);
        level.removeBlockEntity(pos);
        var restored = new PlayerLiquidEntity(pos, level.getBlockState(pos));
        restored.loadWithComponents(saved, level.registryAccess());
        level.setBlockEntity(restored);
        restored.onLoad();
        restored.onLoad();
        helper.assertTrue(owner.equals(restored.owner()), "Reload preserves liquid ownership");
        helper.assertTrue(refreshTickCount(helper, pos) == 1,
                "Reload starts a missing refresh and deduplicates repeated load hooks");
        helper.runAfterDelay(21, () -> {
            helper.assertTrue(restored.color() == 0xC0FFEE, "Reloaded liquid catches up with its current owner color");
            helper.assertTrue(refreshTickCount(helper, pos) == 1, "Reloaded liquid continues refreshing");
            helper.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void liquidOwnerLookupPreservesPriority(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(2, 2, 2));
        UUID above = UUID.randomUUID(), below = UUID.randomUUID(), north = UUID.randomUUID();
        level.setBlock(pos.above(), PlayerLiquids.BLOCK.get().defaultBlockState(), 3);
        level.setBlock(pos.below(), PlayerLiquids.BLOCK.get().defaultBlockState(), 3);
        level.setBlock(pos.north(), PlayerLiquids.BLOCK.get().defaultBlockState(), 3);
        PlayerLiquidEntity.linkAt(level, pos.above(), above);
        PlayerLiquidEntity.linkAt(level, pos.below(), below);
        PlayerLiquidEntity.linkAt(level, pos.north(), north);
        helper.assertTrue(above.equals(PlayerLiquidEntity.flowOwner(level, pos, Direction.DOWN)),
                "Flow prefers its upstream owner");
        level.setBlock(pos.above(), Blocks.AIR.defaultBlockState(), 3);
        helper.assertTrue(below.equals(PlayerLiquidEntity.flowOwner(level, pos, Direction.DOWN)),
                "Fallback checks below before horizontal neighbors");
        level.setBlock(pos.below(), Blocks.AIR.defaultBlockState(), 3);
        helper.assertTrue(north.equals(PlayerLiquidEntity.flowOwner(level, pos, Direction.DOWN)),
                "Fallback finds the remaining neighboring owner");
        level.setBlock(pos.north(), Blocks.AIR.defaultBlockState(), 3);
        helper.assertTrue(PlayerLiquidEntity.flowOwner(level, pos, Direction.DOWN) == null,
                "Unowned surroundings stay unowned");
        helper.succeed();
    }

    private static void encloseLiquid(GameTestHelper helper, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            helper.getLevel().setBlock(pos.relative(direction), Blocks.STONE.defaultBlockState(), 3);
        }
        helper.getLevel().setBlock(pos, PlayerLiquids.BLOCK.get().defaultBlockState(), 3);
    }

    private static long refreshTickCount(GameTestHelper helper, BlockPos pos) {
        var ticks = (LevelChunkTicks<Block>) helper.getLevel().getChunkAt(pos).getBlockTicks();
        return ticks.getAll().filter(tick -> tick.type() == PlayerLiquids.BLOCK.get() && tick.pos().equals(pos))
                .count();
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void dyeBucketAndWaterFlow(GameTestHelper helper) {
        var level = helper.getLevel();
        var recipe =
                level.getRecipeManager().byKey(ResourceLocation.fromNamespaceAndPath(PlayerBlocks.MODID, "player_dye"))
                        .orElseThrow().value();
        var input = CraftingInput.of(1, 1, List.of(new ItemStack(Items.BLUE_DYE)));
        helper.assertTrue(((CraftingRecipe) recipe).matches(input, level),
                "A single vanilla dye converts without amethyst");
        var contents = FluidUtil.getFluidContained(new ItemStack(PlayerLiquids.BUCKET.get())).orElseThrow();
        helper.assertTrue(contents.is(PlayerLiquids.SOURCE.get()) && contents.getAmount() == 1000,
                "Bucket contains four 250mB dye portions");
        var dispenserPos = helper.absolutePos(new BlockPos(3, 1, 3));
        var dispenserState = Blocks.DISPENSER.defaultBlockState().setValue(DispenserBlock.FACING, Direction.WEST);
        level.setBlock(dispenserPos, dispenserState, 3);
        var dispenser = (DispenserBlockEntity) level.getBlockEntity(dispenserPos);
        var source = new BlockSource(level, dispenserPos, dispenserState, dispenser);
        var emptied = DispenserBlock.DISPENSER_REGISTRY.get(PlayerLiquids.BUCKET.get())
                .dispense(source, new ItemStack(PlayerLiquids.BUCKET.get()));
        helper.assertTrue(
                emptied.is(Items.BUCKET) && level.getFluidState(dispenserPos.west()).is(PlayerLiquids.SOURCE.get()),
                "Dispenser places Player Liquid and returns an empty bucket");
        var filled = DispenserBlock.DISPENSER_REGISTRY.get(Items.BUCKET).dispense(source, new ItemStack(Items.BUCKET));
        helper.assertTrue(filled.is(PlayerLiquids.BUCKET.get()),
                "Dispenser picks Player Liquid back up into its bucket");
        var player = TestPlayers.create(helper, GameType.CREATIVE);
        PlayerColors.get(level.getServer()).set(player.getUUID(), 0x38AACC);
        var pos = helper.absolutePos(new BlockPos(1, 2, 1));
        level.setBlock(pos.below(2), Blocks.STONE.defaultBlockState(), 3);
        helper.assertTrue(PlayerLiquids.BUCKET.get().emptyContents(player, level, pos, null), "Bucket places liquid");
        PlayerLiquids.BUCKET.get().checkExtraContent(player, level, new ItemStack(PlayerLiquids.BUCKET.get()), pos);
        helper.assertTrue(level.getBlockEntity(pos) instanceof PlayerLiquidEntity entity &&
                        player.getUUID().equals(entity.owner()) && entity.color() == 0x38AACC,
                "Placed source retains its player's color");
        helper.runAfterDelay(12, () -> {
            helper.assertTrue(level.getFluidState(pos.below()).is(PlayerLiquids.FLOWING.get()),
                    "Liquid flows downward like water");
            helper.assertTrue(level.getBlockEntity(pos.below()) instanceof PlayerLiquidEntity entity &&
                    player.getUUID().equals(entity.owner()), "Flow inherits ownership");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void createSpoutUses250AndPreservesShulker(GameTestHelper helper) throws Exception {
        if (!ModList.get().isLoaded("create")) {
            helper.succeed();
            return;
        }
        var level = helper.getLevel();
        var filling = Class.forName("com.simibubi.create.content.fluids.spout.FillingBySpout")
                .getMethod("fillItem", Level.class, int.class, ItemStack.class, FluidStack.class);
        var fluid = new FluidStack(PlayerLiquids.SOURCE.get(), 1000);
        var tooLittle = (ItemStack) filling.invoke(null, level, 249, new ItemStack(Items.STONE_SLAB), fluid.copy());
        helper.assertTrue(tooLittle.isEmpty(), "Spout requires a full 250mB dye portion");
        var converted = (ItemStack) filling.invoke(null, level, 250, new ItemStack(Items.STONE_SLAB), fluid.copy());
        helper.assertTrue(converted.is(ModContent.SLAB.get().asItem()), "250mB converts a vanilla slab");
        var box = new ItemStack(Items.BLUE_SHULKER_BOX);
        box.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(new ItemStack(Items.DIAMOND, 7))));
        var result = (ItemStack) filling.invoke(null, level, 250, box, fluid.copy());
        helper.assertTrue(result.is(ModContent.SHULKER.get().asItem()) &&
                        result.get(DataComponents.CONTAINER).stream().mapToInt(ItemStack::getCount).sum() == 7,
                "Spout shulker conversion preserves contents");
        helper.succeed();
    }
}
