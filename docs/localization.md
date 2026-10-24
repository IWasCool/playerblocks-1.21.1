# Localization

Player Blocks includes 137 language files, covering the exact language menu
in Minecraft 1.21.1's asset-index 17 `pack.mcmeta`. The pinned menu, language names,
regions, and right-to-left flags are recorded in `tools/localization/minecraft-languages.json`.
Languages introduced in later Minecraft versions are outside this version's menu.

Each file contains the same 85 keys: 58 building/item/fluid/tab names and 27 color
command messages. The files in `src/main/resources/assets/player_blocks/lang/`
are the editable translations used by the game. There is no runtime translation
service, network request, new dependency, or per-tick localization work.

## Wording and review

These are initial translations, not professionally reviewed language packs.
Most prose was machine-assisted. Names reuse vanilla localized item terminology
where possible, with corrections for ambiguous words such as the noun “Block”.
Latin, Asturian, and Toki Pona have authored overrides. Regional varieties share
prose with their base language where appropriate while retaining their vanilla
item terminology. Pirate, LOLCAT, Anglish, Shakespearean English, and Upside Down
have explicit text transformations. Serbian Latin and Belarusian Latin retain
their Latin script. Minecraft handles right-to-left layout through the selected
language's existing metadata.

Cornish (`kw_gb`), Ido (`io_en`), Interslavic (`isv`), Nahuatl (`nah`), Tzotzil
(`tzo_mx`), Jawi Malay (`zlm_arab`), Quenya (`qya_aa`), Klingon (`tlh_aa`), and
Lojban (`jbo_en`) use native vanilla vocabulary with compact numeric instructions
instead of unverified full-sentence translations. For the confirmation prompt,
`≤ 2` expresses the maximum allowance, `remaining/2` shows the remaining uses,
the native Yes choice shows `-1`, and Cancel shows `±0`. These packs are useful
starting points; full prose and grammatical refinements need native review.
Vanilla itself contains English fallback text in some constructed-language keys.

Translation changes never alter permissions, the two-use cap, UUID confirmation
tokens, clickable command strings, saved colors, or team-leader inheritance.
Command messages are sent as Minecraft translatable components, so players on the
same server can read them in different client languages. `/hex`, `/playercolor`,
`/teamcolor`, usernames, team IDs, UUIDs and hex values are data, not translated
command syntax. The English display name remains **Player Block**.

## Validation

`./gradlew.bat build` validates menu coverage, nonempty strings, Unicode and
argument preservation using Gradle itself. No Node installation or internet
connection is needed for the localization validation in a normal build.

For additional authoring checks, run:

```powershell
node tools/localization/check-localizations.cjs
```

The checker also verifies command-key references, distinct confirmation choices,
draft-marker removal, and safe substitution of values containing `%` and `$`.
The existing server tests exercise the actual translated English confirmation
components and click actions, including cancel, expiration, operator exemption,
and the persisted two-change limit.

## Optional draft regeneration

Back up any manually edited language files before regenerating: the final step
overwrites non-English language files. The normal shape/recipe generators retain
the English command keys and do not rewrite other language files.

```powershell
node tools/localization/prepare-localizations.cjs <Minecraft-assets-root>
powershell -NoProfile -ExecutionPolicy Bypass -File tools/localization/import-localization-drafts.ps1 -Targets fr,de,es,ja,zh-CN,zh-TW
node tools/localization/generate-localizations.cjs
node tools/localization/check-localizations.cjs
```

The asset root must contain the downloaded `indexes/17.json` and `objects/`.
Preparation checks the SHA-1 of the vanilla assets before reading them. Import
is an optional authoring step that submits public English UI strings to a
translation endpoint and caches drafts under `build/localization/drafts`.
To reproduce every prose draft, import the targets used by the generator before
regeneration; incomplete drafts are rejected when arguments or markers are lost.
Authored overrides are in `tools/localization/localization-overrides.json`. Review generated
text before publishing; automatic checks cannot establish linguistic accuracy.
