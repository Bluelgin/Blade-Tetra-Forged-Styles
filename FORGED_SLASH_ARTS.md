# Forged Slash Arts

## Purpose

Forged Slash Arts are Blade Tetra's player-authored counterpart to automatic
legacy fusion.

The authoring object remains the dedicated **Slash Art Orb** edited in a normal
Tetra workbench:

- **Slash Art Core / 斩术魂核** — stable carrier identity for the inscription
- **Primary Technique / 主术式** — native opening graph
- **Secondary Technique / 副术式** — native signature follow-up
- **Technique Modifier / 术式修饰** — splice cadence / routing rule

Applying a complete orb snapshots a versioned `ForgedSlashArtSpec` onto the
target blade. Only one runtime Slash Art is registered:
`blade_tetra:forged_slash_art`.

## Runtime design: graph splicing, not two Slash Arts

The forged runtime no longer plays two complete Slash Arts back-to-back.

It also no longer suppresses native damage and rebuilds combat through a second
Blade Tetra executor.

Instead the sequence is:

```text
native Primary entry
        |
        v
Primary signature callback
        |
        |  splice before source recovery / sheath
        v
native Secondary signature state
        |
        v
SlashBlade continues the native graph
```

This means a combination such as Judgement Cut -> Sakura End is read as one
continuous authored technique:

```text
Judgement opening / dimensional cut
        -> Sakura finishing cross-cut
        -> native Sakura recovery
```

It is not:

```text
complete Judgement Cut
        -> sheath / recover
        -> complete Sakura End
```

## Ownership boundary

Blade Tetra owns only:

1. persisted composition;
2. source technique selection;
3. the safe splice point;
4. the transition into the secondary signature node;
5. an optional Echo re-entry.

SlashBlade owns:

- ComboState animation;
- movement;
- clickAction / tickAction;
- native entities and VFX;
- sounds;
- target selection;
- hit effects;
- damage;
- knockback;
- recovery.

There is no forged damage budget, no native-damage cancellation layer, no
presentation-only entity rewriting, and no forged summoned-sword combat runtime.

`ProceduralSlashArtExecutor` remains only for the conservative programmatic
legacy-fusion fallback and is not used by forged Slash Arts.

## Native source fragments

Primary techniques enter through the real SlashArt selector. Secondary
techniques enter directly at a recognizable native signature state where one
exists.

| Technique | Primary contribution | Secondary splice |
|---|---|---|
| Judgement Cut | native Judgement entry / just entry | `judgement_cut_slash` / just slash |
| Sakura End | native left -> right offensive graph | `sakura_end_right` |
| Void Slash | native Void Slash state | `void_slash` |
| Circle Slash | native four-beat ring | `circle_slash` |
| Vertical Drive | native vertical slash + Drive | `drive_vertical` |
| Horizontal Drive | native horizontal slash + Drive | `drive_horizontal` |
| Wave Edge | native vertical slash + Wave Edge | `wave_edge_vertical` |
| Piercing | native preparation -> rush | `piercing_2` / `piercing_just` |

For same-technique pairs, `ISlashBladeState.updateComboSeq` is deliberately
used even when the target state ID is unchanged. SlashBlade resets
`lastActionTime` and re-runs that state's native `clickAction`, so the
secondary fragment genuinely restarts.

## Splice timing

The runtime waits until the defining native combat callback of the primary
fragment has completed, then hands off before the source art reaches its normal
full recovery.

Current safe completion points mirror Resharped's registered ComboStates:

- Judgement Cut: immediately after the dimensional-cut callback
- Sakura End: immediately after the right/cross cut enters
- Void Slash: after the tick-16 native void attack
- Circle Slash: after the tick-4..7 four-beat ring
- Vertical / Horizontal Drive: after the tick-2..3 native slash + Drive
- Wave Edge: after the tick-2..3 native slash + wave emission
- Piercing: after the opening three rush/area-attack ticks

Foreign ComboStates still cancel the pending splice instead of forcing the
player back into the authored route.

## Modifiers

Modifiers no longer multiply or divide a custom damage budget. Their runtime
meaning is intentionally narrow:

- **Balanced** — standard one-tick breathing room after the safe signature point
- **Condensed** — immediate handoff at the earliest safe point
- **Shatter** — keeps a slightly longer two-tick post-signature beat
- **Spread** — standard native route; no synthetic spread projectiles are added
- **Echo** — re-enters the secondary native signature once
- **Haste** — earliest safe handoff without mutating global ComboState speed

This keeps modifiers inside the routing layer. Any future modifier expansion
should prefer another legal native graph edge over reintroducing a parallel
damage engine.

## Core material

The core remains part of the versioned inscription identity and authoring
recipe, but it no longer scales forged damage.

That is deliberate: once SlashBlade owns combat, the forged system should not
silently rescale native callbacks behind its back. If core progression gains a
combat-facing role later, it should use a native-facing cost/gating mechanic
rather than a second damage calculation path.

## Structural ownership

For ordinary SlashBlade/add-on weapons, inscription remembers and replaces the
previous Slash Art and restores it when cleared.

For Blade Tetra's modular katana, named-blade structure remains authoritative:

1. authored legacy fusion
2. programmatic mixed named-blade fusion
3. orthodox named-blade inheritance
4. forged custom Slash Art
5. previous/external Slash Art

A forged spec may stay stored but dormant while a named structural owner exists.

## Persistence

`ForgedSlashArtSpec` remains the versioned persistence boundary stored on the
blade.

Raw Tetra module state remains on the orb and is not copied into the blade.
Re-editing an orb therefore does not mutate already-inscribed weapons until the
orb is applied again.

## Manual smoke-test matrix

1. Build a complete Slash Art Orb and apply it to a normal SlashBlade.
2. Confirm the blade exposes `blade_tetra:forged_slash_art`.
3. Test Judgement -> Sakura and verify the Sakura right/signature cut begins
   directly after Judgement's attack instead of after a full Judgement sheath.
4. Test Piercing -> Circle and verify Piercing rush damage is the real native
   Piercing callback, followed by the real native Circle ring.
5. Test Circle -> Circle and verify the second Circle state restarts correctly.
6. Test an airborne Judgement/Sakura combination and verify air variants are
   selected where appropriate.
7. Test Jackpot/Just release with Judgement or Piercing in either slot.
8. Test Echo and verify only the secondary native signature repeats.
9. During the primary phase, interrupt with another committed ComboState and
   confirm the pending splice is canceled.
10. Change weapon, inscription, dimension, or die during a pending cast and
    confirm the route is discarded.
11. Verify native damage, knockback, projectiles and hit effects match the source
    SlashBlade states; Blade Tetra should not cancel or replace them.
12. Verify named-blade structural SA/SE ownership still outranks a dormant forged
    inscription on the modular katana.
