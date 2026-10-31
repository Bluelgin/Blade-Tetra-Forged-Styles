# Raikiri 3.1 — observe native motion, leave lightning behind

## Baseline and correction

PR #48 stays on `codex/raikiri-3-thunder-resonance`, based directly on #41
`2c4eb4821d182421f11abe1c6e6bd406d1d60047`. No merge/cherry-pick or retarget of #43/#46/#47.

The first implementation cloned Rapid Slash and teleported the player. That design is removed.
**SlashBlade exclusively owns input, routing, ComboState, animation, movement, attacks, Hold/Hit callbacks,
recovery and repeat actions.** BladeTetra never executes or edits any of those operations.
No extra Rank payment, three-step counter, free-followup window or native-hit authentication remains.

Removed: `RaikiriRapidCombo`, both `awakened_raikiri_rapid*` registrations, registration bootstrap,
`RaikiriStepController`, copied native timeline/Hold adapter, teleport/safe-end calculation,
step Rank cast/refund, step-chain state and slash-tag authentication. Old saved transient combo IDs
fall through SlashBlade's normal unknown-registry-ID handling; unlock/inscription/weapon identity NBT is kept.
No replacement/tombstone ComboState is registered. The earlier 74-check teleported-step smoke is superseded.

Actual dependency remains Curse file **8018222**, SlashBlade Resharped **1.9.63**. Production bytecode
was inspected with `javap` for `ISlashBladeState`, native Rapid Slash, `AttackManager`, and `RankPointHandler`.
No reflection, Mixin, native bytecode patch, new key binding or second combat executor.

## Passive native action observation

`RaikiriActionObserver` listens to public `BladeMotionEvent` without altering it. This event precedes
`updateComboSeq` writing the state, so it stores only a bounded candidate (event reference, tick, actual position).
At server tick END it rechecks final cancellation/rewrite, held item/slot/UUID/dimension, actual native
`RAPID_SLASH` ID and native `lastActionTime`. Virtual progress cannot authorize a candidate.

A committed state plus a positive, continuous server-observed displacement during its native lifetime
creates the trail. It does not depend on attack hits or projectile authentication. The sampling phase is server
tick END, after normal player/item processing. Cancelled/rewritten/uncommitted motion cannot meet the
post-commit check. A stationary action produces no path. No native entity is spawned, tagged or modified.

The observer reads only raw state, registered timeout and `lastActionTime`. It deliberately does **not** call
`resolvCurrentComboState`, because 1.9.63's resolver may call `updateComboSeq` and native click callbacks.
Native QUICK-to-RAPID transitions and consecutive real commits are identified by their own motion event and
native timestamp. Repeated polls/attacks in the same action cannot create another run. A new committed
native action seals the preceding run; native recovery, Hold transitions, interruption and expiry stop collection.

## Actual movement and limits

Server tick END reads actual player positions. Each accepted consecutive sample is at most one tick apart;
non-finite positions, excessive displacement, unloaded chunks, illegal height/border and collision-obstructed
segments fail closed. There is no look-vector endpoint, fixed dash length, position write or simulated movement
in production. The native sword can travel farther or shorter than the former 3.5-block custom step.

Defaults: at most **32 observed positions / 40 ticks / 12 blocks total**, at most **2.5 blocks between observations**.
These limits stop only this extension; they never clamp native movement. Forge teleport events, large
unannounced jumps, dimension changes, weapon/slot changes, death/logout/world unload/server stop clear state.
Damage interruption ends the active recording without changing the native action. Small third-party direct
position writes without a Forge teleport event cannot always be distinguished from ordinary movement;
all retained segments must still satisfy continuity, distance, loaded-area and collision checks.

`RaikiriFieldService` remains the **single** scheduler. It receives only committed, observed endpoint segments,
shares one budget per native action, and schedules at most four **actual recorded positions**. It never
interpolates fictional strike locations along a start/end shortcut. Consecutive points use 14/16/19/22-tick delays
relative to their own observation time (configured range normalized), with sample spacing 0.35 blocks.
A very short native move produces a short/single-point trace; a stationary action produces none.
The stream remains open until native exit so an already-fired early strike cannot reset its action budget.

Hard bounds remain 512 owner sessions, 128 global trails by default (max 256), three streams per owner,
four scheduled strikes per stream, 256 global swords
(max 512), three swords per owner, two victims and 32 collected local candidates per pulse.
Nearest legal victims are selected within that bounded candidate collection; exceptionally crowded boxes
can omit a nearer entity outside the collection to preserve work/memory limits. No world entity scan,
chunk forcing, C2S location request or decorative AI entity.

## Preserved fields, resonance, damage and Rank

* Native `aerial_cleave` / loop observed airborne, then real supported landing: one grounded sword,
  no hit or extra Rank requirement. Repeated landing events and expired raw state cannot plant twice.
* Three swords per owner, oldest replacement, **200 ticks** of life, pulse every **20 ticks**, sphere radius **4**,
  at most two legal hostiles per pulse. Native `ss.obj` / `ss.png` renderer remains unchanged.
* Actual committed Rapid Slash movement segments within **2 blocks** of an owned sword grant one saturated
  **1.5x next-pulse** charge. Same native-run UUID cannot charge that sword twice; lifetime never refreshes.
  No charge/field damage can author another run or resonance.
* Trail extra budget **6 total / 3 cumulative per victim per native action**. Sword budget **3 per round /
  12 for its whole life**, including resonance. Existing hostile/ally/ray/protection checks, conservative budget
  reservations, Hurt/Damage amplification caps and secondary/inherited recursion guards are retained.
