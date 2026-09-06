# Blade Tetra server configuration

Gameplay settings are stored per world in:

`<world>/serverconfig/blade-tetra-server.toml`

The default values reproduce the mod's Alpha 25 balance.

## Discoveries

| Setting | Default | Purpose |
| --- | ---: | --- |
| `enableEasterEggUnlocks` | `true` | Allows new progress and unlocks for Shoshin, Bairen, Bansho, Raikiri, Akatsuki, and NBT Sage. Disabling it never removes existing tags. |
| `smithyClueChance` | `0.32` | Chance for one clue fragment to be injected into a village weaponsmith chest. Range: `0.0`–`1.0`. |
| `forgingScrollChance` | `0.55` | Chance for one Blade Tetra forging scroll to be injected into a village weaponsmith chest. Range: `0.0`–`1.0`. |
| `enableContractBladeIntegration` | `true` | Enables optional Akatsuki spirit binding when Contract Blade Core is installed. Existing external spirits are not deleted when disabled. |
| `enableDeveloperTools` | `false` | Allows the command-only Legacy Debug Talisman in a packaged game. The exact player name must still be `Dev`; local Forge development runs allow `Dev` automatically. |
| `akatsukiRequiredContribution` | `0.60` | Fraction of a target's maximum health that the same awakened Akatsuki must personally damage before Final Moon can begin. |
| `akatsukiThresholdHitMultiplier` | `4.0` | Number of representative recent hits used to form the dynamic execution line. |
| `akatsukiThresholdMinimumFraction` | `0.06` | Minimum execution line as a fraction of target maximum health. |
| `akatsukiThresholdMaximumFraction` | `0.12` | Maximum execution line as a fraction of target maximum health. |
| `akatsukiExecutionCutDamageFraction` | `0.12` | Damage of each real execution cut relative to recent effective Akatsuki damage. |
| `akatsukiExecutionPulseTicks` | `2` | Ticks between real cuts; clients render several additional visual cuts. |
| `akatsukiExecutionMaintainTicks` | `40` | Time the execution remains powered without another manual Akatsuki hit. |
| `akatsukiExecutionTimeoutTicks` | `160` | Absolute compatibility fuse for an execution that cannot finish normally. |
| `akatsukiExecutionCooldownTicks` | `500` | Per-blade cooldown after Final Moon begins. |
| `akatsukiExecutionMinimumTargetHealth` | `40.0` | Minimum target maximum health for the full execution timeline. |

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

### Mikage: cross-modpack difficulty

| Setting | Default | Purpose |
| --- | ---: | --- |
| `baseHealth` | `560.0` | One-player baseline health before party and equipment scaling. |
| `baseDamage` | `15.0` | Baseline attack damage used by Mikage's techniques. |
| `partyHealthPerExtraPlayer` | `0.65` | Additional health fraction for each participant after the first. |
| `partyDamagePerExtraPlayer` | `0.06` | Small damage increase for each additional participant. |
| `autoDifficultyScaling` | `true` | Reads participant offense, health, armor and toughness when the trial begins. |
| `maximumEquipmentHealthScale` | `1.60` | Maximum health multiplier contributed by participant offensive power. |
| `maximumDefenseDamageScale` | `1.50` | Maximum initial damage multiplier contributed by visible defensive attributes. |
| `maximumSkillSpeedScale` | `1.25` | Maximum skill-frequency multiplier contributed by offensive power. |
| `adaptivePlayerDamage` | `true` | Learns each participant's actual health-and-absorption loss during the fight, covering defenses that do not appear as armor attributes. |
| `adaptiveDamageMaximumMultiplier` | `4.0` | Maximum gradual raw-damage correction for unusually effective modded defenses. |
| `boundaryAssistAfterLowHits` | `3` | Connected low-damage hits before Mikage temporarily tries boundary/magic damage against that participant. |
| `maximumPlayerHealthFractionPerHit` | `0.55` | Absolute safety ceiling for one calibrated hit; individual light and normal attacks use lower ceilings. |

Adaptive damage is tracked separately for every participant. It changes only after a
real, non-invulnerability-frame hit, rises gradually, and falls again when damage is
already sufficient. Successful SlashBlade guard and other intended defensive answers
do not teach Mikage to overpower that response. The boundary assist is temporary, so
ordinary armor remains valuable instead of being permanently ignored.

