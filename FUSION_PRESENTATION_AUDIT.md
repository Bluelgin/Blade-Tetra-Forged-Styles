# Programmatic fusion presentation audit

## What failed

The old scheduler inferred signature completion from 35% of the first combo's
animation-plus-recovery timeout, clamped to 3–16 ticks. That is not a source
execution contract. In the **locked SlashBlade 1.9.63 itself**, ordinary grounded
judgement cut enters `judgement_cut_slash` only after the initial 23 animation
frames (~16 ticks). Piercing's initial 32 frames take ~22 ticks and contain no
attack; its attack is in `piercing_2`. Void slash attacks at timeline tick 16;
the old computed delay is 16, racing/cutting its tick window. Circle slash's old
delay is only 5 ticks but its four attacks occupy ticks 4–7. This is not merely
a hypothetical third-party problem.

The old `armed` check compared only the original combo on the first later poll.
An internal transition before that poll could cancel B. After arming it checked
no transitions at all, so a later external interruption could instead be
incorrectly overwritten. This is narrower than saying every A->A2 transition
always cancelled B.

## Dependency lifecycle (source, not method-name inference)

Locked CurseForge file 8018222 is **SlashBladeResharped-1.20.1-1.9.63.jar**.
Source commit: [0c1034e](https://github.com/0999312/SlashBlade_Resharped/tree/0c1034e886ca1e76421ead8d82704bde739edd2e).
Read `SlashArts`, `ISlashBladeState`, `ItemSlashBlade`, `ComboState`,
`ComboStateRegistry`, `SlashArtsRegistry`, and `TimeValueHelper`.

* `SlashArts.doArts` selects a combo for Success/Jackpot/Super; it does not run
  that combo's click or tick callbacks.
* `doChargeAction` posts PerformSlashArtEvent, honors cancellation, and applies
  priority / current-combo checks before `updateComboSeq`.
* `updateComboSeq` posts **cancellable/mutable BladeMotionEvent**, commits the
  selected combo and game-time clock, then calls the selected combo's clickAction.
* Item inventory tick resolves the current combo and runs its tickAction.
* Timeout resolution uses **elapsed ms > getTimeoutMS()**, posts
  NextOfTimeOutComboEvent and calls updateComboSeq for the next combo. Timeout is
  animation frame duration / speed plus extra timeout; it is not signature time.
* `getNext` is an input-dependent callback, not a passive list of chain members.
  The observer never calls it or `getNextOfTimeout` speculatively, never advances
  the chain itself, and never manually ticks a source combo.

## Architecture

`ProgrammaticFusionPresentation` now contains audited **returned-combo routes**,
not a blanket FULL boolean. Each source adds O(1) metadata. A route contains exact
combo IDs, audited animation shape, signature safe points and legal timeout edges.
The runtime rejects missing/changed/looping registry shapes before delegation.
Success, Jackpot, airborne selection, and default Super are separate returned
combo routes. Unsupported returned combos fall back rather than acquiring timing
from a different combo.

`FusionHandoff` observes game time and the real state clock at server tick END:

* **SIGNATURE_WINDOW:** wait until the source's signature ticks have run; ignore
  long recovery or independent entity lifetime.
* **AUDITED_CHAIN:** accept only the recorded next stage, after the predecessor's
  actual timeout; wait for that stage's signature window. An early first
  transition is accepted, not mistaken for a cancelled release.
* Same-combo clock reset, unrelated combo, early transition, cancelled/replaced
  release event, changed stack/state/plan, dimension change, death and disconnect
  cancel the pending response. New casts replace old scheduling even on fallback.
* A bounded watchdog expires to semantic response **without changing A's combo**.
* Exact B runs real doArts -> validated combo -> real updateComboSeq. A cancelled
  or redirected B commit is detected. There is no pending task which cuts off B;
  ordinary SlashBlade inventory ticks and transitions continue it.
* Semantic A uses the same audited native handoff routes. Semantic B is also
  delayed behind A's safe point. Missing/invalid/runtime/linkage failures retain
  semantic fallback; unknown source arts are not executed.

The two-tick timeline margin covers initial inventory-tick offset. These are
source-code safe windows, not a claim that entity spawning cannot be cancelled
by another mod. The shape check detects registry drift, not arbitrary callback
replacement preserving the same shape. Visual verification remains necessary.

## Source audit and current policies

| Source SA | Signature evidence | Handoff |
|---|---|---|
| native judgement_cut, normal ground | no attack in initial stage; next stage tick 0 spawns cut | observed chain, slash-stage age 2 |
| native judgement_cut, air / Jackpot / Super | cut at tick 0 / 1 / 0 | 2 / 3 / 2 ticks |
| native sakura_end, ground and air | left and right click actions are separate cuts | observed right stage, age 2 |
| native piercing, normal | initial stage has no attack; piercing_2 attacks while elapsed <3 | observed second stage, age 4 |
| native piercing, Jackpot | same attack window immediately | 4 ticks |
| native void_slash | attack tick 16, first rotation reset tick 21 | 23 ticks (not full recovery) |
| native circle_slash | four attacks ticks 4–7, rotation reset tick 9 | 11 ticks |
| native drives / wave_edge | independent drive(s) spawned tick 3 | 5 ticks |
| SJAP rapid_blistering_swords | timeline 3 spawns BlisteringSwordsEntity; entity rideTick owns its firing delay, no combo test | 5 ticks |
| SJAP spiral_edge | ticks 4,5,6,7 each spawn an independent EntitySlashEffect | 9 ticks |
| TLS iai_cross | two slashes at timeline ticks 0 and 6 | 8 ticks |
| TLS sakura_blistering_swords | tick 3 spawns riding EntityBlisteringSwords | 5 ticks |
| Recasting void_hole_pitch_black | ExtendedSlashArts tick 0 creates independent JudgementCutEntity, entity tick owns attraction/lifetime | 2 ticks |
| Recasting lightning_chain_3_lambda | tick 0 queues actor TIME_RUN pulse timers; server END advances timers independently of combo | 2 ticks |
| Recasting blade_storm_lambda | tick 0 creates 256 SummondSpiralSwordEntity instances; entity tick owns rotation/lifetime | 2 ticks |

All audited selectors also accept their default native Super returned combo with
its own audit. Nothing forces a second Super: B still uses Jackpot or Success.

Audit sources:

* [SJAP 1.2.16 source](https://github.com/0999312/SlashBlade-Japanese-Addon-Pack-Reshaped):
  SBAComboStateRegistry, SBASlashArtsRegistry, RapidBlisteringSwords, SpiralEdge,
  BlisteringSwordsEntity. The project's optional file is 7039817; verify its exact
  binary in-game. Registry shape mismatches fall back.
* [TLS source e4ad906](https://github.com/0999312/The-Last-Smith-Resharpened/tree/e4ad906ca4eb99b0f9d5c84925ba41465c7f5d30):
  ComboStateRegistry, TLSSlashArtsRegistry, SakuraBlisteringSwords. This available
  1.20.1 source reports **1.1.8**, while the local stress profile requests **1.1.10**.
  The two audited routes require matching registry shapes; 1.1.10 binary parity
  and entity behavior still need explicit runtime validation.
* [Recasting2 1.0.45 source e12c175](https://github.com/this-til/recasting2/tree/e12c175b3cc3735072b6a3c0c4cbddd16d168e4e):
  ExtendedSlashArts, SlashArtsRegistry, the three concrete SlashArts classes,
  JudgementCutEntity, SummondSpiralSwordEntity, LightningChainHelper,
  TimeRunEventHandler, TimeRunManage, CapabilityAttachHandler. The required item
  extensions attach to ItemSlashBlade, which ModularSlashBladeItem extends.
  No optional classes/capabilities are added or invoked by Blade Tetra.

### Honest fallback coverage

**All Yakumo entries are semantic-only**, including `gigantjudgement_cut`,
`spiral_sword_ex`, `thrust_swords`, and the candidate multi-stage `combo_a5`.
Its [official source](https://gitee.com/yakumov/slashblade-yakumoblade) was located
(head displayed ce4556f), but source directories/API/raw files could not be read
in this environment. Their timings and whether combo_a5 really chains are
**unverified**, not inferred from names. No successful lifecycle audit is claimed.

The other four SJAP entries, TLS transmigration_slash/fushigiri, and Recasting
entries other than the three listed above retain semantics but no exact
presentation permission. This deliberately replaces blanket FULL registration.
Unknown add-ons remain semantic/Safe. Tooltips now reflect this narrower allow-list.

## Regression coverage and remaining manual work

11 executable/JUnit lifecycle scenarios cover early signatures, >16-tick windows,
long recovery, real observed two-stage progression, progression before the first
poll, external interruption/restart, bounded expiry, ordered A+B/B+A execution,
B selection/commit/click/future ticks, missing/invalid/cancelled/throwing sources,
and native piercing/void metadata. Additional dictionary tests assert per-source
windows and explicit Yakumo fallback. Bytecode contract tests inspect the actual
locked SlashBlade dependency for click/clock/event/timeout/tick ownership.

The pure lifecycle scenarios use a simulated inventory tick, **not** a Minecraft
world or actual third-party entity spawning. CI build/test is necessary but does
not certify visuals. Test Normal and Jackpot, ground and air, both source orders,
weapon switching/recasting/death, and source response event cancellation.

Priority matrix:

1. native/native: piercing -> void_slash and reverse; judgement_cut -> sakura_end.
2. native/add-on: void_slash -> SJAP spiral_edge.
3. add-on/native: SJAP spiral_edge -> piercing; TLS iai_cross -> judgement_cut.
4. add-on/add-on: SJAP rapid_blistering_swords <-> spiral_edge;
   Recasting blade_storm_lambda <-> void_hole_pitch_black;
   lightning_chain_3_lambda <-> TLS sakura_blistering_swords.
5. Yakumo + native and reverse must visibly identify semantic fallback, not claim
   exact Yakumo presentation. Full Yakumo restoration requires its 1.1.4 audit.

No authored fusions, save format, packet, capability, damage formulas, source
implementation, or A+B registry catalog are introduced or redesigned.
