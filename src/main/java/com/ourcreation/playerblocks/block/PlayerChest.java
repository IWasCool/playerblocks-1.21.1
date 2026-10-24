package com.ourcreation.playerblocks.block;

import com.ourcreation.playerblocks.block.entity.PlayerChestEntity;
import com.ourcreation.playerblocks.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class PlayerChest extends ChestBlock implements PlayerShapeBlock {

    public PlayerChest(Properties properties) {
        super(properties, () -> ModContent.CHEST_ENTITY.get());
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PlayerChestEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        if (!level.isClientSide || type != ModContent.CHEST_ENTITY.get()) {
            return null;
        }
        return (world, pos, block, entity) -> ChestBlockEntity.lidAnimateTick(world, pos, block,
                (PlayerChestEntity) entity);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        PlayerShapeBlock.link(level, pos, placer);
    }
}
