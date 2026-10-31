# Raikiri Thunder Armor

Only the selected, awakened Raikiri soul can use this skill (single souls work too).
Hold Sneak + Forward and right-click. The original native Rapid Slash must actually
commit: cancelled, rewritten, virtual or uncommitted events cannot grant the buff.
The native action, movement, attacks and Slash Arts remain unchanged.

- Entry requires Rank A (1200 with native unit 300), and pays 60 native Rank.
- Grants vanilla `blade_tetra:thunder_armor` for 160 ticks / 8 seconds, +20% movement
  speed, a normal status icon and countdown. Recasting neither stacks nor refreshes.
- Every 20 ticks, pays 30 Rank and strikes at most two legal nearby enemies within
  four blocks for 2 damage each. No enemies still costs upkeep. Damage uses the
  existing protected secondary-hit path, has no own knockback and earns no Rank.
- In actual exposed rain, at most one additional legal target within three blocks
  of a struck enemy takes 1 damage. Both targets must be exposed; roofs, dry biomes,
  blocked paths and clear weather prevent conduction. No recursive chain.
- Ends at expiry, below Rank B, before an upkeep payment would cross B, on dispel,
  weapon/slot/soul change, death, teleport, dimension change or logout. Reconnect
  clears saved effects without a live owner session. Vanilla effect removal owns
  attribute cleanup. `enableAwakenedRankCosts=false` disables payments, not thresholds.

The body emits cached, local-space branching arcs: 0.5-1 block main strokes,
one short branch per stroke, tapered white cores and wider translucent blue rims.
Arcs live 4-7 ticks with a short fade, spawning at irregular 1-3 tick intervals.
Fresh activation creates a bounded burst; existing successful armor-hit BOLT cues
(variant 2; rain hop variant 3) accent the body and brighten target connections.
There are no extra packet types or per-frame geometry generation. Late tracking
of an already-running effect does not replay its activation burst.

First person renders the newest two arcs in camera-relative lower corners near the
hands, away from the reticle. Third person follows interpolated body position/yaw.
Caps: 16 nearby players, 4 main arcs each (2 on low quality), one branch each
(no branches on low quality); beyond 16 blocks draw only one arc. Standard combat
VFX enable/intensity/quality/distance/effect-limit controls apply. Cached state is
cleared when buffs end, players leave range, VFX is disabled, or worlds change.
No post shader, dynamic world light or screen flashes are added. The status icon
reuses the editable Raikiri SVG via `export_soul_switch.cjs` at 32px.

Validation: `gradlew test build`; `gradlew runServer -PraikiriCombatSmoke` exercises
native commit admission, rank payments, real attributes/effects, exposed-rain hop,
roof/dry rejection, recasting, expiry, dispel, slot/dimension/death/reconnect and real
fusion soul-switch cleanup. Rendering still needs visual acceptance in-game.
