package com.ourcreation.playerblocks.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.ourcreation.playerblocks.PlayerBlocks;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;

import java.util.HashSet;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

/**
 * Bounded tint textures, kept alive until every queued vertex in the frame has drawn.
 */
@EventBusSubscriber(modid = PlayerBlocks.MODID, value = Dist.CLIENT)
public final class PlayerTextureCache {

    private static final Set<ResourceLocation> RETIRED = new HashSet<>();

    private final Long2ObjectLinkedOpenHashMap<ResourceLocation> textures = new Long2ObjectLinkedOpenHashMap<>();

    private final String path;

    private final int capacity;

    PlayerTextureCache(String name, int capacity) {
        this.path = "dynamic/" + name + "/";
        this.capacity = capacity;
    }

    ResourceLocation get(long key) {
        var cached = textures.getAndMoveToLast(key);
        if (cached != null) {
            return cached;
        }
        var location = location(key);
        // An evicted tint can still be used by pending geometry in this frame.
        if (!RETIRED.remove(location)) {
            return null;
        }
        remember(key, location);
        return location;
    }

    ResourceLocation register(long key, NativeImage pixels) {
        var location = location(key);
        Minecraft.getInstance().getTextureManager().register(location, new DynamicTexture(pixels));
        RETIRED.remove(location);
        remember(key, location);
        return location;
    }

    private ResourceLocation location(long key) {
        // Stable IDs also let Minecraft reuse its memoized render types after eviction/reload.
        return ResourceLocation.fromNamespaceAndPath(PlayerBlocks.MODID, path + Long.toHexString(key));
    }

    private void remember(long key, ResourceLocation location) {
        if (textures.size() >= capacity) {
            RETIRED.add(textures.removeFirst());
        }
        textures.putAndMoveToLast(key, location);
    }

    void clear() {
        var manager = Minecraft.getInstance().getTextureManager();
        textures.values().forEach(manager::release);
        textures.clear();
        releaseRetired();
    }

    @SubscribeEvent
    public static void endFrame(RenderFrameEvent.Post event) {
        releaseRetired();
    }

    static void releaseRetired() {
        if (RETIRED.isEmpty()) {
            return;
        }
        var manager = Minecraft.getInstance().getTextureManager();
        RETIRED.forEach(manager::release);
        RETIRED.clear();
    }
}
