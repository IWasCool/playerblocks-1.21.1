package com.ourcreation.playerblocks;

import com.ourcreation.playerblocks.block.entity.PlayerChestEntity;
import com.ourcreation.playerblocks.block.entity.PlayerShulkerEntity;
import com.ourcreation.playerblocks.color.PlayerColors;
import com.ourcreation.playerblocks.color.TeamColors;
import com.ourcreation.playerblocks.compat.SchematicColors;
import com.ourcreation.playerblocks.recipe.PlayerShulkerRecipe;
import com.ourcreation.playerblocks.registry.ModContent;

import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(PlayerBlocks.MODID)
@PrefixGameTestTemplate(false)
public final class PlayerStorageTests {

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void doubleChestKeepsInventoryAndFollowsTeamLeader(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = TestPlayers.create(helper, GameType.CREATIVE);
        UUID leader = UUID.randomUUID();
        var team = new TeamColors.Team("test:storage_chest", leader);
        TeamColors.registerProvider("storage_chest", (server, owner) ->
                server == level.getServer() && (owner.equals(player.getUUID()) || owner.equals(leader)) ? team : null);
        var colors = PlayerColors.get(level.getServer());
        colors.set(leader, 0x389ACC);
        colors.set(player.getUUID(), 0xFF6600);
        var left = helper.absolutePos(new BlockPos(1, 1, 2));
        var right = left.east();
        player.setYRot(0);
        for (var pos : List.of(left, right)) {
            level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 3);
            var stack = new ItemStack(ModContent.CHEST.get());
            helper.assertTrue(((BlockItem) stack.getItem()).place(context(player, stack, pos.below(), Direction.UP))
                    .consumesAction(), "Chest places with vanilla joining behavior");
        }
        var first = (PlayerChestEntity) level.getBlockEntity(left);
        var second = (PlayerChestEntity) level.getBlockEntity(right);
        first.setItem(0, new ItemStack(Items.DIAMOND, 7));
        second.setItem(0, new ItemStack(Items.EMERALD, 11));
        first.link(UUID.randomUUID());
        SchematicColors.finish(level, right, player.getUUID());
        var menu = (ChestMenu) level.getBlockState(left).getMenuProvider(level, left)
                .createMenu(1, player.getInventory(), player);
        helper.assertTrue(menu.getContainer().getContainerSize() == 54,
                "Two player chests expose the vanilla 54-slot menu");
        helper.assertTrue(
                Stream.of(menu.getContainer().getItem(0).getCount(), menu.getContainer().getItem(27).getCount())
                        .sorted().toList().equals(List.of(7, 11)), "Joined chest menu exposes both inventories");
        for (var entity : List.of(first, second)) {
            helper.assertTrue(player.getUUID().equals(entity.owner()) && entity.color() == 0x389ACC,
                    "Both halves use the team leader's hex, not the member's hex");
            var restored = (PlayerChestEntity) BlockEntity.loadStatic(entity.getBlockPos(), entity.getBlockState(),
                    entity.saveWithFullMetadata(level.registryAccess()), level.registryAccess());
            helper.assertTrue(restored != null && ItemStack.matches(restored.getItem(0), entity.getItem(0)) &&
                    entity.owner().equals(restored.owner()), "Chest registry reload preserves items and ownership");
            entity.triggerEvent(1, 1);
            ChestBlockEntity.lidAnimateTick(level, entity.getBlockPos(), entity.getBlockState(), entity);
            helper.assertTrue(entity.getOpenNess(1) > 0, "Chest retains Minecraft's lid animation");
        }
        menu.removed(player);
        colors.set(leader, 0x92DD33);
        helper.runAfterDelay(45, () -> {
            for (var entity : List.of(first, second)) {
                helper.assertTrue(entity.color() == 0x92DD33 && !entity.getItem(0).isEmpty(),
                        "Recoloring both chest halves preserves their stored items");
            }
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void shulkerConversionPlacementDropsAndAnimationKeepContents(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = TestPlayers.create(helper, GameType.CREATIVE);
        var colors = PlayerColors.get(level.getServer());
        colors.set(player.getUUID(), 0x26B4DD);
        var source = new ItemStack(Items.BLUE_SHULKER_BOX);
        var contents = ItemContainerContents.fromItems(List.of(new ItemStack(Items.DIAMOND, 7)));
        source.set(DataComponents.CONTAINER, contents);
        source.set(DataComponents.CUSTOM_NAME, Component.literal("Tools"));
        var input = CraftingInput.of(2, 1, List.of(source, new ItemStack(ModContent.DYE.get())));
        var recipe = level.getRecipeManager()
                .byKey(ResourceLocation.fromNamespaceAndPath(PlayerBlocks.MODID, "player_shulker_box")).orElseThrow()
                .value();
        helper.assertTrue(recipe instanceof PlayerShulkerRecipe && ((PlayerShulkerRecipe) recipe).matches(input, level),
                "Any vanilla dyed shulker converts through the registered content-preserving recipe");
        var box = ((PlayerShulkerRecipe) recipe).assemble(input, level.registryAccess());
        helper.assertTrue(
                box.is(ModContent.SHULKER.get().asItem()) && contents.equals(box.get(DataComponents.CONTAINER)) &&
                        source.get(DataComponents.CUSTOM_NAME).equals(box.get(DataComponents.CUSTOM_NAME)),
                "Hex conversion keeps contents and custom name");
        helper.assertTrue(!box.getItem().canFitInsideContainerItems(),
                "A player shulker cannot nest inside other shulkers");
        var pos = helper.absolutePos(new BlockPos(2, 2, 2));
        for (var facing : Direction.values()) {
            level.removeBlock(pos, false);
            var support = pos.relative(facing.getOpposite());
            level.setBlock(support, Blocks.STONE.defaultBlockState(), 2);
            helper.assertTrue(
                    ((BlockItem) box.getItem()).place(context(player, box.copy(), support, facing)).consumesAction(),
                    "Shulker places on every face");
            helper.assertTrue(level.getBlockState(pos).getValue(ShulkerBoxBlock.FACING) == facing,
                    "Shulker uses vanilla six-way orientation");
            var entity = (PlayerShulkerEntity) level.getBlockEntity(pos);
            helper.assertTrue(entity.getItem(0).is(Items.DIAMOND) && entity.getItem(0).getCount() == 7 &&
                    entity.color() == 0x26B4DD, "Placement keeps contents and binds the player's hex");
        }
        var entity = (PlayerShulkerEntity) level.getBlockEntity(pos);
        var menu = entity.createMenu(1, player.getInventory(), player);
        helper.assertTrue(menu instanceof ShulkerBoxMenu, "Player shulker uses Minecraft's storage menu");
        var cloned = ModContent.SHULKER.get().getCloneItemStack(level, pos, level.getBlockState(pos));
        helper.assertTrue(cloned.is(box.getItem()) && contents.equals(cloned.get(DataComponents.CONTAINER)),
                "Pick-block preserves the custom box and its contents");
        var drops = Block.getDrops(level.getBlockState(pos), level, pos, entity);
        helper.assertTrue(drops.size() == 1 && drops.getFirst().is(box.getItem()) &&
                        contents.equals(drops.getFirst().get(DataComponents.CONTAINER)),
                "Survival break drops one filled player shulker");
        ModContent.SHULKER.get().playerWillDestroy(level, pos, level.getBlockState(pos), player);
        var creativeDrops = level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(.5));
        helper.assertTrue(creativeDrops.size() == 1 && creativeDrops.getFirst().getItem().is(box.getItem()) &&
                        contents.equals(creativeDrops.getFirst().getItem().get(DataComponents.CONTAINER)),
                "Creative break keeps the player box identity and contents");
        var restored = (PlayerShulkerEntity) BlockEntity.loadStatic(pos, level.getBlockState(pos),
                entity.saveWithFullMetadata(level.registryAccess()), level.registryAccess());
        helper.assertTrue(
                restored != null && player.getUUID().equals(restored.owner()) && restored.getItem(0).getCount() == 7,
                "Shulker registry reload preserves inventory and ownership");
        level.setBlock(pos.above(), Blocks.AIR.defaultBlockState(), 2);
        entity.triggerEvent(1, 1);
        colors.set(player.getUUID(), 0x88CC33);
        helper.runAfterDelay(45, () -> {
            helper.assertTrue(entity.getAnimationStatus() == ShulkerBoxBlockEntity.AnimationStatus.OPENED &&
                    entity.getProgress(1) == 1, "Native shulker ticker animates lid opening");
            helper.assertTrue(entity.color() == 0x88CC33 && entity.getItem(0).getCount() == 7,
                    "Shulker recoloring preserves its inventory");
            helper.succeed();
        });
    }

    private static BlockPlaceContext context(Player player, ItemStack stack, BlockPos pos, Direction face) {
        return new BlockPlaceContext(player, InteractionHand.MAIN_HAND, stack,
                new BlockHitResult(Vec3.atCenterOf(pos), face, pos, false));
    }
}
