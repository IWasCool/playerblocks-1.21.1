package com.ourcreation.playerblocks.item;

import com.ourcreation.playerblocks.block.PlayerConcreteBlock;
import com.ourcreation.playerblocks.color.PlayerColors;
import com.ourcreation.playerblocks.network.PlayerColorNetwork;
import com.ourcreation.playerblocks.registry.ModContent;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReferenceArray;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public final class PlayerBlockItem extends BlockItem {

    public static final String COLOR_SLOT = "cnwPlayerConcreteSlot";

    // Item components are immutable. Share the common one-property component across
    // stacks, allocating it only for slots that actually appear in an inventory.
    private static final AtomicReferenceArray<BlockItemStateProperties> SLOT_PROPERTIES =
            new AtomicReferenceArray<>(PlayerColors.MAX_SLOT + 1);

    public PlayerBlockItem(Properties properties) {
        super(ModContent.CONCRETE.get(), properties);
    }

    public PlayerBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        super.inventoryTick(stack, level, entity, slot, selected);
        if (entity instanceof Player player) {
            prepare(stack, level, player);
        }
    }

    public static void prepare(ItemStack stack, Level level, Player player) {
        if (!(stack.getItem() instanceof PlayerBlockItem || stack.getItem() instanceof PlayerBannerItem) ||
                player == null) {
            return;
        }
        int slot;
        if (level instanceof ServerLevel server) {
            PlayerColors colors = PlayerColors.get(server.getServer());
            int previous = colors.slotCount();
            slot = colors.slot(player.getUUID());
            if (previous != colors.slotCount()) {
                PlayerColorNetwork.broadcastPalette(server.getServer());
            }
        } else {
            slot = player.getPersistentData().getInt(COLOR_SLOT);
            if (slot <= 0 || slot > PlayerColors.MAX_SLOT) {
                return;
            }
        }
        var properties = stack.getOrDefault(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY);
        String key = PlayerConcreteBlock.COLOR_ID.getName();
        var slotProperties = slotProperties(slot);
        String colorSlot = slotProperties.properties().get(key);
        if (colorSlot.equals(properties.properties().get(key))) {
            return;
        }
        if (properties.properties().isEmpty() ||
                (properties.properties().size() == 1 && properties.properties().containsKey(key))) {
            stack.set(DataComponents.BLOCK_STATE, slotProperties);
            return;
        }
        var updated = new HashMap<>(properties.properties());
        updated.put(key, colorSlot);
        var replacement = new BlockItemStateProperties(Map.copyOf(updated));
        stack.set(DataComponents.BLOCK_STATE, replacement);
    }

    private static BlockItemStateProperties slotProperties(int slot) {
        var properties = SLOT_PROPERTIES.get(slot);
        if (properties != null) {
            return properties;
        }
        var created =
                new BlockItemStateProperties(Map.of(PlayerConcreteBlock.COLOR_ID.getName(), Integer.toString(slot)));
        if (SLOT_PROPERTIES.compareAndSet(slot, null, created)) {
            return created;
        }
        return SLOT_PROPERTIES.get(slot);
    }

    public static BlockState material(ItemStack stack) {
        BlockState state = stack.getItem() instanceof BlockItem item &&
                item.getBlock() instanceof PlayerConcreteBlock ? item.getBlock()
                .defaultBlockState() : ModContent.CONCRETE.get().defaultBlockState();
        return stack.getOrDefault(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY).apply(state);
    }

    public static boolean isPaletteMaterial(ItemStack stack) {
        return stack.getItem() instanceof BlockItem item && item.getBlock() instanceof PlayerConcreteBlock;
    }
}
