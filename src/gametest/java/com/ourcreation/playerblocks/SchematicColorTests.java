package com.ourcreation.playerblocks;

import com.mojang.authlib.GameProfile;
import com.ourcreation.playerblocks.block.PlayerConcreteBlock;
import com.ourcreation.playerblocks.block.entity.PlayerColoredEntity;
import com.ourcreation.playerblocks.block.entity.PlayerConcreteEntity;
import com.ourcreation.playerblocks.color.PlayerColors;
import com.ourcreation.playerblocks.color.TeamColors;
import com.ourcreation.playerblocks.compat.SchematicColors;
import com.ourcreation.playerblocks.registry.ModContent;

import java.util.Arrays;
import java.util.HashSet;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(PlayerBlocks.MODID)
@PrefixGameTestTemplate(false)
public final class SchematicColorTests {

    @GameTest(template = "empty")
    public static void pasteRebindsSavedOwnersAndUsesLeaderColor(GameTestHelper helper) {
        var level = helper.getLevel();
        var colors = PlayerColors.get(level.getServer());
        UUID author = UUID.randomUUID(), placer = UUID.randomUUID(), leader = UUID.randomUUID();
        colors.set(author, 0xFF0000);
        colors.set(placer, 0x00FF00);
        colors.set(leader, 0x102030);
        var team = new TeamColors.Team("test:schematic_team", leader);
        var members = new HashSet<UUID>();
        members.add(leader);
        TeamColors.registerProvider("schematic_test",
                (server, member) -> server == level.getServer() && members.contains(member) ? team : null);
        var pos = helper.absolutePos(new BlockPos(2, 2, 2));
        level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(pos.south(), Blocks.STONE.defaultBlockState(), 3);
        for (boolean inTeam : new boolean[]{false, true}) {
            if (inTeam) {
                members.add(placer);
                colors.refreshTeam(level.getServer(), placer);
            }
            for (Block block : ModContent.allBlocks()) {
                var sourceState = block.defaultBlockState();
                if (sourceState.hasProperty(PlayerConcreteBlock.COLOR_ID)) {
                    sourceState = sourceState.setValue(PlayerConcreteBlock.COLOR_ID, colors.slot(author));
                }
                var sourceData = new CompoundTag();
                sourceData.putUUID("owner", author);
                sourceData.putInt("color", colors.color(author));
                var paste = SchematicColors.prepare(level, sourceState, sourceData, placer);
                level.setBlock(pos, paste.state(), 2);
                var entity = (PlayerColoredEntity) level.getBlockEntity(pos);
                level.getBlockEntity(pos).loadWithComponents(paste.data(), level.registryAccess());
                SchematicColors.finish(level, pos, placer);
                helper.assertTrue(placer.equals(entity.owner()), "Paste replaces the original author for " + block);
                helper.assertTrue(entity.color() == (inTeam ? colors.color(leader) : colors.personalColor(placer)),
                        "Paste uses the placing player or the team leader's color for " + block);
                if (sourceState.hasProperty(PlayerConcreteBlock.COLOR_ID)) {
                    helper.assertTrue(
                            level.getBlockState(pos).getValue(PlayerConcreteBlock.COLOR_ID) == colors.slot(placer),
                            "Foreign slots are rebound");
                    helper.assertTrue(sourceState.getValue(PlayerConcreteBlock.COLOR_ID) == colors.slot(author),
                            "Template state is never mutated");
                }
                helper.assertTrue(sourceData.getUUID("owner").equals(author), "Template NBT is never mutated");
                var saved = level.getBlockEntity(pos).saveWithFullMetadata(level.registryAccess());
                var restored = ((EntityBlock) block).newBlockEntity(pos, paste.state());
                restored.loadWithComponents(saved, level.registryAccess());
                helper.assertTrue(placer.equals(((PlayerColoredEntity) restored).owner()) &&
                        entity.color() == ((PlayerColoredEntity) restored).color(), "Pasted ownership survives reload");
            }
        }
        colors.setTeam(team.key(), 0xABCDEF);
        colors.refreshTeam(level.getServer(), leader);
        colors.refreshTeam(level.getServer(), placer);
        helper.assertTrue(colors.color(placer) == colors.color(leader),
                "Leader-set team coloring is identical for leader and member");
        colors.set(leader, 0x456789);
        colors.resetTeam(team.key());
        helper.assertTrue(colors.color(placer) == colors.color(leader) && colors.color(placer) == 0x456789,
                "Removing override follows leader's hex color");
        members.clear();
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void embeddedMaterialsAndDelayedShotsRebindWithoutLeakingContext(GameTestHelper helper) {
        var level = helper.getLevel();
        UUID placer = UUID.randomUUID(), other = UUID.randomUUID();
        var colors = PlayerColors.get(level.getServer());
        var source = new CompoundTag();
        source.put("Material", NbtUtils.writeBlockState(ModContent.CONCRETE.get().defaultBlockState()
                .setValue(PlayerConcreteBlock.COLOR_ID, colors.slot(other))));
        source.put("Item", new ItemStack(ModContent.ITEM.get()).save(level.registryAccess()));
        source.put("Unrelated", NbtUtils.writeBlockState(Blocks.RED_CONCRETE.defaultBlockState()));
        var snapshot = source.copy();
        var shot = SchematicColors.markShot(source, placer);
        var resumed = shot.copy();
        helper.assertTrue(SchematicColors.placer(resumed).equals(placer),
                "Delayed shot retains the operator UUID after reload");
        var paste = SchematicColors.prepare(level, Blocks.STONE.defaultBlockState(), resumed,
                SchematicColors.placer(resumed));
        String slot = Integer.toString(colors.slot(placer));
        helper.assertTrue(
                paste.data().getCompound("Material").getCompound("Properties").getString("color_id").equals(slot),
                "Copycat/framed embedded material gets placer slot");
        helper.assertTrue(
                paste.data().getCompound("Item").getCompound("components").getCompound("minecraft:block_state")
                        .getString("color_id").equals(slot), "Embedded material item components get placer slot");
        helper.assertTrue(paste.data().getCompound("Unrelated").equals(snapshot.getCompound("Unrelated")),
                "Other blocks' material data remains intact");
        helper.assertTrue(source.equals(snapshot) && !paste.data().contains(SchematicColors.PLACER),
                "Template stays intact and transport marker is stripped");
        helper.assertTrue(SchematicColors.prepare(level, Blocks.CHEST.defaultBlockState(), null, placer).data() == null,
                "Unrelated blocks do not get fabricated empty NBT");
        SchematicColors.withPlacer(placer, () -> {
            helper.assertTrue(SchematicColors.currentPlacer().equals(placer), "Placement scope carries the player");
            try {
                SchematicColors.withPlacer(other, () -> {
                    throw new IllegalStateException("test exception");
                });
            } catch (IllegalStateException expected) {
            }
            helper.assertTrue(SchematicColors.currentPlacer().equals(placer),
                    "Exceptional nested paste restores outer placer");
            return null;
        });
        helper.assertTrue(SchematicColors.currentPlacer() == null,
                "Placement scope cannot leak into another player's paste");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void actualCreatePlacementAndCannonShots(GameTestHelper helper) throws ReflectiveOperationException {
        if (!ModList.get().isLoaded("create")) {
            helper.succeed();
            return;
        }
        var level = helper.getLevel();
        UUID placer = UUID.randomUUID();
        PlayerColors.get(level.getServer()).set(placer, 0x987654);
        var blockHelper = Class.forName("com.simibubi.create.foundation.utility.BlockHelper");
        var place = blockHelper.getMethod("placeSchematicBlock", Level.class, BlockState.class, BlockPos.class,
                ItemStack.class, CompoundTag.class);
        var pos = helper.absolutePos(new BlockPos(1, 2, 1));
        SchematicColors.withPlacer(placer, () -> {
            try {
                place.invoke(null, level, ModContent.SLAB.get().defaultBlockState(), pos,
                        new ItemStack(ModContent.SLAB.get()), null);
            } catch (ReflectiveOperationException error) {
                throw new RuntimeException(error);
            }
            return null;
        });
        helper.assertTrue(placer.equals(((PlayerConcreteEntity) level.getBlockEntity(pos)).owner()),
                "Actual Create BlockHelper rebinds the creative paste");
        var cannonPos = helper.absolutePos(new BlockPos(3, 2, 3));
        level.setBlock(cannonPos,
                BuiltInRegistries.BLOCK.get(ResourceLocation.parse("create:schematicannon")).defaultBlockState(), 2);
        var cannon = level.getBlockEntity(cannonPos);
        helper.assertTrue(cannon instanceof SchematicColors.CannonOwner,
                "Actual Create cannon has persistent placer support");
        var player = new ServerPlayer(level.getServer(), level, new GameProfile(placer, "PBSchematicUser"),
                ClientInformation.createDefault());
        player.containerMenu = (AbstractContainerMenu) cannon.getClass()
                .getMethod("createMenu", int.class, Inventory.class, Player.class)
                .invoke(cannon, 1, player.getInventory(), player);
        var packetClass = Class.forName("com.simibubi.create.content.schematics.cannon.ConfigureSchematicannonPacket");
        var optionClass =
                Class.forName("com.simibubi.create.content.schematics.cannon.ConfigureSchematicannonPacket$Option");
        Object play =
                Arrays.stream(optionClass.getEnumConstants()).filter(option -> ((Enum<?>) option).name().equals("PLAY"))
                        .findFirst().orElseThrow();
        var packet = packetClass.getConstructor(optionClass, boolean.class).newInstance(play, true);
        packetClass.getMethod("handle", ServerPlayer.class).invoke(packet, player);
        helper.assertTrue(placer.equals(((SchematicColors.CannonOwner) cannon).playerBlocks$getPlacer()),
                "Actual Start packet captures the operator rather than the schematic author");
        var saved = cannon.saveWithFullMetadata(level.registryAccess());
        ((SchematicColors.CannonOwner) cannon).playerBlocks$setPlacer(null);
        cannon.loadWithComponents(saved, level.registryAccess());
        helper.assertTrue(placer.equals(((SchematicColors.CannonOwner) cannon).playerBlocks$getPlacer()),
                "Actual cannon persists operator across reload");
        var launch = cannon.getClass()
                .getDeclaredMethod("launchBlock", BlockPos.class, ItemStack.class, BlockState.class, CompoundTag.class);
        launch.setAccessible(true);
        var target = helper.absolutePos(new BlockPos(3, 2, 1));
        launch.invoke(cannon, target, new ItemStack(ModContent.ITEM.get()),
                ModContent.CONCRETE.get().defaultBlockState(), null);
        ((SchematicColors.CannonOwner) cannon).playerBlocks$setPlacer(UUID.randomUUID());
        helper.runAfterDelay(45, () -> {
            var entity = level.getBlockEntity(target);
            helper.assertTrue(
                    entity instanceof PlayerConcreteEntity && placer.equals(((PlayerConcreteEntity) entity).owner()) &&
                            ((PlayerConcreteEntity) entity).color() == 0x987654,
                    "Actual launched cannon shot is rebound at landing");
            helper.succeed();
        });
    }
}
