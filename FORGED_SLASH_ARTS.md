# Forged Slash Arts

## Purpose

Forged Slash Arts are the Tetra-first counterpart to Blade Tetra's automatic
legacy fusion system. Automatic fusion remains responsible for native and audited
add-on Slash Arts. Forged Slash Arts deliberately do **not** execute or adapt
third-party SlashArts.

A completed authored art is assembled from four optional Tetra minor modules:

| Slot | Role |
|---|---|
| `slashblade/sa_core` | Mineral-derived total damage budget |
| `slashblade/sa_primary` | Native SlashBlade motion + primary procedural geometry |
| `slashblade/sa_secondary` | Follow-up procedural geometry |
| `slashblade/sa_modifier` | Hit topology, spread, echo or timing |

Only one structural registry entry exists: `blade_tetra:forged_slash_art`.
Combinations are compiled from the blade's Tetra NBT at cast time, so adding
components is O(n) in component definitions rather than O(primary × secondary ×
modifier × material).

## Animation ownership

The eight primary techniques copy only the native Resharped ComboState's visual
metadata (motion resource, frame window, priority, speed, loop/aerial shape and
recovery). Source `clickAction`, `tickAction`, `hitEffect`, transitions and damage
callbacks are not copied. Haste has separate visual-only states at 1.25× animation
speed.

This keeps the player's motion recognizably SlashBlade while Blade Tetra owns all
combat output.

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

Each can be used independently as primary or secondary, giving 64 ordered
technique pairs before core material or modifiers are considered.

## Modifiers

- **Balanced** — unchanged topology and budget.
- **Condensed** — roughly half as many hits, slightly higher total efficiency and
  tighter angles.
- **Shatter** — doubles bounded hit count, but applies a multi-hit tax.
- **Spread** — widens non-radial patterns and trades single-target efficiency.
- **Echo** — repeats the secondary phase after a delay; the same secondary budget
  is divided over both cycles.
- **Haste** — 1.25× primary animation and earlier attack/handoff timing, with a
  small efficiency tax.

Every modifier preserves a bounded total damage budget; adding hit count never
multiplies total power for free.

## Runtime lifecycle

`ForgedSlashArtHandler` snapshots the cast direction and plan, verifies that
SlashBlade committed the selected visual-only ComboState, and schedules the
primary and secondary phases on server tick END. Weapon/plan changes, death,
dimension changes, structural SA changes and foreign ComboState interruptions
cancel the pending cast. Rapid recasts replace the previous pending cast.

All projectile geometry is emitted by the shared `ProceduralSlashArtExecutor`.
The existing programmatic-fusion semantic fallback now uses the same executor,
so fixes to bounded drive geometry apply to both generated systems without
changing exact source-presentation delegation.

## Structural priority

A complete four-part Forged Slash Art intentionally owns the blade's Slash Art
slot ahead of authored/programmatic/orthodox fitting inheritance. Named fitting
Special Effects remain reconciled independently, so choosing a custom Slash Art
does not erase non-SA fitting identity. Removing any forged component restores
the previously displaced Slash Art through the existing structural owner logic.

## Smoke test matrix

1. Install core + primary + secondary + modifier and confirm the blade switches to
   `blade_tetra:forged_slash_art`.
2. Remove any one component and confirm the inherited/previous Slash Art returns.
3. Exercise all eight primary motions; confirm no native source attack callback is
   duplicated and each motion remains visually recognizable.
4. Check at least Judgement→Sakura, Piercing→Circle, Circle→Wave and
   Vertical→Horizontal in both normal and Haste forms.
5. Compare Balanced/Condensed/Shatter/Spread/Echo/Haste and verify hit count,
   spread and timing differ while total damage remains bounded.
6. Change weapon, edit a module, die, change dimension and rapidly recast during
   the primary window; pending secondary attacks must cancel/replace cleanly.
7. Assemble a named-blade fitting with a Forged Slash Art: custom SA should win,
   while fitting Special Effects should remain present.
8. Launch without optional SlashBlade add-ons; the system must behave identically.
