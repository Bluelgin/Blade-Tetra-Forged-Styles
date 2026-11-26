# Blade Seal — unified standalone exporter

> Draft development contract for `OoO/blade-exporter` / PR #72. This is an **incremental implementation**, not a release or a claim that arbitrary graduation swords are portable.

For the current capability matrix and author checklist, see [Blade Studio 2.0 RC](BLADE_STUDIO_RC.md). The earlier sections track the original exporter milestones.

## One player-facing operation

Craft the Blade Sealing Workbench using five planks, an iron ingot and a Proud Soul Ingot. Put a modular blade in its working slot and open Blade Studio. Singleplayer supports survival. On dedicated servers, `[bladeSealing]` in `blade-tetra-server.toml` controls availability and whether only operators may export.

The workbench saves its working slot with the chunk. The depositor can reopen the bench and retrieve the original blade after closing, unloading the chunk, or restarting from a saved world. Breaking the bench drops its contents using normal Minecraft block-inventory behavior. The client keeps project documents in `blade-studio-projects` and publishes verified JARs to `blade-exports`. To reopen a saved layout, enter its ID and select **Open by ID**. Projects keep presentation settings and local picture paths. Exporting a new version requires a current blade in the workbench.

Install the JAR in a compatible Forge instance and restart. Find the weapon in SlashBlade's creative tab, or use `/bladeseal give @s bt_export_<suffix>:sealed_blade`.

The pipeline is always:

1. Capture a server-side, nonmutating snapshot of the exact held stack.
2. Run a capability and appearance preflight. A known unportable feature results in a clear disabled export button, **not** a weaker sword.
3. On confirmation, consume a single-use token bound to the player's held stack and session.
4. On the client, validate active resource/provider bytes and legal embedding constraints; bake world and item models, plus their texture atlas, from supported shapes.
5. Encode the native SlashBlade named-blade definition and language resources, plus a **versioned independent battle profile** and the Forge Jar-in-Jar `bt_export_runtime` provider.
6. Reopen and hash-check all staged JAR entries before publishing a uniquely named file in `blade-exports/`.

Exports never alter or remove the original item. Once exported, the add-on must not require Tetra or Blade Tetra to be installed on the receiving server/client. The single runtime owns only whitelisted data-driven effects; the source mod's Java classes cannot be referenced at runtime.

## Implemented pathways (not yet the whole product)

- Server snapshots: base SlashBlade state, combat style, attributes, enchants, native SA/SE, material appearance, source identity for every named fitting, special traditions, soul records, component effects.
- Portable combat code: named custom moves (bounded at six), Iaido, Rengeki, Dangaku and the component effects explicitly handled by `PortableComponentEffects`.
- Static alpha9 modular OBJ and material PNG baking into generated resources.
- Stable generated identity through `bt_export_<suffix>:sealed_blade`; per-export ID in the certificate; title, author, version, description, package hashes and provider evidence.
- One embedded `bt_export_runtime` release for **every** generated add-on, including swords with otherwise-native combos. Multiple exports use the same Maven coordinates/version and data is scoped by exact named-blade translation key. Precision habaki was introduced in runtime **1.2.0**; reinforced habaki plus the shared capability contract are developed in **1.3.0**. The embedded `META-INF/mods.toml`, runtime binary, Jar-in-Jar metadata and export certificate must agree. The standalone runtime's version gate allows `[1.9.63,1.9.66)`, while each generated add-on still pins its **exact** source SlashBlade version. Do not assume old 1.1.0/1.2.0-generated JARs can coexist with 1.3.0 add-ons until Forge Jar-in-Jar mixed-version resolution is tested.
- Early **server-side** diagnostic summary and final **client-side** resource verification. Passing preflight is not a guarantee that external resources will validate at packaging.

## Explicit blockers

