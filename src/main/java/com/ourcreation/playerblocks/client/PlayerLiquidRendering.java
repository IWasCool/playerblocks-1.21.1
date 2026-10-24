package com.ourcreation.playerblocks.client;

import com.ourcreation.playerblocks.PlayerBlocks;
import com.ourcreation.playerblocks.fluid.PlayerLiquidEntity;
import com.ourcreation.playerblocks.fluid.PlayerLiquids;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

@EventBusSubscriber(modid = PlayerBlocks.MODID, value = Dist.CLIENT)
public final class PlayerLiquidRendering {

    private static final ResourceLocation STILL = ResourceLocation.withDefaultNamespace("block/water_still");

    private static final ResourceLocation FLOWING = ResourceLocation.withDefaultNamespace("block/water_flow");

    private static final ResourceLocation OVERLAY = ResourceLocation.withDefaultNamespace("block/water_overlay");

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ItemBlockRenderTypes.setRenderLayer(PlayerLiquids.SOURCE.get(), RenderType.translucent());
            ItemBlockRenderTypes.setRenderLayer(PlayerLiquids.FLOWING.get(), RenderType.translucent());
        });
    }

    @SubscribeEvent
    public static void extensions(RegisterClientExtensionsEvent event) {
        event.registerFluidType(new IClientFluidTypeExtensions() {

            public ResourceLocation getStillTexture() {
                return STILL;
            }

            public ResourceLocation getFlowingTexture() {
                return FLOWING;
            }

            public ResourceLocation getOverlayTexture() {
                return OVERLAY;
            }

            public int getTintColor() {
                return PlayerConcreteColors.itemColor(ItemStack.EMPTY);
            }

            @Override
            public int getTintColor(FluidState state, BlockAndTintGetter level, BlockPos pos) {
                return level.getBlockEntity(pos) instanceof PlayerLiquidEntity entity && entity.owner() != null ?
                        0xFF000000 | entity.color() : getTintColor();
            }
        }, PlayerLiquids.TYPE.get());
    }
}
