package com.ourcreation.playerblocks.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;

public final class PlayerConcreteDoor extends DoorBlock implements PlayerShapeBlock {

    public PlayerConcreteDoor(Properties properties) {
        this(BlockSetType.OAK, properties);
    }

    public PlayerConcreteDoor(BlockSetType type, Properties properties) {
        super(type, properties);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        PlayerShapeBlock.link(level, pos, placer);
    }
}
