package com.ourcreation.playerblocks.compat;

import com.ourcreation.playerblocks.block.PlayerChest;
import com.ourcreation.playerblocks.block.PlayerConcreteBlock;
import com.ourcreation.playerblocks.block.PlayerShapeBlock;
import com.ourcreation.playerblocks.block.entity.PlayerChestEntity;
import com.ourcreation.playerblocks.block.entity.PlayerColoredEntity;
import com.ourcreation.playerblocks.color.PlayerColors;
import com.ourcreation.playerblocks.fluid.PlayerLiquidBlock;
import com.ourcreation.playerblocks.network.PlayerColorNetwork;

import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * Explicit placer context; never guesses ownership from nearby players or schematic authors.
 */
public final class SchematicColors {

    public static final String PLACER = "player_blocks:SchematicPlacer";

    private static final ThreadLocal<UUID> CURRENT = new ThreadLocal<>();

    private static final Set<String> PALETTE_BLOCK_NAMES =
            Set.of("player_blocks:player_concrete", "player_blocks:player_concrete_chiseled",
                    "player_blocks:player_concrete_tiles");

    private SchematicColors() {
    }

    public interface CannonOwner {

        UUID playerBlocks$getPlacer();

        void playerBlocks$setPlacer(UUID placer);
    }

    public record Placement(BlockState state, CompoundTag data) {
    }

    public static UUID currentPlacer() {
        return CURRENT.get();
    }

    /**
     * Synchronous schematic integrations can wrap their paste with this method. Always restores nested contexts.
     */
    public static <T> T withPlacer(UUID placer, Supplier<T> action) {
        UUID previous = CURRENT.get();
        CURRENT.set(placer);
        try {
            return action.get();
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }

    /**
     * Stored on each cannon shot, so subsequent users cannot take ownership of shots already in flight.
     */
    public static CompoundTag markShot(CompoundTag source, UUID placer) {
        if (placer == null) {
            return source;
        }
        var data = source == null ? new CompoundTag() : source.copy();
        data.putUUID(PLACER, placer);
        return data;
    }

    public static UUID placer(CompoundTag data) {
        UUID current = currentPlacer();
        return current != null ? current : data != null && data.hasUUID(PLACER) ? data.getUUID(PLACER) : null;
    }

    public static Placement prepare(Level level, BlockState state, CompoundTag source, UUID placer) {
        if (placer == null || !(level instanceof ServerLevel server)) {
            return new Placement(state, source);
        }
        boolean concrete =
                state.getBlock() instanceof PlayerConcreteBlock || state.getBlock() instanceof PlayerShapeBlock ||
                        state.getBlock() instanceof PlayerLiquidBlock;
        if (!concrete && (source == null || !hasPaletteData(source))) {
            if (source != null && source.contains(PLACER)) {
                var data = source.copy();
                data.remove(PLACER);
                return new Placement(state, data.isEmpty() ? null : data);
            }
            return new Placement(state, source);
        }
        var colors = PlayerColors.get(server.getServer());
        int slot = colors.slot(placer);
        // Resolve this particular placer immediately, including offline cannon operators.
        colors.ensureTeam(server.getServer(), placer);
        var data = source == null ? new CompoundTag() : source.copy();
        data.remove(PLACER);
        remapMaterials(data, slot);
        if (concrete) {
            if (state.hasProperty(PlayerConcreteBlock.COLOR_ID)) {
                state = state.setValue(PlayerConcreteBlock.COLOR_ID, slot);
            }
            data.putUUID("owner", placer);
            data.putInt("color", colors.color(placer));
        }
        if (colors.paletteChanged()) {
            PlayerColorNetwork.broadcastPalette(server.getServer());
        }
        return new Placement(state, !concrete && data.isEmpty() ? null : data);
    }

    /**
     * Remap only Player Blocks material states and item components, leaving unrelated block data intact.
     */
    private static void remapMaterials(CompoundTag data, int slot) {
        if (PALETTE_BLOCK_NAMES.contains(data.getString("Name"))) {
            var properties = data.getCompound("Properties");
            properties.putString("color_id", Integer.toString(slot));
            data.put("Properties", properties);
        }
        if (data.getString("id").startsWith("player_blocks:player_") &&
                (data.contains("count") || data.contains("components"))) {
            var components = data.getCompound("components");
            var properties = components.getCompound("minecraft:block_state");
            properties.putString("color_id", Integer.toString(slot));
            components.put("minecraft:block_state", properties);
            data.put("components", components);
        }
        for (String key : data.getAllKeys()) {
            Tag child = data.get(key);
            if (child instanceof CompoundTag compound) {
                remapMaterials(compound, slot);
            } else if (child instanceof ListTag list) {
                remapList(list, slot);
            }
        }
    }

    private static void remapList(ListTag list, int slot) {
        for (Tag child : list) {
            if (child instanceof CompoundTag compound) {
                remapMaterials(compound, slot);
            } else if (child instanceof ListTag nested) {
                remapList(nested, slot);
            }
        }
    }

    private static boolean hasPaletteData(Tag tag) {
        if (tag instanceof CompoundTag data) {
            if (PALETTE_BLOCK_NAMES.contains(data.getString("Name")) ||
                    PALETTE_BLOCK_NAMES.contains(data.getString("id"))) {
                return true;
            }
            for (String key : data.getAllKeys()) {
                if (hasPaletteData(data.get(key))) {
                    return true;
                }
            }
        } else if (tag instanceof ListTag list) {
            for (Tag child : list) {
                if (hasPaletteData(child)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static void finish(Level level, BlockPos pos, UUID placer) {
        if (placer == null || !(level instanceof ServerLevel)) {
            return;
        }
        if (level.getBlockEntity(pos) instanceof PlayerColoredEntity entity) {
            entity.link(placer);
            var state = level.getBlockState(pos);
            if (state.getBlock() instanceof PlayerChest && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
                var other = pos.relative(ChestBlock.getConnectedDirection(state));
                if (level.getBlockEntity(other) instanceof PlayerChestEntity paired) {
                    paired.link(placer);
                }
            }
            if (state.hasProperty(DoorBlock.HALF)) {
                var other = state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER ? pos.above() : pos.below();
                if (level.getBlockState(other).is(state.getBlock()) &&
                        level.getBlockEntity(other) instanceof PlayerColoredEntity paired) {
                    paired.link(placer);
                }
            }
            if (state.hasProperty(BedBlock.PART)) {
                var direction = state.getValue(BedBlock.FACING);
                var other = pos.relative(
                        state.getValue(BedBlock.PART) == BedPart.FOOT ? direction : direction.getOpposite());
                if (level.getBlockState(other).is(state.getBlock()) &&
                        level.getBlockEntity(other) instanceof PlayerColoredEntity paired) {
                    paired.link(placer);
                }
            }
        }
    }
}
