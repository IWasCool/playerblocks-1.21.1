package com.ourcreation.playerblocks.block.entity;

import java.util.UUID;

/**
 * Shared ownership contract for player-colored building blocks and schematic integrations.
 */
public interface PlayerColoredEntity {

    UUID owner();

    int color();

    void link(UUID owner);

    void refresh();
}
