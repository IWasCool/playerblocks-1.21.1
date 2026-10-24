package com.ourcreation.playerblocks;

import com.mojang.authlib.GameProfileRepository;

import java.io.File;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.Services;
import net.minecraft.server.players.GameProfileCache;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;

/**
 * Test-only services missing from vanilla GameTestServer but expected by team/claim mods.
 */
@EventBusSubscriber(modid = PlayerBlocks.MODID)
public final class AuditEnvironment {

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void profileCache(ServerAboutToStartEvent event) throws ReflectiveOperationException {
        var server = event.getServer();
        if (server.getProfileCache() != null) {
            return;
        }
        var field = MinecraftServer.class.getDeclaredField("services");
        field.setAccessible(true);
        var old = (Services) field.get(server);
        GameProfileRepository repository = (names, callback) -> {
            for (String name : names) {
                callback.onProfileLookupFailed(name, new IllegalArgumentException("Offline audit player"));
            }
        };
        GameProfileCache.setUsesAuthentication(false);
        var cache = new GameProfileCache(repository, new File("audit-usercache.json"));
        field.set(server,
                new Services(old == null ? null : old.sessionService(), old == null ? null : old.servicesKeySet(),
                        repository, cache));
    }
}
