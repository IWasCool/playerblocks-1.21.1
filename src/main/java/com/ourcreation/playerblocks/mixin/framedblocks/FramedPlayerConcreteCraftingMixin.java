package com.ourcreation.playerblocks.mixin.framedblocks;

import com.ourcreation.playerblocks.item.PlayerBlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "xfacthd.framedblocks.api.camo.block.SimpleBlockCamoContainerFactory", remap = false)
public abstract class FramedPlayerConcreteCraftingMixin {

    @Redirect(method = {"canApplyInCraftingRecipe", "applyCamoInCraftingRecipe"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/Block;defaultBlockState()Lnet/minecraft/world/level/block/state/BlockState;"))
    private BlockState playerBlocks$coloredCraftingCamo(Block block, ItemStack stack) {
        return PlayerBlockItem.isPaletteMaterial(stack) ? PlayerBlockItem.material(stack) : block.defaultBlockState();
    }
}
