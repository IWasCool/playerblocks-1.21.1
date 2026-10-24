package com.ourcreation.playerblocks.mixin.create;

import com.ourcreation.playerblocks.compat.SchematicColors;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.simibubi.create.content.schematics.cannon.ConfigureSchematicannonPacket", remap = false)
public abstract class CreateConfigureSchematicannonMixin {

    @Inject(method = "handle", at = @At("HEAD"))
    private void playerBlocks$operator(ServerPlayer player, CallbackInfo ci) throws ReflectiveOperationException {
        if (player == null || !player.containerMenu.getClass().getName()
                .equals("com.simibubi.create.content.schematics.cannon.SchematicannonMenu")) {
            return;
        }
        var option = (Enum<?>) getClass().getMethod("option").invoke(this);
        if (!option.name().equals("PLAY")) {
            return;
        }
        var cannon = player.containerMenu.getClass().getField("contentHolder").get(player.containerMenu);
        if (cannon instanceof SchematicColors.CannonOwner owner) {
            owner.playerBlocks$setPlacer(player.getUUID());
        }
    }
}
