# Castle Wars

Castle Wars is a competitive Minecraft Forge **1.16.5** minigame built around two opposing medieval castles.

## Core rules

- MVP is **1v1**: RED vs BLUE.
- Each side owns a castle and an objective bed.
- Destroying the enemy bed **does not award a point by itself**.
- After the enemy bed is destroyed, the attacker must kill that defender.
- Only that valid enemy kill awards **+1 point**.
- The first player to **10 points** wins the match.
- After a scored capture, the round is reset: beds and player-built blocks are restored/cleared and players return to their bases.

## Arena

The project bundles the supplied `medieval_castle.schem` asset and reads Sponge Schematic v3 directly. Players do **not** need WorldEdit installed at runtime.

The original schematic is 168×65×122. For the PvP arena the mod uses a focused crop of the actual castle and creates the second base as a 180° mirrored opponent copy, keeping both sides geometrically fair.

The source schematic was exported by a newer WorldEdit/DataVersion than Minecraft 1.16.5. The loader contains explicit compatibility fallbacks for the few newer block states present in the asset.

Because the GitHub connector used during development cannot upload arbitrary binary files through the regular contents endpoint, the 19.6 KB schematic is stored byte-exactly as seven Base64 resource chunks inside the JAR and reconstructed in memory on load.

## Development stack

- Minecraft 1.16.5
- Forge 36.2.42
- ForgeGradle 6
- Gradle 8.4
- mod bytecode/toolchain target: Java 8
- GitHub Actions build and JUnit 5 tests

## Current admin commands

- `/wars arena build` — place two opposing castles centered around the command executor.
- `/wars arena status` — show asynchronous arena-build progress.

Arena construction is tick-budgeted instead of placing the entire structure in one server tick.

## MVP roadmap

1. Forge project + CI.
2. Bundled schematic loader and mirrored arena construction.
3. Objective beds and spawn markers.
4. Match start/stop and RED/BLUE player assignment.
5. Bed destruction state.
6. Kill validation and first-to-10 scoring.
7. Respawn and round reset.
8. Immutable castle shell + tracking/removal of player-placed blocks.
9. HUD/messages and runtime multiplayer testing.
