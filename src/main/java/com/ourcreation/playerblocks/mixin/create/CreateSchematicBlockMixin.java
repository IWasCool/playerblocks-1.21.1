package com.ourcreation.playerblocks.mixin.create;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.ourcreation.playerblocks.compat.SchematicColors;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

@Pseudo
@Mixin(targets = "com.simibubi.create.foundation.utility.BlockHelper", remap = false)
public abstract class CreateSchematicBlockMixin {

    @WrapMethod(method = "placeSchematicBlock")
    private static void playerBlocks$rebind(Level level, BlockState state, BlockPos pos, ItemStack stack,
                                            CompoundTag data, Operation<Void> original) {
        var placer = SchematicColors.placer(data);
        var placement = SchematicColors.prepare(level, state, data, placer);
        original.call(level, placement.state(), pos, stack, placement.data());
        SchematicColors.finish(level, pos, placer);
    }
}
