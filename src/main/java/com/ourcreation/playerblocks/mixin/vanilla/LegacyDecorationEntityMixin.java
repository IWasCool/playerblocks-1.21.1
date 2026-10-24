package com.ourcreation.playerblocks.mixin.vanilla;

import com.ourcreation.playerblocks.block.PlayerBanner;
import com.ourcreation.playerblocks.block.PlayerBed;
import com.ourcreation.playerblocks.block.PlayerChest;
import com.ourcreation.playerblocks.block.PlayerWallBanner;
import com.ourcreation.playerblocks.block.entity.PlayerBannerEntity;
import com.ourcreation.playerblocks.block.entity.PlayerBedEntity;
import com.ourcreation.playerblocks.block.entity.PlayerChestEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Loads beds/banners saved before they used Minecraft's native entity classes.
 */
@Mixin(BlockEntity.class)
public abstract class LegacyDecorationEntityMixin {

    @Inject(method = "loadStatic", at = @At("HEAD"), cancellable = true)
    private static void playerBlocks$upgradeDecoration(BlockPos pos, BlockState state, CompoundTag tag,
                                                       HolderLookup.Provider registries,
                                                       CallbackInfoReturnable<BlockEntity> result) {
        if (!tag.getString("id").equals("player_blocks:player_concrete")) {
            return;
        }
        BlockEntity upgraded;
        if (state.getBlock() instanceof PlayerBed) {
            upgraded = new PlayerBedEntity(pos, state);
        } else if (state.getBlock() instanceof PlayerBanner || state.getBlock() instanceof PlayerWallBanner) {
            upgraded = new PlayerBannerEntity(pos, state);
        } else if (state.getBlock() instanceof PlayerChest) {
            upgraded = new PlayerChestEntity(pos, state);
        } else {
            return;
        }
        upgraded.loadWithComponents(tag, registries);
        result.setReturnValue(upgraded);
    }
}
