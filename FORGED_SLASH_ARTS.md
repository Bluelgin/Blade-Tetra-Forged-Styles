# Forged Slash Arts

## Purpose

Forged Slash Arts are Blade Tetra's player-authored counterpart to automatic
legacy fusion. Automatic fusion remains responsible for native and audited add-on
Slash Arts. Forged Slash Arts deliberately do **not** execute, wrap or adapt
third-party SlashArts.

The authoring object is a dedicated **Slash Art Orb**. The orb is a Tetra modular
item edited at a normal Tetra workbench. The weapon itself no longer exposes the
four authoring slots.

```text
Tetra workbench
    |
    v
Slash Art Orb
  - Slash Art Core
  - Primary Technique
  - Secondary Technique
  - Technique Modifier
    |
    | use while holding a SlashBlade in the other hand
    v
versioned forged-SA inscription snapshot
    |
    v
any compatible SlashBlade
    |
    v
blade_tetra:forged_slash_art
```

## Why the orb is separate from the blade

The custom system is meant for Tetra-oriented players without making every
SlashBlade weapon structurally become a Tetra weapon.

The four editable modules therefore live only on the orb:

| Orb slot | Role |
|---|---|
| `slashblade/sa_core` | Mineral-derived total damage budget |
| `slashblade/sa_primary` | Native SlashBlade motion + primary procedural geometry |
| `slashblade/sa_secondary` | Native SlashBlade follow-up motion + secondary attack geometry |
| `slashblade/sa_modifier` | Hit topology, spread, echo or timing |

A completed orb is an authoring tool. Applying it to a blade copies a compact,
versioned `ForgedSlashArtSpec` into the target blade's NBT. It does not copy the
orb's raw Tetra module tree. Editing the orb later therefore does not mutate
weapons that were already inscribed.

Only one Slash Art registry entry exists: `blade_tetra:forged_slash_art`.
Combinations are compiled from the stored specification at cast time, so adding
components remains O(n) in component definitions rather than requiring a registry
entry for every primary × secondary × modifier × material combination.

## Applying and clearing an inscription

Hold a complete Slash Art Orb in one hand and any item exposing SlashBlade's blade
state capability in the other hand, then use the orb.

For ordinary SlashBlade/add-on weapons, Blade Tetra remembers the displaced Slash
Art, stores the forged specification and switches the blade to
`blade_tetra:forged_slash_art`.

For Blade Tetra's modular katana, the orb stores only the forged specification and
then asks the existing structural ability synchronizer to reconcile the blade.
Named-blade structure remains authoritative: authored fusion, programmatic mixed
fusion and orthodox inheritance all outrank a forged inscription. The forged spec
stays dormant on the item and becomes active again when those structural owners no
longer apply. This prevents coupled SA/SE packages such as Dead Thought from being
split into an illegal forged-SA + legacy-SE hybrid.

Sneak-use the orb with an inscribed blade in the other hand to erase the custom
inscription. Generic SlashBlade weapons restore the remembered Slash Art; the
modular katana delegates restoration to its structural synchronizer.

The orb is reusable in the first implementation. Proud Soul costs can be added to
the inscription operation later without changing the data model.

## Animation ownership

Both primary and secondary techniques copy only the native Resharped ComboState's
visual metadata: motion resource, frame window, priority, speed, loop/aerial shape
and recovery. Source `clickAction`, `tickAction`, `hitEffect`, transitions and
damage callbacks are not copied. At the compiled handoff point, Blade Tetra commits
the secondary visual-only ComboState and schedules the second attack relative to
that motion. Haste has separate visual-only states at 1.25× animation speed.

This keeps the player's motion recognizably SlashBlade while Blade Tetra owns all
combat output and the Slash Art Core remains authoritative over the total damage
budget.

## Technique set

The first complete set uses the eight base Resharped semantic families already
proven by programmatic fusion:

- Judgement Cut
- Sakura End
- Void Slash
- Circle Slash
- Vertical Drive
- Horizontal Drive
- Wave Edge
- Piercing

Each can be selected independently as primary or secondary, giving 64 ordered
technique pairs before core material or modifiers are considered. The ordered pair
is visible in player motion as well as attack output: A→B now performs A's motion,
hands off, then performs B's motion.

