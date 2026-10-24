package com.ourcreation.playerblocks.fluid;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

public final class PlayerLiquidBlock extends LiquidBlock implements EntityBlock {

    public PlayerLiquidBlock() {
        super(PlayerLiquids.SOURCE.get(), BlockBehaviour.Properties.ofFullCopy(Blocks.WATER));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PlayerLiquidEntity(pos, state);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        // Vanilla liquids use fluid ticks. Separate block ticks let owned liquid
        // refresh once a second without ticking every liquid block entity.
        if (level.getBlockEntity(pos) instanceof PlayerLiquidEntity entity) {
            entity.refresh();
        }
    }
}
