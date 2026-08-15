# Castle Wars

Castle Wars **v0.2.0** is a competitive Minecraft Forge **1.16.5** siege minigame built around two opposing medieval castles.

The goal is intentionally more than "BedWars with castles": each assault has multiple routes, a destructible main gate, a neutral battlefield objective, layered castle guards and a Last Stand finish.

## Core match loop

1. RED and BLUE prepare inside mirrored castles during a 3-second synchronized countdown.
2. Players choose between the exposed central causeway, west ruined lane, or east trench route.
3. The central main gate can be breached through shared siege damage, while both flank breaches remain permanently usable.
4. The neutral War Camp can be contested for temporary Siege Momentum.
5. Invade the enemy fortress and destroy its **Castle Core**.
6. Core destruction alone gives **0 points** and starts **LAST STAND**.
7. Defeat the exposed defender after the Core is down to capture the castle for **+1 point**.
8. A 5-second capture presentation runs, then Core/gates/guards/War Camp/player blocks reset and the next assault begins.
9. First to **10 points** wins.

Environmental, self and friendly defeats do not score. An owned castle guard may secure its team's capture if the enemy Core is already destroyed.

## Battlefield routes

### Central causeway

- shortest and most readable route;
- broad stone-brick approach;
- aligned directly with the enemy's central siege gate;
- exposed, so committing to the gate is deliberately visible.

### West ruined lane

- five-block-wide cobble/mossy route;
- alternating stone-wall cover;
- longer but gives more opportunities to break line of sight.

### East trench

- lower route that drops by up to two blocks through the middle;
- stone-brick floor and low wall edges;
- another permanent bypass around the main gate.

## Siege gates

Each central castle gate is a single gameplay object with **250 shared HP**. Individual iron-bar/plank blocks are not mined out independently; attempts to break any gate segment damage the whole gate instead.

Default siege damage per break attempt:

- axe: **35**
- pickaxe: **22**
- empty hand / most other items: **12**
- sword: **8**

At 0 HP the full gate opens. The two flank routes mean a player can always choose to bypass the gate rather than spend time breaching it.

## Castle Core and Last Stand

The old visible objective bed is replaced by a compact team-colored Core shrine built around a **Lodestone**. The pure scoring layer still retains historical bed-state naming internally, but players interact only with the Castle Core.

When a Core falls:

- the Core disappears;
- both sides receive explicit objective feedback;
- the defender enters **LAST STAND**;
- killing that defender becomes the only action that awards the capture point.

## War Camp

A neutral War Camp sits around the midpoint of the battlefield.

- Hold it uncontested for **8 seconds** to capture it.
- Capturing it grants that real player **45 seconds of Speed I + Haste I** (`Siege Momentum`).
- The ownership ring visibly changes to RED or BLUE.
- Contested/abandoned capture progress decays.
- War Camp control never gives match points and therefore cannot replace attacking the Castle Core.

The solo NPC is intentionally defensive and does not currently contest the War Camp; this keeps one-client testing focused on assaulting a real defended castle.

## Castle guards

Every round still contains exactly **5 iron golem guards per castle**, using the verified posts from the supplied schematic.

v0.2 gives those five guards readable jobs:

- **2 Gate Guards** — 110 max health, protect forward areas;
- **2 Courtyard Guards** — patrol short safe paths around their posts;
- **1 Royal Guard** — the post nearest the Core, 140 max health and 18 attack damage, with a visible Royal Guard name.

Guards target only the opposing participant inside their castle territory. Friendly player/guard damage is blocked. Dead guards remain dead for that assault and all five return on a scored round reset.

## Match presentation

v0.2 adds a server-driven HUD without making the client authoritative:

- boss bar with RED/BLUE score, Core status, living guard counts and match stage;
- actionbar context for countdown, gate HP, War Camp capture, fortress invasion and Last Stand;
- fortress-invasion alerts;
- Core-destroyed / Castle Captured / win messaging;
- bell/anvil sound cues resolved through stable registry IDs.

Match stage labels escalate with total scored captures:

- **SKIRMISH** — 0-3 total points
- **SIEGE** — 4-8
- **TOTAL WAR** — 9-13
- **FINAL ASSAULT** — 14+

These stages currently communicate escalation; they do not grant the leader a hidden statistical advantage.

## Solo-test NPC mode

When only one Minecraft client is available, the mod can run the same scoring state against a server-side combat NPC.

- NPC is a persistent Vindicator named `Castle Bot [RED]` or `Castle Bot [BLUE]`.
- It owns a real entity UUID in the normal `MatchState`.
- It stays defensive inside its castle and attacks an invading human.
- It follows the same Core -> defender defeat -> point rule.
- Killing it before its Core falls gives no point.
- It is frozen/invulnerable during PREPARE/reset phases and restored for each new assault.

Commands:

- `/wars test start` — play RED against a BLUE Castle Bot.
- `/wars test start red` — same explicitly.
- `/wars test start blue` — play BLUE against a RED Castle Bot.
- `/wars test score` — show stage, score, Core states, guard counts, gate HP, War Camp owner and NPC side.
- `/wars test stop` — stop the solo match and clean up managed entities/state.

## Arena source

The project bundles the supplied `medieval_castle.schem` asset and reads Sponge Schematic v3 directly. Players do **not** need WorldEdit installed at runtime.

The original schematic is 168×65×122. The PvP arena uses a focused crop of that castle and creates the opposing base as a 180° mirrored copy for geometric fairness.

The five guard posts were selected from the actual schematic after checking for solid floor and 3×3×3 clearance. BLUE receives mirrored equivalents of the same source posts.

The schematic was exported from a newer DataVersion than Minecraft 1.16.5, so the loader contains explicit compatibility fallbacks for the few newer block states in the asset.

The 19.6 KB schematic is stored byte-exactly as seven Base64 resource chunks inside the JAR and reconstructed in memory at load time.

## Admin commands

- `/wars arena build` — build the mirrored castles, then add the v0.2 battlefield/routes/Core shrines/gates/War Camp.
- `/wars arena status` — show asynchronous schematic-build progress.
- `/wars match start <red> <blue>` — start multiplayer Castle Wars.
- `/wars match score` — show detailed live match state.
- `/wars match stop` — stop and clean up the active match.
- `/wars test start [red|blue]` — start one-client solo testing.
- `/wars test score`
- `/wars test stop`

The large schematic portion of arena construction remains tick-budgeted rather than being placed in one server tick.

## Development stack

- Minecraft 1.16.5
- Forge 36.2.42
- official mappings 1.16.5
- ForgeGradle 6
- Gradle 8.4
- CI build JVM: Java 17
- mod bytecode target: Java 8
- JUnit 5
- GitHub Actions

## Validation status

Forge compilation, JUnit tests and JAR artifact generation are required before a revision is called build-green. In-game geometry, AI pathing, route balance and presentation still require actual runtime playtesting even after CI succeeds.
