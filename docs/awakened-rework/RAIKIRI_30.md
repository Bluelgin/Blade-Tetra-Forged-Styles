# Raikiri 3.0 — native rapid extensions and grounded fields

## Baseline and scope

Branch is forked directly from #41 HEAD `2c4eb4821d182421f11abe1c6e6bd406d1d60047`.
PR base must remain `codex/awakened-blades-v2-runtime`. No merge/cherry-pick of #43, #46 or #47.
The art already inherited through #41/#40 is reused unchanged.

Investigated #43 `6e2e344`: true-name presentation and renderer lifecycle are independent of these combat sessions.
#46 `10e5ace`: its extra `AwakenedBladeVfxClient` mirror pass must survive future manual integration.
#47 `ca012313`: its native timeline audit, native source attribution and bounded secondary damage are relevant;
its Sakura runtime, SA controller, packet definitions and stance changes are NOT imported.
Main was inspected at `72331eb` and is not the combat baseline.

Actual dependency: `gradle.properties` Curse file **8018222**; production `META-INF/mods.toml` reports
**SlashBlade Resharped 1.9.63**. Public bytecode (`javap -c -p`) of `ISlashBladeState`,
`ComboStateRegistry`, `SummonedSwordRenderer` was checked, together with the available upstream source.
No Mixin, reflection, bytecode modification, new input binding or external rendering dependency was added.
New Raikiri abilities fail closed on an unaudited SlashBlade version; ordinary native attacks/SA remain available.

## Gameplay

* Native grounded SNEAK+FORWARD+R_CLICK Rapid Slash candidate upgrades to a registered rapid node.
  NextComboEvent only preflights. Payment/teleport occur from clickAction, after updateComboSeq sets
  the actual node and after BladeMotionEvent has accepted it. Virtual, cancelled or rewritten transitions
  cannot spend Rank or move the owner.
* First step uses the existing native B / 75-point transaction. A confirmed native rapid hit within
  22 ticks authorizes the next step within 36 ticks. Steps two/three are free even below B; the fourth
  requires a fresh payment. Sequences have UUIDs; a delayed slash from step one cannot authorize step two.
  Actual post-event health loss is required, including late LOWEST damage cancellation checks.
* The audited native rapid frames, hold/hit/click/release callbacks, 2/3/4/5/7-tick slash cadence,
  0.44 damage input, powered/critical rolls and pose callbacks are preserved. Only native unrestricted
  `moveRelative` is replaced by up to 3.5 blocks of swept-AABB server-authoritative movement.
  Two alternating registered nodes avoid the native self-next rejection; the old rapid registry ID remains.
* Actual accepted endpoints leave a short electric ribbon. Up to four fixed samples strike from start to
  end 6–10 ticks later. They do not follow fleeing victims, create LightningBolt, change weather/blocks,
  confirm a native rapid hit or recursively award awakened resources.
* Observe the server's committed `aerial_cleave`/`aerial_cleave_loop` while airborne, then native landing
  on a real collision-shape support. Plant one lightweight sword anchor without requiring a hit or Rank.
  Ordinary jumping/landing cannot arm it. The airborne observation has a 100-tick ceiling.
* Up to three owner swords remain 120 ticks. Every 15 ticks each selects at most two nearby hostiles
  with legal block ray paths. No chaining was added: all targets are in the sword's own four-block sphere.
* A real accepted step segment within two blocks of its own sword grants one saturated next-round
  1.5x charge. It neither refreshes lifetime nor creates a sword. UUID de-duplication rejects repeat
  resonance for the same step. Discharge/trail damage has no route back into step/resonance creation.
* V2 Raikiri SA finisher, charge resource and charge HUD are removed. The blade's native SlashArts key,
  native charge submission and native SA damage are untouched. Other souls' signatures/fusions and
  `enableAwakenedV2=false` legacy handlers remain intact. Existing unlock NBT is retained.
* The baseline chooses primary souls in inscription enum order. New abilities additionally require
  that legal primary, preventing a dormant primary from accidentally promoting a Raikiri secondary.
  Existing Crimson Thunder / Mirror Thunder / Thunder Blossom fusion paths remain in their own primaries.

## Responsibilities and bounded state

| Module | Responsibility |
|---|---|
| `RaikiriRapidCombo` | Final native RAPID_SLASH candidate routing and audited native action adapter |
| `RaikiriStepController` | Post-commit Rank/step transaction, sequence-bound hit proofs, cleave observation and lifecycle |
| `RaikiriFieldService` | One scheduler for fixed trails, grounded swords, nearest legal targets, resonance and capped damage |
| `RaikiriRules` | Pure segment geometry, timing, saturated charge/de-duplication and conservative budgets |
| `RaikiriConfig` | Tuning in existing server GameplayConfig spec |
| `RaikiriVisualClient` | Stateless drawing helpers within existing AwakenedBladeVfxClient atlas scheduler |

Sessions bind owner UUID, stable blade UUID, held stack identity, selected slot, dimension and random session nonce;
trails retain that exact session; native hit effects retain the exact step UUID and blade UUID. No temporary
fields/swords are written to permanent NBT. Death, logout, dimension transition, world unload, V2 disable,
invalid weapon/slot and server stop release state. Polling validates on both player-tick phases and server END;
there is no C2S ability packet or location authority. Lifecycle callback coverage is distinct from real network QA.

