package com.ourcreation.playerblocks.block.entity;

import com.ourcreation.playerblocks.color.PlayerColorRefresh;
import com.ourcreation.playerblocks.color.PlayerColors;
import com.ourcreation.playerblocks.network.PlayerColorNetwork;

import java.util.UUID;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

final class EntityOwnership {

    private final BlockEntity entity;

    UUID owner;

    int color = 0xFFFFFF;

    private PlayerColors resolvedColors;

    private long colorRevision = -1;

    EntityOwnership(BlockEntity entity) {
        this.entity = entity;
    }

    void link(UUID player) {
        if (!(entity.getLevel() instanceof ServerLevel level)) {
            return;
        }
        var colors = PlayerColors.get(level.getServer());
        colors.slot(player);
        colors.ensureTeam(level.getServer(), player);
        int next = colors.color(player);
        resolvedColors = colors;
        colorRevision = colors.colorRevision();
        if (player.equals(owner) && next == color) {
            PlayerColorRefresh.track(entity);
            return;
        }
        owner = player;
        color = next;
        PlayerColorRefresh.track(entity);
        if (colors.paletteChanged()) {
            PlayerColorNetwork.broadcastPalette(level.getServer());
        }
        sync();
    }

    void refresh() {
        if (owner == null || !(entity.getLevel() instanceof ServerLevel level)) {
            return;
        }
        var colors = PlayerColors.get(level.getServer());
        if (resolvedColors == colors && colorRevision == colors.colorRevision()) {
            return;
        }
        int current = colors.color(owner);
        resolvedColors = colors;
        colorRevision = colors.colorRevision();
        if (color != current) {
            color = current;
            sync();
        }
    }

    private void sync() {
        entity.setChanged();
        entity.getLevel().sendBlockUpdated(entity.getBlockPos(), entity.getBlockState(), entity.getBlockState(), 2);
    }

    void save(CompoundTag tag) {
        if (owner != null) {
            tag.putUUID("owner", owner);
        }
        tag.putInt("color", color);
    }

    void load(CompoundTag tag) {
        owner = tag.hasUUID("owner") ? tag.getUUID("owner") : null;
        color = tag.contains("color") ? tag.getInt("color") & 0xFFFFFF : 0xFFFFFF;
        resolvedColors = null;
        colorRevision = -1;
        PlayerColorRefresh.track(entity);
    }
}
