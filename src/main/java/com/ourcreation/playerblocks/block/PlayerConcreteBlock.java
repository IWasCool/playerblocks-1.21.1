package com.ourcreation.playerblocks.block;

import com.mojang.serialization.MapCodec;
import com.ourcreation.playerblocks.block.entity.PlayerConcreteEntity;
import com.ourcreation.playerblocks.color.PlayerColors;
import com.ourcreation.playerblocks.item.PlayerBlockItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

public final class PlayerConcreteBlock extends BaseEntityBlock {

    public static final MapCodec<PlayerConcreteBlock> CODEC = simpleCodec(PlayerConcreteBlock::new);

    public static final IntegerProperty COLOR_ID = IntegerProperty.create("color_id", 0, PlayerColors.MAX_SLOT);

    public PlayerConcreteBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(COLOR_ID, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(COLOR_ID);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        PlayerBlockItem.prepare(context.getItemInHand(), context.getLevel(), context.getPlayer());
        return context.getItemInHand().getOrDefault(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY)
                .apply(defaultBlockState());
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PlayerConcreteEntity(pos, state);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (placer instanceof Player player && level.getBlockEntity(pos) instanceof PlayerConcreteEntity entity) {
            entity.link(player.getUUID());
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (player.isShiftKeyDown() && level.getBlockEntity(pos) instanceof PlayerConcreteEntity entity &&
                entity.owner() == null) {
            entity.link(player.getUUID());
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return InteractionResult.PASS;
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        // Palette slots recolor directly; legacy slot-zero entities use the shared refresh queue.
        return null;
    }
}
