package com.ourcreation.playerblocks.color;

import com.ourcreation.playerblocks.block.PlayerShapeBlock;
import com.ourcreation.playerblocks.block.entity.PlayerColoredEntity;
import com.ourcreation.playerblocks.item.PlayerBlockItem;
import com.ourcreation.playerblocks.network.PlayerColorNetwork;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Hooks color commands, player lifecycle events, and unowned-block claiming.
 */
public final class PlayerColorEvents {

    private PlayerColorEvents() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(PlayerColorEvents::registerCommands);
        NeoForge.EVENT_BUS.addListener(PlayerColorEvents::onServerTick);
        NeoForge.EVENT_BUS.addListener(PlayerColorEvents::onRightClickBlock);
        NeoForge.EVENT_BUS.addListener(
                (PlayerEvent.PlayerLoggedInEvent event) -> PlayerColorNetwork.syncColor(event.getEntity()));
        NeoForge.EVENT_BUS.addListener(
                (PlayerEvent.PlayerRespawnEvent event) -> PlayerColorNetwork.syncColor(event.getEntity()));
        NeoForge.EVENT_BUS.addListener(
                (PlayerEvent.PlayerChangedDimensionEvent event) -> PlayerColorNetwork.syncColor(event.getEntity()));
    }

    private static void registerCommands(RegisterCommandsEvent event) {
        PlayerColorCommands.register(event.getDispatcher());
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % 20 == 0) {
            PlayerColorNetwork.refreshTeams(event.getServer());
        }
    }

    private static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        var player = event.getEntity();
        var level = event.getLevel();
        var pos = event.getPos();
        if (player.isShiftKeyDown()
                && level.getBlockEntity(pos) instanceof PlayerColoredEntity entity
                && entity.owner() == null) {
            entity.link(player.getUUID());
            if (level.getBlockState(pos).getBlock() instanceof PlayerShapeBlock) {
                PlayerShapeBlock.link(level, pos, player);
            }
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide));
            return;
        }

        for (var hand : InteractionHand.values()) {
            PlayerBlockItem.prepare(player.getItemInHand(hand), level, player);
        }
    }
}
