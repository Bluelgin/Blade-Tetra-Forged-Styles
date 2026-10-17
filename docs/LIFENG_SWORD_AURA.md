# 秘传·离锋 / SSS Sword Aura

Experimental standalone passive, separate from Tetra schematic scrolls and native Slash Arts.

- Study the craftable **离锋秘卷** after **the world** has defeated the Ender Dragon. The knowledge is persistent player data and preserved across death.
- An ordinary, real, server-side \`EntitySlashEffect\` emitted while the player is **SSS** launches a flying sword aura immediately, after that slash and before the next beat.
- **STANDARD** uses native slash yaw/roll unchanged. **IAIDO** is swift and single-bladed; **RENGEKI** has crossed decoration on real combo attacks (not neutral sprint visuals); **DANGAKU** has a doubled heavy silhouette. Extra visual-only Drive entities cannot hit.
- No extra attack from entering a combo, clicking without a slash, SA, or an ownerless decorative effect. B1's same-tick crossed pair shares one aura damage budget.
- Damage uses the same native SlashBlade/global multipliers and Forge damage hooks, with no fixed high-stat ceiling. Initial calibration is \`0.95 * max(ceil(Power enchant), 0.1 * effective Attack Damage)\`. This is **not** an assertion that damage always equals precisely 95% of every phantom sword in modded combat; the two growth channels differ.
- A custom Drive subclass inherits movement/rendering but **does not zero target invulnerability time**. Damage is attributed to the player and marked secondary for legacy guards.
- The 8–11 tick native Drive lifetime at 1.7–2.7 blocks/tick is a *lifetime*, not a proven exact 8-block limit; range must be calibrated in game.

## Responsibilities

- \`SwordAuraMastery\`: player unlock and resurrection copy
- \`LifengScrollItem\`: interaction / player messages
- \`SwordAuraTrigger\`: real slash gating and same-tick dedupe
- \`SwordAuraMotionFilter\`: allowlisted ordinary ComboStates, not SA
- \`SwordAuraStyleProfile\`: visual-only style parameters
- \`SwordAuraSpawner\`: immutable per-beat emission geometry
- \`SwordAuraDamage\`: launch-time balance snapshot
- \`SwordAuraDriveEntity\`: projectile-specific hit handling

## Validation needed before merging

1. Dedicated Forge server build, JUnit, item registration, client entity renderer.
2. Verify SSS with each style against idle, C draw, A3 double slashes, native B1 pair, Dangaku circle, aerial and upper slashes.
3. Verify no proc from Rengeki sprint B's ownerless visuals, SA, forged SA, burst effects, no recursion.
4. Compare first-person actual animation to yaw/roll; verify Drive renderer's base-size behavior and heavy dual geometry (a native Drive renderer may ignore size).
5. Check normal server protections/iframes and all relevant damage hooks with high modded attack values.
6. Exact 8-block range and Power vs panel-derived 95% tuning.