### Mikage: Eightfold Torii

| Setting | Default | Purpose |
| --- | ---: | --- |
| `cagePulseDamageMultiplier` | `0.28` | Each unguarded pressure pulse as a fraction of Mikage's configured attack damage. |
| `cageGuardDamageMultiplier` | `0.10` | Fraction of pressure damage retained while successfully holding SlashBlade guard. |
| `cageFinalDamageMultiplier` | `0.40` | Extra final burst when the full guard requirement is not met. |
| `cagePerfectGuardTicks` | `50` | Guarded ticks required to shatter the suppression. |
| `cageStaggerTicks` | `60` | Shared damage-window duration after a perfect counter. |
| `cageStaggerDamageMultiplier` | `1.50` | Damage Mikage receives while staggered. |

### Mikage: Pursuing Phantom Sword Rain

| Setting | Default | Purpose |
| --- | ---: | --- |
| `pursuitRainHitThreshold` | `6` | Distinct effective hits required before the highest-pressure player is pursued. |
| `pursuitRainDurationTicks` | `140` | Active pursuit duration; twenty ticks equal one second. |
| `pursuitRainIntervalTicks` | `8` | Delay between locked-position phantom-sword shots. |
| `pursuitRainCooldownTicks` | `360` | Cooldown after the pursuit ends. |
| `pursuitRainDamageMultiplier` | `0.30` | Damage of each impact as a fraction of Mikage's configured attack damage. |
| `pursuitRainFinalDamageMultiplier` | `0.55` | Damage of the final crimson sword if it is neither dodged nor reflected. |
| `pursuitRainStaggerTicks` | `60` | Shared output-window duration after a successful reflection. |
| `pursuitRainStaggerDamageMultiplier` | `1.50` | Damage Mikage receives during that output window. |

### Mikage: interactive sword trials

| Setting | Default | Purpose |
| --- | ---: | --- |
| `stepIaidoOpeningTicks` | `15` | Punish window after the close Step Iaido is evaded. |
| `stepIaidoOpeningDamageMultiplier` | `1.20` | Damage received during that short recovery. |
| `mirrorDuelOpeningTicks` | `40` | Shared opening after winning the active crossing-blades counter. |
| `mirrorDuelOpeningDamageMultiplier` | `1.40` | Damage received during the crossing-blades opening. |
| `boundarySealOpeningTicks` | `60` | Shared opening after all three vermilion seals are broken. |
| `boundarySealOpeningDamageMultiplier` | `1.45` | Damage received during Boundary Sever's recoil. |
| `moonEchoOpeningTicks` | `40` | Shared opening after finding Moonshadow's true body. |
| `moonEchoOpeningDamageMultiplier` | `1.35` | Damage received during the Moonshadow opening. |
| `shadowCrossThreshold` | `3` | Repeated same-direction Shadow-Cross Iaido hits before Mikage reads the route. |
| `shadowCrossWindowTicks` | `120` | Time in which repeated crossings count as one movement pattern. |
| `zanshinWarningTicks` | `14` | Warning before Returning Sakura cuts the predicted exit point. |
| `zanshinDamageMultiplier` | `0.72` | Returning Sakura damage as a fraction of Mikage's configured attack damage. |

- **Step Iaido** now lands roughly two blocks from its target, locks its cut direction and grants a short punish window only when the cut is actually evaded.
- **Mirror Reversal** can be avoided, guarded for reduced damage, or countered by striking Mikage during her crossing dash for a two-second shared opening.
- **Boundary Sever** creates three targetable vermilion seals. Every broken seal reduces the arena-wide finishing cut; breaking all three staggers Mikage for three seconds.
- **Moonshadow** creates three lock-on decoys. The real body carries the deep-crimson blade; finding it grants a two-second shared opening, while striking a pale-bladed echo triggers its cut early.
- **Shadow-Cross Iaido** remains fully valid. Repeating the same crossing route teaches Mikage the exit point; Returning Sakura marks that point before cutting it, so changing direction, sidestepping or guarding remains a valid answer.
- Technique selection now considers range, height difference, held SlashBlade guard/charge, player health and the previous two techniques. At low health, pursuit rain is withheld and interactive techniques receive greater weight.

## Client visuals and audio

Visual and audio settings are stored per client in:

`config/blade-tetra-client.toml`

