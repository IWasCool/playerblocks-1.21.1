package com.ourcreation.playerblocks.mixin.create;

import com.ourcreation.playerblocks.compat.SchematicColors;

import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.simibubi.create.content.schematics.cannon.SchematicannonBlockEntity", remap = false)
public abstract class CreateSchematicannonMixin extends BlockEntity implements SchematicColors.CannonOwner {

    @Unique
    private UUID playerBlocks$placer;

    protected CreateSchematicannonMixin(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public UUID playerBlocks$getPlacer() {
        return playerBlocks$placer;
    }

    @Override
    public void playerBlocks$setPlacer(UUID placer) {
        playerBlocks$placer = placer;
        setChanged();
    }

    @Inject(method = "read", at = @At("TAIL"))
    private void playerBlocks$read(CompoundTag data, HolderLookup.Provider registries, boolean clientPacket,
                                   CallbackInfo ci) {
        playerBlocks$placer = data.hasUUID(SchematicColors.PLACER) ? data.getUUID(SchematicColors.PLACER) : null;
    }

    @Inject(method = "write", at = @At("TAIL"))
    private void playerBlocks$write(CompoundTag data, HolderLookup.Provider registries, boolean clientPacket,
                                    CallbackInfo ci) {
        if (playerBlocks$placer != null) {
            data.putUUID(SchematicColors.PLACER, playerBlocks$placer);
        }
    }

    @ModifyVariable(method = "launchBlock", at = @At("HEAD"), argsOnly = true)
    private CompoundTag playerBlocks$markShot(CompoundTag data) {
        return SchematicColors.markShot(data, playerBlocks$placer);
    }
}
