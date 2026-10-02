# 攻速效率转换 / Speed efficiency

## 玩家规则 / Player rules

只对持在主手、未破损且未封印的模块化拔刀剑生效，涵盖本传与所有派生流派。
读取玩家最终 `generic.attack_speed`，包含 Tetra 材料、打磨、配重与外部属性效果，不读取 Tetra 私有打磨 NBT。

Only a functional modular blade in the main hand is eligible, for every style including the native style.
The final vanilla attack-speed attribute includes Tetra construction/honing and other attribute modifiers.

- 地面闪避恢复：默认 20 tick，最多缩短到 16；连续次数仍为 3，不改距离或单次无敌。
- 特殊幻影剑准备：默认 10 tick，最多缩短到 6；保留原回调、方向输入判定、剑数、伤害与魂值消耗。
- 普通 SA：默认 9 tick，最多缩短到 6；蓄满提示和精准释放窗口起点一起提前，不扩大窗口。
- Super SA：默认 20 tick，最多缩短到 14；对应准备提示由 5 tick 最多提前到 4。
- Ground-dodge recovery: 20 → 16 ticks; native three-dodge limit, distance and invulnerability remain unchanged.
- Special summoned-sword preparation: 10 → 6 ticks; original callbacks, inputs, sword count, damage and costs remain native.
- Normal SA preparation: 9 → 6 ticks, with consistent readiness feedback and Just-window start.
- Super SA preparation: 20 → 14 ticks; its preparation VFX shifts from 5 → 4 ticks.

普通右键斩击、连段动画、普通单发幻影剑、锁定传送延迟、位置恢复计数、上升身法的落地恢复条件均不修改。
更快的准备依然可能提高施放效率或总体闪避覆盖率；不承诺完全不影响实战强度。

Ordinary right-click combo timelines, single swords, teleport execution delays, position restoration and Trick-Up landing recovery remain untouched.
Faster preparation may still improve cast throughput or overall dodge availability; it is not a promise of zero combat-power impact.

## 参数 / Configuration

服务器配置 `blade-tetra-server.toml` 的 `[speedEfficiency]`：

- `enabled = true`：整体开关 / master switch.
- `referenceAttackSpeed = 1.6`：中性参照，每秒攻击次数；低于参照不惩罚 / neutral attacks-per-second reference, no penalty below it.
- `maximumTimeReduction = 0.20`：仅闪避恢复上限；保留旧键与原值 / ground-dodge cap, retains the legacy key and value.
- `swordPreparationReduction = 0.40`：特殊幻影剑准备上限 / special summoned-sword cap.
- `normalSaPreparationReduction = 0.3333333333333333`：普通 SA 蓄力上限 / normal SA cap.
- `superSaPreparationReduction = 0.30`：Super SA 蓄势上限 / Super SA cap.
- 四个上限都可以调低，但不能高于各自默认值；旧配置自动补齐新键 / each cap can be lowered, not raised; existing configs receive missing keys automatically.

`reduction = actionMaximum × clamp(finalSpeed / reference - 1, 0, 1)`。
默认从 1.6 到 3.2 攻速线性增长，在 3.2 封顶；时间按整 tick 四舍五入，因此实际手感是逐 tick 的台阶，而非连续小数时间。
闪避恢复和准备回调在动作开始时确定计时；切换装备不会重新缩短已经排好的回调。被加速的准备回调在切刀、死亡或换维度后取消，不能把快刀的蓄势收益转移给另一把刀。普通 SA 继续通过本体统一阈值接口读取当前属性，保证提示与释放规则一致。配置支持 Forge 的服务端同步。

Linear progress starts at 1.6 attack speed and reaches each action's separate cap at 3.2. Timings round to whole ticks, so gains appear in discrete steps.
Queued callbacks keep their original input timestamps and are not accelerated again on equipment changes.
Accelerated callbacks cancel on blade changes, death or dimension changes. Normal SA uses the native shared threshold with current attributes, keeping release checks and readiness feedback consistent.

## 架构 / Architecture

