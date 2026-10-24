# Player Blocks release audit

Original audit: 2026-10-12 for Minecraft 1.21.1, NeoForge 21.1.251, Java 21, mod version 0.0.1. The optimization follow-up below is included in version 0.0.2.

This is historical test evidence. Refer to [Development](development.md) for the current
project layout and commands. Log and preview paths below refer to local build artifacts,
which are excluded from Git.

## Version 0.0.2 (2026-10-17)

- Packages the runtime optimizations, the earlier 35-test validation, and the Thirst dispenser fix checked by the expanded 37-test pack suite below.
- Preserves existing block/item registry IDs, ownership data, palette slots, and the saved-data format. No block or data migration is introduced by upgrading from 0.0.1.
- Corrects the WorldCore integration documentation: the current sibling WorldCore project has no Player Blocks references and creates starter hulls from vanilla/copycat blocks. Automatic Player Blocks starter-ship coloring is not implemented.
- Intended for installation on both the client and server. Automated checks cover the installed server and client mod sets described below; lengthy multiplayer sessions have not been exercised.

## Create New World pack checks (2026-10-17)

The disposable fixture in `build/pack-check/server` copies the installed **Server Pack v4.1.0+** mod set: 158 JARs, with none omitted, using NeoForge **21.1.259**. All **37 required tests pass**, and the recipe check confirms that all **157 Player Blocks recipes** survive pack loading (`build/release-0.0.2-pack-server-final.log`). This includes the installed `create_new_world_core-0.0.3.jar`, with SHA-256 `7BB52DF8E58C76F2BBF804428602858B3E656ACF5F5E95F2DF88C00AE6017545`; WorldCore was not replaced by a newer sibling build.

Testing exposed a real interaction with **Thirst Was Reclaimed 3.0.6**: dispenser pickup of Player Liquid produced a vanilla water bucket. Debugger evidence followed Moonlight's `FillFluidHolderBehavior` through Thirst's `WaterPurity` dispenser lambda into vanilla `DispenseItemBehavior$7`, and observed the filled result as `1 water_bucket`. The optional compatibility mixin now preserves the actual fluid's bucket result while retaining normal vanilla-water purity handling. Regression tests cover Player Liquid pickup, stacked empty buckets, occupied dispenser inventories, and vanilla-water purity; the corrected pack run passes all 37 tests.

Fixture preparation is recorded in `build/pack-check/server/manifest.json`. Of the 158 copied JARs, 157 match their installed SHA-256 hashes exactly. The Create Central Kitchen fixture copy removes only nine bundled optional-integration GameTest classes and their directory entry, because NeoForge reflects their absent optional API signatures before applying test namespace filtering. All 404 retained ZIP entries are byte-identical to the installed JAR. A pinned PowerGrid native DLL was copied only into the disposable fixture. Installed mod JARs and gameplay configuration were not modified for these test accommodations.

The server test player reports `isLocalPlayer() == false`, matching a server-side player and avoiding ParCool's client-config access in the fixture; no ParCool gameplay configuration was changed. The Player Blocks GameTest classes, including the two new regression tests that bring the suite to 37 tests, remain development-only and are excluded from the release JAR.

The client gameplay selection contains 158 of the installed client's 184 JARs, with the 26 omitted client-side mods recorded in `build/pack-check/client-gameplay/manifest.json` and included in the separate full-client check. With copied root `config`/`defaultconfigs`, NeoForge **21.1.256**, and the client's newer **Open Parties and Claims 0.33.0**, all **37 required tests pass** and all 157 Player Blocks recipes load (`build/release-0.0.2-pack-client-gameplay.log`). This is a server-side GameTest run against the client's gameplay mod selection, not an interactive multiplayer session.

The complete **184-JAR client** fixture, with no installed client JAR omitted, passes `PLAYER_BLOCKS_VISUAL_CHECK: PASS` on NeoForge **21.1.256**, including the texture-cache checks (`build/release-0.0.2-pack-client-visual-final.log`). **Sodium 0.8.13**, **Iris 1.8.14-beta.1**, **Entity Model Features**, and **Entity Texture Features** actually load in this run. The initial Sodium discovery problem came from launching renderer JARs as development-classpath mods; the successful fixture uses the normal `mods` directory. No installed mod was modified for that correction. The rendered inventory preview is `build/pack-check/client/inventory-preview.png`.

These checks establish automated behavior and rendering against the recorded installed-pack inputs. They do not measure long-running multiplayer performance or every shader/resource-pack combination. The earlier standalone and curated optional-mod 35-test results remain historical evidence below.