Hard bounds: 512 sessions, three trails per owner, configurable 128 global trails (max 256), four strikes per trail,
three swords per owner, configurable 256 global swords (max 512), two victims per pulse, 32 collected candidates
per bounded AABB, at most 16 pending native hit proofs per session, 64 remembered resonance IDs per sword.
Selection is nearest legal among the bounded candidate collection; extremely dense (>32 entities) boxes may
exclude a nearer entity outside that collection to preserve server work limits. No full-world entity scan.
No chunk is force-loaded. Sweeps, searches, supports and ray paths require already loaded chunks and legal heights.

Default trail total extra budget **6**, per-target cumulative **3** per step. Default sword round budget **3**,
charged round **4.5**, whole-life budget **12** regardless of charges. Damage attempts reserve budget before
calling hurt, so protection cancellation cannot create unbounded retries. Scoped inherited/secondary guards
prevent resource/skill recursion; LOWEST Hurt and Damage caps constrain third-party amplification.
Rejected/no-health-loss secondary impacts restore prior hurt immunity. No added knockback/stun customization.

## Configuration and protocol

All new configurable values are under `discoveries.raikiri3` in the existing server config:
`trailSamples`, `delayStart`, `delayEnd`, `globalTrailLimit`, `trailDamageBudget`, `trailTargetCap`, `strikeRadius`,
`swordsPerPlayer`, `globalSwordLimit`, `swordLifetime`, `dischargePeriod`, `targetsPerRound`, `swordRange`,
`roundDamageBudget`, `lifetimeDamageBudget`, `resonanceRadius`, `resonanceMultiplier`.
Reversed configured delay endpoints are normalized. Bounds/defaults are defined together in `RaikiriConfig`.
Existing `awakenedRankCostRaikiriStep` and the shared native Rank settings retain their meaning.
`awakenedRankCostRaikiriFinisher` is a deprecated compatibility tombstone, explicitly ignored; it is never
reinterpreted as a trail or sword budget. Old fallback lightning configuration keeps its original meaning.

Existing S2C `AwakenedBladeVfxPacket` gains types 5–8: anchor create, remove, bolt, resonance. No new packet ID.
Protocol is **`raikiri3-1`**, deliberately distinct from other unmerged branches' protocol integers; client/server
must match. Integration must choose a fresh shared protocol after combining packet semantics.
Anchor create is sent once per nearby viewer; a 20-tick viewer check permits later arrivals without resending
static locations. Lifetime is remaining ticks, not a client guess. Remove is keyed by a server visual ID.

Client reuses native `slashblade:model/util/ss.obj` / `ss.png`, public BladeModelManager/BladeRenderState,
white-blue translucent luminous `ss` group. The native model tip (+Z=300) is rotated downward and buried 0.05
blocks in the supported surface. No summoned sword entity/AI/collision is spawned for this display.
Existing Atlas tiles/step_flicker remain the VFX source. Quality 0/1/2 changes arc counts; existing effect cap,
distance and intensity apply, sword models also use frustum culling. World replacement clears the cache.
Graphical appearance/latency/FPS still require actual client acceptance.

## Verification and remaining acceptance

* Standalone Java 17 JUnit: 12 RaikiriRules tests passed, 0 failed (initial implementation).
* Full `test build` and Forge smoke are being verified; consult exact final HEAD CI, not prior #41/#47 runs.
* `./gradlew runServer -PraikiriCombatSmoke` uses a disposable test world (`build/raikiri-combat-smoke`, EULA required).
  Real native capabilities/ComboState routing, virtual/cancel/rewrite transactions, source-attributed hurt,
  real wall collision, native cleave-loop landing callback, field damage, saturation/expiry, late protection,
  amplification and ownership/lifecycle callbacks are asserted. Time and grounded state are controlled by
  the test; this is not natural player input or a multiplayer network/graphical simulation.
* CI retains review JAR, JUnit XML and smoke logs. Never treat previous PR CI as this implementation's result.
* Not accepted yet: first/third person visuals, real input/network feel, real multi-client skill isolation,
  natural physics cleave/landing observation across all five styles, GUI scaling/resource reload, frame times
  at low/medium/high quality, protection-mod combinations and third-party renderers.

## Future integration conflicts

Do not retarget/merge another PR automatically. #43/#46 extract/add common Atlas passes and instrument rituals;
preserve their passes while inserting Raikiri draw helpers. This branch does not edit ritual classes.
#47 edits `AwakenedBladeCombat`, `GameplayConfig`, `ModNetwork`, build/CI and removes its Sakura legacy queues;
resolve by keeping its Sakura-specific blocks and this branch's Raikiri-only removal. Never restore the removed
Raikiri charge/finisher or use Sakura's SA damage provenance for Raikiri confirmation. Packet ID is unchanged,
but new VFX type semantics and protocol string need explicit review. Re-run all native/style smoke profiles
and graphical QA after integration.
