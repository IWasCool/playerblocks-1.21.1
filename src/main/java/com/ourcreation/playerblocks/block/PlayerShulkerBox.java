package com.ourcreation.playerblocks.block;

import com.ourcreation.playerblocks.block.entity.PlayerShulkerEntity;
import com.ourcreation.playerblocks.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class PlayerShulkerBox extends ShulkerBoxBlock implements PlayerShapeBlock {

    public PlayerShulkerBox(Properties properties) {
        super(DyeColor.WHITE, properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PlayerShulkerEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        if (type != ModContent.SHULKER_ENTITY.get()) {
            return null;
        }
        return (world, pos, block, entity) -> {
            ShulkerBoxBlockEntity.tick(world, pos, block, (PlayerShulkerEntity) entity);
        };
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        PlayerShapeBlock.link(level, pos, placer);
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        var stack = new ItemStack(this);
        if (level.getBlockEntity(pos) instanceof PlayerShulkerEntity entity) {
            entity.saveToItem(stack, level.registryAccess());
        }
        return stack;
    }
}