| Setting | Default | Purpose |
| --- | ---: | --- |
| `enableEmissiveTextures` | `true` | Enables the full-bright material and awakened-inscription overlay. Disabling it preserves the ordinary dynamic texture. |
| `emissiveIntensity` | `0.85` | Opacity multiplier for material emission. Range: `0.0`–`1.0`. |
| `soulGlowIntensity` | `0.90` | Opacity multiplier for awakened soul-inscription veins and legacy-specific glow. Range: `0.0`–`1.0`. |
| `enableIaidoImpactFeedback` | `true` | Enables the brief camera impulse and screen flash for prepared Iaido hits. |
| `iaidoCameraImpactIntensity` | `0.65` | Camera impulse strength. Range: `0.0`–`1.0`. |
| `iaidoFlashIntensity` | `0.55` | Impact-flash opacity. Range: `0.0`–`1.0`. |
| `enableMikageMusic` | `true` | Plays *The Final Trial* after Mikage formally enters combat. |
| `mikageMusicVolume` | `0.78` | Mikage music multiplier, applied in addition to Minecraft's Music slider. Range: `0.0`–`1.0`. |
| `enableMikageBoundary` | `true` | Enables the geometric mirror-realm battle boundary. It does not spawn particles. |
| `mikageBoundaryBrightness` | `1.0` | Brightness of boundary seals, spectral torii and mirror walls. Range: `0.0`–`1.5`. |
| `enableAkatsukiExecutionTint` | `true` | Enables the local crimson-orange world grade during your Final Moon execution. |
| `akatsukiExecutionTintIntensity` | `0.72` | Strength of Final Moon's local world grading. Range: `0.0`–`1.0`. |
| `enableMikageBossBar` | `true` | Replaces only Mikage's vanilla boss bar with the torii-themed custom HUD. Disable for boss-bar overhaul compatibility. |
| `mikageBossBarScale` | `1.0` | Custom boss-bar scale. Range: `0.65`–`1.35`. |
| `mikageBossBarOpacity` | `0.94` | Custom boss-bar opacity. Range: `0.25`–`1.0`. |
| `showMikageTechniqueBar` | `true` | Shows the progress bar for Mikage's signature and interactive trial techniques. |
| `reduceMikageHudMotion` | `false` | Disables flowing highlights, damage shake and phase-change shake. |
| `enableBladeCombatVfx` | `true` | Enables custom clash, tracked blade trail, seal-link, phase and defeat geometry. Core combat logic is unaffected. |
| `bladeCombatVfxIntensity` | `1.0` | Brightness and size multiplier for blade combat effects. Range: `0.0`–`1.5`. |
| `bladeCombatVfxQuality` | `1` | Technique detail: `0` low, `1` medium, `2` high. Telegraphs remain visible at every level. |
| `bladeCombatVfxDistance` | `128.0` | Maximum client creation distance for technique effects. Range: `24.0`–`192.0`. |
| `bladeCombatVfxMaxEffects` | `96` | Maximum simultaneous combat-effect timelines. Optional trails are discarded before core telegraphs. Range: `24`–`256`. |
| `enableBladeCombatHighlights` | `true` | Enables the brightest white confirmation flashes. Disable for photosensitivity or a softer presentation. |
| `enableBladeCombatCameraImpact` | `true` | Enables subtle camera impulses for major technique results; first person is automatically weaker. |
| `enableParryCameraImpact` | `true` | Enables the short camera impulse when the local player participates in a parry. |
| `bladeCombatCameraIntensity` | `1.0` | Multiplier for optional combat camera impulses. Range: `0.0`–`1.5`; `0` disables movement. |

These options are client-side only. They do not change server combat, discovery
progress, blade identity, durability, or saved item data.

## Material providers

Blade Tetra does not generate materials from `forge:ingots/*` and has no
material compatibility blacklist. Tetra owns vanilla material data, while
dedicated providers such as More Mod Tetra (MMT) own third-party material
stats, processing requirements, repair ingredients, and effects.

MMT is optional. Without it, Tetra's built-in materials and Blade Tetra's own
potato material remain available. With it, all compatible Tetra material
categories are accepted automatically; no Blade Tetra setting is required.

The pre-1.3.0 third-party metal definitions remain hidden so existing weapons
continue to resolve their original material keys. They are not offered as new
workbench outcomes.
