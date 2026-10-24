package com.ourcreation.playerblocks;

import com.ourcreation.playerblocks.block.PlayerBanner;
import com.ourcreation.playerblocks.block.PlayerBed;
import com.ourcreation.playerblocks.block.entity.PlayerBannerEntity;
import com.ourcreation.playerblocks.block.entity.PlayerColoredEntity;
import com.ourcreation.playerblocks.block.entity.PlayerConcreteEntity;
import com.ourcreation.playerblocks.color.PlayerColors;
import com.ourcreation.playerblocks.color.TeamColors;
import com.ourcreation.playerblocks.registry.ModContent;

import java.util.ArrayList;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.BannerBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.level.block.entity.BannerPatterns;
import net.minecraft.world.level.block.entity.BedBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(PlayerBlocks.MODID)
@PrefixGameTestTemplate(false)
public final class ReferenceBlockTests {

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void vanillaBedAndBannerEntitiesKeepPatternsAndRecolor(GameTestHelper helper) {
        var level = helper.getLevel();
        var owner = TestPlayers.create(helper, GameType.CREATIVE);
        UUID leader = UUID.randomUUID();
        var team = new TeamColors.Team("test:native_decorations", leader);
        TeamColors.registerProvider("native_decorations", (server, player) ->
                server == level.getServer() && (player.equals(owner.getUUID()) || player.equals(leader)) ? team : null);
        var colors = PlayerColors.get(level.getServer());
        colors.set(owner.getUUID(), 0xCC1100);
        colors.set(leader, 0x27B6D9);
        var patterns = new BannerPatternLayers.Builder().add(
                level.registryAccess().lookupOrThrow(Registries.BANNER_PATTERN).getOrThrow(BannerPatterns.CROSS),
                DyeColor.RED).build();
        var positions = new ArrayList<BlockPos>();
        int x = 1;
        for (var block : new Block[]{ModContent.BED.get(), ModContent.BANNER.get(), ModContent.WALL_BANNER.get()}) {
            var pos = helper.absolutePos(new BlockPos(x++, 1, 2));
            positions.add(pos);
            level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 2);
            level.setBlock(pos.south(), Blocks.STONE.defaultBlockState(), 2);
            level.setBlock(pos, block.defaultBlockState(), 2);
            var entity = level.getBlockEntity(pos);
            helper.assertTrue(entity instanceof BedBlockEntity || entity instanceof BannerBlockEntity,
                    "Decorations use Minecraft's entity classes");
            var vanilla =
                    block instanceof PlayerBed ? Blocks.WHITE_BED : block instanceof PlayerBanner ? Blocks.WHITE_BANNER : Blocks.WHITE_WALL_BANNER;
            helper.assertTrue(
                    block.defaultBlockState().getRenderShape() == vanilla.defaultBlockState().getRenderShape(),
                    "Decorations use exactly Minecraft's render shape");
            ((PlayerColoredEntity) entity).link(owner.getUUID());
            if (entity instanceof PlayerBannerEntity banner) {
                var stack = new ItemStack(ModContent.BANNER.get());
                stack.set(DataComponents.BANNER_PATTERNS, patterns);
                banner.applyComponentsFromItemStack(stack);
                helper.assertTrue(banner.getItem().is(ModContent.BANNER.get().asItem()) &&
                                banner.getItem().get(DataComponents.BANNER_PATTERNS).equals(patterns),
                        "Banner pick item keeps its mod identity and vanilla patterns");
                var drops = Block.getDrops(level.getBlockState(pos), level, pos, banner);
                helper.assertTrue(
                        drops.size() == 1 && patterns.equals(drops.getFirst().get(DataComponents.BANNER_PATTERNS)),
                        "Breaking either banner form keeps the patterns");
            }
            var saved = entity.saveWithFullMetadata(level.registryAccess());
            var restored = BlockEntity.loadStatic(pos, level.getBlockState(pos), saved, level.registryAccess());
            helper.assertTrue(restored != null && restored.getClass() == entity.getClass() &&
                            owner.getUUID().equals(((PlayerColoredEntity) restored).owner()),
                    "Native entity registry reload preserves ownership");
            saved.putString("id", "player_blocks:player_concrete");
            var migrated = BlockEntity.loadStatic(pos, level.getBlockState(pos), saved, level.registryAccess());
            helper.assertTrue(migrated != null && migrated.getClass() == entity.getClass() &&
                            ((PlayerColoredEntity) migrated).color() == 0x27B6D9,
                    "Existing custom-model decorations migrate without losing team colors");
        }
        helper.assertTrue(BannerBlock.byColor(DyeColor.WHITE) == Blocks.WHITE_BANNER,
                "Vanilla white banner lookup remains unchanged");
        helper.assertTrue(ModContent.BANNER.get().asItem() instanceof BannerItem,
                "Banner uses Minecraft's item type for loom and washing behavior");
        colors.set(leader, 0x67DD88);
        helper.runAfterDelay(45, () -> {
            for (var pos : positions) {
                helper.assertTrue(((PlayerColoredEntity) level.getBlockEntity(pos)).color() == 0x67DD88,
                        "Both decoration types recolor to the team's leader color through their server tickers");
            }
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void allGlazedPatternsRotatePersistAndFollowTeamLeader(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = TestPlayers.create(helper, GameType.CREATIVE);
        UUID leader = UUID.randomUUID();
        var team = new TeamColors.Team("test:glazed_team", leader);
        TeamColors.registerProvider("glazed_tests", (server, member) -> server == level.getServer() &&
                (member.equals(player.getUUID()) || member.equals(leader)) ? team : null);
        var colors = PlayerColors.get(level.getServer());
        colors.set(player.getUUID(), 0xCC5500);
        colors.set(leader, 0x3488BB);
        colors.slot(leader);
        colors.refreshTeam(level.getServer(), leader);
        helper.assertTrue(ModContent.GLAZED_TERRACOTTA.size() == 16, "All sixteen vanilla pattern variants exist");
        var pos = helper.absolutePos(new BlockPos(2, 1, 2));
        level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 3);
        for (var holder : ModContent.GLAZED_TERRACOTTA) {
            for (var facing : new Direction[]{Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST}) {
                level.removeBlock(pos, false);
                player.setYRot(facing.toYRot());
                var stack = new ItemStack(holder.get());
                var context = context(player, stack, pos.below(), Direction.UP);
                helper.assertTrue(((BlockItem) stack.getItem()).place(context).consumesAction(),
                        "Glazed terracotta can be placed");
                var state = level.getBlockState(pos);
                helper.assertTrue(state.is(holder.get()) &&
                                state.getValue(BlockStateProperties.HORIZONTAL_FACING) == facing.getOpposite(),
                        "Glazed pattern follows vanilla placement rotation");
                var entity = (PlayerConcreteEntity) level.getBlockEntity(pos);
                helper.assertTrue(player.getUUID().equals(entity.owner()) && entity.color() == colors.color(leader),
                        "Every pattern uses the team leader rather than the member's personal hex");
                var restored = new PlayerConcreteEntity(pos, state);
                restored.loadWithComponents(entity.saveWithFullMetadata(level.registryAccess()),
                        level.registryAccess());
                helper.assertTrue(player.getUUID().equals(restored.owner()) && restored.color() == 0x3488BB,
                        "Pattern ownership and tint survive saved-data reload");
            }
        }
        colors.set(leader, 0x42E78A);
        helper.runAfterDelay(25, () -> {
            helper.assertTrue(((PlayerConcreteEntity) level.getBlockEntity(pos)).color() == 0x42E78A,
                    "Already placed glazed blocks recolor when the leader changes hex");
            helper.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void bedCandlesAndBothBannerFormsKeepOwnership(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = TestPlayers.create(helper, GameType.CREATIVE);
        PlayerColors.get(level.getServer()).set(player.getUUID(), 0x27B6D9);
        var foot = helper.absolutePos(new BlockPos(1, 1, 1));
        level.setBlock(foot.below(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(foot.south().below(), Blocks.STONE.defaultBlockState(), 3);
        player.setYRot(0);
        var bed = new ItemStack(ModContent.BED.get());
        helper.assertTrue(
                ((BlockItem) bed.getItem()).place(context(player, bed, foot.below(), Direction.UP)).consumesAction(),
                "Player bed places normally");
        var head = foot.relative(level.getBlockState(foot).getValue(BedBlock.FACING));
        for (var half : new BlockPos[]{foot, head}) {
            helper.assertTrue(level.getBlockState(half).is(ModContent.BED.get()), "Bed creates both halves");
            assertOwner(helper, level.getBlockEntity(half), player.getUUID());
        }
        var candlePos = helper.absolutePos(new BlockPos(3, 1, 1));
        level.setBlock(candlePos.below(), Blocks.STONE.defaultBlockState(), 3);
        for (int count = 1; count <= 4; count++) {
            var candle = new ItemStack(ModContent.CANDLE.get());
            var hitPos = count == 1 ? candlePos.below() : candlePos;
            helper.assertTrue(((BlockItem) candle.getItem()).place(context(player, candle, hitPos, Direction.UP))
                    .consumesAction(), "Candles stack up to four");
            helper.assertTrue(level.getBlockState(candlePos).getValue(BlockStateProperties.CANDLES) == count,
                    "Stacked candle count matches placement");
            assertOwner(helper, level.getBlockEntity(candlePos), player.getUUID());
        }
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.FLINT_AND_STEEL));
        var light = new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(candlePos), Direction.UP, candlePos, false));
        helper.assertTrue(player.getMainHandItem().useOn(light).consumesAction() &&
                        level.getBlockState(candlePos).getValue(BlockStateProperties.LIT),
                "Colored candles can be lit using vanilla flint and steel");
        var cakePos = helper.absolutePos(new BlockPos(2, 1, 4));
        level.setBlock(cakePos.below(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(cakePos, Blocks.CAKE.defaultBlockState(), 3);
        var cakeCandle = new ItemStack(ModContent.CANDLE.get());
        level.getBlockState(cakePos).useItemOn(cakeCandle, level, player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(cakePos), Direction.UP, cakePos, false));
        helper.assertTrue(level.getBlockState(cakePos).is(Blocks.CAKE) && cakeCandle.getCount() == 1,
                "Standalone player candles do not call vanilla's missing candle-cake mapping");
        var standing = helper.absolutePos(new BlockPos(1, 1, 3));
        level.setBlock(standing.below(), Blocks.STONE.defaultBlockState(), 3);
        var banner = new ItemStack(ModContent.BANNER.get());
        helper.assertTrue(((BlockItem) banner.getItem()).place(context(player, banner, standing.below(), Direction.UP))
                        .consumesAction() && level.getBlockState(standing).is(ModContent.BANNER.get()),
                "Standing banner uses its colored block");
        assertOwner(helper, level.getBlockEntity(standing), player.getUUID());
        var wall = helper.absolutePos(new BlockPos(3, 1, 3));
        level.setBlock(wall.south(), Blocks.STONE.defaultBlockState(), 3);
        var wallStack = new ItemStack(ModContent.BANNER.get());
        helper.assertTrue(
                ((BlockItem) wallStack.getItem()).place(context(player, wallStack, wall.south(), Direction.NORTH))
                        .consumesAction() && level.getBlockState(wall).is(ModContent.WALL_BANNER.get()),
                "Same banner item also places the wall form");
        assertOwner(helper, level.getBlockEntity(wall), player.getUUID());
        helper.assertTrue(new ItemStack(ModContent.WALL_BANNER.get()).is(ModContent.BANNER.get().asItem()),
                "Both banner forms share one inventory item");
        helper.succeed();
    }

    private static BlockPlaceContext context(Player player, ItemStack stack, BlockPos support, Direction face) {
        return new BlockPlaceContext(player, InteractionHand.MAIN_HAND, stack,
                new BlockHitResult(Vec3.atCenterOf(support), face, support, false));
    }

    private static void assertOwner(GameTestHelper helper, BlockEntity entity, UUID owner) {
        helper.assertTrue(
                entity instanceof PlayerColoredEntity && owner.equals(((PlayerColoredEntity) entity).owner()) &&
                        ((PlayerColoredEntity) entity).color() == 0x27B6D9,
                "Reference block retains player ownership and color");
    }
}
