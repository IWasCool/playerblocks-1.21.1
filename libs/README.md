# Optional development mods

This directory is for locally supplied NeoForge 1.21.1 mod JARs. JARs are ignored by Git.
The build downloads WorldEdit and JEI APIs automatically; this directory can be empty.

`./gradlew.bat installLocalMods` copies locally supplied `create-*.jar` and `jei-*.jar`
into `run/mods/`. Normal development client/server runs also perform that copy.

For the optional integration GameTests, put compatible mods and their dependencies
in `build/compat-mods/` and use `-PcompatibilityTests`.
See [Development](../docs/development.md) for tested versions.
