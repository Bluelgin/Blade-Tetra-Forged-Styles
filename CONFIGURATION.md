# Blade Tetra server configuration

Gameplay settings are stored per world in:

`<world>/serverconfig/blade-tetra-server.toml`

The default values reproduce the mod's Alpha 25 balance.

## Discoveries

| Setting | Default | Purpose |
| --- | ---: | --- |
| `enableEasterEggUnlocks` | `true` | Allows new progress and unlocks for Shoshin, Bairen, Bansho, Raikiri, Akatsuki, and NBT Sage. Disabling it never removes existing tags. |
| `smithyClueChance` | `0.32` | Chance for one clue fragment to be injected into a village weaponsmith chest. Range: `0.0`–`1.0`. |
| `enableContractBladeIntegration` | `true` | Enables optional Akatsuki spirit binding when Contract Blade Core is installed. Existing external spirits are not deleted when disabled. |
| `enableDeveloperTools` | `false` | Allows the command-only Legacy Debug Talisman in a packaged game. The exact player name must still be `Dev`; local Forge development runs allow `Dev` automatically. |

## Conductivity

| Setting | Default | Purpose |
| --- | ---: | --- |
| `enablePlayerLightningAttraction` | `true` | Allows a conductive held blade to attract real lightning in exposed thunderstorms. |
| `attractionRollDenominator` | `1200` | Rolled once per second as `conductivity / denominator`. One point averages twenty exposed thunder minutes at the default. |
| `lightningDamageMultiplier` | `0.65` | Incoming lightning damage multiplier while holding a conductive blade. |
| `enableOffensiveLightning` | `true` | Enables controlled lightning procs on exposed targets during thunderstorms. |
| `offensiveChancePerPoint` | `0.02` | Proc chance added per conductivity point. |
| `offensiveChanceCap` | `0.08` | Maximum proc chance for ordinary conductive blades. |
| `raikiriOffensiveChance` | `0.12` | Fixed proc chance for an unlocked Raikiri. |
| `offensiveDamageBase` | `4.0` | Base controlled-lightning damage. |
| `offensiveDamagePerPoint` | `0.5` | Extra damage per conductivity point. |
| `offensiveDamageCap` | `7.0` | Maximum controlled-lightning damage. |
| `offensiveCooldownTicks` | `140` | Per-blade cooldown; twenty ticks equal one second. |

Changing these settings affects server-authoritative gameplay only. The config
does not alter item NBT layouts, recipes, module definitions, or existing world
data.

## Client visuals

Visual settings are stored per client in:

`config/blade-tetra-client.toml`

| Setting | Default | Purpose |
| --- | ---: | --- |
| `enableEmissiveTextures` | `true` | Enables the full-bright material and awakened-inscription overlay. Disabling it preserves the ordinary dynamic texture. |
| `emissiveIntensity` | `0.85` | Opacity multiplier for material emission. Range: `0.0`–`1.0`. |
| `soulGlowIntensity` | `0.90` | Opacity multiplier for awakened soul-inscription veins and legacy-specific glow. Range: `0.0`–`1.0`. |

These options are visual only. They do not change server combat, discovery
progress, blade identity, durability, or saved item data.
