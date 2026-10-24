package com.ourcreation.playerblocks;

import com.mojang.authlib.GameProfile;

import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;

/**
 * Server-side test players must not request optional mods' client-only config.
 */
final class TestPlayers {

    private TestPlayers() {
    }

    static Player create(GameTestHelper helper, GameType mode) {
        return new Player(helper.getLevel(), BlockPos.ZERO, 0, new GameProfile(UUID.randomUUID(), "pb-test-player")) {

            @Override
            public boolean isSpectator() {
                return mode == GameType.SPECTATOR;
            }

            @Override
            public boolean isCreative() {
                return mode.isCreative();
            }

            @Override
            public boolean isLocalPlayer() {
                return false;
            }
        };
    }
}
