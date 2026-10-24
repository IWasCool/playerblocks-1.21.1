package com.ourcreation.playerblocks.block.entity;

import com.ourcreation.playerblocks.color.PlayerColorRefresh;
import com.ourcreation.playerblocks.registry.ModContent;

import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Keeps Minecraft's actual entity model, components, and behavior.
 */
public final class PlayerChestEntity extends ChestBlockEntity implements PlayerColoredEntity {

    private final EntityOwnership ownership = new EntityOwnership(this);

    public PlayerChestEntity(BlockPos pos, BlockState state) {
        super(ModContent.CHEST_ENTITY.get(), pos, state);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        PlayerColorRefresh.track(this);
    }

    @Override
    public BlockEntityType<?> getType() {
        return ModContent.CHEST_ENTITY.get();
    }

    @Override
    public UUID owner() {
        return ownership.owner;
    }

    @Override
    public int color() {
        return ownership.color;
    }

    @Override
    public void link(UUID owner) {
        ownership.link(owner);
    }

    @Override
    public void refresh() {
        ownership.refresh();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ownership.save(tag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        if (tag.contains("Items") || tag.contains("LootTable") || !tag.getBoolean("colorOnly")) {
            super.loadAdditional(tag, registries);
        }
        ownership.load(tag);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        var tag = new CompoundTag();
        ownership.save(tag);
        tag.putBoolean("colorOnly", true);
        return tag;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
