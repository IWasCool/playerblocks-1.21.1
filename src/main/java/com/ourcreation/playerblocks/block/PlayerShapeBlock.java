package com.ourcreation.playerblocks.block;

import com.ourcreation.playerblocks.block.entity.PlayerChestEntity;
import com.ourcreation.playerblocks.block.entity.PlayerColoredEntity;
import com.ourcreation.playerblocks.block.entity.PlayerConcreteEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * Keeps vanilla shape and interaction behavior while adding persistent player ownership.
 */
public interface PlayerShapeBlock extends EntityBlock {

    @Override
    default BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PlayerConcreteEntity(pos, state);
    }

    @Override
    default <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                   BlockEntityType<T> type) {
        return null;
    }

    static void link(Level level, BlockPos pos, LivingEntity placer) {
        if (!(placer instanceof Player player)) {
            return;
        }
        if (level.getBlockEntity(pos) instanceof PlayerColoredEntity entity) {
            entity.link(player.getUUID());
        }
        var state = level.getBlockState(pos);
        if (state.hasProperty(DoorBlock.HALF)) {
            BlockPos other = state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER ? pos.above() : pos.below();
            if (level.getBlockState(other).is(state.getBlock()) &&
                    level.getBlockEntity(other) instanceof PlayerColoredEntity paired) {
                paired.link(player.getUUID());
            }
        }
        if (state.getBlock() instanceof PlayerChest && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
            var other = pos.relative(ChestBlock.getConnectedDirection(state));
            if (level.getBlockEntity(other) instanceof PlayerChestEntity paired) {
                paired.link(player.getUUID());
            }
        }
        if (state.hasProperty(BedBlock.PART)) {
            var direction = state.getValue(BedBlock.FACING);
            var other =
                    pos.relative(state.getValue(BedBlock.PART) == BedPart.FOOT ? direction : direction.getOpposite());
            if (level.getBlockState(other).is(state.getBlock()) &&
                    level.getBlockEntity(other) instanceof PlayerColoredEntity paired) {
                paired.link(player.getUUID());
            }
        }
    }
}
