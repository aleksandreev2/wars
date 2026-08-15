# Castle Wars agent instructions

- Target Minecraft Java Edition 1.16.5 and Forge 36.2.42.
- The latest 1.16.5 Forge MDK uses ForgeGradle 6 / Gradle 8.4 but the mod bytecode targets Java 8.
- Do not paste APIs from modern Minecraft versions into this project. Verify 1.16.5 names and behavior first.
- Server owns match state, scoring, arena mutation, objectives, and castle guards.
- A point is awarded only after the enemy objective bed has been destroyed and that defender is then defeated by the opposing side. The opposing participant or one of that side's owned castle guard golems may secure the defeat; environment/self/friendly deaths do not score.
- Match target is first to 10 points unless a later explicit project decision changes it.
- Every round each castle has exactly 5 iron golem guards at separate verified posts. Guards attack only the opposing participant while that player is inside the guard's castle territory. Friendly player-to-guard and guard-to-friendly damage is forbidden.
- Killed guards stay dead for the rest of the current round. A scored round reset restores the full 5 guards per side.
- The bundled castle schematic is a Sponge Schematic v3 exported by a newer WorldEdit. Compatibility remaps belong in the schematic loader; do not require WorldEdit at runtime.
- Do not let players destroy the immutable castle shell. Track player-placed blocks separately and allow those to be broken during a round.
- Arena reset must remove player-placed blocks and restore objective beds/guards without rebuilding the whole castle during active play.
- Keep geometry/loading code separate from game rules so rules can be unit-tested without Minecraft.
- Never claim a build or runtime test passed unless it was actually run. Check GitHub Actions after substantive changes.
