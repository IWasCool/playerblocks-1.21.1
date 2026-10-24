package com.ourcreation.playerblocks.block.entity;

import com.ourcreation.playerblocks.color.PlayerColorRefresh;
import com.ourcreation.playerblocks.registry.ModContent;

import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Keeps Minecraft's actual entity model, components, and behavior.
 */
public final class PlayerBannerEntity extends BannerBlockEntity implements PlayerColoredEntity {

    private final EntityOwnership ownership = new EntityOwnership(this);

    public PlayerBannerEntity(BlockPos pos, BlockState state) {
        super(pos, state);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        PlayerColorRefresh.track(this);
    }

    @Override
    public BlockEntityType<?> getType() {
        return ModContent.BANNER_ENTITY.get();
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
        super.loadAdditional(tag, registries);
        ownership.load(tag);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public ItemStack getItem() {
        var stack = new ItemStack(ModContent.BANNER.get());
        stack.applyComponents(collectComponents());
        return stack;
    }
}
