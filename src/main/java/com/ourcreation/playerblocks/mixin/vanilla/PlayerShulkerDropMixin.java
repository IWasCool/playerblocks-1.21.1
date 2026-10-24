package com.ourcreation.playerblocks.mixin.vanilla;

import com.ourcreation.playerblocks.block.PlayerShulkerBox;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ShulkerBoxBlock.class)
public abstract class PlayerShulkerDropMixin {

    @Redirect(method = "playerWillDestroy", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/ShulkerBoxBlock;getColoredItemStack(Lnet/minecraft/world/item/DyeColor;)Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack playerBlocks$keepPlayerBox(DyeColor color) {
        return (Object) this instanceof PlayerShulkerBox block ? new ItemStack(
                block) : ShulkerBoxBlock.getColoredItemStack(color);
    }
}
