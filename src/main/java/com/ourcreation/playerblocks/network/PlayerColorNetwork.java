package com.ourcreation.playerblocks.network;

import com.ourcreation.playerblocks.PlayerBlocks;
import com.ourcreation.playerblocks.client.PlayerConcreteColors;
import com.ourcreation.playerblocks.color.PlayerColors;
import com.ourcreation.playerblocks.item.PlayerBlockItem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * Synchronizes each player's preview color and the world's shared material palette.
 */
public final class PlayerColorNetwork {

    public static final String PREVIEW_COLOR = "cnwPlayerConcreteColor";

    private PlayerColorNetwork() {
    }

    public static void syncColor(Player user) {
        if (user instanceof ServerPlayer player) {
            var colors = PlayerColors.get(player.server);
            colors.refreshTeams(player.server);
            int slot = colors.slot(player.getUUID());
            broadcastPalette(player.server);
            PacketDistributor.sendToPlayer(player, new ColorPayload(colors.color(player.getUUID()), slot));
        }
    }

    public static void syncAll(MinecraftServer server) {
        var colors = PlayerColors.get(server);
        colors.refreshTeams(server);
        syncResolvedColors(server, colors);
    }

    public static void refreshTeams(MinecraftServer server) {
        var colors = PlayerColors.get(server);
        colors.refreshTeams(server);
        if (colors.paletteChanged()) {
            syncResolvedColors(server, colors);
        }
    }

    private static void syncResolvedColors(MinecraftServer server, PlayerColors colors) {
        for (var player : server.getPlayerList().getPlayers()) {
            colors.slot(player.getUUID());
        }
        broadcastPalette(server);
        for (var player : server.getPlayerList().getPlayers()) {
            PacketDistributor.sendToPlayer(player,
                    new ColorPayload(colors.color(player.getUUID()), colors.slot(player.getUUID())));
        }
    }

    public static void broadcastPalette(MinecraftServer server) {
        var colors = PlayerColors.get(server);
        colors.paletteChanged();
        if (server.getPlayerList().getPlayers().isEmpty()) {
            return;
        }
        // Encoders only read the payload, so all recipients can share one defensive snapshot.
        var payload = new PalettePayload(colors.palette());
        for (var player : server.getPlayerList().getPlayers()) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("2");
        registrar.playToClient(ColorPayload.TYPE, ColorPayload.CODEC, (payload, context) -> {
            context.player().getPersistentData().putInt(PREVIEW_COLOR, payload.rgb() & 0xFFFFFF);
            context.player().getPersistentData().putInt(PlayerBlockItem.COLOR_SLOT, payload.slot());
        });
        registrar.playToClient(PalettePayload.TYPE, PalettePayload.CODEC,
                (payload, context) -> PlayerConcreteColors.setPalette(payload.colors()));
    }

    public record PalettePayload(CompoundTag colors) implements CustomPacketPayload {

        public static final Type<PalettePayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(PlayerBlocks.MODID, "player_concrete_palette"));

        public static final StreamCodec<RegistryFriendlyByteBuf, PalettePayload> CODEC =
                StreamCodec.composite(ByteBufCodecs.COMPOUND_TAG, PalettePayload::colors, PalettePayload::new);

        @Override
        public Type<PalettePayload> type() {
            return TYPE;
        }
    }

    public record ColorPayload(int rgb, int slot) implements CustomPacketPayload {

        public static final Type<ColorPayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(PlayerBlocks.MODID, "player_concrete_color"));

        public static final StreamCodec<RegistryFriendlyByteBuf, ColorPayload> CODEC =
                StreamCodec.composite(ByteBufCodecs.INT, ColorPayload::rgb, ByteBufCodecs.VAR_INT, ColorPayload::slot,
                        ColorPayload::new);

        @Override
        public Type<ColorPayload> type() {
            return TYPE;
        }
    }
}
