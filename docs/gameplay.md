# Gameplay guide

Player Blocks gives your builds a personal or team color. Blocks remember who placed them and
follow that player's color, including the team color when the owner belongs to a supported team.

## Choose your color

Use a six-digit hex color, with or without the `#`. For example:

```text
/hex Alex #27B6D9
```

Replace `Alex` with your Minecraft username. Your placed Player Blocks update automatically.

| Command | Use |
| --- | --- |
| `/hex <username> #RRGGBB` | Set your personal color. Operators can also change another player's color. |
| `/playercolor` | Show the color your blocks currently use. |
| `/playercolor hex #RRGGBB` | Set your own color without entering a username. |
| `/playercolor reset` | Restore your original personal color. |

Players have **two confirmed personal color changes per world**. Setting or resetting your personal
color uses one change after you click **Confirm** in chat. **Cancel**, invalid input, and expired
prompts do not use a change. The allowance persists after leaving the server or restarting the world.
Operators have no personal color-change limit; changing another player's color as an operator
does not use that player's allowance.

## Team colors

FTB Teams parties and Open Parties and Claims parties use their leader's personal color by default,
including while the leader is offline. A separate team color overrides that default for every member.
Each member's personal color is saved for when they leave the team.

| Command | Use |
| --- | --- |
| `/teamcolor` | Show your team's current color and leader. |
| `/teamcolor hex #RRGGBB` | Set a separate color for your team. |
| `/teamcolor reset` | Remove the override and follow the leader's personal color again. |

Only the leader or an operator **in that team** can set or reset its color.
When several team systems are installed, FTB Teams parties take priority, then Open Parties and Claims,
then vanilla scoreboard teams. FTB personal and server teams without a party leader do not supply a color.
These commands change Player Blocks colors; map and chat colors belong to their respective mods.

Vanilla scoreboard teams need an operator to assign a leader who is already a member:

```text
/teamcolor leader <scoreboard-team> <username>
```

## Crafting

Start with **Player Dye**, made by placing any one vanilla dye in a crafting slot.
It takes your personal or team color. Use it with vanilla materials to craft colored blocks,
or use a stonecutter to turn Player Block into building shapes.

| Recipe | Result |
| --- | --- |
| Any vanilla dye | Player Dye |
| White concrete + Player Dye | Player Block |
| A compatible vanilla source block + Player Dye | Its Player Blocks counterpart |
| Player Dye surrounded by eight glass blocks | Eight Player Glass |
| Six Player Glass | Sixteen Player Glass Panes |
| Any vanilla shulker box + Player Dye | Player Shulker Box, retaining contents, name, and components |
| Four Player Dye + an empty bucket | Player Liquid Bucket |

A stonecutter turns one Player Block into two slabs or one of the other available shapes.
Matching chiseled and tiled full blocks also cut into their own slabs, stairs, and walls.
Player Terracotta can be stonecut into any of the 16 glazed patterns.
All block items, Player Dye, and the liquid bucket appear in the **Player Blocks** creative tab.

## Blocks and ownership

- Slabs, stairs, walls, fences, gates, and pillars retain their familiar placement and connection behavior.
- Player Door and Player Trapdoor open by hand or redstone. Player Iron Door requires redstone.
- Stone buttons activate for one second. Wooden buttons activate for 1.5 seconds and can be triggered by arrows.
- The normal pressure plate detects living entities. Light and heavy weighted plates use their vanilla signal behavior.
- Glass and panes are translucent. Glazed terracotta keeps each original pattern and its four-way placement.
- Beds retain sleeping and spawn behavior; their pillows and wooden frames keep their original appearance.
- Banners retain loom patterns. The base cloth follows your color while the patterns retain their dye colors.
- Chests retain storage, double-chest behavior, waterlogging, and hopper access. Colors do not restrict access.
- Shulker boxes retain their contents when converted, placed, picked up, or broken, and cannot be nested.
- Candles stack from one to four and support lighting and waterlogging; a candle-on-cake variant is not included.

Ownership and colors persist through saving and reloading. Both halves of doors, beds, and double chests
share ownership. Shift-right-click an unowned **Player Block** to claim it.

## Create, Copycats+, FramedBlocks, and WorldEdit

**Player Block**, **Player Chiseled**, and **Player Tiles** retain their color when used as supported
copycat or FramedBlocks materials. Beds, banners, chests, shulkers, and candles are not supported FramedBlocks camos.

Create's instant schematic placement and player-run WorldEdit operations assign placed Player Blocks
to the player doing the placement. Their personal or team color applies, and storage contents are preserved.
Copied copycat and framed materials also adopt that player's color.

The **Schematicannon** uses the player who presses **Start/Play**. This owner is saved for delayed shots
and server restarts. Press Start/Play again on a cannon from an older version to assign its operator.
WorldEdit console operations do not assign a player owner.

JEI includes the shulker conversion recipe alongside the regular recipes.

## Player Liquid

Player Liquid flows like water and supports swimming, drowning, extinguishing, boats, and source renewal
when the world's water source-conversion rule allows it. Bucket placement links it to the placer;
flowing liquid inherits that owner and follows their personal or team color.

A bucket holds **1,000 mB**. Create adds these optional processing recipes:

| Process | Recipe |
| --- | --- |
| Mechanical Mixer | One Player Dye produces 250 mB of Player Liquid. |
| Spout | 250 mB of Player Liquid converts a supported vanilla source item into its Player Blocks counterpart. |
| Bucket filling / emptying | Transfers 1,000 mB. |

Spout conversions preserve shulker contents and components. Tank and portable fluid previews show
the viewing player's color; placed liquid uses its saved owner's color.

With **Thirst Was Reclaimed**, dispenser pickup keeps the Player Liquid bucket, while vanilla water
pickup retains Thirst's purity data.

## Current limits

- Personal color changes are limited to two per world for non-operators.
- Palette-based blocks and copied materials support up to 4,095 owners per world.
- Team support covers the systems listed above; another team system needs an adapter or a supported scoreboard setup.

Block names and command feedback cover all 137 languages selectable in Minecraft 1.21.1.
Translations use Minecraft's terminology where available; wording improvements are welcome.

For installation and build instructions, see the [README](../README.md).
