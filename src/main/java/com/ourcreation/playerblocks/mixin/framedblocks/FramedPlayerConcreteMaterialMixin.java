package com.ourcreation.playerblocks.mixin.framedblocks;

import com.ourcreation.playerblocks.item.PlayerBlockItem;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "xfacthd.framedblocks.api.camo.block.AbstractBlockCamoContainerFactory", remap = false)
public abstract class FramedPlayerConcreteMaterialMixin {

    @Inject(method = "getStateFromItemStack", at = @At("HEAD"), cancellable = true)
    private void playerBlocks$coloredCamo(Level level, BlockPos pos, Player player, ItemStack stack,
                                          CallbackInfoReturnable<BlockState> callback) {
        if (PlayerBlockItem.isPaletteMaterial(stack)) {
            PlayerBlockItem.prepare(stack, level, player);
            callback.setReturnValue(PlayerBlockItem.material(stack));
        }
    }
}