## Final artifact and local import (2026-10-17)

The final normal production build passes all **37 required standalone tests** on the minimum supported NeoForge **21.1.251**, with no pack-specific loader override (`build/release-0.0.2-final-build.log`). The release JAR is **699,577 bytes**, version **0.0.2**, SHA-256 `35BBEFCCA40E71EB21CDA0F2560E501932833F6745F41CC8601418B57EA9EFBD`. Its metadata retains NeoForge 21.1.251 as the minimum. Compared with the optimized 0.0.1 artifact, all block/item assets and recipes are identical: the only added entry is `ThirstPlayerLiquidDispenserMixin.class`, the only changed entries are the mod metadata and mixin configuration, and no entries are removed. GameTest and visual-check classes are excluded. Evidence and hashes are recorded in validation.json (`build/release/0.0.2/validation.json`); the frozen release artifact is player_blocks-0.0.2.jar (`build/release/0.0.2/player_blocks-0.0.2.jar`).

The local import completed successfully at **21:04 EDT**. Both installed copies match the release SHA-256, and each instance declares Player Blocks exactly once:

| Local instance | Top-level JARs after import | Player Blocks version |
| --- | --- | --- |
| Create New World  A Locked Progression Experience | 185 | 0.0.2 |
| Server Pack v4.1.0+ | 159 | 0.0.2 |

The installed `create_new_world_core-0.0.3.jar` checksum is unchanged in both instances. The import did not modify running games or worlds, and **the client and server must restart to load the new JAR**. Exported pack archives and public releases were not updated.

deployment.json (`build/release/0.0.2/deployment.json`) records installed paths, checksums, and exact rollback paths. The before-import manifest (`build/backups/player-blocks/player-blocks-20261017-210418-005-543dec069cd54e55b0a6c5cba99f4771/before.json`) records the original state; no previous Player Blocks JAR was replaced. Before the next startup, rollback consists of removing only the two recorded Player Blocks paths. Once worlds use this mod's content, preserve its JAR.

## Optimization follow-up (2026-10-17)

- Replaced passive shape/server chest tickers with a shared, staggered refresh queue: each owned shape is considered once per 20 ticks instead of invoking its block-entity ticker every tick. Native shulker animation and client chest animation remain intact. Unowned shapes do not enter the queue. Palette blocks normally require no queue entry; legacy slot-zero edits and reloads are repaired automatically.
- The queue uses weak level/entity references, deduplicates registration, respects frozen ticks, world borders and vanilla chunk/entity ticking gates, and checks live block-entity identity without loading chunks. Bulk replacement/unload cleanup uses linear-time swap removal.
- Replaced liquid block-entity tickers with scheduled block refreshes once per 20 ticks for owned liquid. Vanilla fluid ticks remain separate. Pending ticks survive chunk saves, and repeated link/load calls do not create duplicate scheduled entries. Unowned liquid schedules no color refresh. Neighbor ownership lookup reuses one mutable position and the direction array.
- Added transient color revisions so unchanged shape/container refreshes skip color and state computation. Repeating an identical personal/team override does not invalidate the palette or mark saved data dirty. Limited `/hex` confirmations still persist their use count, including same-color confirmations and already-default resets.
- Removed NBT copying from palette change detection, reduced broadcasts to one defensive snapshot shared across recipients, removed the second team-provider pass during periodic synchronization, cached the SavedData factory and optional team API entry points, and avoided eagerly computing unused fallback colors.
- Shared lazily allocated, immutable inventory color components per slot. Unchanged inventory ticks no longer create a slot string; stacks with other block-state properties preserve those properties when ownership changes.
- Cached decoded source textures until resource reload, used primitive LRU keys, reduced render wrapper/item NBT work, and reused fixed fluid texture IDs. Bed/container caches keep their 256/512 limits, retain evicted textures until frame end, reclaim same-frame reuse, and use stable texture IDs across recreation. This prevents queued geometry from losing an evicted texture and avoids creating a new memoized render type each time a previously seen tint returns.

Validation: the original 26-test standalone baseline passed (`build/optimization-baseline.log`). The expanded suite passes all 35 required tests both standalone (`build/optimization-standalone.log`) and with the optional integrations listed below; the client check reports `PLAYER_BLOCKS_VISUAL_CHECK: PASS` (`build/optimization-compatibility.log`). This includes 2,000 duplicate queue registrations, callback cadence/removal, native entity reload, automatic slot migration, inventory component isolation/property preservation, unchanged-color persistence, liquid scheduling/reload/priority, and texture-cache eviction/reclamation/cleanup with real GPU textures. All 53 shape asset checks pass. The final JAR contains the runtime helpers and excludes all GameTest/client check classes.

