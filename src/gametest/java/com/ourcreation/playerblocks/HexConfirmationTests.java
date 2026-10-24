package com.ourcreation.playerblocks;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.ourcreation.playerblocks.color.PlayerColors;

import java.util.ArrayList;
import java.util.UUID;

import net.minecraft.commands.CommandSource;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.ServerOpListEntry;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(PlayerBlocks.MODID)
@PrefixGameTestTemplate(false)
public final class HexConfirmationTests {

    @GameTest(template = "empty")
    public static void chatConfirmationConsumesOnlyTwoChangesAndPersists(
            GameTestHelper helper) throws CommandSyntaxException {
        var server = helper.getLevel().getServer();
        var player = new ServerPlayer(server, helper.getLevel(), new GameProfile(UUID.randomUUID(), "PBHexLimited"),
                ClientInformation.createDefault());
        var messages = new Messages();
        var source = server.createCommandSourceStack().withSource(messages).withEntity(player).withPermission(0);
        var dispatcher = server.getCommands().getDispatcher();
        var colors = PlayerColors.get(server);
        int original = colors.personalColor(player.getUUID());
        dispatcher.execute("hex PBHexLimited #27B6D9", source);
        helper.assertTrue(
                colors.personalColor(player.getUUID()) == original && colors.hexChanges(player.getUUID()) == 0,
                "Requesting a color never applies it or consumes a change");
        var prompt = messages.getLast();
        helper.assertTrue(prompt.getString().contains("only 2 times") && prompt.getString().contains("2 changes left"),
                "Chat explains the limit before applying a color");
        String cancel = button(prompt, "[Cancel]"), confirm = button(prompt, "[Confirm]");
        dispatcher.execute(cancel.substring(1), source);
        helper.assertTrue(
                colors.personalColor(player.getUUID()) == original && colors.hexChanges(player.getUUID()) == 0,
                "Cancel leaves both color and allowance intact");
        String canceledConfirm = confirm;
        reject(helper, () -> dispatcher.execute(canceledConfirm.substring(1), source));
        dispatcher.execute("hex PBHexLimited #27B6D9", source);
        confirm = button(messages.getLast(), "[Confirm]");
        dispatcher.execute(confirm.substring(1), source);
        helper.assertTrue(
                colors.personalColor(player.getUUID()) == 0x27B6D9 && colors.hexChanges(player.getUUID()) == 1,
                "The clickable confirmation applies the selected hex once");
        String usedButton = confirm;
        reject(helper, () -> dispatcher.execute(usedButton.substring(1), source));
        var restored = PlayerColors.load(colors.save(new CompoundTag(), helper.getLevel().registryAccess()),
                helper.getLevel().registryAccess());
        helper.assertTrue(
                restored.hexChanges(player.getUUID()) == 1 && restored.personalColor(player.getUUID()) == 0x27B6D9 &&
                        restored.pendingHex(player.getUUID()) == null,
                "Allowance and hex survive restart; pending confirmations do not");
        dispatcher.execute("playercolor hex #102030", source);
        helper.assertTrue(messages.getLast().getString().contains("1 change left"),
                "Second prompt shows the last remaining use");
        dispatcher.execute(button(messages.getLast(), "[Confirm]").substring(1), source);
        helper.assertTrue(
                colors.personalColor(player.getUUID()) == 0x102030 && colors.hexChanges(player.getUUID()) == 2,
                "The personal-color alias shares the same two-use allowance");
        reject(helper, () -> dispatcher.execute("hex PBHexLimited #AABBCC", source));
        reject(helper, () -> dispatcher.execute("playercolor hex #AABBCC", source));
        reject(helper, () -> dispatcher.execute("playercolor reset", source));
        helper.assertTrue(
                colors.personalColor(player.getUUID()) == 0x102030 && colors.pendingHex(player.getUUID()) == null,
                "Exhausted commands cannot change the color or issue a new prompt");
        restored = PlayerColors.load(colors.save(new CompoundTag(), helper.getLevel().registryAccess()),
                helper.getLevel().registryAccess());
        helper.assertTrue(restored.hexChanges(player.getUUID()) == 2,
                "Both used changes remain exhausted in saved data");
        dispatcher.execute("hex PBHexLimited #ABCDEF", source.withPermission(2));
        helper.assertTrue(
                colors.personalColor(player.getUUID()) == 0xABCDEF && colors.hexChanges(player.getUUID()) == 2,
                "Operators can change exhausted players without spending or restoring their allowance");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void staleForeignExpiredAndCanceledButtonsCannotSpendChanges(
            GameTestHelper helper) throws CommandSyntaxException {
        var server = helper.getLevel().getServer();
        var player = new ServerPlayer(server, helper.getLevel(), new GameProfile(UUID.randomUUID(), "PBHexButtons"),
                ClientInformation.createDefault());
        var other = new ServerPlayer(server, helper.getLevel(), new GameProfile(UUID.randomUUID(), "PBHexOther"),
                ClientInformation.createDefault());
        var source = server.createCommandSourceStack().withSuppressedOutput().withEntity(player).withPermission(0);
        var otherSource = source.withEntity(other);
        var dispatcher = server.getCommands().getDispatcher();
        var colors = PlayerColors.get(server);
        dispatcher.execute("hex PBHexButtons #112233", source);
        var stale = colors.pendingHex(player.getUUID()).token();
        dispatcher.execute("hex PBHexButtons #445566", source);
        var current = colors.pendingHex(player.getUUID()).token();
        reject(helper, () -> dispatcher.execute("playercolor confirm " + stale, source));
        reject(helper, () -> dispatcher.execute("playercolor confirm " + current, otherSource));
        reject(helper, () -> dispatcher.execute("playercolor cancel " + current, otherSource));
        helper.assertTrue(
                colors.hexChanges(player.getUUID()) == 0 && current.equals(colors.pendingHex(player.getUUID()).token()),
                "Old or another player's buttons do not consume or cancel the active request");
        reject(helper, () -> dispatcher.execute("hex PBHexButtons #XYZ123", source));
        reject(helper, () -> dispatcher.execute("hex PBHexOther #ABCDEF", source));
        helper.assertTrue(current.equals(colors.pendingHex(player.getUUID()).token()),
                "Invalid or unauthorized commands preserve the valid pending request");
        dispatcher.execute("playercolor confirm " + current, source);
        helper.assertTrue(
                colors.personalColor(player.getUUID()) == 0x445566 && colors.hexChanges(player.getUUID()) == 1,
                "Only the latest player's selected color is confirmed");
        var expired = colors.requestHex(player.getUUID(), 0xAABBCC,
                server.overworld().getGameTime() - PlayerColors.HEX_CONFIRMATION_TICKS);
        reject(helper, () -> dispatcher.execute("playercolor confirm " + expired.token(), source));
        helper.assertTrue(
                colors.hexChanges(player.getUUID()) == 1 && colors.personalColor(player.getUUID()) == 0x445566,
                "Expired confirmations do not change color or use allowance");
        dispatcher.execute("playercolor reset", source);
        dispatcher.execute("playercolor confirm " + colors.pendingHex(player.getUUID()).token(), source);
        helper.assertTrue(colors.hexChanges(player.getUUID()) == 2 &&
                        colors.personalColor(player.getUUID()) == PlayerColors.defaultColor(player.getUUID()),
                "A confirmed reset uses the same personal change allowance");
        // Even low-permission operators remain exempt, as requested for all opped players.
        server.getPlayerList().getOps().add(new ServerOpListEntry(player.getGameProfile(), 1, false));
        try {
            for (String rgb : new String[]{"010203", "040506", "070809"}) {
                dispatcher.execute("hex PBHexButtons #" + rgb, source);
            }
            helper.assertTrue(
                    colors.personalColor(player.getUUID()) == 0x070809 && colors.hexChanges(player.getUUID()) == 2 &&
                            colors.pendingHex(player.getUUID()) == null,
                    "All opped players can change hex repeatedly without prompts or spending their non-op allowance");
        } finally {
            server.getPlayerList().getOps().remove(player.getGameProfile());
        }
        reject(helper, () -> dispatcher.execute("hex PBHexButtons #AABBCC", source));
        helper.succeed();
    }

    private static String button(Component prompt, String label) {
        var component = prompt.getSiblings().stream().filter(child -> child.getString().equals(label)).findFirst()
                .orElseThrow();
        var event = component.getStyle().getClickEvent();
        if (event == null || event.getAction() != ClickEvent.Action.RUN_COMMAND) {
            throw new IllegalStateException("Missing chat action for " + label);
        }
        return event.getValue();
    }

    @FunctionalInterface
    private interface Command {

        void run() throws CommandSyntaxException;
    }

    private static void reject(GameTestHelper helper, Command command) {
        try {
            command.run();
        } catch (CommandSyntaxException expected) {
            return;
        }
        helper.fail("Command should reject an unauthorized, exhausted, or stale color request");
    }

    private static final class Messages implements CommandSource {

        private final ArrayList<Component> messages = new ArrayList<>();

        Component getLast() {
            return messages.getLast();
        }

        @Override
        public void sendSystemMessage(Component message) {
            messages.add(message);
        }

        @Override
        public boolean acceptsSuccess() {
            return true;
        }

        @Override
        public boolean acceptsFailure() {
            return true;
        }

        @Override
        public boolean shouldInformAdmins() {
            return false;
        }
    }
}
