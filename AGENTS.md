# Castle Wars agent instructions

- Target Minecraft Java Edition 1.16.5 and Forge 36.2.42.
- ForgeGradle 6 / Gradle 8.4 runs under Java 17 in CI; mod bytecode targets Java 8.
- Do not paste APIs from modern Minecraft versions into this project. Verify 1.16.5 official/MojMaps names and behavior first. Prefer stable registry IDs when mapping-specific constants are uncertain.
- Server owns match state, scoring, arena mutation, Castle Cores, siege gates, War Camp state, castle guards, round lifecycle and solo-test NPC behavior.

## Core scoring canon

- Castle Wars is 1v1 RED vs BLUE and first to 10 points.
- The visible objective is a single-block **Castle Core**, not a bed. Pure rules may retain historical `bedDestroyed` naming internally until deliberately refactored.
- Destroying the enemy Castle Core alone awards **0 points**.
- After that Core is destroyed, the defender must be defeated by the opposing side to award exactly **+1 point**.
- The opposing real participant or one of that side's owned castle guard golems may secure the defeat. Environment/self/friendly deaths do not score.
- Core destruction creates a **LAST STAND** phase until the defender is defeated or the round otherwise ends.

## v0.2 siege battlefield canon

- The two imported castles stay mirrored for geometric fairness.
- There are three intentional routes between castles:
  - central exposed stone causeway aligned with the main siege gate;
  - west ruined lane with cover;
  - east lower trench route.
- Each castle has a two-block-thick, seven-wide, five-high central siege gate with **250 shared HP**.
- Gate blocks are not individually mined away. Break attempts damage shared gate HP; at 0 HP the whole gate opens.
- Default siege damage: axe 35, pickaxe 22, sword 8, other item/hand 12.
- Two flank breaches remain open, so the gate is a tactical fast route rather than a mandatory hard lock.
- The central **War Camp** is a secondary objective only: 8 seconds uncontested capture grants 45 seconds of Speed I + Haste I (Siege Momentum) to that real participant. It never awards match points.
- Round start uses a synchronized 3-second PREPARE countdown. A scored capture uses a 5-second presentation/reset pause, then restores round state and starts another countdown.
- Match escalation labels are presentation/state context: SKIRMISH (0-3 total points), SIEGE (4-8), TOTAL WAR (9-13), FINAL ASSAULT (14+). Do not give the leading player free power solely for leading.

## Guards

- Every round each castle has exactly **5 iron golem guards** at separate verified schematic posts.
- Guard roles are two Gate Guards, two Courtyard Guards and one Royal Guard (verified post index 2 nearest the Core).
- Royal Guard is stronger (140 max health, 18 attack damage) and visibly named; Gate Guards use 110 max health. Other guards retain vanilla-derived combat values.
- Non-Royal guards patrol only short, safe offsets around their verified posts. Royal Guard stays at its Core post.
- Guards attack only the opposing participant while that opponent is inside their castle territory. Friendly player-to-guard and guard-to-friendly damage is forbidden.
- Killed guards stay dead for the rest of the current round. A scored reset restores the full five per side.

## Solo testing

- `/wars test start [red|blue]` must continue to support one-client runtime validation with a persistent Vindicator participant.
- The NPC owns a stable match UUID, obeys the same Core -> defender defeat -> point rules, is held/invulnerable during countdown/reset, and stays primarily defensive inside its castle.
- Do not replace this with Forge `FakePlayer` unless a later feature specifically needs automated player interactions; FakePlayer by itself is not the combat AI.

## Arena integrity / engineering

- The bundled castle is Sponge Schematic v3 exported by a newer WorldEdit. Compatibility remaps belong in the loader; do not require WorldEdit at runtime.
- Do not let players destroy the immutable castle shell. Track player-placed blocks separately and allow those to be broken during active combat.
- Round reset removes player-placed blocks and restores Core/gates/guards/War Camp without rebuilding the entire castle.
- Keep geometry/loading, secondary objectives and pure scoring rules separated so rule logic remains cheaply unit-testable.
- UI in v0.2 is server-driven boss bar + actionbar/messages; server state remains authoritative even if a later custom client HUD replaces presentation.
- Never claim a build or runtime test passed unless it actually ran. Check GitHub Actions after substantive changes. A green compile/JUnit run is not equivalent to an in-game pathing/layout playtest.
