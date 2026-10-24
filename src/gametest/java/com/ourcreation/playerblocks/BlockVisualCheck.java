package com.ourcreation.playerblocks;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import com.ourcreation.playerblocks.block.entity.PlayerBedEntity;
import com.ourcreation.playerblocks.block.entity.PlayerChestEntity;
import com.ourcreation.playerblocks.client.PlayerBedTexture;
import com.ourcreation.playerblocks.client.PlayerConcreteColors;
import com.ourcreation.playerblocks.client.PlayerTextureCacheCheck;
import com.ourcreation.playerblocks.compat.PlayerJeiPlugin;
import com.ourcreation.playerblocks.fluid.PlayerLiquids;
import com.ourcreation.playerblocks.registry.ModContent;

import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;

/**
 * Opt-in client model bake/render check, excluded from release builds.
 */
@EventBusSubscriber(modid = PlayerBlocks.MODID, value = Dist.CLIENT)
public final class BlockVisualCheck {

    private static boolean captured;

    @SubscribeEvent
    public static void render(ScreenEvent.Render.Post event) {
        if (!Boolean.getBoolean("player_blocks.visualCheck") || captured ||
                !(event.getScreen() instanceof TitleScreen)) {
            return;
        }
        captured = true;
        var minecraft = Minecraft.getInstance();
        try {
            PlayerTextureCacheCheck.verify(minecraft);
            if (ModList.get().isLoaded("jei")) {
                var examples = new ArrayList<RecipeHolder<?>>();
                var registration = (IRecipeRegistration) Proxy.newProxyInstance(BlockVisualCheck.class.getClassLoader(),
                        new Class<?>[]{IRecipeRegistration.class}, (proxy, method, arguments) -> {
                            if (method.getName().equals("addRecipes")) {
                                examples.addAll((List) arguments[1]);
                            }
                            return null;
                        });
                new PlayerJeiPlugin().registerRecipes(registration);
                if (examples.size() != 1 || examples.getFirst().value().getIngredients().size() != 2) {
                    throw new IllegalStateException("JEI needs a display recipe for the dynamic shulker conversion");
                }
            }
            for (int rgb : new int[]{0x27B6D9, 0xD83B20}) {
                try (var stream = minecraft.getResourceManager()
                        .getResourceOrThrow(ResourceLocation.withDefaultNamespace("textures/entity/bed/white.png"))
                        .open();
                     var original = NativeImage.read(stream);
                     var tinted = new NativeImage(original.getWidth(), original.getHeight(), false)) {
                    tinted.copyFrom(original);
                    PlayerBedTexture.tint(tinted, rgb);
                    // Actual vanilla UV samples: pillow top, both side wraps, head/back end, and wooden leg.
                    for (int[] uv : new int[][]{{14, 9}, {2, 10}, {24, 10}, {10, 2}, {52, 14}}) {
                        int x = uv[0] * original.getWidth() / 64, y = uv[1] * original.getHeight() / 64;
                        if (original.getPixelRGBA(x, y) != tinted.getPixelRGBA(x, y)) {
                            throw new IllegalStateException(
                                    "Bed pillow/wood incorrectly tinted at " + Arrays.toString(uv));
                        }
                    }
                    if (original.getPixelRGBA(14, 18) == tinted.getPixelRGBA(14, 18)) {
                        throw new IllegalStateException("Bed blanket must retain its player/team tint");
                    }
                }
            }
            var state = ModContent.PILLAR.get().defaultBlockState();
            var model = minecraft.getBlockRenderer().getBlockModel(state);
            for (Direction direction : Direction.values()) {
                var faces = model.getQuads(state, direction, RandomSource.create(1));
                if (faces.isEmpty() || faces.stream().anyMatch(face -> !face.isTinted())) {
                    throw new IllegalStateException("Pillar face did not bake with color tint: " + direction);
                }
            }
            for (var entry : Map.of(ModContent.CHISELED.get(), "player_blocks:block/tinted/chiseled_quartz_block",
                    ModContent.TILES.get(), "player_blocks:block/tinted/quartz_bricks", ModContent.WOOL.get(),
                    "player_blocks:block/tinted/white_wool", ModContent.TERRACOTTA.get(),
                    "player_blocks:block/tinted/terracotta", ModContent.CONCRETE.get(),
                    "player_blocks:block/tinted/white_concrete").entrySet()) {
                var placed = entry.getKey().defaultBlockState();
                var baked = minecraft.getBlockRenderer().getBlockModel(placed);
                var faces = new ArrayList<>(baked.getQuads(placed, null, RandomSource.create(1)));
                for (Direction direction : Direction.values()) {
                    faces.addAll(baked.getQuads(placed, direction, RandomSource.create(1)));
                }
                if (faces.isEmpty() || faces.stream().anyMatch(face -> !face.isTinted() ||
                        !face.getSprite().contents().name().toString().equals(entry.getValue()))) {
                    throw new IllegalStateException(
                            "Placed patterned block did not bake with its own texture: " + entry.getKey());
                }
                var sprite = faces.getFirst().getSprite();
                double total = 0;
                int pixels = 0;
                for (int y = 0; y < sprite.contents().height(); y++) {
                    for (int x = 0; x < sprite.contents().width(); x++) {
                        int pixel = sprite.getPixelRGBA(0, x, y);
                        if ((pixel >>> 24) == 0) {
                            continue;
                        }
                        total += pixel & 255;
                        pixels++;
                    }
                }
                if (Math.abs(total / pixels - 235) > .6) {
                    throw new IllegalStateException("Old/new textures have mismatched brightness: " + entry.getKey());
                }
            }
            var palette = new CompoundTag();
            palette.putInt("1", 0xD8A50A);
            PlayerConcreteColors.setPalette(palette);
            var fluidExtension = IClientFluidTypeExtensions.of(PlayerLiquids.TYPE.get());
            if (fluidExtension.getStillTexture() == null || fluidExtension.getFlowingTexture() == null ||
                    (fluidExtension.getTintColor() >>> 24) != 255) {
                throw new IllegalStateException("Player Liquid needs water sprites and an opaque tint");
            }
            var bucket = new ItemStack(PlayerLiquids.BUCKET.get());
            bucket.set(DataComponents.BLOCK_STATE, new BlockItemStateProperties(Map.of("color_id", "1")));
            if (minecraft.getItemColors().getColor(bucket, 0) != 0xFFFFFFFF ||
                    minecraft.getItemColors().getColor(bucket, 1) != 0xFFD8A50A) {
                throw new IllegalStateException(
                        "Bucket metal must remain neutral while its liquid follows the hex color");
            }
            for (var block : new Block[]{ModContent.BED.get(), ModContent.BANNER.get(), ModContent.WALL_BANNER.get(), ModContent.CHEST.get(), ModContent.SHULKER.get()}) {
                var entity = ((EntityBlock) block).newBlockEntity(BlockPos.ZERO, block.defaultBlockState());
                if (minecraft.getBlockEntityRenderDispatcher().getRenderer(entity) == null) {
                    throw new IllegalStateException("Missing vanilla decoration renderer: " + block);
                }
            }
            var graphics = event.getGuiGraphics();
            graphics.flush();
            RenderSystem.clear(256, Minecraft.ON_OSX);
            graphics.fill(0, 0, event.getScreen().width, event.getScreen().height, 0xFF20242A);
            graphics.drawString(minecraft.font, "Player Blocks - inventory model check (54 items)", 12, 10, 0xFFFFFF);
            var items = Stream.concat(Arrays.stream(ModContent.allItems()), Stream.of(PlayerLiquids.BUCKET.get()))
                    .toArray(Item[]::new);
            int rows = (items.length + 7) / 8;
            int cell = Math.min(Math.min(64, (event.getScreen().width - 24) / 8),
                    (event.getScreen().height - 36) / rows - 12);
            for (int i = 0; i < items.length; i++) {
                int x = 12 + (i % 8) * cell, y = 30 + (i / 8) * (cell + 12);
                graphics.fill(x, y, x + cell - 4, y + cell - 4, 0xFF505963);
                var stack = new ItemStack(items[i]);
                stack.set(DataComponents.BLOCK_STATE, new BlockItemStateProperties(Map.of("color_id", "1")));
                if ((minecraft.getItemColors().getColor(stack, 0) >>> 24) != 255) {
                    throw new IllegalStateException("Item tint alpha must be opaque: " + items[i]);
                }
                graphics.pose().pushPose();
                graphics.pose().translate(x + 4, y + 4, 0);
                float scale = (cell - 12) / 16F;
                graphics.pose().scale(scale, scale, 1);
                graphics.renderItem(stack, 0, 0);
                graphics.pose().popPose();
                graphics.drawString(minecraft.font, Integer.toString(i + 1), x + 2, y + cell - 2, 0xFFFFFF);
            }
            // Actual open chest renderer: verify the interior/trim visually under two different hex colors.
            for (int i = 0; i < 2; i++) {
                var chest = new com.ourcreation.playerblocks.block.entity.PlayerChestEntity(BlockPos.ZERO,
                        ModContent.CHEST.get().defaultBlockState());
                var data = new CompoundTag();
                data.putInt("color", i == 0 ? 0xD8A50A : 0x27B6D9);
                data.putBoolean("colorOnly", true);
                chest.loadCustomOnly(data, RegistryAccess.EMPTY);
                chest.triggerEvent(1, 1);
                for (int tick = 0; tick < 10; tick++) {
                    ChestBlockEntity.lidAnimateTick(null, BlockPos.ZERO, chest.getBlockState(), chest);
                }
                int x = 24 + cell * 8, y = 40 + i * 110;
                graphics.drawString(minecraft.font, i == 0 ? "Open Player Chest" : "Same gray trim, different hex", x,
                        y, 0xFFFFFF);
                graphics.pose().pushPose();
                graphics.pose().translate(x + 48, y + 62, 150);
                graphics.pose().scale(55, -55, 55);
                graphics.pose().mulPose(Axis.XP.rotationDegrees(30));
                graphics.pose().mulPose(Axis.YP.rotationDegrees(45));
                graphics.pose().translate(-.5, -.5, -.5);
                minecraft.getBlockEntityRenderDispatcher()
                        .renderItem(chest, graphics.pose(), graphics.bufferSource(), LightTexture.FULL_BRIGHT,
                                OverlayTexture.NO_OVERLAY);
                graphics.pose().popPose();
            }
            for (int index = 0; index < 2; index++) {
                var bed = new com.ourcreation.playerblocks.block.entity.PlayerBedEntity(BlockPos.ZERO,
                        ModContent.BED.get().defaultBlockState());
                var bedData = new CompoundTag();
                bedData.putInt("color", index == 0 ? 0x27B6D9 : 0x102030);
                bed.loadCustomOnly(bedData, RegistryAccess.EMPTY);
                int bedX = event.getScreen().width - 118, bedY = 48 + index * 132;
                graphics.drawString(minecraft.font, "Bed pillow wrap", bedX, bedY, 0xFFFFFF);
                graphics.pose().pushPose();
                graphics.pose().translate(bedX + 55, bedY + 64, 150);
                graphics.pose().scale(42, -42, 42);
                graphics.pose().mulPose(Axis.XP.rotationDegrees(30));
                graphics.pose().mulPose(Axis.YP.rotationDegrees(45));
                graphics.pose().translate(-.5, -.5, 0);
                minecraft.getBlockEntityRenderDispatcher()
                        .renderItem(bed, graphics.pose(), graphics.bufferSource(), LightTexture.FULL_BRIGHT,
                                OverlayTexture.NO_OVERLAY);
                graphics.pose().popPose();
            }
            graphics.flush();
            try (var screenshot = Screenshot.takeScreenshot(minecraft.getMainRenderTarget())) {
                screenshot.writeToFile(Path.of(System.getProperty("player_blocks.visualOutput")));
            }
            System.out.println("PLAYER_BLOCKS_VISUAL_CHECK: PASS");
        } catch (Exception error) {
            error.printStackTrace();
            System.out.println("PLAYER_BLOCKS_VISUAL_CHECK: FAIL");
        } finally {
            minecraft.stop();
        }
    }
}
