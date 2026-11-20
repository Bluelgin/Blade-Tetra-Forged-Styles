# Blade Seal / OoO Exporter — portability v2

> Development branch based on `OoO/blade-exporter`. This is an **unreleased, draft implementation**, not a promise that arbitrary final swords can be safely exported.

## Product goal

One finished modular sword should be exportable as an individually named Forge add-on: its model, material colors, recipe, base SlashBlade state, supported styles and compatible combat effects survive without requiring Tetra or Blade Tetra **in the receiving modpack**. The original build remains in the author's save; exporting must never mutate the stack.

The exporter is not a general Java-code copier. When a source mechanic is unportable, export must **refuse it with a specific explanation**, never silently strip its ability or create a deceptively weaker sword.

## Two explicit export modes

| Mode | Output | Eligibility | Intended use |
| --- | --- | --- | --- |
| **Full seal** (default) | data-only Forge add-on + separately compiled/reobfuscated `bt_export_runtime` through Forge Jar-in-Jar | Server checks source-only effects, model support, state identity and third-party assets. Currently supports the mapped standard, iaido, rengeki, dangaku and bounded custom-combo roots | Normal finished sword with portable combat |
| **Native-only** | data-only Forge add-on with **no embedded runtime or extra runtime dependency** | Strict subset of Full seal: standard native combo, zero custom attribute modifiers, zero Tetra component effects. No abilities are downgraded | A genuinely plain SlashBlade-compatible sword |

UI toggle changes the package format, not the live held weapon. A single-use server token approves an exact snapshot. The client packages resources only after receiving authorization. Saved files go to `<game-directory>/blade-exports/` with a unique UUID and a fixed `.jar` suffix.

Both modes still require **Minecraft 1.20.1, Forge 47.x, and an installed SlashBlade: Resharped provider**. The present full runtime has been written against **SlashBlade 1.9.63**; larger version ranges must not be declared before separate runtime verification. Neither exported mode declares `tetra` nor `blade_tetra` as a dependency.

## Current feature / compatibility matrix

| Source feature | Full seal | Native-only | Rule |
| --- | --- | --- | --- |
| Native SlashBlade attack, durability, color, native SA, native SE, enchantments | code path present | code path present | All registry and provider references must resolve |
| Regular Blade Tetra modular OBJ / atlas | static baker present | static baker present | Needs the expected alpha9 source model and required rendering groups |
| Iaido / Rengeki / Dangaku | portable combo runtime implemented | refused | Uses exported combo roots, not Blade Tetra at runtime |
| Custom sword style | up to six supported moves | refused | Bounded timing and known action IDs only |
| Quickdraw saya, spirit saya, heavy pommel, stable grip, guard tsuba | runtime handlers present | refused | Preserved only while their runtime profile can represent the effect |
| Precision / reinforced habaki | refused | refused | Rank decay and knockback hooks require explicit standalone ports before enabling |
| Boundary forging, special soul inscriptions, awakened ability chains, source-only SA / SE, Shoshin / Bairen / Bansho / NBT Sage | refused | refused | **Never discard hidden combat mechanics in a release artifact** |
| Named-ingot mixed blade, sheath, guard or backplate | refused | refused | Exact provider geometry and art permissions are unresolved; do not bake a fake replacement |
| Salty Fish modular parts or special saya banner decorations | refused | refused | Dynamic geometry / material projections not yet safely reproducible |
| Resource-pack overrides | verified/rejected | verified/rejected | Selected input bytes must match approved original provider, unless explicitly embedded with documented rights |

This table describes **implemented paths and guards**. It is not a statement of successful end-to-end tests.

## Safety and compatibility decisions

1. Package the generated OBJ and PNG only after confirming their input provenance; keep source license notices. Do not copy arbitrary third-party OBJ/PNG merely because they are installed.
2. Record each archive entry's SHA-256 in `blade-export.json`. The writer additionally reopens and verifies every staged ZIP entry **before making the output file visible**. On filesystems without atomic rename, use a same-directory move of the already verified staged file.
3. Keep `bt_export_runtime` as one versioned, uniquely identified Jar-in-Jar dependency across exports; bump its artifact version only when testing side-by-side mixed exports and old worlds.
4. Source saves must not be mutated or deleted. Every session is one-time, bound to the server-side held ItemStack and its snapshot.
5. Never claim support for Tetra 6.x, other SlashBlade 1.x builds, third-party blade models, AE2, dedicated multiplayer, or original-provider redistribution solely because a local smoke test passes.
6. Reject unrecognized registry IDs, invalid source paths, unknown asset permissions, oversized archives, and custom effects that cannot be represented. Preserve clear user-visible failure messages.

## Required evidence before merging into main

- **Java 17** Gradle build, production JAR / runtime reobfuscation and resource validation.
- Native Forge `exportPackageSmoke`: one-time authorization, snapshot roundtrip, zip hashes, no accidental Tetra dependency, and correct native-only no-runtime package.
- Clean receiving **dedicated server** with only Minecraft, Forge, SlashBlade, exported add-on(s), and test probe: recipe output, save/reload, combo timing, attribute ownership, SA, durability and no missing classes.
- Clean receiving **client** with same dependency boundary: icon, first person, third person, GUI / atlas UV and model orientation.
- Two exports installed together with the **same runtime version**. Confirm no duplicate registry, resource reload or Jar-in-Jar conflicts.
- Reinstall the same export in an old world, remove it, then reinstall; report what Minecraft does to missing named-blade definitions rather than promising reversibility.
- Version matrix (minimum): exporting instance Tetra 6.3.0 and one current 6.x; receiving SlashBlade 1.9.63 and separately 1.9.65; integrated client plus dedicated server.
- Negative cases: named/fish/banner/awakening/boundary/extended habaki all block **before** writing any JAR; malformed item IDs and oversized assets fail without leaving partial output.

Suggested narrow commands from the existing build configuration: `./gradlew build verifyProductionJar`, `./gradlew runServer -PexportPackageSmoke`, `./gradlew runClient -PexportPackageSmoke`. The existing `src/exportRuntimeTest` probes require a **separately constructed receiver environment** (do not count a source-mod run as a standalone pass).

## Remaining implementation milestones

1. **Named mixed fittings**: build a geometry/UV flattening layer that uses *exactly the rendered source* (body, saya, tsuba, backplate, inventory groups), with user-supplied or independently redistributable art inputs and byte-level provenance. Validate source provider rendering and allow opt-in fallback only if clearly labeled as **visually degraded**, never as a faithful export.
2. **Complex combat**: introduce capability-scoped modules for habaki, awakened states, Soul/SA, and source-only styles. Use a stable data schema and explicit runtime hooks. Never implement a generic arbitrary Java-code export.
3. **Compatibility**: verify clean standalone Forge 47/SlashBlade version matrix, dedicated multiplayer, third-party resource packs, reinstall persistence, and mixed exported-mod coexistence.
4. **Player UX**: compatibility report / preview per ability, dependency list and required permissions, plus human-readable failure summary for complex swords.

## Release gate

**Draft only.** The above remaining milestones and clean-room runs must be completed before this exporter is described as a universally portable finished-sword tool.