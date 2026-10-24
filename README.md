# Player Blocks

**Build with your own colors.**

Player Blocks adds over 50 building and decorative blocks that match your personal or team hex color.
Choose a color, place your blocks, and watch them update whenever your color changes.

For **Minecraft 1.21.1 on NeoForge**. Install the mod on both the client and server.

## What's included

- **Building shapes:** blocks, slabs, stairs, walls, fences, gates, pillars, and chiseled and tiled variants.
- **Decorative details:** glass, panes, doors, trapdoors, buttons, pressure plates, carpet, and candles.
- **More materials:** wool, terracotta, purpur, stone bricks, and all 16 glazed terracotta patterns.
- **Matching furnishings:** beds, banners, chests, and shulker boxes with their familiar Minecraft behavior.
- **Player Dye and Player Liquid:** one dye ingredient for the block set, plus a matching liquid that flows like water.

Craft Player Dye from any vanilla dye, then combine it with vanilla blocks or use a stonecutter to make shapes.
Shulker box conversions preserve their contents, names, and components.

## Personal and team colors

On your own, blocks use your personal color. In a supported team, they use the team leader's color
unless the team has chosen a separate color. Your personal color is saved for when you leave the team.

| Command | What it does |
| --- | --- |
| `/hex <username> #RRGGBB` | Set your personal color. Replace `<username>` with your Minecraft username. |
| `/playercolor` | Show your current block color. |
| `/teamcolor` | Show your team's color and leader. |
| `/teamcolor hex #RRGGBB` | Set a separate color for your team. |
| `/teamcolor reset` | Return your team to its leader's personal color. |

For example, `/hex Alex #27B6D9` gives Alex a turquoise personal color.

Players get **two confirmed personal color changes per world**. The chat prompt lets you confirm or cancel
before a change is used. Operators have no personal color-change limit and can change other players' colors.
Only the team leader or an operator in that team can change its color.

## Compatibility

All integrations are optional.

| Mod or system | Support |
| --- | --- |
| FTB Teams / Open Parties and Claims | Party members share their leader's block color, with optional team overrides. |
| Vanilla scoreboard teams | Team colors work after an operator assigns a leader. |
| Create copycats / Copycats+ / FramedBlocks | Player Block, Player Chiseled, and Player Tiles retain their colors as supported materials. |
| Create schematics / WorldEdit | Placed Player Blocks follow the player placing the schematic or running the edit. |
| Create recipes | Mix Player Dye into Player Liquid and use a Spout to convert vanilla blocks. |
| JEI | Browse recipes, including the shulker conversion that keeps its contents. |

See the [gameplay guide](docs/gameplay.md) for crafting, team setup, liquid behavior, and limits.
Block names and command feedback support all 137 languages selectable in Minecraft 1.21.1.

## Installation

1. Use **Minecraft 1.21.1** with **NeoForge 21.1.251 or newer**.
2. Download the `player-blocks` artifact from a successful [GitHub Actions build](https://github.com/IWasCool/playerblocks-1.21.1/actions/workflows/build.yml) and extract the ZIP, or build from source below. GitHub requires sign-in to download build artifacts.
3. Put `player_blocks-0.0.2.jar` in the `mods` folder on both the client and server, then launch the game.

The `-sources.jar` is source code for developers. Create and the other integration mods are optional.

## Build from source

Install **JDK 21**, then run from the project root:

```powershell
./gradlew.bat build
```

On Linux or macOS, run `./gradlew build`. The mod JAR is written to
`build/libs/player_blocks-0.0.2.jar`.
Gradle downloads the build dependencies automatically; the first build needs an internet connection.
