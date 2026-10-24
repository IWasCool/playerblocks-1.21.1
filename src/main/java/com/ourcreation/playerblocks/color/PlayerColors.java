package com.ourcreation.playerblocks.color;

import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * World-specific hex overrides; the default stays stable across worlds and username changes.
 */
public final class PlayerColors extends SavedData {

    private static final Factory<PlayerColors> FACTORY = new Factory<>(PlayerColors::new, PlayerColors::load);

    private final Map<UUID, Integer> overrides = new HashMap<>();

    private final Map<UUID, Integer> hexChanges = new HashMap<>();

    private final Map<UUID, HexRequest> hexRequests = new HashMap<>();

    public static final int MAX_HEX_CHANGES = 2;

    public static final long HEX_CONFIRMATION_TICKS = 20 * 60 * 5;

    private final Map<UUID, Integer> slots = new HashMap<>();

    private final BitSet usedSlots = new BitSet(MAX_SLOT + 1);

    private final Map<String, Integer> teamOverrides = new HashMap<>();

    private final Map<String, UUID> scoreboardLeaders = new HashMap<>();

    private final Map<UUID, TeamColors.Team> teams = new HashMap<>();

    private final Map<UUID, Integer> teamResolvedTick = new HashMap<>();

    private CompoundTag cachedPalette;

    private boolean paletteDirty = true;

    private CompoundTag sentPalette;

    private long colorRevision;

    public static final int MAX_SLOT = 4095;

    public record HexRequest(UUID token, Integer rgb, long expiresAt) {
    }

    public int hexChanges(UUID owner) {
        return hexChanges.getOrDefault(owner, 0);
    }

    public HexRequest pendingHex(UUID owner) {
        return hexRequests.get(owner);
    }

    public HexRequest requestHex(UUID owner, Integer rgb, long now) {
        hexRequests.values().removeIf(request -> request.expiresAt() <= now);
        var request = new HexRequest(UUID.randomUUID(), rgb, now + HEX_CONFIRMATION_TICKS);
        hexRequests.put(owner, request);
        return request;
    }

    public void clearHexRequest(UUID owner) {
        hexRequests.remove(owner);
    }

    public void applyPersonalChange(UUID owner, Integer rgb, boolean limited) {
        if (limited) {
            if (hexChanges(owner) >= MAX_HEX_CHANGES) {
                throw new IllegalStateException("Personal color change limit reached");
            }
            hexChanges.put(owner, hexChanges(owner) + 1);
            setDirty();
        }
        clearHexRequest(owner);
        if (rgb == null) {
            reset(owner);
        } else {
            set(owner, rgb);
        }
    }

    public int slotCount() {
        return slots.size();
    }

    public int slot(UUID owner) {
        Integer existing = slots.get(owner);
        if (existing != null) {
            return existing;
        }
        if (slots.size() >= MAX_SLOT) {
            throw new IllegalStateException("Player concrete supports up to 4095 owners per world");
        }
        int slot = usedSlots.nextClearBit(1);
        slots.put(owner, slot);
        usedSlots.set(slot);
        paletteDirty = true;
        setDirty();
        return slot;
    }

    /**
     * Changes when cached entity tints may need refreshing; never persisted across worlds.
     */
    public long colorRevision() {
        return colorRevision;
    }

    private void invalidateColors() {
        paletteDirty = true;
        colorRevision++;
    }

    private CompoundTag currentPalette() {
        if (paletteDirty || cachedPalette == null) {
            cachedPalette = new CompoundTag();
            slots.forEach((owner, slot) -> cachedPalette.putInt(Integer.toString(slot), color(owner)));
            paletteDirty = false;
        }
        return cachedPalette;
    }

    public CompoundTag palette() {
        return currentPalette().copy();
    }

