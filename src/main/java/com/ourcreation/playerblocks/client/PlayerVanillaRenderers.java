package com.ourcreation.playerblocks.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.ourcreation.playerblocks.PlayerBlocks;
import com.ourcreation.playerblocks.block.entity.PlayerBannerEntity;
import com.ourcreation.playerblocks.item.PlayerBannerItem;
import com.ourcreation.playerblocks.registry.ModContent;

import java.io.IOException;
import java.util.function.Function;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BannerRenderer;
import net.minecraft.client.renderer.blockentity.BedRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.Material;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

/**
 * Delegates geometry, transforms, texture layout, and animation to Minecraft.
 */
@EventBusSubscriber(modid = PlayerBlocks.MODID, value = Dist.CLIENT)
public final class PlayerVanillaRenderers {

    private static final PlayerTextureCache BED_TEXTURES = new PlayerTextureCache("bed", 256);

    private static NativeImage bedSource;

    @SubscribeEvent
    public static void reload(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) manager -> {
            BED_TEXTURES.clear();
            if (bedSource != null) {
                bedSource.close();
                bedSource = null;
            }
        });
    }

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModContent.BED_ENTITY.get(), context -> {
            var vanilla = new BedRenderer(context);
            return (entity, tick, poses, buffers, light, overlay) -> vanilla.render(entity, tick, poses,
                    bedBuffers(buffers, entity.color()), light, overlay);
        });
        event.registerBlockEntityRenderer(ModContent.BANNER_ENTITY.get(), context -> {
            var vanilla = new BannerRenderer(context);
            return new BlockEntityRenderer<PlayerBannerEntity>() {

                @Override
                public void render(PlayerBannerEntity entity, float tick, PoseStack poses, MultiBufferSource buffers,
                                   int light, int overlay) {
                    vanilla.render(entity, tick, poses, bannerBuffers(buffers, entity.color()), light, overlay);
                }

                @Override
                public AABB getRenderBoundingBox(PlayerBannerEntity entity) {
                    return vanilla.getRenderBoundingBox(entity);
                }
            };
        });
    }

    @SubscribeEvent
    public static void items(RegisterClientExtensionsEvent event) {
        event.registerItem(new IClientItemExtensions() {

            private BlockEntityWithoutLevelRenderer renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) {
                    var mc = Minecraft.getInstance();
                    renderer = new BlockEntityWithoutLevelRenderer(mc.getBlockEntityRenderDispatcher(),
                            mc.getEntityModels()) {

                        @Override
                        public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack poses,
                                                 MultiBufferSource buffers, int light, int overlay) {
                            int color = PlayerConcreteColors.itemColor(stack) & 0xFFFFFF;
                            // The built-in renderer uses vanilla bed/banner entities and their vanilla renderers.
                            mc.getItemRenderer().getBlockEntityRenderer().renderByItem(stack, context, poses,
                                    stack.getItem() instanceof PlayerBannerItem ? bannerBuffers(buffers,
                                            color) : bedBuffers(buffers, color), light, overlay);
                        }
                    };
                }
                return renderer;
            }
        }, ModContent.BED.get().asItem(), ModContent.BANNER.get().asItem());
    }

    private static MultiBufferSource bannerBuffers(MultiBufferSource buffers, int rgb) {
        // Vanilla requests the cloth base first, then each dyed pattern. Keep the pole and patterns untouched.
        return new MultiBufferSource() {

            private final RenderType cloth = RenderType.entityNoOutline(Sheets.BANNER_SHEET);

            private boolean base = true;

            @Override
            public VertexConsumer getBuffer(RenderType type) {
                var target = buffers.getBuffer(type);
                if (base && type == cloth) {
                    base = false;
                    return new ForwardingVertex(target, null, rgb);
                }
                return target;
            }
        };
    }

    private static MultiBufferSource bedBuffers(MultiBufferSource buffers, int rgb) {
        TextureAtlasSprite sprite = Sheets.BED_TEXTURES[0].sprite();
        ResourceLocation texture = bedTexture(rgb);
        RenderType original = RenderType.entitySolid(sprite.atlasLocation()), tinted = RenderType.entitySolid(texture);
        return type ->
                type == original ? new ForwardingVertex(buffers.getBuffer(tinted), sprite, -1) : buffers.getBuffer(
                        type);
    }

    static MultiBufferSource textureBuffers(MultiBufferSource buffers, Material material,
                                            Function<ResourceLocation, RenderType> renderType,
                                            ResourceLocation texture) {
        var sprite = material.sprite();
        RenderType original = renderType.apply(sprite.atlasLocation()), tinted = renderType.apply(texture);
        return type ->
                type == original ? new ForwardingVertex(buffers.getBuffer(tinted), sprite, -1) : buffers.getBuffer(
                        type);
    }

    private static ResourceLocation bedTexture(int rgb) {
        rgb &= 0xFFFFFF;
        var cached = BED_TEXTURES.get(rgb);
        if (cached != null) {
            return cached;
        }
        var mc = Minecraft.getInstance();
        try {
            if (bedSource == null) {
                try (var stream = mc.getResourceManager()
                        .getResourceOrThrow(ResourceLocation.withDefaultNamespace("textures/entity/bed/white.png"))
                        .open()) {
                    bedSource = NativeImage.read(stream);
                }
            }
            NativeImage pixels = new NativeImage(bedSource.getWidth(), bedSource.getHeight(), false);
            pixels.copyFrom(bedSource);
            PlayerBedTexture.tint(pixels, rgb);
            return BED_TEXTURES.register(rgb, pixels);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read Minecraft bed texture", exception);
        }
    }

    private static final class ForwardingVertex implements VertexConsumer {

        private final VertexConsumer target;

        private final TextureAtlasSprite sprite;

        private final int rgb;

        ForwardingVertex(VertexConsumer target, TextureAtlasSprite sprite, int rgb) {
            this.target = target;
            this.sprite = sprite;
            this.rgb = rgb;
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            target.addVertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            target.setColor(rgb == -1 ? r : r * (rgb >>> 16 & 255) / 255, rgb == -1 ? g : g * (rgb >>> 8 & 255) / 255,
                    rgb == -1 ? b : b * (rgb & 255) / 255, a);
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            target.setUv(sprite == null ? u : sprite.getUOffset(u), sprite == null ? v : sprite.getVOffset(v));
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            target.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            target.setUv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            target.setNormal(x, y, z);
            return this;
        }
    }
}
