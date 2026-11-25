# Named assembly and custom portrait integration (PR #76)

This is a **draft code integration**, not a claim that every named-inigot sword can now be distributed. PR #76 targets the Blade Seal development PR #72 and imports Model Assembly 2.0 from PR #75 without changing main.

## Exact meaning of "backplate"

Blade Tetra's existing named backplate decorates SlashBlade's **inventory durability background portrait**. It is not a physical three-dimensional attachment visible on the player's back. The exporter must keep that native render target and its frame/gauge geometry distinct from the sword's normal OBJ.

## Local picture options

The Blade Seal screen has three modes: `original` (current named portrait if available), `image` (replace with user's local PNG/JPEG), and `overlay` (composite the user's image over a supported named portrait). The image path is held only in the local client screen, never in blade ItemStack NBT or the server-side export snapshot. A local file browser is available where Java AWT supports it; users may also enter a path.

- Input limit: 4 MiB file; accepted extensions `.png`, `.jpg`, `.jpeg`; original dimensions 1..2048 px.
- The client normalizes to a transparent 256×256 portrait, letterboxing to preserve aspect ratio. The selected picture is baked into the generated JAR, and the raw local path is not serialized.
- The generated archive contains `assets/<export-id>/model/sealed_backplate.png` and `sealed_backplate_frame.obj` only when an actual portrait exists.
- The source-free `bt_export_runtime` client hook identifies the exact export namespace from the BladeState translation key. It draws the native durability frame using SlashBlade's texture, then draws the per-export portrait using its own texture.
- User-selected images remain subject to the user's own copyright/redistribution permissions. The generated add-on is still for private modpack use only.
- Cosmetic controls for portrait scale/opacity/glow or 3D wearable attachment are **not part of this first implementation**; do not claim they work from the UI mock-up.

## Named geometry export

`NamedBladeExportAssembler` resolves the inherited blade, full hilt, and sheath source identity from the exact item snapshot, uses Model Assembly 2.0's sockets/views to place each part into native world and inventory groups, and freezes an OBJ and unified PNG atlas. It also emits the damaged, blade-fragment and luminous state groups where the selected source provides them. Missing/invalid mounts require the matching stock-part fallback; missing essential states cause export failure.

Resource identity and embedding rights are separate. This branch intentionally restricts geometry embedding to the project's own namespace and `slashblade` (which has publicly licensed source resources); SJAP/Yakumo and arbitrary resource-pack replacements are **not enabled for export** just because they render in-game. An installed third-party JAR is not a license grant. Source evidence must match the installed mod and final archive manifests.

## Before merging into #72

1. Complete Java 17 build, unit tests and release-JAR verification for the combined source.
2. Source-client acceptance: visual comparison in GUI, first person, third person, damaged and sheathed states, with and without a named source; resource-pack reload.
3. Clean receiving client **without Blade Tetra/Tetra**: verify the exported native item and its texture atlas, the custom portrait and native gauge, reopen/reload, 1.9.63 and 1.9.65.
4. Confirm two generated add-ons with different local portraits coexist without texture leakage or shared runtime collisions.
5. Negative tests: unsupported third-party model, renamed/removed resource, invalid/oversized PNG/JPEG, unsupported source art and unaudited provider overrides must stop packaging and explain why.
6. Check all portrait resources and Atlas bytes in the signed certificate and enforce the existing archive size limits.

Do not merge this Draft into the exporter branch or main until tests that exercise the **new** code paths have passed.
