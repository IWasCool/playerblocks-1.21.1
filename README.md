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

## Build

Install **JDK 21**, then run from the project root:

```powershell
./gradlew.bat build
```

On Linux or macOS, use `./gradlew build`.
The release mod is written to `build/libs/player_blocks-0.0.2.jar`.
The `-sources.jar` contains source code and is for developers.

Gradle downloads the optional WorldEdit and JEI APIs from their Maven repositories.
The first build requires an internet connection. The checked-in source and Gradle wrapper
contain everything needed to build the mod.

## Project layout

| Path | Contents |
| --- | --- |
| `src/main/java/com/ourcreation/playerblocks/` | Mod code, grouped by responsibility |
| `src/main/resources/` | Models, textures, recipes, tags, translations, and mixin configuration |
| `src/main/templates/` | NeoForge metadata expanded by Gradle |
| `gradle/wrapper/` | Gradle wrapper and download configuration |
| `docs/gameplay.md` | Recipes, commands, integrations, and limits |
| `.github/workflows/build.yml` | Builds the mod and source JARs on GitHub |
