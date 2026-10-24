# 千门剑谱 / Custom Style

Core recording, the client editor, native Tetra scroll forging, combo routing fixes and compact offline sharing are merged into `main` (source version 1.6.5). This page describes the recording/storage contract. See [current player operations and timing](CUSTOM_STYLE_FINALIZATION.md) and the [current share-code protocol](CUSTOM_STYLE_COMMUNITY_PROTOCOL.md). A source version is not a published release announcement.

## Committed recording contract

- Any SlashBlade with an observed, committed combo may contribute its last eligible state.
- Use the Style Recorder after executing an attack: right-click adds **only** the most recent eligible state.
- Repeatedly pressing the tool does not add the same attack twice. Re-perform the attack to capture it twice.
- Sneak + use opens the server-authoritative style editor; its Finish action creates the **native reusable Tetra scroll**. The `/blade_tetra_style finish` command remains as a text fallback.
- The scroll's `ScrollData.details` stores a bounded versioned sequence. That is intentional: Tetra's `ScrollTile` strips extra stack NBT when placing an unfolded scroll.
- Scrolls advertise three stable Tetra schematic IDs (short, standard, long), plus a native Tetra crafting effect.
- The placed intricate scroll is read from **exactly above** the workbench by a dedicated bridge. More than one valid custom scroll makes crafting ambiguous and blocks the upgrade.
- Workbench crafting snapshots the scroll specification onto the upgraded modular blade via a Tetra CraftingEffectOutcome.

## Current boundaries

- A registered action is not automatically permitted: third-party ordinary actions require the trusted compatibility adapter.
- The native editor supports naming, selection, moving, deleting, undoing, clearing, finishing and import/export. See the linked player operations for details.
- UI status and animations use acknowledged state; missing visual interfaces are listed in the [client integration record](custom-style-art/CLIENT_INTEGRATION.md).
- Existing scroll/blade storage is unchanged by the compact sharing format.

## Attack privileges

Any equipped SlashBlade may be sampled, but only registry-backed *ordinary* ComboStates vetted by `ComboCompatibilityResolver` can be written or forged. The initial safe set is native A/B/C combos and selected BladeTetra normal attacks; other add-ons may opt in via `registerOrdinaryAction` after checking the animation's callbacks and resource prerequisites. SA entry states are deliberately excluded to prevent free special attacks through ordinary clicks.

## Forge integration regression

PR CI builds and launches an isolated server with `-PcustomStyleSmoke`. The smoke builds a real Tetra workbench and unfolded ScrollTile above it, checks three schematic unlocks, applies the crafting-effect pipeline to a copied modular blade, asserts custom combo root resolution, and confirms ambiguous nearby scroll data fails closed. Completion is reported only when the log contains `CUSTOM_STYLE_SMOKE_PASS`.
