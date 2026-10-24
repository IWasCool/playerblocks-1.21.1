package com.ourcreation.playerblocks.mixin.vanilla;

import com.ourcreation.playerblocks.block.PlayerBanner;

import java.util.Map;

import net.minecraft.world.level.block.BannerBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Custom white-based banners must not replace vanilla's white banner lookup.
 */
@Mixin(BannerBlock.class)
public abstract class BannerColorRegistryMixin {

    @Redirect(method = "<init>", at = @At(value = "INVOKE",
            target = "Ljava/util/Map;put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"))
    private Object playerBlocks$keepVanillaBanner(Map<Object, Object> colors, Object key, Object block) {
        return block instanceof PlayerBanner ? colors.get(key) : colors.put(key, block);
    }
}
