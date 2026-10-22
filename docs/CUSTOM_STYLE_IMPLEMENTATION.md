# 千门剑谱 / Custom Style (Draft)

This is an **in-progress implementation**, not a playable finished fifth style.

## Committed recording contract

- Any SlashBlade with an observed, committed combo may contribute its last eligible state.
- Use the Style Recorder after executing an attack: right-click adds **only** the most recent eligible state.
- Repeatedly pressing the tool does not add the same attack twice. Re-perform the attack to capture it twice.
- Sneak + use completes the bounded draft and **immediately grants a native Tetra scroll** (not a consumable upgrade material).
- The scroll's `ScrollData.details` stores a bounded versioned sequence. That is intentional: Tetra's `ScrollTile` strips extra stack NBT when placing an unfolded scroll.
- Scrolls advertise three stable Tetra schematic IDs (short, standard, long), plus a native Tetra crafting effect.\n- The placed intricate scroll is read from **exactly above** the workbench by a dedicated bridge. More than one valid custom scroll makes crafting ambiguous and blocks the upgrade.\n- Workbench crafting snapshots the scroll specification onto the upgraded modular blade via a Tetra CraftingEffectOutcome.

## Not implemented yet

- Real-game validation of dynamic schematic and three blade-module outcomes.
- Detailed combat validation, timing tuning, third-party action compatibility and in-game testing.
- Source mod action support guarantees, client UI and naming.

Never advertise completion until a real Tetra workbench can read the unfolded scroll's sequence and forge a playable custom blade. Preset styles must not be modified during this PR.
