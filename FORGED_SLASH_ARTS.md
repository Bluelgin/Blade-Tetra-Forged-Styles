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
| `slashblade/sa_core` | Small material modifier on the wielder's attack-derived budget |
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

## Animation and state-machine ownership

Primary and secondary techniques now enter Resharped's **real SlashArt / ComboState
graphs**. Blade Tetra no longer clones one visual node and guesses the remainder of
the timeline. Native `clickAction`, `tickAction`, movement, pose changes, sounds,
timeout edges and recovery nodes are allowed to execute normally, so multi-node
arts such as Piercing and Sakura End retain their complete source choreography.

Blade Tetra still owns combat power. Native entities spawned while a forged graph
owns the player are tagged presentation-only and native direct damage is suppressed.
The bounded forged budget is emitted separately at the technique's signature timing.
The A -> B handoff occurs only after SlashBlade naturally leaves A's native graph;
foreign ComboStates are treated as interruption rather than a valid handoff.

Haste still scales the authored damage timing and efficiency. Native source graph
playback is intentionally not time-warped in this first delegation pass; changing a
global registered ComboState's speed per cast would affect non-forged users and will
need a dedicated per-cast proxy if accelerated native choreography is added later.

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

Forged techniques prefer Resharped's own presentation vocabulary instead of
recreating every Slash Art from Drive projectiles:

| Technique | Native reuse | Forged-owned combat |
|---|---|---|
| Judgement Cut | native `EntityJudgementCut` / `slashdim` renderer | converging phantom-sword halo |
| Sakura End | native `EntitySlashEffect` geometry, 22.5° / 157.5° cross | bounded compact follow-up swords |
| Void Slash | native `AttackManager.newVoidSlashEffect` presentation | bounded phantom-sword fan |
| Circle Slash | native four-beat SlashEffect ring: 180° / 90° / 0° / -90° | radial phantom swords |
| Vertical / Horizontal Drive | native `Drive.doSlash` entity path | forged budget/count |
| Wave Edge | native Drive family with staggered speed/timing | forged budget/count |
| Piercing | native-style forward rush + piercing sound | exact bounded close-range hit |

Native presentation entities are sanitized when their source callback would own
unbounded damage: shooter/owner is detached where necessary, damage is zeroed, and
unsafe terminal callbacks (notably Void Slash's 5.1× finisher) are cut off before
they can run.

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

At cast time, the wielder's current attack damage is snapshotted. The complete
two-phase budget starts at 1.1× that value, with the orb core contributing only
a 0.9–1.1× adjustment. Modifiers preserve a bounded budget; adding hit count
never multiplies total power for free.

## Runtime lifecycle

`ForgedSlashArtSpec` is the persistence boundary. `ForgedSlashArtPlan` validates
and compiles that snapshot into bounded damage, timing and topology.
`ForgedNativeComboFlow` resolves each authored technique back to SlashBlade's
registered SlashArt and identifies the native ComboState graph it owns.
`ForgedSlashArtHandler` snapshots the cast direction and compiled plan, redirects
the PerformSlashArt event into A's native entry, observes the graph until natural
recovery, then enters B's native entry. Echo repeats B by entering the complete
native graph again rather than replaying only a synthetic effect.

`ProceduralSlashArtExecutor` remains the bounded combat layer. During native
delegation it does not respawn the hand-built Judgement/Sakura/Void/Circle cues or
repeat Piercing's movement/sound; those now come from SlashBlade itself. Forged
phantom swords / drives remain available as the authored modifier layer and carry
only the compiled Blade Tetra damage budget.

Forged Judgement Cut directly reuses SlashBlade's own `EntityJudgementCut`
renderer/model for presentation, but the entity is spawned with no shooter and
zero damage. Blade Tetra discards it at the native ten-tick lifetime boundary
before the entity can enter its burst/potion cleanup path. The surrounding forged
phantom swords and one bounded center hit share Judgement's phase budget. Forged
summoned swords keep native flight and rendering, but Blade Tetra owns their
single collision hit instead of accepting native rounding and attack scaling.
Geometry fixes can still be shared without
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
