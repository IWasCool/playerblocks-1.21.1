package com.ourcreation.playerblocks.fluid;

import com.ourcreation.playerblocks.block.entity.PlayerColoredEntity;
import com.ourcreation.playerblocks.color.PlayerColors;

import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class PlayerLiquidEntity extends BlockEntity implements PlayerColoredEntity {

    private static final Direction[] NEIGHBORS = Direction.values();

    private UUID owner;

    private int color = 0xFFFFFF;

    private boolean tracked;

    public PlayerLiquidEntity(BlockPos pos, BlockState state) {
        super(PlayerLiquids.ENTITY.get(), pos, state);
    }

    public UUID owner() {
        return owner;
    }

    public int color() {
        return color;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        scheduleRefresh();
    }

    private void scheduleRefresh() {
        if (owner == null || !(level instanceof ServerLevel server)) {
            return;
        }
        // Scheduling deduplicates by block and position, including saved ticks on
        // chunk reload. Keep the old stagger and a maximum delay of twenty ticks.
        int delay =
                (int) Math.floorMod(Math.floorMod(worldPosition.asLong(), 20) - Math.floorMod(server.getGameTime(), 20),
                        20);
        server.scheduleTick(worldPosition, getBlockState().getBlock(), delay == 0 ? 20 : delay);
    }

    public void link(UUID uuid) {
        if (!(level instanceof ServerLevel) || uuid == null) {
            return;
        }
        if (uuid.equals(owner)) {
            refresh();
            return;
        }
        owner = uuid;
        tracked = false;
        int previousColor = color;
        refresh();
        if (previousColor == color) {
            sync();
        }
    }

    public void refresh() {
        if (owner == null || !(level instanceof ServerLevel server)) {
            return;
        }
        scheduleRefresh();
        var colors = PlayerColors.get(server.getServer());
        if (!tracked) {
            colors.slot(owner);
            colors.ensureTeam(server.getServer(), owner);
            tracked = true;
        }
        int next = colors.color(owner);
        if (next != color) {
            color = next;
            sync();
        }
    }

    private void sync() {
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
    }

    public static UUID flowOwner(LevelAccessor level, BlockPos pos, Direction direction) {
        Direction upstream = direction.getOpposite();
        BlockPos.MutableBlockPos neighbor = new BlockPos.MutableBlockPos();
        if (level.getBlockEntity(neighbor.setWithOffset(pos, upstream)) instanceof PlayerLiquidEntity entity &&
                entity.owner != null) {
            return entity.owner;
        }
        for (Direction side : NEIGHBORS) {
            if (side != upstream &&
                    level.getBlockEntity(neighbor.setWithOffset(pos, side)) instanceof PlayerLiquidEntity entity &&
                    entity.owner != null) {
                return entity.owner;
            }
        }
        return null;
    }

    public static void linkAt(LevelAccessor level, BlockPos pos, UUID owner) {
        if (owner != null && level.getBlockEntity(pos) instanceof PlayerLiquidEntity entity) {
            entity.link(owner);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (owner != null) {
            tag.putUUID("owner", owner);
        }
        tag.putInt("color", color);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        owner = tag.hasUUID("owner") ? tag.getUUID("owner") : null;
        tracked = false;
        color = tag.contains("color") ? tag.getInt("color") & 0xFFFFFF : 0xFFFFFF;
        scheduleRefresh();
        if (level != null && level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
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