This verifies behavior and eliminated work, not a production-world FPS/TPS benchmark. Colored oceans still carry one block entity per liquid block; eliminating that memory cost would require a separate storage/rendering design and world migration. The existing 4,095-owner palette limit remains.

## Fixes made

- Cached team resolution during bulk placement: 2,000 calls for one owner in the same tick now resolve the provider once. Explicit team refresh remains available; periodic membership/leadership checks still run once per second.
- Cached palette snapshots and avoided sending unchanged palettes. Reading or modifying a returned snapshot cannot suppress or corrupt a pending update.
- Used a bit set for allocating/loading palette slots, avoiding repeated scans of the entire owner map. Duplicate UUID spellings in damaged saved data cannot reserve ghost slots.
- Removed normal block-entity ticking from palette-backed full blocks. Server color queries and saved data use the current owner color; unclaimed/legacy slot-zero blocks retain the migration path.
- Avoided building a replacement item component map on every inventory tick when the color slot is unchanged.
- Removed per-liquid-block team API polling, staggered liquid refresh work, and avoided duplicate color-update packets on placement.
- Made bed/container texture caches use least-recently-used eviction while retaining bounded limits and resource-reload cleanup.
- Added a WorldEdit extent at actual world placement. Player sessions assign the actor, use the team leader's color, retain storage, and leave the source NBT unchanged. Buffered sessions use the same extent.
- Extended Create/Copycats+/FramedBlocks palette materials to Player Block, Player Chiseled, and Player Tiles while retaining each material's texture. Restricted the FramedBlocks entity whitelist to those three supported materials.
- Added an optional JEI display recipe for the dynamic shulker conversion. The actual server recipe continues preserving contents and components.
- Removed deprecated fluid client initialization and cleaned release metadata.
- Registered the standard filled-bucket dispenser behavior for Player Liquid; dispenser placement and pickup are tested.
- Added localization resources for all 137 Minecraft 1.21.1 menu languages, including color-command feedback and clickable confirmation labels. Locale key coverage and indexed arguments are checked during builds. Translations are initial drafts; nine packs use compact native labels and numeric instructions. See [localization.md](localization.md) for wording/review limits.

## Functional and integration evidence

The original audit's 26 required server tests passed with the expanded mod set and in the standalone run. The optimization follow-up expanded this to 35 required tests, as recorded above. Optional integrations skip their live portions in the standalone run; the main functionality still runs.

After localization, the standalone server suite still passes all 26 tests, including the actual translated confirmation components and clickable actions (`build/localization-build.log`). All 137 packs contain 85 matching keys; Unicode and argument checks pass. Model/recipe regeneration retains the command translations and the shape asset checks still pass. The final build validates and packages all 137 language files (`build/localization-final-build.log`).

| Integration | Tested version | Evidence |
| --- | --- | --- |
| Create | 6.0.10 | Schematic placement/cannon hooks, palette materials, exact 250 mB spout conversion, filled shulker component preservation |
| Copycats+ | 3.0.9 | Real material acceptance for all three supported palette blocks preserves their texture and color slot |
| FramedBlocks | 10.6.3 | Real crafting camo acceptance/container state for all three palette blocks |
| FTB Teams | 2101.1.11 | Native party/player records resolve the owner; leadership changes update the color |
| FTB Quests | 2101.1.36 | Mod loads; native item-task predicates recognize colored Player Blocks across slots and Player Dye |
| Open Parties and Claims | 0.32.7; 0.33.0 | Real API creates a party, adds/removes a member, and resolves the owner's color; newer client-pack 0.33.0 passes the 37-test gameplay check |
| WorldEdit | 7.3.8 | Real player edit session pastes a chest and patterned block, changes ownership, follows the leader color, preserves diamonds, and leaves template NBT intact |
| JEI | 19.53.0.426 | Client loads; optional plugin registers the two-ingredient shulker display recipe through the JEI registration API |
| Thirst Was Reclaimed | 3.0.6 | Actual server-pack 37-test run verifies Player Liquid dispenser pickup, stack/inventory handling, and vanilla-water purity preservation |

