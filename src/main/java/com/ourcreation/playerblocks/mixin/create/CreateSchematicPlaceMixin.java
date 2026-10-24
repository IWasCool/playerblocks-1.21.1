package com.ourcreation.playerblocks.mixin.create;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.ourcreation.playerblocks.compat.SchematicColors;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

@Pseudo
@Mixin(targets = "com.simibubi.create.content.schematics.packet.SchematicPlacePacket", remap = false)
public abstract class CreateSchematicPlaceMixin {

    @WrapMethod(method = "handle")
    private void playerBlocks$placer(ServerPlayer player, Operation<Void> original) {
        if (player == null) {
            original.call(player);
            return;
        }
        SchematicColors.withPlacer(player.getUUID(), () -> {
            original.call(player);
            return null;
        });
    }
}
