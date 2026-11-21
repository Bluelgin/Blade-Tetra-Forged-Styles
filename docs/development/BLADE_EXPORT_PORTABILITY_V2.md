# Blade Seal — unified standalone exporter

> Draft development contract for `OoO/blade-exporter` / PR #72. This is an **incremental implementation**, not a release or a claim that arbitrary graduation swords are portable.

## One player-facing operation

A player in creative mode with operator permission holds the Blade Seal opposite the original modular sword, then opens **one** export dialog. They choose the exported weapon/mod name, ID, author, version and crafting grid. There is **no native-only/portable mode switch**.

The pipeline is always:

1. Capture a server-side, nonmutating snapshot of the exact held stack.
2. Run a capability and appearance preflight. A known unportable feature results in a clear disabled export button, **not** a weaker sword.
3. On confirmation, consume a single-use token bound to the player's held stack and session.
4. On the client, validate active resource/provider bytes and legal embedding constraints; bake world and item models, plus their texture atlas, from supported shapes.
5. Encode the native SlashBlade named-blade definition, recipe and language resources, plus a **versioned independent battle profile** and the Forge Jar-in-Jar `bt_export_runtime` provider.
6. Reopen and hash-check all staged JAR entries before publishing a uniquely named file in `blade-exports/`.

Exports never alter or remove the original item. Once exported, the add-on must not require Tetra or Blade Tetra to be installed on the receiving server/client. The single runtime owns only whitelisted data-driven effects; the source mod's Java classes cannot be referenced at runtime.

## Implemented pathways (not yet the whole product)

- Server snapshots: base SlashBlade state, combat style, attributes, enchants, native SA/SE, material appearance, source identity for every named fitting, special traditions, soul records, component effects.
- Portable combat code: named custom moves (bounded at six), Iaido, Rengeki, Dangaku and the component effects explicitly handled by `PortableComponentEffects`.
- Static alpha9 modular OBJ and material PNG baking into generated resources.
- Stable generated identity through `bt_export_<suffix>:sealed_blade`; per-export ID in the certificate; title, author, version, description, recipe, package hashes and provider evidence.
- One embedded `bt_export_runtime` release for **every** generated add-on, including swords with otherwise-native combos. Multiple exports use the same Maven coordinates/version and data is scoped by exact named-blade translation key. The precision-habaki extension bumps the runtime from **1.1.0 to 1.2.0**: both the embedded `META-INF/mods.toml` and Jar-in-Jar metadata must agree. The standalone runtime's version gate allows `[1.9.63,1.9.66)`, while each generated add-on still pins its **exact** source SlashBlade version. Do not assume older 1.1.0-generated JARs can coexist with 1.2.0 add-ons until Forge Jar-in-Jar mixed-version resolution is tested.
- Early **server-side** diagnostic summary and final **client-side** resource verification. Passing preflight is not a guarantee that external resources will validate at packaging.

## Explicit blockers

| Feature | Status | Why |
| --- | --- | --- |
| Modular alpha9 blade with standard / portable style | Implemented; requires clean receiver testing | Static OBJ, PNG and bounded runtime paths exist |
| Mixed named-ingot blade/saya/tsuba + named backplate | **Blocked** | Needs actual geometry/UV flattening, texture provenance and redistributable source art |
| Salty Fish lengths / alternate fish geometry | **Blocked** | Dynamic per-part 3D construction cannot be flattened faithfully by the current static baker |
| Saya banner decorations | **Blocked** | Banner-specific render path is absent from the static atlas baker |
| Precision habaki (Rank decay) | **Portable implementation in review** | Standalone native Rank clock compensates every second protected tick; parity and clean receiver verification still required |
| Reinforced habaki (knockback) | **Blocked** | Actual melee knockback is routed through native/mixin impulse handling; simple numeric approximation would change combat |
| Special forged SA / source-only SE | **Blocked** | No verified independent implementation yet |
| Soul inscriptions, awakening/ritual abilities, Shoshin/Bairen/Bansho/NBT Sage | **Blocked** | Needs persistent state, combat/visual hooks and clean-room behavioral tests |
| Arbitrary external resource pack overrides | **Blocked unless verified** | Source bytes and rights must be attributable; copying an unknown pack would not establish licensing |

**Safety gate:** never treat an external model's availability as permission to redistribute it. The pack's visible rights and any allowed dependencies have to be evaluated before embedding derived OBJ/PNG. Public redistribution is not supported by the current private-modpack export policy.

## Architecture for further implementation

**Visual Baker.** Treat rendering as a scene graph of world/item groups with each source's model, clipping, mount transform, material/UV region and license evidence. Bake the same final geometry seen in game into an exported OBJ and its texture atlas; handle durability backplates and dynamic emissive effects separately. Test 1st/3rd person, icon, sheathed/unsheathed, dual model variations and 2+ named source mods.

**Portable capability registry.** Each supported capability defines its stable ID, version, serializer, game logic and side restrictions. Resolve the blade's features to a graph of exact capabilities and dependencies, then reject unknown or conflicting behavior. No generic code copying, Java scripting or silent fallback. Keep persistent per-blade/player state namespaced by export ID.

**Shared runtime.** Continue `bt_export_runtime`, with one Forge Jar-in-Jar artifact/version for the whole modpack. Never casually ship incompatible runtime classes with the same Maven version. Use schema migrations for future exported worlds, check multiple generated add-ons side by side, and test reload/removal/reinstall.

**Identity.** Preserve the player-selected title, mod metadata, recipe and source provenance. Keep the icon and visual appearance owned by the exported resources, without overriding global SlashBlade textures/definitions.

## Required release evidence

- Java 17 / Forge 47.x build and release-JAR verification.
- Dedicated Forge packaging smoke, including a blocked mixed named-fitting preview and proof that the output always includes the battle profile plus Jar-in-Jar runtime.
- Clean receiving dedicated server and client **without** `blade_tetra` or `tetra`: craft, combo timing, damage, attributes, save/reload, source-restriction and render checks.
- Two+ exported add-ons installed together; mixed-version shared-runtime resolution, world reopen, removal/reinstall.
- SlashBlade: Resharped **1.9.63** verified initially; test **1.9.65** independently before expanding the declared dependency range. Test the exporting environment with Tetra 6.3.0 and a later 6.x.
- Negative export coverage for named mix, backplates, fish, banner, **reinforced** habaki, forged art, and soul/awakening; none may produce a misleading successful JAR. Precision habaki must be positively captured and its Rank timing tested without the source mod.
- Third-party models, resource packs and licenses inspected before any derived assets are embedded.

## Near-term priorities

1. Complete the single-mode and early diagnostic changes with CI.
2. Prototype a source-rights-aware scene-graph bake: geometry and UV verification first, permissions as an enforceable gate, not an afterthought.
3. Validate the newly added precision-habaki portable Rank clock against the original, then port reinforced knockback and optional source-only abilities only with parity tests.
4. Create clean receiving client/server automation instead of counting an exporter-side `runServer` as standalone proof.

**No merge to main or public release until the corresponding behavior is verified.**
