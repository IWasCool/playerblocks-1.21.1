package com.ourcreation.playerblocks.client;

import com.ourcreation.playerblocks.PlayerBlocks;
import com.ourcreation.playerblocks.block.PlayerConcreteBlock;
import com.ourcreation.playerblocks.block.entity.PlayerColoredEntity;
import com.ourcreation.playerblocks.color.PlayerColors;
import com.ourcreation.playerblocks.fluid.PlayerLiquids;
import com.ourcreation.playerblocks.network.PlayerColorNetwork;
import com.ourcreation.playerblocks.registry.ModContent;

import java.util.Arrays;

import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

@EventBusSubscriber(modid = PlayerBlocks.MODID, value = Dist.CLIENT)
public final class PlayerConcreteColors {

    private static final int[] PALETTE = new int[PlayerColors.MAX_SLOT + 1];

    static {
        Arrays.fill(PALETTE, 0xFFFFFF);
    }

    public static void setPalette(CompoundTag colors) {
        int[] next = new int[PALETTE.length];
        Arrays.fill(next, 0xFFFFFF);
        for (String key : colors.getAllKeys()) {
            int slot = Integer.parseInt(key);
            if (slot > 0 && slot < next.length) {
                next[slot] = colors.getInt(key) & 0xFFFFFF;
            }
        }
        if (Arrays.equals(PALETTE, next)) {
            return;
        }
        System.arraycopy(next, 0, PALETTE, 0, next.length);
        var minecraft = Minecraft.getInstance();
        if (minecraft.level != null) {
            minecraft.levelRenderer.allChanged();
        }
    }

    @SubscribeEvent
    public static void items(RegisterColorHandlersEvent.Item event) {
        event.register((stack, tint) -> itemColor(stack), ModContent.allItems());
        event.register((stack, tint) -> tint == 1 ? itemColor(stack) : 0xFFFFFFFF, PlayerLiquids.BUCKET.get());
    }

    public static int itemColor(ItemStack stack) {
        var properties = stack.get(DataComponents.BLOCK_STATE);
        if (properties != null) {
            String value = properties.properties().get(PlayerConcreteBlock.COLOR_ID.getName());
            if (value != null) {
                try {
                    int slot = Integer.parseInt(value);
                    if (slot > 0 && slot < PALETTE.length) {
                        return 0xFF000000 | PALETTE[slot];
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }
        var player = Minecraft.getInstance().player;
        if (player == null) {
            return 0xFFFFFFFF;
        }
        var data = player.getPersistentData();
        return 0xFF000000 | (data.contains(PlayerColorNetwork.PREVIEW_COLOR) ? data.getInt(
                PlayerColorNetwork.PREVIEW_COLOR) : PlayerColors.defaultColor(player.getUUID()));
    }

    @SubscribeEvent
    public static void blocks(RegisterColorHandlersEvent.Block event) {
        event.register((state, level, pos, tint) -> {
            if (state.hasProperty(PlayerConcreteBlock.COLOR_ID)) {
                int slot = state.getValue(PlayerConcreteBlock.COLOR_ID);
                if (slot != 0) {
                    return 0xFF000000 | PALETTE[slot];
                }
            }
            return 0xFF000000 | (level != null && pos != null &&
                    level.getBlockEntity(pos) instanceof PlayerColoredEntity entity ? entity.color() : 0xFFFFFF);
        }, ModContent.allBlocks());
    }
}