* 1.9.63's native `RankPointHandler` awards Rank on player-attributed `LivingHurtEvent`. Scoped extra impacts
  restore only the owner's native Rank side effects and resync through the native capability, preserving
  natural decay. They do not impose a fee or erase Rank from independent native attacks. If native inventory
  ticking has not retired a stale combo, the extra impact is skipped rather than allowing the native Rank
  callback to advance that timeout. Protection cancellation restores prior hurt immunity.
* Native Slash Arts and other souls/fusions remain intact. V2-off selects the existing legacy handlers;
  temporary new fields are cleared, saved awakenings are retained. Secondary Raikiri never promotes itself
  over a dormant or other legal primary soul.

## Configuration, visuals and integration

Existing server spec, `discoveries.raikiri3`: field budgets/radii/lifetime/limits plus
`maxObservationTicks=40`, `maxObservedSegment=2.5`, `maxObservedPath=12`, `trailSampleSpacing=0.35`.
`discoveries.awakenedRankCostRaikiriStep` and `awakenedRankCostRaikiriFinisher` are explicitly deprecated,
ignored compatibility keys. They are not reinterpreted as lightning damage or sampling values.
Other souls' Rank settings and legacy conductivity settings keep their meaning.

Existing S2C VFX packet types 5–8 and Atlas/model drawing helpers are reused. Anchor create is once per viewer,
remove is keyed by visual ID; later viewers get remaining lifetime. No per-tick static-anchor resync.
Protocol **`raikiri3-native-2`** requires matched client/server. Existing quality/distance/effect budgets apply.

#43 has independent ritual/session/model passes; #46 adds mirror passes in the shared VFX renderer.
Keep those passes during manual integration. #47 overlaps AwakenedBladeCombat/GameplayConfig/ModNetwork,
build/CI and documentation; preserve its Sakura-only logic and this branch's removal of Raikiri SA charge.
Do not restore Rapid Slash replacement, teleports, copied native callbacks or Raikiri Rank fees. Choose a fresh
combined protocol and rerun native/style regression after integration. No other PR is merged or retargeted here.

## Verification and acceptance boundary

The updated tests compare registered **native** Rapid Slash callbacks on two server actors (ordinary and awakened),
including attack count/parameters, native velocity and actual vanilla collision movement. Controlled clock,
gravity and friction make this deterministic; this is not a claim of natural client physics or network acceptance.
They also cover rejected/rewritten/virtual commits, real endpoint samples, local damage/Rank non-recursion,
grounded swords/resonance, cancellation, lifecycle cleanup and ownership. See exact current HEAD CI results.
Old cloned-node/3.5-block teleport/free-three-step assertions are removed.

Still requires real client acceptance: first/third-person motion and lightning lag, natural mouse/input/airborne
landing timing, real multi-client latency/observer rendering, resource reload and low/medium/high frame times.
Dedicated FakePlayer smoke and JUnit are not substitutes. No fabricated gameplay video or FPS result.

## 3.1 pacing and stable residence

Default delays are 14–22 ticks (both configurable 1–80; reversed bounds normalize).
Each endpoint retains its own observation timestamp. Trail expiry includes maximum observation time,
maximum configured delay and two ticks of margin; every new sample also extends the deadline through
its due tick plus margin. Four samples and the shared 6/3 damage budgets are unchanged.
Existing server TOML values remain authoritative: update existing delayStart/delayEnd and
swordLifetime/dischargePeriod explicitly to adopt new defaults, or remove those keys to regenerate.

Ground swords now reside for 200 ticks and discharge every 20. Their round/lifetime budgets stay 3/12;
spent swords remain visible until natural expiry. ss.obj/ss.png, native cleave and 1.5x nonstacking resonance
are unchanged. The model already fades at birth/expiry and the atlas supplies subtle resident arcs;
Rapid STEP ribbons last 26 cosmetic ticks to bridge the default charge wait, without extra packets.

Vanilla player-attributed hurt synchronously calls LivingEntity.knockback. Forge 47.4.0's public
LivingKnockBackEvent fires before velocity writes. The existing scoped Impact now classifies TRAIL_STRIKE
and SWORD_DISCHARGE. Only the latter cancels that event on its own target, only during its hurt call;
finally removes the scope even on exceptions. No velocity overwrite, AI freeze, attribute changes or Mixin.
Trail and independent/native attack knockback are untouched. Protection/Rank/recursion guards are retained.

AwakenedEffectQueue keeps bounded independent admission for ordinary effects and server-owned anchors.
Transient saturation evicts only transient effects. Anchors deduplicate by visual ID and have a hard cap of
512 (the server maximum), retain tick expiry, removal and world-reset handling. Removal packets are handled
before optional-effect/distance filtering, including an out-of-range observer. No tick-by-tick anchor sync.
This is storage policy in the existing VFX renderer, not a second effect engine. Protocol payload is unchanged.

Incremental tests cover delayed final settlement after the former +12 deadline, 20-tick real discharges,
200-tick expiry/whole-life budget, real hurt velocity behavior (sword versus trail/ordinary), and saturated
client queue admission/removal. Existing native action/landing/resonance and cleanup smoke remains.
Graphical readability, fade, natural input/movement and multi-client timing still require manual client QA.

## Optional thunder armor extension

The Rank-free native action guarantees above still apply to Rapid Slash, trails
and grounded swords. A successful sneak-forward-right-click can additionally
activate the Rank-burning Thunder Armor buff at Rank A. Its separate entry/upkeep
costs, vanilla status tracking, rain hop and body arcs are documented in
[RAIKIRI_THUNDER_ARMOR.md](RAIKIRI_THUNDER_ARMOR.md).
