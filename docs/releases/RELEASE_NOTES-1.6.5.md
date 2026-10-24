# Blade Tetra: Forged Styles 1.6.5

## Qianmen: author your own style

- Record the latest eligible ordinary move with the Style Recorder after actually performing it, up to six actions. Repeats are supported, but require another real execution.
- Name, select, reorder, remove, undo or clear recorded actions in the editor, then finish a reusable native Tetra scroll.
- Forge short, standard or long custom blades in the Tetra workbench, with different ordinary-melee reach and safe transition timing.
- Integrated the finished recorder textures, paper-and-ink editor, workbench icons and brief HUD feedback driven by acknowledged recording and compilation results.
- Fixed custom combos becoming stuck on their first action, client/server sequence disagreement and incorrect advancement through repeated actions.

## Compact offline sharing

- Added import, decode preview, export and copy screens. Codes preserve the style name, action order and repeats.
- Replaced the development format with compact prefix-free codes. A four-Chinese-character name with two built-in moves takes about 27 characters, or 32 with six moves; longer names and external actions increase the size.
- Preview dependencies before importing. The server still validates required mods, versions and ordinary-action permissions; a code cannot grant free Slash Arts.
- The unreleased `BTSTYLE1:` format is unsupported. Existing blade and scroll save formats are unchanged by the sharing update.

## Lifeng sword aura

- Retained and refined the aura behavior from 1.6.0hotfix. Learn the craftable Lifeng scroll, then emit auras from real ordinary slashes at SSS, without a dragon-kill prerequisite.
- Added CUSTOM support across all three blade lengths and repeated actions. Slash Arts, harmless decorations and combo-state changes alone cannot emit extra auras.
- Fixed ordinary hurt cooldown swallowing aura contacts while preserving true invulnerability, defenses and cancelled damage. Aura hits add no extra knockback.
- Rengeki crossed and Dangaku doubled silhouettes now render on one physical projectile, reducing decorative entities and redundant client searches.
- Added main-only, balanced and full aura detail, with optional-layer distance and per-frame budgets. These affect decoration only, not damage, range or attack count.

## Other changes

- Mikage combat and rematches are temporarily suspended while the encounter is refined. Existing progress and eligible non-combat visits are retained.
- Reorganized the project overview, guides, development notes, plans and historical records. Release notes now live in `docs/releases/`.

## Installation

For Minecraft 1.20.1 / Forge 47.x. Requires Tetra 6.3.0–6.x, mutil 6.2.0–6.x and SlashBlade: Resharped 1.9.63–1.x.

Replace the previous mod file with `blade_tetra-1.6.5.jar`; do not keep two Blade Tetra versions installed together. Update both clients and the server to 1.6.5 for multiplayer, and back up your world before upgrading.