1. `SpeedEfficiencyRules`：纯函数，`Timing` 定义各行为上限，统一线性公式、整数边界与可调整的原生计时名称；普通单元测试无需游戏注册表。
2. `SpeedEfficiencyRuntime`：资格判断、服务器配置、最终攻速读取与主手 blade-state 身份校验。
3. `SpeedEfficiencyInputHandler`：原生输入前后观察地面闪避，使用公开 MOB_EFFECT capability；弱引用关联幻影剑调度器的输入状态与玩家。
4. 三个 `SpeedEfficiency*Mixin`：只对接 Resharped 1.9.63 的普通满蓄入口和两个准备调度入口，不复制技能，不加第二份回调。
5. `SpeedEfficiencySmokeTest`：仅 `-PspeedEfficiencySmoke` 启用，使用真实 Forge/Mixin 验证并关闭隔离测试服务器；反射只用于诊断原生私有调度函数。

本层没有 Tetra API 方法依赖。Tetra 版本差异继续由现有模块属性接口与兼容桥处理；不同版本材料数值可能不同，最终收益随实际属性变化，而非硬编码版本号。
兼容检查还将已有魂珠全息统计条的类引用隔离到 `TetraHoloStatsCompat`：6.10.0 的 `.craft.schematic.HoloStatsGui` 与其他已验版本的 `.craft.HoloStatsGui` 按实际类存在与公开 `addBar` 方法解析，不按版本号硬编码分支。
原生调度器方法与匹配数量严格校验，升级 SlashBlade 后必须重新验收。未知计时名称/延迟不做修改。

No version-specific Tetra APIs are used here. Native scheduler hooks are pinned to the installed Resharped build and require revalidation after a SlashBlade upgrade.

## 验证 / Verification

- `./gradlew test`：非法值、上限、收益单调性、非目标回调不变。
- `./gradlew runServer -PspeedEfficiencySmoke -Ptetra_file_id=<id>`：独立 `build/speed-efficiency-smoke-<id>` 目录，不使用日常测试存档；需该目录 `eula.txt` 已接受 EULA。
- 日志 `SPEED_EFFICIENCY_SMOKE_PASS` 才代表真实运行验证通过，编译通过不等于实机验收。
- 后续手感验收：不同攻速下特殊剑术方向切换、精确 SA、断岳蓄力与普通 SA 分界、联机与外部属性 Mod。

此前接入层的兼容矩阵使用 SlashBlade: Resharped 1.9.63 与 Forge 47.4.0；下表不代表此次调参已重新跑完所有版本：

| Tetra | 文件 ID | Mutil | 检查范围 |
| --- | --- | --- | --- |
| 6.3.0 | 5236744 | 6.2.0 | 编译、隔离服务端计时、换刀取消检查 |
| 6.9.0 | 6418957 | 6.2.0 | 编译、隔离服务端计时检查 |
| 6.10.0 | 6848811 | 6.2.0 | 编译、隔离服务端计时检查 |
| 6.13.0 | 7769412 | 6.3.0 | 编译、隔离服务端计时、换刀取消检查 |
| 6.17.0 | 8570756 | 6.3.0 | 编译、隔离服务端计时、换刀取消检查 |

较新 Tetra 需要配套的 Mutil：例如 `-Ptetra_file_id=8570756 "-Pmutil_version=1.20.1-6.3.0"`。
以上不是对所有中间版本或整合包的兼容承诺；全息预览的实际客户端显示、多人手感与外部战斗 Mod 仍需客户端验收。

The matrix records the earlier integration checks with Resharped 1.9.63, not a full rerun of every version after this tuning. Newer Tetra must use its matching Mutil dependency. It does not certify every intermediate release, client holographic rendering, multiplayer feel or third-party combat mods.

本次线性曲线与独立上限调参：286 项自动化测试通过，无失败或跳过；Tetra 6.3.0 / Mutil 6.2.0 与 Tetra 6.17.0 / Mutil 6.3.0 的隔离服务端检查均通过。
实际计时为普通 SA 6、特殊幻影剑 6、Super SA 14、准备特效 4、闪避恢复 16 tick；保留非模块化武器的原生计时、连续闪避次数和换刀取消保护。6.3.0 的旧配置也成功自动补齐了三项新上限。
客户端手感与特效同步仍需试玩验收。

Current tuning: 286 automated tests passed, none failed or skipped. Isolated server checks passed on Tetra 6.3.0 / Mutil 6.2.0 and Tetra 6.17.0 / Mutil 6.3.0, with timings 6/6/14/4/16 ticks, native fallback, dodge-count limits and blade-switch cancellation preserved. The old 6.3.0 config received all three new caps automatically. Client feel and visual synchronization still require playtesting.
