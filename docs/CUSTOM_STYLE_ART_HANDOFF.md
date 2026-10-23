# 千门剑谱 · UI / Astra asset handoff

Core implementation provides the editor logic; Astra owns **only** texture assets,
icons, layout mockups and visual polish. Do not edit the Java command, combat,
Tetra or network classes from the art branch.

## Current client interaction

- Keep the existing physical `blade_tetra:style_recorder` object: right-click
  captures one last committed ordinary SlashBlade action, sneak-use opens the editor (Finish in editor mints the scroll)
  and grants a reusable native Tetra scroll.
- `/blade_tetra_style edit` opens the text-first `CustomStyleEditorScreen`.
- The editor shows the current draft's name and ordered ComboState IDs. The
  record button does **not** synthesize ComboStates: players record by playing.
- Controls: rename, move an already-captured action one position up/down,
  remove selected action, undo last, clear, complete as Tetra scroll.
- Server is authoritative. The client renders snapshots from
  `CustomStyleDraftSyncPacket`; actions use `CustomStyleDraftActionPacket`.
- `/blade_tetra_style export` shows the portable `BTSTYLE1:` code from an
  existing completed scroll and supports click-to-copy. The import command
  uses the exact same server validation as future community integration.

## Suggested texture resources (not required for current build)

- `assets/blade_tetra/textures/gui/custom_style/editor_panel.png`
- `assets/blade_tetra/textures/gui/custom_style/attack_row.png`
- `assets/blade_tetra/textures/gui/custom_style/capture_feedback.png`
- `assets/blade_tetra/textures/gui/custom_style/length_short.png`
- `assets/blade_tetra/textures/gui/custom_style/length_standard.png`
- `assets/blade_tetra/textures/gui/custom_style/length_long.png`
- `assets/blade_tetra/textures/item/style_recorder.png` (replace temporary vanilla paper)
- Optional 32×32 icon for the custom Tetra schematic.

These are **candidate** paths, not currently referenced assets. Do not
introduce hard dependencies on unmerged artwork.

## Localized string keys

The editor uses `screen.blade_tetra.custom_style.*`, the recorder uses
`tooltip.blade_tetra.style_recorder.*`, and command responses use
`message.blade_tetra.style_exchange.*`.

Do not bake the style name, action count, preview IDs, tooltips, or received
community names into pixel artwork.

## Visual review acceptance

- Fits ordinary Minecraft GUI scales without occluding client chat.
- Active selection and move/remove operations visually distinct.
- Six actions maximum; readable at 16:9 and 4:3; Chinese labels fit.
- Explicit feedback for an empty draft and a completed Tetra scroll.
- The currently implemented preview is an ordered list, **not** a rendered
  animation or executable combo timeline. An animation preview, if built, should
  share the native ComboState animation definitions, never create new damage.
