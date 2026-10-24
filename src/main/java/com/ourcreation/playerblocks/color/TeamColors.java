package com.ourcreation.playerblocks.color;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import net.minecraft.server.MinecraftServer;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Optional adapters use public APIs without making the team mods required dependencies.
 */
public final class TeamColors {

    private static final Logger LOGGER = LoggerFactory.getLogger(TeamColors.class);

    private static final List<Adapter> PROVIDERS = new ArrayList<>();

    private static final Set<String> WARNED = new HashSet<>();

    private static final ApiEntryPoint FTB_API =
            new ApiEntryPoint("dev.ftb.mods.ftbteams.api.FTBTeamsAPI", "api", null);

    private static final ApiEntryPoint OPEN_PAC_API =
            new ApiEntryPoint("xaero.pac.common.server.api.OpenPACServerAPI", "get", MinecraftServer.class);

    private static final ClassValue<Map<MethodKey, Method>> METHODS = new ClassValue<>() {

        @Override
        protected Map<MethodKey, Method> computeValue(Class<?> type) {
            return new HashMap<>();
        }
    };

    static {
        registerOptionalProvider("ftbteams", (server, member) -> {
            var api = FTB_API.invoke(null);
            if (!Boolean.TRUE.equals(call(api, "isManagerLoaded"))) {
                return null;
            }
            var manager = call(api, "getManager");
            var optional = (Optional<?>) call(manager, "getTeamForPlayerID", UUID.class, member);
            if (optional.isEmpty()) {
                return null;
            }
            var team = optional.get();
            if (!Boolean.TRUE.equals(call(team, "isPartyTeam"))) {
                return null;
            }
            return new Team("ftbteams:" + call(team, "getId"), (UUID) call(team, "getOwner"));
        });
        registerOptionalProvider("openpartiesandclaims", (server, member) -> {
            var api = OPEN_PAC_API.invoke(server);
            var party = call(call(api, "getPartyManager"), "getPartyByMember", UUID.class, member);
            if (party == null) {
                return null;
            }
            return new Team("openpartiesandclaims:" + call(party, "getId"),
                    (UUID) call(call(party, "getOwner"), "getUUID"));
        });
    }

    private TeamColors() {
    }

    public record Team(String key, UUID leader) {

        public Team {
            Objects.requireNonNull(leader, "Team leader");
            if (key == null || key.isBlank()) {
                throw new IllegalArgumentException("Team key must be nonempty");
            }
        }
    }

    @FunctionalInterface
    public interface Provider {

        Team find(MinecraftServer server, UUID member) throws ReflectiveOperationException;
    }

    private record Adapter(String id, Provider provider) {
    }

    private record MethodKey(String name, Class<?> argument) {
    }

    /**
     * Mod availability is fixed after loading; defer the first check until a server actually resolves teams.
     */
    private static void registerOptionalProvider(String id, Provider provider) {
        registerProvider(id, new Provider() {

            private Boolean loaded;

            @Override
            public Team find(MinecraftServer server, UUID member) throws ReflectiveOperationException {
                if (loaded == null) {
                    loaded = ModList.get().isLoaded(id);
                }
                return loaded ? provider.find(server, member) : null;
            }
        });
    }

    /**
     * Cache reflective lookup, while fetching the live API/manager again for each server.
     */
    private static final class ApiEntryPoint {

        private final String className;

        private final String methodName;

        private final Class<?> argumentType;

        private Method method;

        private ApiEntryPoint(String className, String methodName, Class<?> argumentType) {
            this.className = className;
            this.methodName = methodName;
            this.argumentType = argumentType;
        }

        private Object invoke(Object argument) throws ReflectiveOperationException {
            if (method == null) {
                method = Class.forName(className)
                        .getMethod(methodName, argumentType == null ? new Class<?>[0] : new Class<?>[]{argumentType});
            }
            return argumentType == null ? method.invoke(null) : method.invoke(null, argument);
        }
    }

    /**
     * Other team mods can register a provider during common setup on the mod work queue. First match wins.
     */
    public static void registerProvider(String id, Provider provider) {
        PROVIDERS.add(new Adapter(id, provider));
    }

    public static Team resolve(MinecraftServer server, UUID member, PlayerColors colors) {
        for (var adapter : PROVIDERS) {
            try {
                var team = adapter.provider().find(server, member);
                if (team != null) {
                    return team;
                }
            } catch (ReflectiveOperationException | LinkageError | RuntimeException error) {
                if (WARNED.add(adapter.id())) {
                    LOGGER.warn("Cannot resolve Player Blocks colors through {}", adapter.id(), error);
                }
            }
        }
        var player = server.getPlayerList().getPlayer(member);
        var profile = player == null && server.getProfileCache() != null ? server.getProfileCache().get(member)
                .orElse(null) : null;
        String name = player != null ? player.getGameProfile().getName() : profile != null ? profile.getName() : null;
        var team = name == null ? null : server.getScoreboard().getPlayersTeam(name);
        UUID leader = team == null ? null : colors.scoreboardLeader(team.getName());
        return leader == null ? null : new Team("scoreboard:" + team.getName(), leader);
    }

    private static Object call(Object target, String name) throws ReflectiveOperationException {
        return method(target, name, null).invoke(target);
    }

    private static Object call(Object target, String name, Class<?> type,
                               Object value) throws ReflectiveOperationException {
        return method(target, name, type).invoke(target, value);
    }

    private static Method method(Object target, String name, Class<?> argument) throws NoSuchMethodException {
        var key = new MethodKey(name, argument);
        var cache = METHODS.get(target.getClass());
        var cached = cache.get(key);
        if (cached != null) {
            return cached;
        }
        var method = target.getClass().getMethod(name, argument == null ? new Class<?>[0] : new Class<?>[]{argument});
        if (!method.canAccess(target)) {
            method.trySetAccessible();
        }
        cache.put(key, method);
        return method;
    }
}