FTB support was tested with FTB Library 2101.1.37, FTB XMod Compat 21.1.12, and Architectury 13.0.11. Most test JARs were copied from sibling projects. The locally available FramedBlocks 5.11.5 was unsuitable for 1.21.1; the official [FramedBlocks 10.6.3 release](https://modrinth.com/mod/framedblocks/version/5g0Yxvfb) was downloaded for testing and checked against its published SHA-512 hash.

The suite also covers commands and confirmation limits, save/load, team fallback, slab merging, stair/wall/fence behavior, doors and buttons, pressure plates, pillar orientation, glazed patterns, bed halves, banner patterns, candle state, chest pairing/storage, portable shulker contents, liquid bucket capabilities, flow ownership, and recipe loading.

## Rendering and opacity

- The client check passes with the expanded mod set. All 54 inventory icons render, with native chest previews in two colors and beds in bright cyan and dark blue.
- All 31 generated block texture masks preserve every original Minecraft alpha value. Solid concrete, wool, terracotta, patterned blocks, and glazed terracotta remain opaque; glass retains its native transparency.
- Combined chest body/trim alpha matches vanilla pixel for pixel for single, left, and right variants. Gray interior/corners and the original metal latch stay intact.
- The pane's 2D icon preserves the vanilla glass transparent center, border, and highlights.
- Bed pillow top/sides/back and wood pixels remain unchanged under multiple colors. The blanket retains expanded native fabric contrast.
- Bucket metal remains untinted; only its liquid layer receives the hex color. Fluid extensions have water sprites and an opaque tint alpha.

Evidence: render preview (`build/player-blocks-inventory-preview.png`), expanded server run (`build/release-audit-server.log`), client run (`build/release-audit-client.log`), standalone run (`build/release-audit-standalone.log`), alpha checks (`build/release-texture-alpha.txt`).

## Release scope and limits

- The tests verify the versions above, not every version or modpack combination. Quest GUI interactions, shaders/resource packs, and lengthy multiplayer play sessions were not fully exercised.
- Other team systems need `TeamColors.registerProvider` or a scoreboard mapping with an explicitly assigned leader. There is no reliable automatic leader inference for arbitrary mods.
- Palette-backed/copied materials support 4,095 registered owner slots per world. This pre-existing limit remains; it is not an unlimited-owner implementation.
- Colored liquid stores ownership per fluid block, so large colored oceans carry more memory/tick overhead than ordinary water. These fixes reduce repeated lookups and spread refresh work; they are not a claim of measured FPS/TPS on a large production world.
- Player Liquid provides the documented water-style flow, swimming, hydration, extinguishing, boat support, and source renewal. Vanilla-only special interactions such as colored waterlogging in arbitrary vanilla blocks are not advertised.
- Create and JEI fluid/container previews follow the viewing player's color; bucket-placed liquid retains its placer and team leader color.
- External API JARs are compile-only or local test installations. The release JAR must contain neither test classes/assets nor third-party mod code.

## Reproduce

```powershell
$env:JAVA_HOME = 'C:/Program Files/Java/jdk-21'
./gradlew.bat runGameTestServer build
./gradlew.bat runGameTestServer runBlockVisualClient build -PcompatibilityTests
node tools/assets/check-shape-assets.cjs
java tools/assets/AuditTextureAlpha.java build/moddev/artifacts/neoforge-21.1.251-client-extra-aka-minecraft-resources.jar src/main/resources/assets/player_blocks/textures build/release-texture-alpha.txt
```

The expanded command uses matching JARs from `build/compat-mods`. The older `-PschematicCompatTests` flag remains an alias.

To reproduce the installed-pack checks, first prepare a fresh disposable `build/pack-check`
snapshot with `tools/dev/prepare-pack-check.ps1`. Supply the instance paths, gameplay-selection
manifest, native DLL, and checksum explicitly as described in [Development](development.md).
The tool refuses an existing output directory and never copies original saves. Then run:

```powershell
./gradlew.bat --no-configuration-cache -I gradle/pack-check.init.gradle '-PpackTarget=server' '-Pneo_version=21.1.259' runGameTestServer
./gradlew.bat --no-configuration-cache -I gradle/pack-check.init.gradle '-PpackTarget=client-gameplay' '-Pneo_version=21.1.256' runGameTestServer
./gradlew.bat --no-configuration-cache -I gradle/pack-check.init.gradle '-PpackTarget=client' '-Pneo_version=21.1.256' runBlockVisualClient
./gradlew.bat runGameTestServer build
```

The final normal build restores the release's minimum NeoForge version metadata after the pack-specific loader overrides. Test fixtures, copied mods, GameTest code, and visual-check assets are excluded from the release JAR.
