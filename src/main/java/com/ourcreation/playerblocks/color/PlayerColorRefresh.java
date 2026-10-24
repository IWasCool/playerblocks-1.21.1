package com.ourcreation.playerblocks.color;

import com.ourcreation.playerblocks.block.PlayerConcreteBlock;
import com.ourcreation.playerblocks.block.entity.PlayerColoredEntity;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Refreshes passive owned shapes once per second without a per-block entity ticker.
 */
public final class PlayerColorRefresh {

    private static final int INTERVAL = 20;

    // Neither the levels nor their entities are kept alive by this scheduler.
    private static final Map<ServerLevel, RefreshQueue> LEVELS = new WeakHashMap<>();

    private PlayerColorRefresh() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(PlayerColorRefresh::tick);
    }

    public static void track(BlockEntity entity) {
        if (!(entity.getLevel() instanceof ServerLevel level) || !needsRefresh(entity)) {
            return;
        }
        var queue = LEVELS.computeIfAbsent(level, ignored -> new RefreshQueue());
        if (queue.tracked.put(entity, Boolean.TRUE) == null) {
            queue.buckets.get(Math.floorMod(entity.getBlockPos().asLong(), INTERVAL)).add(new WeakReference<>(entity));
        }
    }

    private static boolean needsRefresh(BlockEntity entity) {
        return !entity.isRemoved() && entity instanceof PlayerColoredEntity colored && colored.owner() != null &&
                (!entity.getBlockState().hasProperty(PlayerConcreteBlock.COLOR_ID) ||
                        entity.getBlockState().getValue(PlayerConcreteBlock.COLOR_ID) == 0);
    }

    private static void tick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !level.tickRateManager().runsNormally()) {
            return;
        }
        var queue = LEVELS.get(level);
        if (queue == null) {
            return;
        }
        // Access also drains WeakHashMap's reference queue after entities are collected.
        if (queue.tracked.isEmpty()) {
            LEVELS.remove(level);
            return;
        }
        var bucket = queue.buckets.get(Math.floorMod(level.getGameTime(), INTERVAL));
        for (int index = 0; index < bucket.size(); ) {
            var entity = bucket.get(index).get();
            boolean discard = entity == null || entity.getLevel() != level || !needsRefresh(entity);
            if (!discard) {
                var pos = entity.getBlockPos();
                if (level.shouldTickBlocksAt(pos) && level.getWorldBorder().isWithinBounds(pos) &&
                        level.areEntitiesLoaded(ChunkPos.asLong(pos))) {
                    var chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
                    if (chunk != null && chunk.getFullStatus().isOrAfter(FullChunkStatus.BLOCK_TICKING)) {
                        discard = chunk.getBlockEntities().get(pos) != entity;
                        if (!discard) {
                            ((PlayerColoredEntity) entity).refresh();
                        }
                    }
                }
            }
            if (discard) {
                // Order is irrelevant; swapping bounds bulk unload/replacement cleanup to O(n).
                int last = bucket.size() - 1;
                if (index != last) {
                    bucket.set(index, bucket.get(last));
                }
                bucket.remove(last);
                if (entity != null) {
                    queue.tracked.remove(entity);
                }
            } else {
                index++;
            }
        }
    }

    private static final class RefreshQueue {

        final Map<BlockEntity, Boolean> tracked = new WeakHashMap<>();

        final List<List<WeakReference<BlockEntity>>> buckets = new ArrayList<>(INTERVAL);

        RefreshQueue() {
            for (int index = 0; index < INTERVAL; index++) {
                buckets.add(new ArrayList<>());
            }
        }
    }
}
