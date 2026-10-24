package com.ourcreation.playerblocks.block.entity;

import com.ourcreation.playerblocks.block.PlayerConcreteBlock;
import com.ourcreation.playerblocks.color.PlayerColorRefresh;
import com.ourcreation.playerblocks.color.PlayerColors;
import com.ourcreation.playerblocks.network.PlayerColorNetwork;
import com.ourcreation.playerblocks.registry.ModContent;

import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class PlayerConcreteEntity extends BlockEntity implements PlayerColoredEntity {

    private UUID owner;

    private int color = 0xFFFFFF;

    private PlayerColors resolvedColors;

    private long colorRevision = -1;

    public PlayerConcreteEntity(BlockPos pos, BlockState state) {
        super(ModContent.ENTITY.get(), pos, state);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        PlayerColorRefresh.track(this);
    }

    @Override
    public void setBlockState(BlockState state) {
        super.setBlockState(state);
        PlayerColorRefresh.track(this);
    }

    public UUID owner() {
        return owner;
    }

    public int color() {
        return owner != null && level instanceof ServerLevel server ? PlayerColors.get(server.getServer())
                .color(owner) : color;
    }

    public void link(UUID player) {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        var colors = PlayerColors.get(server.getServer());
        colors.slot(player);
        colors.ensureTeam(server.getServer(), player);
        int next = colors.color(player);
        resolvedColors = colors;
        colorRevision = colors.colorRevision();
        if (player.equals(owner) && next == color) {
            updateColorState(server, colors);
            PlayerColorRefresh.track(this);
            return;
        }
        owner = player;
        color = next;
        if (colors.paletteChanged()) {
            PlayerColorNetwork.broadcastPalette(server.getServer());
        }
        updateColorState(server, colors);
        PlayerColorRefresh.track(this);
        sync();
    }

    public void refresh() {
        if (owner == null || !(level instanceof ServerLevel server)) {
            return;
        }
        var colors = PlayerColors.get(server.getServer());
        if (resolvedColors == colors && colorRevision == colors.colorRevision()) {
            // A state-only paste can restore slot zero without reloading the block entity.
            if (getBlockState().hasProperty(PlayerConcreteBlock.COLOR_ID) &&
                    getBlockState().getValue(PlayerConcreteBlock.COLOR_ID) == 0) {
                updateColorState(server, colors);
            }
            return;
        }
        int current = colors.color(owner);
        updateColorState(server, colors);
        resolvedColors = colors;
        colorRevision = colors.colorRevision();
        if (current != color) {
            color = current;
            sync();
        }
    }

    private void updateColorState(ServerLevel server, PlayerColors colors) {
        if (!getBlockState().hasProperty(PlayerConcreteBlock.COLOR_ID)) {
            return;
        }
        int previous = colors.slotCount();
        int slot = colors.slot(owner);
        if (previous != colors.slotCount()) {
            PlayerColorNetwork.broadcastPalette(server.getServer());
        }
        BlockState state = getBlockState();
        if (state.getValue(PlayerConcreteBlock.COLOR_ID) != slot) {
            server.setBlock(worldPosition, state.setValue(PlayerConcreteBlock.COLOR_ID, slot), Block.UPDATE_CLIENTS);
        }
    }

    private void sync() {
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (owner != null) {
            tag.putUUID("owner", owner);
        }
        tag.putInt("color", color());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        owner = tag.hasUUID("owner") ? tag.getUUID("owner") : null;
        color = tag.contains("color") ? tag.getInt("color") & 0xFFFFFF : 0xFFFFFF;
        resolvedColors = null;
        colorRevision = -1;
        PlayerColorRefresh.track(this);
        if (level != null && level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
