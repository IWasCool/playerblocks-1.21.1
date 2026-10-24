package com.ourcreation.playerblocks.compat;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.event.extent.EditSessionEvent;
import com.sk89q.worldedit.extent.AbstractDelegateExtent;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.neoforge.NeoForgeAdapter;
import com.sk89q.worldedit.neoforge.internal.NBTConverter;
import com.sk89q.worldedit.util.concurrency.LazyReference;
import com.sk89q.worldedit.util.eventbus.Subscribe;
import com.sk89q.worldedit.world.block.BlockStateHolder;
import net.minecraft.nbt.CompoundTag;

/**
 * An optional WorldEdit extent runs at actual world placement, including buffered pastes.
 */
public final class WorldEditColors {

    private WorldEditColors() {
    }

    public static void register() {
        WorldEdit.getInstance().getEventBus().register(new WorldEditColors());
    }

    @Subscribe
    public void session(EditSessionEvent event) {
        if (event.getStage() != EditSession.Stage.BEFORE_CHANGE || event.getActor() == null ||
                !event.getActor().isPlayer() || event.getWorld() == null) {
            return;
        }
        var placer = event.getActor().getUniqueId();
        var level = NeoForgeAdapter.adapt(event.getWorld());
        event.setExtent(new AbstractDelegateExtent(event.getExtent()) {

            @Override
            public <T extends BlockStateHolder<T>> boolean setBlock(BlockVector3 position,
                                                                    T block) throws WorldEditException {
                var original = block.toBaseBlock();
                var reference = original.getNbtReference();
                CompoundTag data = reference == null ? null : NBTConverter.toNative(reference.getValue());
                var prepared =
                        SchematicColors.prepare(level, NeoForgeAdapter.adapt(block.toImmutableState()), data, placer);
                var state = NeoForgeAdapter.adapt(prepared.state());
                var replacement = prepared.data() == null ? state.toBaseBlock() : state.toBaseBlock(
                        LazyReference.computed(NBTConverter.fromNative(prepared.data())));
                boolean changed = super.setBlock(position, replacement);
                if (changed) {
                    SchematicColors.finish(level, NeoForgeAdapter.toBlockPos(position), placer);
                }
                return changed;
            }
        });
    }
}
