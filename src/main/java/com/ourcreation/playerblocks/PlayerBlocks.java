package com.ourcreation.playerblocks;

import com.ourcreation.playerblocks.color.PlayerColorEvents;
import com.ourcreation.playerblocks.color.PlayerColorRefresh;
import com.ourcreation.playerblocks.compat.WorldEditColors;
import com.ourcreation.playerblocks.fluid.PlayerLiquids;
import com.ourcreation.playerblocks.network.PlayerColorNetwork;
import com.ourcreation.playerblocks.registry.ModContent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

@Mod(PlayerBlocks.MODID)
public final class PlayerBlocks {

    public static final String MODID = "player_blocks";

    public PlayerBlocks(IEventBus modEventBus) {
        ModContent.register(modEventBus);
        PlayerLiquids.register(modEventBus);
        PlayerColorEvents.register();
        PlayerColorRefresh.register();
        modEventBus.addListener(PlayerColorNetwork::register);
        modEventBus.addListener(PlayerBlocks::commonSetup);
    }

    private static void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            if (ModList.get().isLoaded("worldedit")) {
                WorldEditColors.register();
            }
        });
    }
}
