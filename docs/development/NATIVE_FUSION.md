# Programmatic Fusion 2.0

A confirmed mixed scabbard and guard cast their registered Slash Arts in that order. Each art keeps its original combo, native callbacks, independently spawned entities and delayed effects. An unavailable side is skipped. A failed or interrupted cast does not create a substitute attack.

## Why the previous path failed

The old plan used the first SE or blade name when a named source lacked an SA. Exact-ID dictionaries and animation-shape audits then decided whether a registered art was allowed to execute. Rejected or unknown sources produced built-in presentation combos and procedural drives. An allowed A could be interrupted by a 3–6 tick splice. B entered through `updateComboSeq` alone, leaving out the perform event and release eligibility checks. Super went straight through the wrapper's semantic selector because Resharped's Super entry does not post the ordinary perform event.

## Native release and handoff

- The confirmed fitting pair and imprint snapshots keep their existing storage. At release, the scheduler reads only the explicit SA field of each snapshot or legacy catalog entry, and checks SlashBlade's live SA registry. The two structural dispatchers are excluded as source arts.
- Normal and Just retain `ISlashBladeState.doChargeAction`: charge reception, quick charge, priority and the native `PerformSlashArtEvent`. The first eligible selector runs in that path. SlashBlade applies the normal item cost using the structural fusion art after the state call returns.
- Super hooks the art-selection call inside `SuperSlashArts.releaseSSA`, after native eligibility and durability handling. An explicit Super combo runs with its Super tier. If a different art returns SlashBlade's generic Judgement Cut End, its own Normal variant runs and the event carries the actual Normal tier. Calling the wrapper selector directly cannot trigger Super.
- A and B use the same native `updateComboSeq` implementation. A motion counts as committed only after a non-cancelled `BladeMotionEvent`, a matching combo and a new state write at the current game time. B and Super each emit a perform event for their source, then verify the state and priority again. They do not re-enter item release or repeat its cost.
- Each session follows the native `NextOfTimeOutComboEvent` and resulting motion through to neutral. Cancelled or redirected timeout transitions, external state writes, loss of the held stack, a changed pair, logout, death and dimension changes cancel handoff. The 200-tick limit is a watchdog that only drops the session.
- The server starts each source's real tick callback at frame zero and deduplicates that frame if inventory tick runs in the same game tick. While a fusion is executing, timeline actions read the current source combo's elapsed time instead of the offset shared by the registered timeline object. Clients receive the existing native motion broadcasts.
- Selectors, perform listeners, combo resolution and tick callbacks can read the selected registry object through `getSlashArts()`. The stored key and serialization retain the structural fusion ID. This context ends as soon as the synchronous callback returns. Independent effects and timers keep running as implemented by their add-on.

## Add-on audit and limits

| Source | Observed behavior and policy |
| --- | --- |
| SJAP | Spiral Edge and Fire Spiral have four actions on ticks 4–7 before sheath. Rapid Blistering Swords, Gale Swords, Water Drive and Lighting Swords generate their payload on tick 3. The scheduler checks those frames and waits for native recovery. |
| The Last Smith | Iai Cross attacks on ticks 0 and 6 and has a two-state recovery. Sakura Blistering Swords emits swords on tick 3. Fushigiri has native click actions on its left and right states. Transmigration's A1 attacks on tick 3, while A2/A3 are player-input branches and count as interruptions to an automatic handoff. |
| Recasting | Void Hole and Lightning Chain use ExtendedSlashArts' frame-zero callback and item capabilities. The black hole is an independent entity and Lightning Chain schedules its pulses through TIME_RUN. Soul Sever activates at tick 16 and later actions are left to the native state. The audited variants check their required Recasting capabilities before selection. |
| Recasting Time Beyond | Its charge listener checks the current SA while the item is held and builds a buff before release. It is unavailable in either fusion slot until there is an adapter for that charge lifecycle. Its standalone SA continues through its original route. |
| Recasting Treasure Barrage | The SE listens to perform events and queues another lookup of the held blade's current art 20 ticks later. The fusion scope does not extend to that lookup or replay the fusion. Recasting's existing cooldown decides how it reacts to perform events. Delayed recast of an individual fused source is a separate compatibility case. |
| YakumoBlade | The released 1.1.4 Forge JAR registers native selectors and combo timelines. The public source head has evolved since the release. Runtime attempts use the live registry and conservative timeout chain. Add-on-specific capability, charge, or weapon-class behavior requires further skill-by-skill validation. |

Other registered skills can follow the generic native chain. A skill that demands a different item class, a raw stored SA key, pre-release charge state, or a deferred lookup of the original SA may need its own adapter. A timeout or cancellation only drops the pending work. Independently spawned attacks remain owned by the original add-on.

Sources inspected: [Resharped 1.9.63](https://github.com/0999312/SlashBlade_Resharped/tree/0c1034e886ca1e76421ead8d82704bde739edd2e), [1.9.65](https://github.com/0999312/SlashBlade_Resharped/tree/6e2a0a0), [SJAP](https://github.com/0999312/SlashBlade-Japanese-Addon-Pack-Reshaped/tree/07ab7f0a8b0297d52f3579543aba56717bd1d192), [TLS](https://github.com/0999312/The-Last-Smith-Resharpened/tree/e9e01a7e7a1a35234b7485ec6a354aead6355e51), [Recasting 1.20](https://github.com/this-til/recasting2/tree/e12c175b3cc3735072b6a3c0c4cbddd16d168e4e), [Yakumo source](https://gitee.com/yakumov/slashblade-yakumoblade), [Yakumo release JAR](https://www.curseforge.com/minecraft/mc-mods/yakumoblade/files/7264677). Yakumo JAR SHA-256: `8417e5ddcc7ea5c8dd7547ad86c4cdd53cc2c8736319f8e4e351b7a79d715ed2`.

## Verification

`python3 tools/ci/run_suite.py forging` runs the opt-in Forge server checks. Look for `NATIVE_FUSION_SMOKE_PASS` for live registry selection, original perform and motion events, actual native Drive entities, recursion, cancellation and two actors. `MANUAL_FUSION_SMOKE_PASS` covers workbench confirmation, legacy migration and save/reload. Fake player inventory is ticked through its native item path on an advanced server clock. These checks do not verify a client's animations or any uninstalled add-on.

Client acceptance pairs: SJAP Spiral Edge → TLS Iai Cross, TLS Sakura Blistering Swords → SJAP Rapid Blistering Swords, Recasting Pitch Black → Lambda Lightning Chain, and Recasting Soul Sever → TLS Iai Cross. Verify Time Beyond in each slot skips that source while preserving its usable partner.