## Modifiers

- **Balanced** — unchanged topology and budget.
- **Condensed** — roughly half as many hits, slightly higher total efficiency,
  tighter geometry and target-focused aim when a valid lock exists.
- **Shatter** — doubles bounded hit count, applies a multi-hit tax and delays the
  latter half into a distinct second micro-burst.
- **Spread** — widens non-radial origins/patterns and trades single-target
  efficiency; true radial techniques remain radial.
- **Echo** — repeats the secondary phase after a delay; the same secondary budget
  is divided over both cycles.
- **Haste** — 1.25× primary and secondary animation, earlier attacks/handoff and
  shorter echo spacing, with a small efficiency tax.

Every modifier preserves a bounded total damage budget; adding hit count never
multiplies total power for free.

## Runtime lifecycle

`ForgedSlashArtSpec` is the persistence boundary. `ForgedSlashArtPlan` validates
and compiles that snapshot into damage, timing, two motion IDs and topology.
`ForgedSlashArtHandler` snapshots the cast direction and compiled plan, verifies
the primary visual-only ComboState, executes the primary signature, commits the
secondary visual-only ComboState at handoff, then schedules the secondary signature
relative to that second motion.

`ProceduralSlashArtExecutor` keeps the conservative Drive-only grammar used by
programmatic-fusion fallback, but forged Slash Arts use richer authored primitives:
native Judgement Cut presentation + bounded phantom swords, localized cross cuts,
summoned-sword fans, radial drives and focused drive families.

Forged Judgement Cut directly reuses SlashBlade's own `EntityJudgementCut`
renderer/model for presentation, but the entity is spawned with no shooter and
zero damage. Blade Tetra discards it at the native ten-tick lifetime boundary
before the entity can enter its burst/potion cleanup path. The surrounding forged
phantom swords therefore remain the only source of Judgement damage. Geometry fixes can still be shared without
letting forged attacks execute source SlashArt callbacks.

Weapon/spec changes, death, dimension changes, structural SA changes, foreign
ComboState interruptions and rapid recasts cancel or replace pending casts.

## Smoke test matrix

1. Put a fresh Slash Art Orb in a Tetra workbench and confirm that only the four
   forged-SA slots are exposed; the modular katana must not expose those slots.
2. Install core + primary + secondary + modifier on the orb and confirm its tooltip
   resolves a complete generated Slash Art.
3. Hold the orb and a vanilla/base SlashBlade in opposite hands, use the orb, and
   confirm the blade switches to `blade_tetra:forged_slash_art`.
4. Repeat with an add-on SlashBlade that exposes the normal blade-state capability.
   No add-on SlashArt callback should be invoked by the forged SA.
5. Re-edit the orb after applying it. Previously inscribed blades must retain their
   old snapshot until the orb is explicitly applied again.
6. Sneak-use the orb on an inscribed generic blade and confirm the displaced Slash
   Art is restored.
7. Apply an inscription on Blade Tetra's modular katana while an authored,
   programmatic or orthodox named fitting owns the SA slot. The forged spec should
   remain stored but dormant; the named structural SA/SE package must stay intact.
   Remove the named owner and confirm the forged SA becomes active again.
8. Exercise representative ordered pairs such as Judgement→Sakura,
   Piercing→Circle, Circle→Wave and Vertical→Horizontal. Confirm both primary and
   secondary player motions visibly occur in order.
9. Verify Judgement Cut shows SlashBlade's native dimensional-rift/slashdim
   presentation in the blade color, surrounded by forged phantom swords. The
   native presentation entity must deal no damage or potion/burst effect by itself.
   Compare Sakura/Void/Circle against the Drive-family techniques and confirm they
   use visibly different primitive families rather than only different Drive angles.
10. Compare Balanced/Condensed/Shatter/Spread/Echo/Haste and verify target focus,
    second-burst timing, spread, echo and Haste motion timing differ while total
    damage remains bounded.
11. Change weapon, die, change dimension, interrupt between motions and rapidly
    recast during the primary window; pending secondary attacks must cancel/replace
    cleanly.
