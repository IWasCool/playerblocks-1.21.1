package com.ourcreation.playerblocks.color;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.ourcreation.playerblocks.network.PlayerColorNetwork;

import java.util.Locale;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

public final class PlayerColorCommands {

    private PlayerColorCommands() {
    }

    public static int parseHex(String input) throws CommandSyntaxException {
        input = input.trim();
        String hex = input.startsWith("#") ? input.substring(1) : input;
        if (!hex.matches("[0-9a-fA-F]{6}")) {
            throw error("invalid_hex");
        }
        return Integer.parseInt(hex, 16);
    }

    private static MutableComponent text(String key, Object... arguments) {
        return Component.translatable("command.player_blocks." + key, arguments);
    }

    private static String hex(int rgb) {
        return String.format(Locale.ROOT, "#%06X", rgb);
    }

    private static CommandSyntaxException error(String key, Object... arguments) {
        return new SimpleCommandExceptionType(text(key, arguments)).create();
    }

    private static GameProfile profile(CommandSourceStack source, String username) throws CommandSyntaxException {
        var self = source.getPlayer();
        if (self != null && self.getGameProfile().getName().equalsIgnoreCase(username)) {
            return self.getGameProfile();
        }
        var online = source.getServer().getPlayerList().getPlayerByName(username);
        if (online != null) {
            return online.getGameProfile();
        }
        var cache = source.getServer().getProfileCache();
        var profile = cache == null ? null : cache.get(username).orElse(null);
        if (profile == null) {
            throw error("unknown_player", username);
        }
        return profile;
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("hex").then(Commands.argument("username", StringArgumentType.word())
                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                        context.getSource().getServer().getPlayerNames(), builder))
                .then(Commands.argument("color", StringArgumentType.greedyString()).executes(context -> {
                    var source = context.getSource();
                    String username = StringArgumentType.getString(context, "username");
                    if (!source.hasPermission(2) && (source.getPlayer() == null ||
                            !source.getPlayer().getGameProfile().getName().equalsIgnoreCase(username))) {
                        throw error("own_color_only_op");
                    }
                    var target = profile(source, username);
                    if (!source.hasPermission(2) &&
                            (source.getPlayer() == null || !source.getPlayer().getUUID().equals(target.getId()))) {
                        throw error("own_color_only_op");
                    }
                    int rgb = parseHex(StringArgumentType.getString(context, "color"));
                    return requestPersonalChange(source, target.getId(), target.getName(), rgb);
                }))));
        dispatcher.register(Commands.literal("playercolor").executes(context -> {
            var source = context.getSource();
            var player = source.getPlayerOrException();
            var colors = PlayerColors.get(source.getServer());
            source.sendSuccess(() -> text("effective_color", hex(colors.color(player.getUUID()))), false);
            return 1;
        }).then(Commands.literal("hex")
                .then(Commands.argument("color", StringArgumentType.greedyString()).executes(context -> {
                    var source = context.getSource();
                    var player = source.getPlayerOrException();
                    return requestPersonalChange(source, player.getUUID(), player.getGameProfile().getName(),
                            parseHex(StringArgumentType.getString(context, "color")));
                }))).then(Commands.literal("reset").executes(context -> {
            var source = context.getSource();
            var player = source.getPlayerOrException();
            return requestPersonalChange(source, player.getUUID(), player.getGameProfile().getName(), null);
        })).then(Commands.literal("confirm").then(Commands.argument("confirmation", UuidArgument.uuid()).executes(
                context -> finishPersonalChange(context.getSource(), UuidArgument.getUuid(context, "confirmation"),
                        true)))).then(Commands.literal("cancel")
                .then(Commands.argument("confirmation", UuidArgument.uuid()).executes(
                        context -> finishPersonalChange(context.getSource(),
                                UuidArgument.getUuid(context, "confirmation"), false)))));
        dispatcher.register(Commands.literal("teamcolor").executes(context -> {
                    var source = context.getSource();
                    var player = source.getPlayerOrException();
                    var colors = PlayerColors.get(source.getServer());
                    var team = TeamColors.resolve(source.getServer(), player.getUUID(), colors);
                    if (team == null) {
                        throw error("no_team");
                    }
                    colors.refreshTeams(source.getServer());
                    source.sendSuccess(
                            () -> text("team_info", team.key(), hex(colors.color(player.getUUID())), team.leader().toString()),
                            false);
                    return 1;
                }).then(Commands.literal("hex").then(Commands.argument("color", StringArgumentType.greedyString())
                        .executes(context -> changeTeam(context.getSource(), StringArgumentType.getString(context, "color")))))
                .then(Commands.literal("reset").executes(context -> changeTeam(context.getSource(), null)))
                .then(Commands.literal("leader").requires(source -> source.hasPermission(2))
                        .then(Commands.argument("team", StringArgumentType.word()).suggests(
                                        (context, builder) -> SharedSuggestionProvider.suggest(
                                                context.getSource().getServer().getScoreboard().getTeamNames(), builder))
                                .then(Commands.argument("username", StringArgumentType.word()).executes(context -> {
                                    var source = context.getSource();
                                    String name = StringArgumentType.getString(context, "team");
                                    var team = source.getServer().getScoreboard().getPlayerTeam(name);
                                    if (team == null) {
                                        throw error("unknown_team");
                                    }
                                    var leader = profile(source, StringArgumentType.getString(context, "username"));
                                    if (!team.getPlayers().contains(leader.getName())) {
                                        throw error("leader_not_member");
                                    }
                                    PlayerColors.get(source.getServer()).setScoreboardLeader(name, leader.getId());
                                    PlayerColorNetwork.syncAll(source.getServer());
                                    source.sendSuccess(() -> text("leader_assigned", name, leader.getName()), true);
                                    return 1;
                                })))));
    }

    private static boolean unlimited(CommandSourceStack source) {
        var player = source.getPlayer();
        return source.hasPermission(2) ||
                player != null && source.getServer().getPlayerList().isOp(player.getGameProfile());
    }

    private static int requestPersonalChange(CommandSourceStack source, UUID target, String name,
                                             Integer rgb) throws CommandSyntaxException {
        var colors = PlayerColors.get(source.getServer());
        if (unlimited(source)) {
            colors.applyPersonalChange(target, rgb, false);
            PlayerColorNetwork.syncAll(source.getServer());
            source.sendSuccess(() -> text("player_color", name, hex(colors.personalColor(target))), false);
            return 1;
        }
        var player = source.getPlayerOrException();
        if (!player.getUUID().equals(target)) {
            throw error("own_color_only");
        }
        int remaining = PlayerColors.MAX_HEX_CHANGES - colors.hexChanges(target);
        if (remaining <= 0) {
            throw error("limit_reached");
        }
        var request = colors.requestHex(target, rgb, source.getServer().overworld().getGameTime());
        Component action = rgb == null ? text("action_reset") : text("action_set", hex(rgb));
        var prompt = text(remaining == 1 ? "confirm_prompt_one" : "confirm_prompt_many", remaining, action).append(
                        choice("confirm", request.token(), ChatFormatting.GREEN)).append(Component.literal(" "))
                .append(choice("cancel", request.token(), ChatFormatting.RED));
        source.sendSuccess(() -> prompt, false);
        return 1;
    }

    private static Component choice(String action, UUID token, ChatFormatting color) {
        return text(action).withStyle(style -> style.withColor(color)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/playercolor " + action + " " + token))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, text(action + "_hover"))));
    }

    private static int finishPersonalChange(CommandSourceStack source, UUID token,
                                            boolean confirm) throws CommandSyntaxException {
        var player = source.getPlayerOrException();
        var colors = PlayerColors.get(source.getServer());
        var request = colors.pendingHex(player.getUUID());
        if (request == null || !request.token().equals(token)) {
            throw error("invalid_confirmation");
        }
        if (request.expiresAt() <= source.getServer().overworld().getGameTime()) {
            colors.clearHexRequest(player.getUUID());
            throw error("expired_confirmation");
        }
        if (!confirm) {
            colors.clearHexRequest(player.getUUID());
            source.sendSuccess(() -> text("canceled"), false);
            return 1;
        }
        boolean limited = !unlimited(source);
        if (limited && colors.hexChanges(player.getUUID()) >= PlayerColors.MAX_HEX_CHANGES) {
            colors.clearHexRequest(player.getUUID());
            throw error("limit_reached");
        }
        colors.applyPersonalChange(player.getUUID(), request.rgb(), limited);
        PlayerColorNetwork.syncAll(source.getServer());
        source.sendSuccess(() -> {
            var result = text("personal_color", hex(colors.personalColor(player.getUUID()))).copy();
            if (limited) {
                result.append(
                        text("changes_remaining", PlayerColors.MAX_HEX_CHANGES - colors.hexChanges(player.getUUID())));
            }
            return result;
        }, false);
        return 1;
    }

    private static int changeTeam(CommandSourceStack source, String hex) throws CommandSyntaxException {
        var player = source.getPlayerOrException();
        var colors = PlayerColors.get(source.getServer());
        var team = TeamColors.resolve(source.getServer(), player.getUUID(), colors);
        if (team == null) {
            throw error("no_team");
        }
        if (!source.hasPermission(2) && !player.getUUID().equals(team.leader())) {
            throw error("team_leader_only");
        }
        if (hex == null) {
            colors.resetTeam(team.key());
        } else {
            colors.setTeam(team.key(), parseHex(hex));
        }
        PlayerColorNetwork.syncAll(source.getServer());
        source.sendSuccess(() -> text("team_color", team.key(), hex(colors.color(player.getUUID()))), false);
        return 1;
    }
}
