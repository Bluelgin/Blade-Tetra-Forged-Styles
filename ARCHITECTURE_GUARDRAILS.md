# Blade Tetra architecture guardrails

This document records the extension paths that should be used before expanding
named-blade addon support (SJAP, Yakumo and similar packs).

## Why this exists

Several mature classes are intentionally feature-rich and already expensive to
review:

- `client/BladeTechniqueVfxClient.java`
- `client/MaterialTextureManager.java`
- `challenge/MikageEntity.java`
- `challenge/ChallengeManager.java`
- `combat/StyleCombatHandler.java`
- `combat/VoidScatteringFusionHandler.java`

They are not being rewritten in one risky change. Instead, this branch freezes
those hotspots and establishes extension gateways around them. Future features
should grow through the gateways rather than through another branch in a giant
switch or another provider-specific material case.

## Technique VFX

### Existing core techniques

Existing `BladeTechniqueVfxPacket` integer ids and `BladeTechniqueVfxClient`
remain stable for compatibility. Their behavior is not changed by the
architecture refactor.

### New visual families

Use:

- `ModularTechniqueVfxPacket`
- `TechniqueVfxRegistry`

The effect id is a `ResourceLocation`. A feature-specific client class owns its
state, render events and lifecycle, then registers its packet handler with the
registry. Do not add a new integer constant to `BladeTechniqueVfxPacket` just to
support a new addon or fusion.

Recommended shape:

```text
client/vfx/sjap/FrostWolfVfxClient
client/vfx/sjap/KamuyVfxClient
client/vfx/yakumo/...
```

A feature renderer may share low-level geometry helpers, but should not route
its entire lifecycle back through `BladeTechniqueVfxClient`.

## Material visuals

`MaterialTextureManager` remains the atlas compositor and cache owner for the
existing rendering path. It must stay provider-agnostic.

Generic Tetra material information continues to flow through
`TetraMaterialVisualResolver`. When an addon material needs an explicit visual
correction, use `MaterialVisualOverrideRegistry` instead of adding a provider
check to `MaterialTextureManager`.

Rules:

- no `ModList.isLoaded(...)` branches in `MaterialTextureManager`;
- no SJAP/Yakumo/other addon ids in the texture compositor;
- do not copy provider stats or assets;
- use the provider's public Tetra material data first;
- explicit overrides should remain visual only.

## Named-blade addon discovery

`NamedLegacyCatalog` is the generic discovery path. It reads installed
SlashBlade named-blade definitions and recipes, then combines them with
`LegacyModelAdapter` data.

Addon support should therefore prefer:

1. standard named-blade data that auto-discovers with no code;
2. a `data/blade_tetra/legacy_adapters/*.json` correction when an OBJ needs a
   provider/model-specific partition;
3. code only when the addon exposes behavior that cannot be represented by the
   generic catalog/adapter layer.

Malformed optional provider files are tolerated. Debug logging is retained so
an addon audit can explain why a blade was skipped instead of silently hiding
that failure.

## Hotspot budgets

`ArchitectureDebtGuardTest` places generous byte ceilings around known large
legacy classes. These are not normal code-style limits. Their purpose is to
make growth explicit during review.

If a hotspot hits its ceiling, do not raise the ceiling as the first response.
Extract a focused collaborator/controller/renderer and keep behavior identical.

## Mikage / challenge code

`MikageEntity` and `ChallengeManager` are deliberately not rewritten as part of
the addon-extensibility refactor. They are largely isolated from named-blade
addon discovery and are higher-risk gameplay code.

Future cleanup should be behavior-preserving and incremental, for example:

```text
MikageEntity
  -> ToriiSweepController
  -> ToriiCageController
  -> BoundaryFlashController
  -> PursuitRainController
  -> MirrorDuelController

ChallengeManager
  -> ChallengeGateManager
  -> ChallengeSessionManager
  -> ChallengeRealmMaintenance
```

That work should happen in dedicated PRs with targeted regression tests rather
than being mixed into addon compatibility work.