    public static PlayerColors get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, "create_new_world_core_player_colors");
    }

    public static int defaultColor(UUID owner) {
        int raw = owner.hashCode() & 0xFFFFFF;
        int red = raw >> 16, green = (raw >> 8) & 255, blue = raw & 255;
        int maximum = Math.max(red, Math.max(green, blue));
        if (maximum == 0) {
            return 0xE6E6E6;
        }
        return (Math.round(red * 230F / maximum) << 16) | (Math.round(green * 230F / maximum) << 8) |
                Math.round(blue * 230F / maximum);
    }

    public int personalColor(UUID owner) {
        Integer override = overrides.get(owner);
        return override != null ? override : defaultColor(owner);
    }

    public int color(UUID owner) {
        var team = teams.get(owner);
        if (team == null) {
            return personalColor(owner);
        }
        Integer override = teamOverrides.get(team.key());
        return override != null ? override : personalColor(team.leader());
    }

    public void setTeam(String key, int rgb) {
        rgb &= 0xFFFFFF;
        Integer previous = teamOverrides.put(key, rgb);
        if (previous != null && previous == rgb) {
            return;
        }
        invalidateColors();
        setDirty();
    }

    public void resetTeam(String key) {
        if (teamOverrides.remove(key) == null) {
            return;
        }
        invalidateColors();
        setDirty();
    }

    public UUID scoreboardLeader(String name) {
        return scoreboardLeaders.get(name);
    }

    public void setScoreboardLeader(String name, UUID leader) {
        if (!Objects.equals(scoreboardLeaders.put(name, leader), leader)) {
            setDirty();
        }
    }

    public void refreshTeam(MinecraftServer server, UUID owner) {
        var team = TeamColors.resolve(server, owner, this);
        var previous = teams.get(owner);
        if (team == null) {
            teams.remove(owner);
        } else {
            teams.put(owner, team);
        }
        if (!Objects.equals(previous, team)) {
            invalidateColors();
        }
        teamResolvedTick.put(owner, server.getTickCount());
    }

    /**
     * Bulk placement resolves each owner once per server tick, not once per block.
     */
    public void ensureTeam(MinecraftServer server, UUID owner) {
        if (teamResolvedTick.getOrDefault(owner, Integer.MIN_VALUE) != server.getTickCount()) {
            refreshTeam(server, owner);
        }
    }

    public void refreshTeams(MinecraftServer server) {
        var owners = new HashSet<>(slots.keySet());
        server.getPlayerList().getPlayers().forEach(player -> owners.add(player.getUUID()));
        for (UUID owner : owners) {
            refreshTeam(server, owner);
        }
    }

    public boolean paletteChanged() {
        if (!paletteDirty && cachedPalette == sentPalette) {
            return false;
        }
        var current = currentPalette();
        if (current.equals(sentPalette)) {
            sentPalette = cachedPalette;
            return false;
        }
        sentPalette = cachedPalette;
        return true;
    }

    public void set(UUID owner, int rgb) {
        rgb &= 0xFFFFFF;
        Integer previous = overrides.put(owner, rgb);
        if (previous != null && previous == rgb) {
            return;
        }
        invalidateColors();
        setDirty();
    }

    public void reset(UUID owner) {
        if (overrides.remove(owner) == null) {
            return;
        }
        invalidateColors();
        setDirty();
    }

    public static PlayerColors load(CompoundTag tag, HolderLookup.Provider registries) {
        var result = new PlayerColors();
        var colors = tag.getCompound("colors");
        var uses = tag.getCompound("hexChanges");
        for (String owner : uses.getAllKeys()) {
            try {
                result.hexChanges.put(UUID.fromString(owner),
                        Math.max(0, Math.min(MAX_HEX_CHANGES, uses.getInt(owner))));
            } catch (IllegalArgumentException ignored) {
            }
        }
        var savedSlots = tag.getCompound("slots");
        var teamColors = tag.getCompound("teamColors");
        for (String key : teamColors.getAllKeys()) {
            result.teamOverrides.put(key, teamColors.getInt(key) & 0xFFFFFF);
        }
        var leaders = tag.getCompound("scoreboardLeaders");
        for (String key : leaders.getAllKeys()) {
            if (leaders.hasUUID(key)) {
                result.scoreboardLeaders.put(key, leaders.getUUID(key));
            }
        }
        for (String owner : savedSlots.getAllKeys()) {
            int slot = savedSlots.getInt(owner);
            try {
                UUID uuid = UUID.fromString(owner);
                if (slot > 0 && slot <= MAX_SLOT && !result.usedSlots.get(slot) && !result.slots.containsKey(uuid)) {
                    result.slots.put(uuid, slot);
                    result.usedSlots.set(slot);
                }
            } catch (IllegalArgumentException ignored) {
            }
        }
        for (String owner : colors.getAllKeys()) {
            try {
                result.overrides.put(UUID.fromString(owner), colors.getInt(owner) & 0xFFFFFF);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return result;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        var uses = new CompoundTag();
        hexChanges.forEach((owner, count) -> uses.putInt(owner.toString(), count));
        tag.put("hexChanges", uses);
        var colors = new CompoundTag();
        overrides.forEach((owner, rgb) -> colors.putInt(owner.toString(), rgb));
        tag.put("colors", colors);
        var savedSlots = new CompoundTag();
        slots.forEach((owner, slot) -> savedSlots.putInt(owner.toString(), slot));
        tag.put("slots", savedSlots);
        var teamColors = new CompoundTag();
        teamOverrides.forEach(teamColors::putInt);
        tag.put("teamColors", teamColors);
        var leaders = new CompoundTag();
        scoreboardLeaders.forEach(leaders::putUUID);
        tag.put("scoreboardLeaders", leaders);
        return tag;
    }
}
