package com.ourcreation.playerblocks.item;

import com.ourcreation.playerblocks.registry.ModContent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class PlayerBannerItem extends BannerItem {

    public PlayerBannerItem(Properties properties) {
        super(ModContent.BANNER.get(), ModContent.WALL_BANNER.get(), properties);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        super.inventoryTick(stack, level, entity, slot, selected);
        if (entity instanceof Player player) {
            PlayerBlockItem.prepare(stack, level, player);
        }
    }
}
