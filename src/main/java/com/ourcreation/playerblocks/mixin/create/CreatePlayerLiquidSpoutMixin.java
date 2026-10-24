package com.ourcreation.playerblocks.mixin.create;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.ourcreation.playerblocks.fluid.PlayerLiquids;
import com.ourcreation.playerblocks.registry.ModContent;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.neoforged.neoforge.fluids.FluidStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

/**
 * Keeps portable storage components when a Create spout converts a shulker box.
 */
@Pseudo
@Mixin(targets = "com.simibubi.create.content.fluids.spout.FillingBySpout", remap = false)
public abstract class CreatePlayerLiquidSpoutMixin {

    @WrapMethod(method = "fillItem")
    private static ItemStack playerBlocks$preserveShulkerContents(Level level, int available, ItemStack input,
                                                                  FluidStack fluid, Operation<ItemStack> original) {
        ItemStack source = ItemStack.EMPTY;
        if (fluid.is(PlayerLiquids.SOURCE.get()) && input.getItem() instanceof BlockItem item &&
                item.getBlock() instanceof ShulkerBoxBlock) {
            source = input.copy();
        }
        ItemStack result = original.call(level, available, input, fluid);
        if (!source.isEmpty() && result != null && result.is(ModContent.SHULKER.get().asItem())) {
            return source.transmuteCopy(ModContent.SHULKER.get(), result.getCount());
        }
        return result;
    }
}
