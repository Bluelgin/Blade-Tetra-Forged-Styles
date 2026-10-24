# Named Imprinting V2

## Goal

Named-blade addons are optional providers. A provider with malformed data, an unusual renderer, a very large model, a removed dependency, or hundreds of named blades must never become a world-load or player-login dependency for Blade Tetra.

The compatibility rule is:

> Unsupported named-blade content may lose imprint support, but it may not prevent the world or player from loading.

## Architecture

### Server discovery is metadata-only

`NamedLegacyCatalog` scans only standard `data/<namespace>/slashblade/named_blades` definitions and the recipe directories belonging to namespaces that actually contributed named blades.

The server does **not** open or partition third-party OBJ geometry during automatic discovery. Recipes are optional enrichment only; failure to understand a recipe falls back to the ordinary proud-soul material.

Definitions are isolated per blade. Invalid ids, malformed render metadata, conditionally disabled entries, non-OBJ/custom renderer entries and conflicting duplicate ids are diagnosed and skipped without invalidating unrelated providers.

### Client geometry is lazy

A physical client installs `LegacyClientModelAnalysis` as the optional geometry resolver. It analyses SlashBlade's already parsed `WavefrontObject`, with explicit group/face/vertex-reference budgets.

If the model cannot expose a safe blade/hilt/saya partition, the source is treated as visually unsupported on that client. New research is refused by the client UI gate and existing soft references fall back to ordinary Blade Tetra fitting visuals. Dedicated servers still retain the source identity for gameplay/state reconciliation.

`LegacyModelAnalysis` remains as a bounded raw-OBJ utility for development/tests and explicit adapter workflows; automatic server discovery no longer depends on it.

### Two generic Tetra variants

V1.5 generated one saya and one hilt module variant for every discovered named blade. V2 always uses only:

- `legacy_saya/imprinted`
- `legacy_tsuba/imprinted`

The selected source is stored separately in the Blade Tetra stack tag:

```text
blade_tetra_named_imprints
  saya  = <catalog id>
  tsuba = <catalog id>
```

Per-blade schematic ids are deliberately retained. This preserves existing Tetra scroll unlocks and lets the existing workbench UI remain the source selector without introducing another screen.

A Tetra crafting effect reads the current schematic key after a saya/tsuba craft. A named-imprint schematic writes its source id; an ordinary replacement clears the old source id. This prevents stale SA/SE/affinity ownership after a fitting is replaced.

### Migration

Existing 1.5.x stacks with variants such as:

```text
legacy_saya/sjap/foo
legacy_tsuba/sjap/foo
```

are migrated through the existing `LegacyCalibration` item-load migration path to:

```text
legacy_saya/imprinted
legacy_tsuba/imprinted
```

while preserving `sjap/foo` in `blade_tetra_named_imprints`.

Provider ids are soft references. If an addon is removed, the id remains available for diagnostics/recovery but the missing part simply falls back; it does not invalidate player NBT.

### Dynamic pack isolation

Per-blade schematics are built into a temporary resource map and committed only after the complete blade resource set validates. A failure for one provider cannot leave a half-generated schematic/module state or poison resources for other blades.

The module variant table is constant-size regardless of whether the instance contains 15, 150 or 500 named blades.

## Compatibility boundaries

Unchanged:

- existing per-blade scroll ids and unlocks
- named-blade study flow and minigame
- imprint affinity formulas
- ordered fusion definitions
- orthodox SA/SE registry validation and inheritance priority
- old fox compatibility ids
- Tetra workbench requirement and material costs

Changed:

- automatic server discovery no longer requires provider OBJ geometry to be parseable
- provider duplicate ids are quarantined instead of depending on mod scan order
- recipe parsing is best-effort
- module variants no longer scale with addon count
- source identity is a Blade Tetra soft reference instead of being encoded in a Tetra variant key

## Regression expectations

Tests should keep these invariants:

1. malformed face indices and non-finite geometry never throw from the bounded raw analyzer;
2. V1.5 per-blade variants migrate idempotently to generic variants;
3. removed-addon source ids survive migration as soft references;
4. replacing an imprinted fitting with an ordinary fitting clears its source identity;
5. a direct schematic smoke test plus the V2 identity outcome still produces a complete matching set;
6. server catalog code never regains a dependency on `LegacyModelAnalysis`;
7. provider count never expands the Tetra module-variant table.

## Runtime test focus

For the reported large addon instance, test at minimum:

- clean world and existing player data with Blade Tetra V2;
- all reported SlashBlade addons enabled simultaneously;
- old 1.5.1 modular blades carrying per-source variants;
- research/craft one standard SJAP blade and one large-addon blade;
- remove one provider after crafting and re-enter the world;
- replace one imprinted saya/hilt with an ordinary Tetra fitting and verify inherited identity disappears;
- resource reload, reconnect and dedicated-server join.

The expected failure mode for an unsupported provider is a diagnostic/unsupported-research message, never `Invalid player data`.
