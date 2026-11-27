# Blade Tetra configuration

Gameplay settings are stored per world in:

`<world>/serverconfig/blade-tetra-server.toml`

For the current neutral (1.0) combat multipliers and their scope, see [Combat balance configuration](docs/guides/COMBAT_BALANCE_CONFIG.md).

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
| `akatsukiExecutionCooldownTicks` | `500` | Per-blade cooldown after Final Moon begins or the blood threads first execute a target. |
| `akatsukiExecutionMinimumTargetHealth` | `40.0` | Minimum target maximum health for the full execution timeline. |

## Forging treatises

| Setting | Default | Purpose |
| --- | ---: | --- |
| `forgingTreatises.damageMultiplier` | `1.0` | Scales the attack damage bonuses from the flat edge (6%), thin edge (10%), hon-sanmai (12%) and shihozume (8%). Range: `0.0` to `10.0`. A value of `0.0` disables these four bonuses. |

Composite forging consumes 3, 5 or 8 pieces of secondary metal and uses 50%, 75% or 100% of its eligible effect. Recipes, hammer requirements and material data are defined in Tetra datapacks. The default inheritance priority is stun, bleeding, armor penetration, then stabilizing. A Tetra material's `features` list can override this with one of:

- `blade_tetra:forging_core/level/<effect-key>` scales the effect level while retaining its efficiency. Tetra's bleeding instead scales its trigger chance.
- `blade_tetra:forging_core/efficiency/<effect-key>` scales the effect efficiency while retaining its level. Use this for effects whose chance is encoded as efficiency.
- `blade_tetra:forging_core/none` disables inheritance for that material.

The selected effect must already be defined on the material. If a blade already has that effect, the stronger contribution is used.

## Raikiri 3.1 (awakened V2)

Requires `discoveries.enableAwakenedV2=true`; false keeps the original legacy handlers.
New values live in `discoveries.raikiri3`. Full behavior and bounds: [Raikiri 3.1](docs/awakened-rework/RAIKIRI_30.md).

| Settings | Defaults | Purpose |
| --- | --- | --- |
| `trailSamples`, `delayStart`, `delayEnd` | `4`, `14`, `22` | Actual path samples and delayed strike ticks |
| `globalTrailLimit`, `trailDamageBudget`, `trailTargetCap`, `strikeRadius` | `128`, `6`, `3`, `1.25` | Bounded active trails, shared damage, per-victim cap, local strike radius |
| `swordsPerPlayer`, `globalSwordLimit`, `swordLifetime` | `3`, `256`, `200` | Owner/global anchor limits and lifetime |
| `dischargePeriod`, `targetsPerRound`, `swordRange` | `20`, `2`, `4` | Pulse interval, target count and local search radius |
| `roundDamageBudget`, `lifetimeDamageBudget` | `3`, `12` | Per-pulse and absolute whole-life damage caps |
| `resonanceRadius`, `resonanceMultiplier` | `2`, `1.5` | Real step-segment trigger radius and one saturated next-pulse boost |

Native Rapid Slash and its lightning extension have no additional Rank gate or payment.
`discoveries.awakenedRankCostRaikiriStep=75` is also a deprecated, ignored compatibility key.
`awakenedRankCostRaikiriFinisher=240` is a deprecated, ignored compatibility key; native SA has no Raikiri extra cost.
Observation limits: `maxObservationTicks=40`, `maxObservedSegment=2.5`, `maxObservedPath=12`, `trailSampleSpacing=0.35`.
They limit recording only, never native movement.
Conductivity/legacy lightning values below retain their old meaning and are not used as Raikiri 3.0 budgets.

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

## Client visuals and audio

Visual and audio settings are stored per client in:

`config/blade-tetra-client.toml`

The three `swordAura*` settings below are in `[visuals]`. See [Lifeng performance details](docs/guides/LIFENG_SWORD_AURA.md#sword-aura-performance-pass) for behavior and limits.

| Setting | Default | Purpose |
| --- | ---: | --- |
| `swordAuraDetail` | `2` | Lifeng detail: `0` main layer only, `1` balanced, `2` full. Changes decoration only, never attacks or damage. |
| `swordAuraDecorationDistance` | `48.0` | Optional aura-layer camera distance. Range: `8.0`–`128.0`; balanced detail halves this distance. |
| `swordAuraMaxDecorations` | `32` | Optional aura layers per frame. Range: `0`–`128`; balanced detail halves this budget (minimum 1 when nonzero). Main projectiles remain visible. |
| `enableEmissiveTextures` | `true` | Enables the full-bright material and awakened-inscription overlay. Disabling it preserves the ordinary dynamic texture. |
| `emissiveIntensity` | `0.85` | Opacity multiplier for material emission. Range: `0.0`–`1.0`. |
| `soulGlowIntensity` | `0.90` | Opacity multiplier for awakened soul-inscription veins and legacy-specific glow. Range: `0.0`–`1.0`. |
| `enableIaidoImpactFeedback` | `true` | Enables the brief camera impulse and screen flash for prepared Iaido hits. |
| `iaidoCameraImpactIntensity` | `0.65` | Camera impulse strength. Range: `0.0`–`1.0`. |
| `iaidoFlashIntensity` | `0.55` | Impact-flash opacity. Range: `0.0`–`1.0`. |
| `enableAkatsukiExecutionTint` | `true` | Enables the local crimson-orange world grade during your Final Moon execution. |
| `akatsukiExecutionTintIntensity` | `0.72` | Strength of Final Moon's local world grading. Range: `0.0`–`1.0`. |
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

## 镜花水月4.0

服务器旧 `[kyouka3]` 名称保留用于配置迁移。`startCost`、`drainPerSecond`、`actionCost` 已弃用且忽略；`range`（默认32格）、`serverSessions`（64）、`worldSessions`（32）继续限制状态/结算。SSS追忆：60刻历史、10刻延迟、600刻冷却、总75%。水镜衣：原版airSupply；水斩14刻/40%、最多8份预约、每动作首个成功窗口；卸力最高30%、60刻间隔、200刻内最多6HP、成功另耗20空气。参数目前固定在有界运行时中，不复用弃用Rank价格。

客户端与服务端必须同时更新到 `awakened2-integrated-1` 协议。原版氧气、Buff和呼吸增强负责续航，没有新增能量条。详见 `docs/awakened-rework/KYOUKA_40.md`。

## 千本樱 3.0

旧 `awakenedSakuraFlowDrainPerSecond` 键保留，仅用于散华无限连舞 SA；普通散华攻击不额外燃烧 Rank。新增 `sakuraSaMaxTicks=1200`、`sakuraConcertoInterval=8`、`sakuraConcertoDamageFraction=0.25`、`sakuraConcertoDamageCap=6.0`、`sakuraSaStageDamage=8.0`。详细作用和上下限见 [千本樱维护说明](docs/awakened-rework/SAKURA_30.md)。

### Raikiri 3.1 pacing

Under `discoveries.raikiri3`, defaults are `delayStart=14`, `delayEnd=22` (each 1–80 ticks),
`swordLifetime=200` and `dischargePeriod=20`. Existing TOML values are preserved; edit these four keys
or remove them to regenerate the new defaults. Delays belong to each actual server path sample;
reversed delay endpoints normalize. Trail/sword budgets remain 6 total / 3 per trail victim and
3 per sword round / 12 per sword lifetime; exhausted swords remain visible but cannot exceed that budget.
Sword discharges suppress only their scoped knockback. Native attacks and path strikes retain theirs.
