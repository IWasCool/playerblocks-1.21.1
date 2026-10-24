package com.ourcreation.playerblocks.mixin.thirst;

import com.ourcreation.playerblocks.fluid.PlayerLiquids;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.DispenseItemBehavior;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.DispenserBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Preserve custom bucket identity through Thirst's water-tag dispenser wrapper.
 */
@Pseudo
@Mixin(targets = "cn.mlus.thirst.content.purity.WaterPurity", remap = false)
public abstract class ThirstPlayerLiquidDispenserMixin {

    @Inject(method = "lambda$registerDispenserBehaviours$2(Lnet/minecraft/core/dispenser/DispenseItemBehavior;Lnet/minecraft/core/dispenser/BlockSource;Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/item/ItemStack;",
            at = @At("HEAD"), cancellable = true, require = 0)
    private static void playerBlocks$preserveLiquidBucket(DispenseItemBehavior fallback, BlockSource source,
                                                          ItemStack stack, CallbackInfoReturnable<ItemStack> callback) {
        var target = source.pos().relative(source.state().getValue(DispenserBlock.FACING));
        if (source.level().getBlockState(target).is(PlayerLiquids.BLOCK.get())) {
            // Thirst discards BucketPickup's result and creates a water bucket.
            // Its original fallback preserves the fluid and vanilla inventory rules.
            callback.setReturnValue(fallback.dispense(source, stack));
        }
    }
}
