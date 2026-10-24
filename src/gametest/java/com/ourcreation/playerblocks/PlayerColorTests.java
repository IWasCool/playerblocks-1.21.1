package com.ourcreation.playerblocks;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.ourcreation.playerblocks.block.PlayerConcreteBlock;
import com.ourcreation.playerblocks.block.entity.PlayerConcreteEntity;
import com.ourcreation.playerblocks.color.PlayerColors;
import com.ourcreation.playerblocks.color.TeamColors;
import com.ourcreation.playerblocks.item.PlayerBlockItem;
import com.ourcreation.playerblocks.registry.ModContent;

import java.util.HashMap;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(PlayerBlocks.MODID)
@PrefixGameTestTemplate(false)
public final class PlayerColorTests {

    @GameTest(template = "empty")
    public static void commandsTeamsPersistenceAndMaterials(GameTestHelper helper) throws CommandSyntaxException {
        var level = helper.getLevel();
        var server = level.getServer();
        var leaderProfile = new GameProfile(UUID.randomUUID(), "PBLeader");
        var memberProfile = new GameProfile(UUID.randomUUID(), "PBMember");
        var leader = new ServerPlayer(server, level, leaderProfile, ClientInformation.createDefault());
        var member = new ServerPlayer(server, level, memberProfile, ClientInformation.createDefault());
        var console = server.createCommandSourceStack().withSuppressedOutput();
        var leaderSource = console.withEntity(leader).withPermission(0);
        var memberSource = console.withEntity(member).withPermission(0);
        var operatorSource = leaderSource.withPermission(2);
        var membership = new HashMap<UUID, TeamColors.Team>();
        TeamColors.registerProvider("player_blocks_test", (s, id) -> s == server ? membership.get(id) : null);
        var dispatcher = server.getCommands().getDispatcher();
        var colors = PlayerColors.get(server);
        colors.slot(leader.getUUID());
        int memberSlot = colors.slot(member.getUUID());
        dispatcher.execute("hex PBLeader #27B6D9", leaderSource);
        dispatcher.execute("playercolor confirm " + colors.pendingHex(leader.getUUID()).token(), leaderSource);
        dispatcher.execute("hex PBMember #FF1234", memberSource);
        dispatcher.execute("playercolor confirm " + colors.pendingHex(member.getUUID()).token(), memberSource);
        helper.assertTrue(colors.personalColor(leader.getUUID()) == 0x27B6D9,
                "Named command accepts hex and self permission");
        expectFailure(helper, () -> dispatcher.execute("hex PBLeader #000000", memberSource));
        expectFailure(helper, () -> dispatcher.execute("hex PBMember #XYZ123", memberSource));
        helper.assertTrue(colors.personalColor(member.getUUID()) == 0xFF1234, "Rejected commands leave colors intact");
        var team = server.getScoreboard().addPlayerTeam("pb_color_test");
        server.getScoreboard().addPlayerToTeam(leaderProfile.getName(), team);
        server.getScoreboard().addPlayerToTeam(memberProfile.getName(), team);
        dispatcher.execute("teamcolor leader pb_color_test PBLeader", operatorSource);
        var resolvedTeam = new TeamColors.Team("scoreboard:pb_color_test", leader.getUUID());
        membership.put(member.getUUID(), resolvedTeam);
        membership.put(leader.getUUID(), resolvedTeam);
        colors.refreshTeams(server);
        helper.assertTrue(colors.color(member.getUUID()) == 0x27B6D9, "Members inherit the offline leader's color");
        expectFailure(helper, () -> dispatcher.execute("teamcolor hex #010203", memberSource));
        dispatcher.execute("teamcolor hex #ABCDEF", leaderSource);
        helper.assertTrue(colors.color(member.getUUID()) == 0xABCDEF, "Leader changes the whole team's color");
        var stack = new ItemStack(ModContent.ITEM.get());
        PlayerBlockItem.prepare(stack, level, member);
        helper.assertTrue(PlayerBlockItem.material(stack).getValue(PlayerConcreteBlock.COLOR_ID) == memberSlot,
                "Copycat and framed material retain the member's stable slot");
        helper.assertTrue(colors.palette().getInt(Integer.toString(memberSlot)) == 0xABCDEF,
                "Material palette follows team override");
        var restored =
                PlayerColors.load(colors.save(new CompoundTag(), level.registryAccess()), level.registryAccess());
        restored.refreshTeams(server);
        helper.assertTrue(restored.color(member.getUUID()) == 0xABCDEF && restored.slot(member.getUUID()) == memberSlot,
                "Team color, assigned leader, and material slot survive saved-data reload");
        dispatcher.execute("teamcolor reset", leaderSource);
        helper.assertTrue(colors.color(member.getUUID()) == 0x27B6D9, "Reset restores leader inheritance");
        dispatcher.execute("hex PBLeader #010203", operatorSource);
        helper.assertTrue(colors.color(member.getUUID()) == 0x010203, "Leader personal recoloring reaches the team");
        var pos = helper.absolutePos(new BlockPos(2, 2, 2));
        level.setBlock(pos, ModContent.CONCRETE.get().defaultBlockState(), 2);
        ((PlayerConcreteEntity) level.getBlockEntity(pos)).link(member.getUUID());
        server.getScoreboard().removePlayerFromTeam(memberProfile.getName(), team);
        membership.remove(member.getUUID());
        helper.runAfterDelay(45, () -> {
            helper.assertTrue(colors.color(member.getUUID()) == 0xFF1234,
                    "Leaving the team restores personal color automatically");
            helper.assertTrue(((PlayerConcreteEntity) level.getBlockEntity(pos)).color() == 0xFF1234,
                    "Previously placed blocks also follow membership changes");
            helper.assertTrue(colors.slot(member.getUUID()) == memberSlot,
                    "Membership changes preserve material slots");
            server.getScoreboard().removePlayerTeam(team);
            membership.clear();
            helper.succeed();
        });
    }

    @FunctionalInterface
    private interface Command {

        void run() throws CommandSyntaxException;
    }

    private static void expectFailure(GameTestHelper helper, Command command) {
        try {
            command.run();
        } catch (CommandSyntaxException expected) {
            return;
        }
        helper.fail("Command should have rejected unauthorized or malformed input");
    }
}
