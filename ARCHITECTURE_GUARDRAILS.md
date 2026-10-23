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

Explicit overrides use the complete provider-aware material key. Do not shorten
`example:metal/steel` to `steel`: different providers may legitimately expose
materials with the same leaf name.

Rules:

- no `ModList.isLoaded(...)` branches in `MaterialTextureManager`;
- no SJAP/Yakumo/other addon ids in the texture compositor;
- do not copy provider stats or assets;
- use the provider's public Tetra material data first;
- explicit overrides should remain visual only.

## Sword attachments and component texture composition

Sword socket/wrap/coating compatibility belongs in `compat/attachments`, not in
`ModularSlashBladeItem` or a provider-specific branch of the renderer. These
adapters retain loaded Tetra module/improvement values; do not introduce a
parallel balance table for gems, fabrics or coatings.

`BladeComponentLayout` owns the physical slot layout. `BladeAttachmentAppearance`
reads native attachment identities without duplicate persisted material NBT.
`MaterialTextureCompositor` now owns the extracted component-painting loop;
`AttachmentFinishPainter` handles the visual finishes. `MaterialTextureManager`
keeps rendering entrypoints and atlas/cache lifecycle responsibilities. New
attachment visuals should extend the focused painter, not grow the manager.

Material/attribute compatibility does not promise every third-party combat
behavior works: providers that require `ItemModularHandheld` need a separate
review because our item must remain an `ItemSlashBlade`.

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

`ArchitectureDebtGuardTest` places generous line-count ceilings around known
large legacy classes. These are not normal code-style limits. Their purpose is
to make growth explicit during review without changing across LF/CRLF platforms.

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

## Style combat ownership

`StyleCombatHandler` is now a Forge-event facade only. It may perform cheap
common filtering and route an event by `BladeStyle`, but style-specific state,
NBT keys, geometry and presentation belong to focused collaborators:

- `IaidoStyleCombat` owns Iaido transient state, draw readiness, spacing,
  deflect timing and Iaido-only slash feedback.
- `DangakuStyleCombat` owns cleave/sweep target geometry, damage tuning and
  armor timing.
- `StyleTargeting` owns reusable geometric predicates.

Do not put a new style's runtime state machine back into
`StyleCombatHandler`. Add a focused style collaborator and keep the subscriber
thin.

## Material style ownership

`MaterialTextureStyleEngine` owns pixel decoration, surface algorithms and
emission behavior. It no longer owns the material identity catalog.

`MaterialStyleCatalog` owns:

- exact built-in material mappings;
- curated addon material mappings;
- Tetra semantic material resolution;
- ordered fallback heuristics and generated palettes.

The ordering in the catalog is behavior. New compatibility rules should be
reviewed there rather than mixed with rendering algorithms.

## Mikage signature controllers

`MikageEntity` remains the Minecraft entity lifecycle and stable public
surface, but authored multi-tick encounter scripts should move into focused
controllers when they become independently reviewable.

Current extracted domains:

- `MikagePursuitRainController` — pressure tracking, rain waves, final return
  sword and counter/stagger path.
- `MikageToriiController` — torii sweep/scissor guard sequence and torii cage
  sequence.

Mutable encounter data stays in the existing state holders
(`MikageTechniqueRuntime`, `MikageArenaController`,
`MikageDefenseController`). Controllers advance that state; they do not create
parallel copies of it. Runtime coordinators call the responsible collaborator directly. `MikageEntity`
keeps Minecraft overrides, synchronized accessors and the public configuration
surface, rather than forwarding every old skill method. Damage filtering goes
through `MikageDamageService` with an explicit `super::hurt` callback; never call
`owner.hurt` from that filter. Calibration, counters, sword-wheel behavior,
arena movement and presentation each have a focused collaborator and line budget.
Existing combat values, counter windows and story hooks remain unchanged by this
extraction.

## Mikage reactive rebuild

The dedicated Mikage rebuild supersedes the incremental-extraction advice above
for **new skills**. `MikageEncounter` owns encounter flow;
`challenge/mikage/ReactiveCombatDirector` owns evidence-based selection;
`SkillRunner` owns a single foreground cast. Each release implements
`SkillExecution` and owns its own target/timing state and `CastScope` resources.

`MikageLegacySkillPool`, `MikageLegacySkillExecution` and
`MikageLegacySkillEffects` are transitional compatibility adapters for the
existing moveset. Do not add the replacement moveset to their switches or grow
`MikageTechniqueRuntime` with more global skill timers. Remaining legacy state
holders retain one copy of old runtime state until those effects are replaced.

`MikagePlayerObserver` only observes server-visible participant behavior;
selection must respect cooldowns, phase protection and counter openings.
Authored `SkillTransition` rules are re-evaluated at legal decision boundaries,
not a promise to execute an entire preselected combo against a changed target.
New skill content and authored links require the author's moveset discussion.

Keep dialogue/reward/visitor behavior behind `MikageStoryBridge` and the
existing `ChallengeSession`. Cancellation is not victory. Recovery restores
the phase without replaying phase dialogue. Persistent arena hazards have
separate lifetime ownership from their originating cast.

See `docs/MIKAGE_REACTIVE_ENCOUNTER.md` for the current migration boundary.

## Combat balance configuration

`CombatBalanceConfig` belongs to the existing SERVER spec. Defaults must stay
neutral (1.0), with no hard dependency on a configuration-menu mod.
`CombatBalanceRules` is registry-free arithmetic; `CombatBalanceRuntime` owns
server-side attack classification and projectile identity snapshots. Do not
copy these multipliers into material tables, animation timings or item stats.
SA and ordinary summoned-sword categories are mutually exclusive. Secondary
effects which inherit an already-scaled budget must use `InheritedCombatDamage`
instead of multiplying that budget again. Recursion guards alone do not mean
that damage has already been balanced. Keep player attacks isolated from boss
and third-party damage, and preserve delayed attacks' originating style.
