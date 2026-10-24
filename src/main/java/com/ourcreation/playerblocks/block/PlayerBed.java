package com.ourcreation.playerblocks.block;

import com.ourcreation.playerblocks.block.entity.PlayerBedEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Vanilla sleeping, spawn, two-part behavior, and the original Minecraft bed model.
 */
public final class PlayerBed extends BedBlock implements PlayerShapeBlock {

    public PlayerBed(Properties properties) {
        super(DyeColor.WHITE, properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PlayerBedEntity(pos, state);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        PlayerShapeBlock.link(level, pos, placer);
    }
}
