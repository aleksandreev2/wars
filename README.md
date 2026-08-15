# Castle Wars

Castle Wars is a competitive Minecraft Forge **1.16.5** minigame built around two opposing medieval castles.

## Core rules

- MVP is **1v1**: RED vs BLUE.
- Each side owns a castle and an objective bed.
- Each castle starts every round with **5 iron golem guards** stationed at separate verified posts inside the fortress.
- Guards attack only the opposing participant while that opponent is inside their castle territory; they do not damage their own player or other guards.
- Destroying the enemy bed **does not award a point by itself**.
- After the enemy bed is destroyed, that defender must be defeated.
- A valid opposing-player defeat or a defeat by that side's castle guard awards **+1 point**.
- The first player to **10 points** wins the match.
- After a scored capture, the round is reset: beds, guards and player-built blocks are restored/cleared and players return to their bases.

## Solo-test NPC mode

When only one Minecraft client is available, the mod can create a server-side combat NPC and run the same match state against it.

- The NPC is a persistent vanilla Vindicator named `Castle Bot [RED]` or `Castle Bot [BLUE]`.
- It has a real entity UUID and occupies one side in the normal match state, so bed -> defender defeat -> point is tested through the same scoring code as multiplayer.
- It stays in its own castle and attacks the human opponent when that player invades its castle territory.
- If defeated before its bed is destroyed, it immediately respawns with no point awarded.
- If defeated after its bed is destroyed, the human side receives +1 and the normal round reset runs.
- The NPC is recreated/reset automatically between rounds and cleaned up when the test stops.

Commands:

- `/wars test start` — start solo test as RED against a BLUE NPC.
- `/wars test start red` — explicitly play RED against a BLUE NPC.
- `/wars test start blue` — play BLUE against a RED NPC.
- `/wars test score` — show score, guard counts and NPC side.
- `/wars test stop` — stop solo test and clean up the NPC/guards.

## Arena

The project bundles the supplied `medieval_castle.schem` asset and reads Sponge Schematic v3 directly. Players do **not** need WorldEdit installed at runtime.

The original schematic is 168×65×122. For the PvP arena the mod uses a focused crop of the actual castle and creates the second base as a 180° mirrored opponent copy, keeping both sides geometrically fair.

The five guard posts were selected from the actual schematic after checking for solid floor and 3×3×3 clearance. BLUE receives the mirrored equivalents of the same five positions.

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
- `/wars match start <red> <blue>` — start a 1v1 match and summon five guards for each castle.
- `/wars match score` — show current score and remaining living guards on both sides.
- `/wars match stop` — stop the active match and clean up guards/player-built blocks.
- `/wars test start [red|blue]` — run a one-client match against the combat NPC.
- `/wars test score` — show solo-test status.
- `/wars test stop` — stop and clean up solo-test mode.

Arena construction is tick-budgeted instead of placing the entire structure in one server tick.

## MVP roadmap

1. Forge project + CI.
2. Bundled schematic loader and mirrored arena construction.
3. Objective beds, spawn markers and five guard posts per side.
4. Match start/stop and RED/BLUE player assignment.
5. Bed destruction state.
6. Opposing-player/guard capture validation and first-to-10 scoring.
7. Respawn and round reset.
8. Immutable castle shell + tracking/removal of player-placed blocks.
9. Solo-test NPC for one-client gameplay validation.
10. HUD/messages and runtime multiplayer testing.
