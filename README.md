# Player Blocks

Player-colored building blocks, dye, and water-like liquid for **Minecraft 1.21.1 / NeoForge**.
Current version: **0.0.2**. Install the mod on both the client and server.

## Features

- 52 colored block items, including building shapes, glazed terracotta, beds, banners, chests, and shulker boxes.
- Personal hex colors and team-leader colors, with persistent ownership and automatic recoloring.
- Player Dye, crafting and stonecutting recipes, and Player Liquid with water-style flow.
- Optional integrations with Create, Copycats+, FramedBlocks, WorldEdit, JEI, FTB Teams, and Open Parties and Claims.
- Names and command feedback in all 137 languages selectable in Minecraft 1.21.1.

Use `/hex <username> #RRGGBB` to choose a personal color. Non-operators get two confirmed
personal color changes per world. `/playercolor` shows your color; `/teamcolor` shows your team's color.
See the [gameplay guide](docs/gameplay.md) for recipes, permissions, integrations, and limits.

## Build and test

Install **JDK 21**, then run from the project root:

```powershell
./gradlew.bat runGameTestServer build
```

On Linux or macOS, use `./gradlew runGameTestServer build`.
The release mod is written to `build/libs/player_blocks-0.0.2.jar`.
The `-sources.jar` contains source code and is for developers.

Gradle downloads the optional WorldEdit and JEI APIs from their Maven repositories.
No files in `libs/`, local Minecraft installation, or sibling project are needed to build.
The first build requires an internet connection. See [Development](docs/development.md) for IDE setup,
client previews, compatibility tests, and asset tools.

## Project layout

| Path | Contents |
| --- | --- |
| `src/main/java/com/ourcreation/playerblocks/` | Mod code, grouped by responsibility |
| `src/main/resources/` | Models, textures, recipes, tags, translations, and mixin configuration |
| `src/main/templates/` | NeoForge metadata expanded by Gradle |
| `src/gametest/` | Server tests and client visual checks; excluded from the release mod |
| `gradle/` | Wrapper, verification tasks, and test configuration |
| `tools/` | Asset, localization, and local development tools |
| `docs/` | Gameplay, development, localization, and historical test evidence |

## Documentation

- [Gameplay guide](docs/gameplay.md)
- [Development and GitHub setup](docs/development.md)
- [Localization](docs/localization.md)
- [Release audit](docs/release-audit.md)

Registry IDs, ownership tags, and saved-data names are unchanged by the source reorganization.
The mod retains its **All Rights Reserved** license; see [LICENSE](LICENSE).
The original NeoForge MDK template notice is retained [here](docs/licenses/NeoForge-MDK.txt).
