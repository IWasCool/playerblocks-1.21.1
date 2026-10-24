package com.ourcreation.playerblocks.mixin.create;

import com.ourcreation.playerblocks.item.PlayerBlockItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.simibubi.create.content.decoration.copycat.CopycatBlock", remap = false)
public abstract class CreatePlayerConcreteMaterialMixin {

    @Inject(method = "getAcceptedBlockState", at = @At("HEAD"), cancellable = true)
    private void playerBlocks$coloredMaterial(Level level, BlockPos pos, ItemStack stack, Direction face,
                                              CallbackInfoReturnable<BlockState> callback) {
        if (PlayerBlockItem.isPaletteMaterial(stack)) {
            callback.setReturnValue(PlayerBlockItem.material(stack));
        }
    }
}
