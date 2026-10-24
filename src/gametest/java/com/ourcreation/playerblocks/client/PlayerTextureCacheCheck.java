package com.ourcreation.playerblocks.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

/**
 * Exercises cache churn with tiny real GPU textures instead of full entity images.
 */
public final class PlayerTextureCacheCheck {

    public static void verify(Minecraft minecraft) {
        var cache = new PlayerTextureCache("cache_check", 8);
        var manager = minecraft.getTextureManager();
        var locations = new ResourceLocation[32];
        AbstractTexture first = null;
        RenderType secondType = null;
        try {
            for (int color = 0; color < locations.length; color++) {
                locations[color] = cache.register(color, pixel(color));
                if (color == 0) {
                    first = manager.getTexture(locations[0], null);
                }
                if (color == 1) {
                    secondType = RenderType.entitySolid(locations[1]);
                }
            }
            if (manager.getTexture(locations[0], null) != first) {
                throw new IllegalStateException("Eviction released a texture before pending geometry could draw");
            }
            if (!locations[0].equals(cache.get(0)) || manager.getTexture(locations[0], null) != first) {
                throw new IllegalStateException("Same-frame reuse must reclaim the existing texture");
            }
            PlayerTextureCache.endFrame(new RenderFrameEvent.Post(minecraft.getTimer()));
            int live = 0;
            for (var location : locations) {
                if (manager.getTexture(location, null) != null) {
                    live++;
                }
            }
            if (live != 8 || manager.getTexture(locations[0], null) != first ||
                    manager.getTexture(locations[1], null) != null) {
                throw new IllegalStateException(
                        "Frame completion must release only retired textures and retain the eight cached tints");
            }
            if (cache.get(1) != null) {
                throw new IllegalStateException("Released tint incorrectly remained cached");
            }
            var recreated = cache.register(1, pixel(1));
            if (!locations[1].equals(recreated) || RenderType.entitySolid(recreated) != secondType) {
                throw new IllegalStateException("Recreated colors must reuse stable texture IDs and render types");
            }
        } finally {
            cache.clear();
        }
        for (var location : locations) {
            if (location != null && manager.getTexture(location, null) != null) {
                throw new IllegalStateException("Cache cleanup left a generated texture registered");
            }
        }
    }

    private static NativeImage pixel(int color) {
        var pixels = new NativeImage(1, 1, false);
        pixels.setPixelRGBA(0, 0, 0xFF000000 | color);
        return pixels;
    }
}
