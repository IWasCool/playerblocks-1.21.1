# Development

## Requirements

- JDK 21 and the checked-in Gradle wrapper.
- Internet access for the first dependency download.
- Node.js 22 or newer for resource checks and authoring tools. Node is optional for a normal Gradle build.

Open the project root in IntelliJ IDEA as a Gradle project and select JDK 21 for Gradle.
The Java toolchain also targets 21. `.editorconfig` records four-space Java indentation,
explicit imports, braces, and a 120-column margin.

JavaScript authoring tools use two spaces and the settings in `.prettierrc.json`.
To reformat them, run `npx prettier@3.5.3 --write "tools/**/*.cjs"`.

The build pins Minecraft, NeoForge, mapping, WorldEdit, and JEI versions in `gradle.properties`.
WorldEdit and JEI APIs are compile-only Maven dependencies. Test mod JARs are never bundled.
The WorldEdit repository uses POM metadata to avoid its separate strict pins for Mojang's libraries.

## Commands

Run these from the repository root. On Linux/macOS replace `./gradlew.bat` with `./gradlew`.

```powershell
./gradlew.bat build
./gradlew.bat runGameTestServer
node tools/assets/check-shape-assets.cjs
node tools/localization/check-localizations.cjs
node tools/dev/check-project.cjs
```

`build` validates all language files and creates the release and source JARs under `build/libs/`.
`runGameTestServer` runs 37 required tests and exits. Tests cover ownership, commands,
confirmation limits, teams, persistence, block behavior, storage, liquids, and schematic rebinding.
The separate `gametest` source set and its generated structure are excluded from release JARs.
GitHub Actions runs the standalone tests and all three Node checks on pushes and pull requests.

To start a development game:

```powershell
./gradlew.bat runClient
./gradlew.bat runServer
./gradlew.bat runBlockVisualClient
```

The visual check opens a temporary client, validates models, rendering, and texture caching,
writes `build/player-blocks-inventory-preview.png`, and exits. It needs a graphical desktop.
Ordinary client/server files live in `run/`; test clients and servers use directories under `build/`.
Local Create and JEI JARs in `libs/` are copied into `run/mods/` for ordinary development runs.

## Code map

All paths below are relative to `src/main/java/com/ourcreation/playerblocks/`.

| Package | Responsibility |
| --- | --- |
| `block` | Vanilla-style blocks and shared placement ownership |
| `block.entity` | Persistent ownership and native bed, banner, chest, and shulker entities |
| `color` | Saved colors, team providers, commands, events, and refresh scheduling |
| `fluid` | Player Liquid registration, flow ownership, and scheduled color refreshes |
| `item` | Block items, banner placement, and cached color-slot components |
| `recipe` | Content-preserving shulker conversion |
| `registry` | Block, item, entity, recipe serializer, and creative-tab registration |
| `network` | Color preview packets and palette synchronization |
| `client` | Tinting, renderers, and texture caches |
| `compat` | WorldEdit, JEI, and shared schematic ownership |
| `mixin` | Hooks grouped into `vanilla`, `create`, `copycats`, `framedblocks`, and `thirst` |

Mixins with distinct targets stay separate. Create's spout hook uses one method wrapper
to keep shulker components local to the conversion call. `player_blocks.mixins.json`
lists all hooks, and `check-project.cjs` checks that every entry matches a source file.

The old `playercolor` package was split during cleanup. Integrations now import
`com.ourcreation.playerblocks.color.TeamColors` and
`com.ourcreation.playerblocks.compat.SchematicColors`.
Registry IDs, packet IDs, protocol version, NBT keys, and the saved-data name
`create_new_world_core_player_colors` retain their original values.
Keep legacy bed/banner/chest migration and slot-zero migration when changing ownership code.

## Optional compatibility tests

Put NeoForge 1.21.1 mods and all their dependencies in `build/compat-mods/`, then run:

```powershell
./gradlew.bat runGameTestServer build -PcompatibilityTests
./gradlew.bat runBlockVisualClient -PcompatibilityTests
```

The flag adds the supplied JARs only to the test runtime. Live portions skip when their mods
are absent. The older `-PschematicCompatTests` flag remains an alias.
See the [historical release audit](release-audit.md) for tested versions and limits.
Create 6.0.10, Copycats+ 3.0.9, FramedBlocks 10.6.3, FTB Teams 2101.1.11,
FTB Quests 2101.1.36, OPAC 0.32.7, WorldEdit 7.3.8, and JEI 19.53.0.426
are the versions used by the local compatibility fixture.

## Authoring resources

Run the shape generator against an extracted Minecraft 1.21.1 resource JAR:

```powershell
node tools/assets/generate-shapes.cjs <extracted-resource-directory>
node tools/assets/check-shape-assets.cjs
```

The shape generator also runs the recipe and liquid-recipe generators. The shared
`tools/lib/resources.cjs` writer keeps their JSON output consistent and inside the resource directory.
The model reference check removes unused generated parents and obsolete variants; every retained
model must be reachable from a block state or an item model.
Checked-in models and recipes are the build inputs; regeneration is an authoring operation.
Commit regenerated resource files with any generator changes.

Texture tools run directly through Java's source-file launcher:

```powershell
java tools/assets/GenerateTintTextures.java <Minecraft-resource-jar> src/main/resources/assets/player_blocks/textures/block/tinted
java tools/assets/GenerateItemIcons.java <Minecraft-resource-jar> src/main/resources/assets/player_blocks/textures/item
java tools/assets/AuditTextureAlpha.java <Minecraft-resource-jar> src/main/resources/assets/player_blocks/textures build/texture-alpha.txt
```

See [Localization](localization.md) before regenerating translations; generation overwrites
non-English files. All generators and checkers use only Node or JDK standard libraries.

## Local pack tools

These PowerShell tools require explicit local paths. They are optional and never run in CI.

`tools/dev/prepare-pack-check.ps1` creates disposable pack fixtures under `build/pack-check`.
Supply `-ClientInstance`, `-ServerInstance`, `-GameplaySelection`, `-NativeLibrary`, and
`-NativeSha256`. The gameplay-selection JSON contains a `copied_jars` array with a `jar`
name for each selected client mod. The native library and checksum are for packs using
Power Grid. The tool refuses an existing output directory and does not copy original saves.
`gradle/pack-check.init.gradle` selects the resulting fixture; see the audit's reproduction commands.
Use `-PpackDebug` to suspend the disposable game for a debugger on `localhost:5007`.

`tools/dev/install-player-blocks.ps1` installs a verified release into two explicit instance paths.
Supply `-ClientInstance`, `-ServerInstance`, and `-ExpectedSha256`; optional `-Jar`,
`-Evidence`, and `-BackupRoot` override paths derived from `gradle.properties`.
It records checksums and deployment evidence under `build/` and refuses to overwrite
a different installed Player Blocks JAR. It is not needed to build or publish the source.

## Creating the GitHub repository

The source directory does not need to include local mod JARs, worlds, caches, logs, or IDE settings.
`.gitignore` excludes those files; retain the Gradle wrapper JAR.

After reviewing the source, create an empty repository on GitHub and run:

```powershell
git init -b main
git add .
git status --short
git commit -m "Initial Player Blocks source"
git remote add origin https://github.com/<account>/<repository>.git
git push -u origin main
```

Review the staged files before committing. The existing mod license is All Rights Reserved;
the NeoForge MDK template has its own retained MIT notice.
