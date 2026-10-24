package com.ourcreation.playerblocks;

import com.mojang.authlib.GameProfile;
import com.ourcreation.playerblocks.block.PlayerConcreteBlock;
import com.ourcreation.playerblocks.color.PlayerColors;
import com.ourcreation.playerblocks.registry.ModContent;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(PlayerBlocks.MODID)
@PrefixGameTestTemplate(false)
public final class LiveCompatibilityTests {

    @GameTest(template = "empty")
    public static void worldEditPlacementAndStorage(GameTestHelper helper) throws Exception {
        if (!ModList.get().isLoaded("worldedit")) {
            helper.succeed();
            return;
        }
        WorldEditColorTests.worldEditPastesRebindBlocksAndKeepStorage(helper);
    }

    private static Object call(Object object, String name, Class<?>[] types,
                               Object... values) throws ReflectiveOperationException {
        return object.getClass().getMethod(name, types).invoke(object, values);
    }

    @GameTest(template = "empty")
    @SuppressWarnings("unchecked")
    public static void actualFtbTeamsAndQuestItemMatching(GameTestHelper helper) throws Exception {
        if (!ModList.get().isLoaded("ftbteams")) {
            helper.succeed();
            return;
        }
        var api = Class.forName("dev.ftb.mods.ftbteams.api.FTBTeamsAPI").getMethod("api").invoke(null);
        var manager = call(api, "getManager", new Class<?>[0]);
        var implementation = manager.getClass();
        var leader = UUID.randomUUID();
        var member = UUID.randomUUID();
        var id = UUID.randomUUID();
        var partyClass = Class.forName("dev.ftb.mods.ftbteams.data.PartyTeam");
        var party = partyClass.getConstructor(implementation, UUID.class).newInstance(manager, id);
        var ownerField = partyClass.getDeclaredField("owner");
        ownerField.setAccessible(true);
        ownerField.set(party, leader);
        var playerClass = Class.forName("dev.ftb.mods.ftbteams.data.PlayerTeam");
        var personal = playerClass.getConstructor(implementation, UUID.class).newInstance(manager, member);
        call(personal, "setEffectiveTeam", new Class<?>[]{Class.forName("dev.ftb.mods.ftbteams.data.AbstractTeam")},
                party);
        var playersField = implementation.getDeclaredField("knownPlayers");
        playersField.setAccessible(true);
        var players = (Map<UUID, Object>) playersField.get(manager);
        var teams = (Map<UUID, Object>) call(manager, "getTeamMap", new Class<?>[0]);
        players.put(member, personal);
        teams.put(id, party);
        try {
            var colors = PlayerColors.get(helper.getLevel().getServer());
            colors.set(leader, 0x1199DD);
            colors.set(member, 0xCC5500);
            colors.refreshTeam(helper.getLevel().getServer(), member);
            helper.assertTrue(colors.color(member) == 0x1199DD,
                    "Actual FTB party uses its owner's color, not the member's");
            ownerField.set(party, member);
            colors.refreshTeam(helper.getLevel().getServer(), member);
            helper.assertTrue(colors.color(member) == 0xCC5500, "Actual FTB leadership changes update the color");
            if (ModList.get().isLoaded("ftbquests")) {
                var fileClass = Class.forName("dev.ftb.mods.ftbquests.quest.BaseQuestFile");
                var file = Class.forName("dev.ftb.mods.ftbquests.quest.ServerQuestFile").getField("INSTANCE").get(null);
                helper.assertTrue(file != null, "FTB Quests starts alongside Player Blocks");
                var groupClass = Class.forName("dev.ftb.mods.ftbquests.quest.ChapterGroup");
                var group = groupClass.getConstructor(long.class, fileClass).newInstance(91001L, file);
                var chapterClass = Class.forName("dev.ftb.mods.ftbquests.quest.Chapter");
                var chapter =
                        chapterClass.getConstructor(long.class, fileClass, groupClass).newInstance(91002L, file, group);
                var questClass = Class.forName("dev.ftb.mods.ftbquests.quest.Quest");
                var quest = questClass.getConstructor(long.class, chapterClass).newInstance(91003L, chapter);
                var taskClass = Class.forName("dev.ftb.mods.ftbquests.quest.task.ItemTask");
                var task = taskClass.getConstructor(long.class, questClass).newInstance(91004L, quest);
                call(task, "setStackAndCount", new Class<?>[]{ItemStack.class, int.class},
                        new ItemStack(ModContent.ITEM.get()), 1);
                var colored = new ItemStack(ModContent.ITEM.get());
                colored.set(DataComponents.BLOCK_STATE,
                        new BlockItemStateProperties(Map.of("color_id", Integer.toString(colors.slot(member)))));
                helper.assertTrue(Boolean.TRUE.equals(call(task, "test", new Class<?>[]{ItemStack.class}, colored)),
                        "Default FTB item task recognizes the block across owner color slots");
                call(task, "setStackAndCount", new Class<?>[]{ItemStack.class, int.class},
                        new ItemStack(ModContent.DYE.get()), 1);
                helper.assertTrue(Boolean.TRUE.equals(
                                call(task, "test", new Class<?>[]{ItemStack.class}, new ItemStack(ModContent.DYE.get()))),
                        "FTB item tasks accept Player Dye");
            }
        } finally {
            players.remove(member);
            teams.remove(id);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void actualOpacMembershipUsesOwner(GameTestHelper helper) throws Exception {
        if (!ModList.get().isLoaded("openpartiesandclaims")) {
            helper.succeed();
            return;
        }
        var api = Class.forName("xaero.pac.common.server.api.OpenPACServerAPI").getMethod("get", MinecraftServer.class)
                .invoke(null, helper.getLevel().getServer());
        var manager = call(api, "getPartyManager", new Class<?>[0]);
        var leader = UUID.randomUUID();
        var member = UUID.randomUUID();
        var party = call(manager, "createPartyForOwner", new Class<?>[]{GameProfile.class},
                new GameProfile(leader, "PBAuditOwner"));
        var rank = (Class<? extends Enum>) Class.forName("xaero.pac.common.parties.party.member.PartyMemberRank");
        call(party, "addMember", new Class<?>[]{UUID.class, rank, String.class}, member, Enum.valueOf(rank, "MEMBER"),
                "PBAuditMember");
        try {
            var colors = PlayerColors.get(helper.getLevel().getServer());
            colors.set(leader, 0x5599AA);
            colors.set(member, 0xAA5500);
            colors.refreshTeam(helper.getLevel().getServer(), member);
            helper.assertTrue(colors.color(member) == 0x5599AA, "Actual OPAC member uses the party owner's hex color");
            call(party, "removeMember", new Class<?>[]{UUID.class}, member);
            colors.refreshTeam(helper.getLevel().getServer(), member);
            helper.assertTrue(colors.color(member) == 0xAA5500, "Leaving an OPAC party restores the personal color");
        } finally {
            call(manager, "removePartyByOwner", new Class<?>[]{UUID.class}, leader);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void actualCopycatsAndFramedMaterialsKeepSlots(GameTestHelper helper) throws Exception {
        var level = helper.getLevel();
        int slot = PlayerColors.get(level.getServer()).slot(UUID.randomUUID());
        for (var material : List.of(ModContent.CONCRETE.get(), ModContent.CHISELED.get(), ModContent.TILES.get())) {
            var stack = new ItemStack(material);
            stack.set(DataComponents.BLOCK_STATE,
                    new BlockItemStateProperties(Map.of("color_id", Integer.toString(slot))));
            if (ModList.get().isLoaded("copycats")) {
                var block =
                        BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("copycats", "copycat_block"));
                var accepted = (BlockState) call(block, "getAcceptedBlockState",
                        new Class<?>[]{Level.class, BlockPos.class, ItemStack.class, Direction.class}, level,
                        helper.absolutePos(new BlockPos(1, 1, 1)), stack, Direction.UP);
                helper.assertTrue(accepted != null && accepted.is(material) &&
                                accepted.getValue(PlayerConcreteBlock.COLOR_ID) == slot,
                        "Actual Copycats+ material acceptance retains the texture and owner slot");
            }
            if (ModList.get().isLoaded("framedblocks")) {
                var holder = Class.forName("xfacthd.framedblocks.common.FBContent").getField("FACTORY_BLOCK").get(null);
                var factory = call(holder, "get", new Class<?>[0]);
                helper.assertTrue(Boolean.TRUE.equals(
                                call(factory, "canApplyInCraftingRecipe", new Class<?>[]{ItemStack.class}, stack)),
                        "Actual FramedBlocks accepts Player Block as a crafting camo");
                var camo = call(factory, "applyCamoInCraftingRecipe", new Class<?>[]{ItemStack.class}, stack);
                var state = (BlockState) call(camo, "getState", new Class<?>[0]);
                helper.assertTrue(state.is(material) && state.getValue(PlayerConcreteBlock.COLOR_ID) == slot,
                        "Actual FramedBlocks camo retains the texture and owner slot");
            }
            if (ModList.get().isLoaded("create")) {
                var copycat =
                        BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("create", "copycat_panel"));
                var accepted = (BlockState) call(copycat, "getAcceptedBlockState",
                        new Class<?>[]{Level.class, BlockPos.class, ItemStack.class, Direction.class}, level,
                        helper.absolutePos(new BlockPos(1, 1, 1)), stack, Direction.UP);
                helper.assertTrue(accepted != null && accepted.is(material) &&
                                accepted.getValue(PlayerConcreteBlock.COLOR_ID) == slot,
                        "Create copycats retain the texture and owner slot");
            }
        }
        helper.succeed();
    }
}