| Feature | Status | Why |
| --- | --- | --- |
| Modular alpha9 blade with standard / portable style | Implemented; requires clean receiver testing | Static OBJ, PNG and bounded runtime paths exist |
| Mixed named-ingot blade/saya/tsuba + named backplate | **Draft implementation in PR #76** | Static OBJ/UV/atlas projection and portrait resource output are implemented only for audited source namespaces; clean-client parity and external source permissions are still required |
| Salty Fish lengths / alternate fish geometry | **Blocked** | Dynamic per-part 3D construction cannot be flattened faithfully by the current static baker |
| Saya banner decorations | **Blocked** | Banner-specific render path is absent from the static atlas baker |
| Precision habaki (Rank decay) | **Source-free server verified** | Native Rank, switching/re-equipping and full explicit Rank costs tested on SlashBlade 1.9.63 and 1.9.65 |
| Reinforced habaki (knockback) | **Source-free server verified in v1.3 candidate** | Native melee/slash Mixin hooks preserve horizontal impulse (0.12 ordinary vs 0.66 reinforced) without changing Y or Toss; dual-version independent server proof exists. Co-installation/client parity remains open |
| Special forged SA / source-only SE | **Blocked** | No verified independent implementation yet |
| Awakened soul inscriptions, exclusive abilities, rituals and forms | **Explicit opt-in omission (PR #76)** | Source stack remains untouched; export is reconstructed from a normalized ordinary sword copy. A warning and server-validated player acknowledgement are mandatory. Awakening skills, forms, soul state, art and VFX are not exported. |
| Shoshin/Bairen/Bansho/NBT Sage and non-awakening unsupported abilities | **Blocked** | Not part of the awakening omission; must be implemented before export or explicitly handled in a future scoped consent policy |
| Arbitrary external resource pack overrides | **Blocked unless verified** | Source bytes and rights must be attributable; copying an unknown pack would not establish licensing |

**Safety gate:** never treat an external model's availability as permission to redistribute it. The pack's visible rights and any allowed dependencies have to be evaluated before embedding derived OBJ/PNG. Public redistribution is not supported by the current private-modpack export policy.

## Architecture for further implementation

**Visual Baker.** PR #76 introduces a client-side frozen assembly path. Treat rendering as a scene graph of world/item groups with each source's model, clipping, mount transform, material/UV region and license evidence. Bake the same final geometry seen in game into an exported OBJ and its texture atlas; handle durability backplates and dynamic emissive effects separately. Test 1st/3rd person, icon, sheathed/unsheathed, dual model variations and 2+ named source mods.

**Portable capability registry.** `PortableCapabilityCatalog` is the single Java source rewritten into the independent runtime's package at build time. Each supported capability declares a stable ID, payload key, execution side and first runtime version. The exporter derives an immutable capability list from the exact combo/effect shape; the independent runtime independently re-derives and validates it from the packaged profile on resource load, refusing absent/unknown/conflicting features. This is a **contract foundation**, not yet a full behavior-handler registry for special SA/SE or awakened state machines. Preserve persistent per-blade/player state under stable export identities.

**Shared runtime.** Continue `bt_export_runtime`, with one Forge Jar-in-Jar artifact/version for the whole modpack. Never casually ship incompatible runtime classes with the same Maven version. Use schema migrations for future exported worlds, check multiple generated add-ons side by side, and test reload/removal/reinstall.

**Identity.** Preserve the player-selected title, mod metadata and source provenance. Keep the icon and visual appearance owned by the exported resources, without overriding global SlashBlade textures/definitions.

## Current independent server evidence

- **Runtime v1.3.0 reinforced-habaki candidate:** tested on Forge 47.4.0 with SlashBlade 1.9.63 and 1.9.65 without Tetra or Blade Tetra; five generated add-ons installed together. Both runs printed `BLADE_EXPORT_REINFORCED_KNOCKBACK_PASS` and `BLADE_EXPORT_CLEAN_RECEIVER_PASS` after native ordinary, reinforced and special Toss comparisons.
- Exact measured target horizontal movement: ordinary Z=0.12, reinforced Z≈0.66, special Toss Y=0.75; the reinforced normal slash Y remained 0.04.
- `PortableCapabilityCatalog` contract-validation changes after the above run still require their own CI pass on the latest commit.
- Not yet proof of model correctness, visual effects, server+client synchronized presentation, mixed old/new runtime Jar-in-Jar resolution or source+generated-add-on co-installation.

## Required release evidence

- Java 17 / Forge 47.x build and release-JAR verification.
- Dedicated Forge packaging smoke, including a blocked mixed named-fitting preview and proof that the output always includes the battle profile plus Jar-in-Jar runtime.
- Clean receiving dedicated server and client **without** `blade_tetra` or `tetra`: craft, combo timing, damage, attributes, save/reload, source-restriction and render checks.
- Two+ exported add-ons installed together; mixed-version shared-runtime resolution, world reopen, removal/reinstall.
- SlashBlade: Resharped **1.9.63** verified initially; test **1.9.65** independently before expanding the declared dependency range. Test the exporting environment with Tetra 6.3.0 and a later 6.x.
- Negative export coverage for third-party named art without audited embedding permission, malformed custom backplate pictures, fish, banner, boundary forging, forged art, and non-awakening abilities; none may produce a misleading successful JAR. Awakened soul export must be rejected **without explicit omission consent**, must strip only awakening-scoped content from a copy, and must preserve or separately block all non-awakening abilities. Both precision and reinforced habaki must be positively captured and validated on real source-free combat paths.
- Third-party models, resource packs and licenses inspected before any derived assets are embedded.

## Near-term priorities

1. Complete the single-mode and early diagnostic changes with CI.
2. Validate PR #76's source-rights-aware scene-graph bake with real mixed-component export and a clean client; keep unsupported third-party model sources blocked pending explicit audited rights.
3. Preserve the now-tested precision/reinforced habaki server parity while adding co-installed Blade Tetra + sealed add-on coverage, legacy-runtime version resolution and true client rendering acceptance.
4. Extend the capability catalog with real portable SA/SE handlers, persistent state codecs and awakened traditions one verified feature at a time; keep unsupported weapons blocked.
5. Add separate production-client tests without Tetra/Blade Tetra, rather than counting source development `runClient` as standalone proof.

**No merge to main or public release until the corresponding behavior is verified.**


### Workbench integration

The historical client smoke at `98ac2af2` timed out after the server rejected the confirmation. Running that revision with diagnostics reproduced the rejection before packaging. The player still had operator permission and the original stack in hand. The weapon tick had updated derived tags including `bladeState`, enchantments and legacy caches, so comparing the full stack with the preview copy failed. The workbench removes the weapon from hand ticking while the block owns it, and binds confirmation to that menu, stack identity, frozen contents and current permission.

The Studio opens on the blade preview, name and description. Advanced mode exposes the metadata, art, multilingual text and project panels. Draft identity follows the current workbench deposit and survives world reload while its blade remains in the slot. The comparison screen renders the original through SlashBlade's render events and the result through the baked OBJ and atlas.

| Source feature | Standalone export |
| --- | --- |
| Portable styles and authored moves | Existing runtime dispatch handles Iaido, Rengeki, Dangaku and Custom. |
| Stock fittings with Salty Fish bone or sheath | Static geometry and textures are baked into the package. A fishbone blade carries the original sound and particles in runtime 1.6.0. |
| Salty Fish with an audited salvaged or named guard | Uses the live fish-mouth guard fit and records the model, texture and adapter provenance. |
| Salty Fish with a named blade or saya | Preflight rejects combinations that still need portable seating and coverage. |
| One registered provider Slash Art | Preserves its ID and declares the required provider and version. |
| Programmatic fusion | The independent scheduler supports the audited stock horizontal drive, vertical drive and Judgement Cut. Preflight rejects unaudited sources, including YakumoBlade fusion. |
| Recasting Time Beyond | Requires pre-release charge events. The source compatibility layer skips it. |
| Treasure Barrage | Delayed replay needs source context beyond synchronous dispatch. |
| SJAP, YakumoBlade, The Last Smith: Resharpened and Recasting model resources | Embedding requires licensing and provider evidence for the actual model, texture and adapter. |
| Banner-pattern saya, forged source-only Slash Arts and Boundary Forging | Preflight reports these capabilities as source-dependent. |

Exports pin their embedded runtime version. Use a separate instance for older runtime JARs or re-seal the source blades and replace the old JARs when upgrading to runtime 1.7.0.
