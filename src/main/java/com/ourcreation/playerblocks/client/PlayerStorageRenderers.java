package com.ourcreation.playerblocks.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.ourcreation.playerblocks.PlayerBlocks;
import com.ourcreation.playerblocks.block.entity.PlayerChestEntity;
import com.ourcreation.playerblocks.block.entity.PlayerColoredEntity;
import com.ourcreation.playerblocks.block.entity.PlayerShulkerEntity;
import com.ourcreation.playerblocks.registry.ModContent;

import java.io.IOException;
import java.util.EnumMap;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.ChestRenderer;
import net.minecraft.client.renderer.blockentity.ShulkerBoxRenderer;
import net.minecraft.client.resources.model.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

/**
 * Minecraft's animated containers with tinted body textures and neutral chest trim.
 */
@EventBusSubscriber(modid = PlayerBlocks.MODID, value = Dist.CLIENT)
public final class PlayerStorageRenderers {

    private static final PlayerTextureCache TEXTURES = new PlayerTextureCache("storage", 512);

    private static final EnumMap<TextureVariant, TextureSource> SOURCES = new EnumMap<>(TextureVariant.class);

    @SubscribeEvent
    public static void reload(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) manager -> {
            TEXTURES.clear();
            SOURCES.clear();
        });
    }

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModContent.CHEST_ENTITY.get(),
                context -> new ChestRenderer<PlayerChestEntity>(context) {

                    @Override
                    protected Material getMaterial(PlayerChestEntity entity, ChestType type) {
                        return Sheets.chooseMaterial(entity, type, false);
                    }

                    @Override
                    public void render(PlayerChestEntity entity, float tick, PoseStack poses, MultiBufferSource buffers,
                                       int light, int overlay) {
                        ChestType type =
                                entity.hasLevel() ? entity.getBlockState().getValue(ChestBlock.TYPE) : ChestType.SINGLE;
                        TextureVariant variant = switch (type) {
                            case LEFT -> TextureVariant.CHEST_LEFT;
                            case RIGHT -> TextureVariant.CHEST_RIGHT;
                            case SINGLE -> TextureVariant.CHEST;
                        };
                        super.render(entity, tick, poses,
                                PlayerVanillaRenderers.textureBuffers(buffers, getMaterial(entity, type),
                                        RenderType::entityCutout, texture(variant, entity.color())), light, overlay);
                    }
                });
        event.registerBlockEntityRenderer(ModContent.SHULKER_ENTITY.get(), context -> {
            var vanilla = new ShulkerBoxRenderer(context);
            return new BlockEntityRenderer<PlayerShulkerEntity>() {

                @Override
                public void render(PlayerShulkerEntity entity, float tick, PoseStack poses, MultiBufferSource buffers,
                                   int light, int overlay) {
                    vanilla.render(entity, tick, poses,
                            PlayerVanillaRenderers.textureBuffers(buffers, Sheets.SHULKER_TEXTURE_LOCATION.get(0),
                                    RenderType::entityCutoutNoCull, texture(TextureVariant.SHULKER, entity.color())),
                            light, overlay);
                }

                @Override
                public AABB getRenderBoundingBox(PlayerShulkerEntity entity) {
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

                        private final PlayerChestEntity chest =
                                new PlayerChestEntity(BlockPos.ZERO, ModContent.CHEST.get().defaultBlockState());

                        private final PlayerShulkerEntity shulker =
                                new PlayerShulkerEntity(BlockPos.ZERO, ModContent.SHULKER.get().defaultBlockState());

                        private final CompoundTag colorData = new CompoundTag();

                        {
                            colorData.putBoolean("colorOnly", true);
                        }

                        @Override
                        public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack poses,
                                                 MultiBufferSource buffers, int light, int overlay) {
                            var entity = stack.is(ModContent.CHEST.get().asItem()) ? chest : shulker;
                            int color = PlayerConcreteColors.itemColor(stack) & 0xFFFFFF;
                            if (((PlayerColoredEntity) entity).color() != color) {
                                colorData.putInt("color", color);
                                entity.loadCustomOnly(colorData,
                                        mc.level == null ? RegistryAccess.EMPTY : mc.level.registryAccess());
                            }
                            mc.getBlockEntityRenderDispatcher().renderItem(entity, poses, buffers, light, overlay);
                        }
                    };
                }
                return renderer;
            }
        }, ModContent.CHEST.get().asItem(), ModContent.SHULKER.get().asItem());
    }

    private static ResourceLocation texture(TextureVariant variant, int rgb) {
        rgb &= 0xFFFFFF;
        long key = (long) variant.ordinal() << 24 | rgb;
        var cached = TEXTURES.get(key);
        if (cached != null) {
            return cached;
        }
        // The four source variants are decoded once per resource reload, regardless of owner count.
        var source = SOURCES.computeIfAbsent(variant, PlayerStorageRenderers::loadSource);
        return TEXTURES.register(key, source.tint(rgb));
    }

    private static TextureSource loadSource(TextureVariant variant) {
        try (var stream = Minecraft.getInstance().getResourceManager().getResourceOrThrow(variant.body).open();
             var pixels = NativeImage.read(stream)) {
            int width = pixels.getWidth(), height = pixels.getHeight();
            int[] body = new int[width * height];
            int[] trim = variant.overlay == null ? null : new int[body.length];
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    body[y * width + x] = pixels.getPixelRGBA(x, y);
                }
            }
            if (trim != null) {
                try (var trimStream = Minecraft.getInstance().getResourceManager().getResourceOrThrow(variant.overlay)
                        .open();
                     var overlay = NativeImage.read(trimStream)) {
                    for (int y = 0; y < height; y++) {
                        for (int x = 0; x < width; x++) {
                            trim[y * width + x] = overlay.getPixelRGBA(x, y);
                        }
                    }
                }
            }
            return new TextureSource(width, height, body, trim);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot load player container texture", exception);
        }
    }

    private enum TextureVariant {

        CHEST("chest/normal_body", "chest/normal_outline"), CHEST_LEFT("chest/normal_left_body",
                "chest/normal_left_outline"), CHEST_RIGHT("chest/normal_right_body",
                "chest/normal_right_outline"), SHULKER("player_shulker", null);

        private final ResourceLocation body, overlay;

        TextureVariant(String body, String overlay) {
            this.body = ResourceLocation.fromNamespaceAndPath(PlayerBlocks.MODID, "textures/entity/" + body + ".png");
            this.overlay = overlay == null ? null : ResourceLocation.fromNamespaceAndPath(PlayerBlocks.MODID,
                    "textures/entity/" + overlay + ".png");
        }
    }

    private record TextureSource(int width, int height, int[] body, int[] trim) {

        NativeImage tint(int rgb) {
            var pixels = new NativeImage(width, height, false);
            int red = rgb >>> 16 & 255, green = rgb >>> 8 & 255, blue = rgb & 255;
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int index = y * width + x;
                    int p = body[index];
                    int result = trim != null && (trim[index] >>> 24) != 0 ? trim[index] :
                            (p & 0xFF000000) | ((p & 255) * red / 255) | (((p >>> 8 & 255) * green / 255) << 8) |
                                    (((p >>> 16 & 255) * blue / 255) << 16);
                    pixels.setPixelRGBA(x, y, result);
                }
            }
            return pixels;
        }
    }
}
