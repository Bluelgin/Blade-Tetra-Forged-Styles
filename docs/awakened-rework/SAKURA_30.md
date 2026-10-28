# 千本樱 3.0：散华剑舞与无限连舞 SA

基线：PR #45 `7baa1a3a59ab4ae8b8489f4b0dec4393ff66deee`。本分支只重构千本樱战斗，PR base 是 `codex/sakura-22-native-stance`；不包含镜花 #46 的实现。

## 玩法

- 原生空中下劈散华、地面上挑凝刃保持不变；空挥也能切换，零 Rank 能凝刃。
- 散华普通攻击恢复原生流程：B1–B7、方向技、空中技正常。没有长按自动续连、按键缓存、续连宽限和普通攻击 Rank 燃烧。
- 原生攻击真实命中后，背后一个固定索引碎刃飞往视野左上/右上，短暂停留并发射原生幻影剑，然后回到自己的阵位。每个原生动作最多一次，左右与索引轮换；默认每 8 tick 最多启动一次。空挥不触发。
- 散华时通过原生 SA 蓄力释放无限连舞，首次至少 A Rank。正常 Success、Jackpot、Super 都路由到同一 SA；失败不启动。凝刃保留武器原有 SA 配置。
- SA 自动 B1→B2→B3→B4→B5→B6→B1，保留完整原生攻击回调。无需长按；手动普通攻击和重复 SA 暂停接受，可以转向并以降低后的速度移动。
- SA 消耗原生 Rank，每 10 tick 扣一次。入场等级固定：A 100%、S 88%、SS 80%、SSS 67%。散华始终禁止 Rank 回补，原生自然衰减仍存在。
- Rank 不够支付下一次燃烧时停止新增碎刃攻击，完成当前招式后退出；保持散华。受击、控制、死亡、换刀/槽、形态失效、跨维度或退出会中断并清理，不补播遗失攻击。默认最长 1200 tick，再在当前动作边界退出。

## 维护边界

### 原生依赖审计

`gradle.properties` 固定 Curse Maven 文件 **8018222**。实际 JAR 的 `META-INF/mods.toml` 是 **SlashBlade Resharped 1.9.63**，不能用上游 HEAD 的 1.9.65 代替依赖证据。本次核对实际 JAR 反编译的 ComboState/Builder、ISlashBladeState、SlashArts、ItemSlashBlade、AttackManager/AttackHelper、SummonedSwordArts、EntityAbstractSummonedSword 和输入处理。

- `L_DOWN/R_DOWN` 来自 MoveInputHandler 持续状态；`L_CLICK/R_CLICK` 由物品攻击方法临时添加、随后删除。新系统没有长按判断或输入缓存。
- `doChargeAction` 发出 PerformSlashArtEvent，然后根据优先级调用 `updateComboSeq`；BladeMotionEvent 仍可取消/改写。只有写入完成后专属节点的 clickAction 能创建会话；候选事件本身不锁玩家、不扣费。
- B1 在 tick 6 发出两次，tick 7–14 各一次，共 **10 次**；B2–B6 在 tick 0–6 各一次，共 **7 次**。专属节点在 tick 15 / tick 7 边界推进。普通攻击不改上游时序或节点。
- 原生范围近战也会经过 Forge AttackEntityEvent。输入锁只拦截 `!state.onClick()` 的外部手动攻击，保留 AttackManager 在 `onClick=true` 作用域内的原生伤害。
- EntityAbstractSummonedSword 的 damage 会向上取整并乘上游倍率，且命中会调用原生 StunManager。因此额外幻影剑通过 LivingHurt/LivingDamage 双重上限约束有效伤害；**保留原生幻影剑自身的眩晕机制**，没有增加额外的自定义僵直。

### 组件与状态

| 组件 | 职责 |
|---|---|
| SakuraStanceController | 原生动作提交后的凝散验证，沿用旧 NBT |
| SakuraEndlessFlow | 仅保留散华 Rank 非递增上限；无普通续连或扣费 |
| SakuraInfiniteDanceSA | 候选提交、会话、动作边界、燃烧、输入锁和清理 |
| SakuraDanceCombos | 六个本 Mod 专属节点，调用上游完整 tick/click/hold/hit 回调 |
| SakuraCombatVisuals | 原生动作与真实伤害关联、每动作一次普通协奏资格 |
| SakuraShardAttackService | 两种攻击互斥、目标校验、普通发射队列、幻影剑清理、SA 每招总伤害预算 |
| SakuraShardPool/Choreography/Renderer | 唯一有界索引池、发射平台/六式编排、原有樱色批次渲染 |

IDLE→STARTING（候选经原生 commit 确认）→DANCING→FINISHING→IDLE。候选被取消/改写不会进入会话；强制中断直接走清理。会话绑定真实 ItemStack 对象、热键槽、维度、动作时间。丢失会话的存档中间 SA 节点在服务器 tick 恢复为 NONE，避免重登残留锁定。

专属节点只改变本 Mod 的调度边界和输入路由，继承原生动作帧、动作资源及速度；保留原 Rengeki .85 伤害倍率和 .82 刀光尺寸，不提高原生伤害。B1 的完整晚段使一轮比旧截断续连更长，这是保留原生回调的结果。

Builder 公开 API 只有 `addTickAction`，默认已装有 ArrowReflector。直接追加 `original::tickAction` 会重复执行反射。沿用 #44 已验证的**仅注册时**有类型校验的 tickAction Consumer 替换，转发上游回调一次；没有 Mixin、全局 ComboState 修改或每 tick 反射。任何适配失败禁用新 SA，普通原生攻击和凝散仍可用。保留旧 `awakened_sakura_b6` 注册 ID 处理旧存档中间动作，但不再路由普通攻击到它，也不再回接 B1。

### 伤害、目标和性能

普通协奏使用原生 `EntityAbstractSummonedSword` 的实体类型、所有者、shoot、颜色同步、模型、碰撞、生命周期。没有自制投射物引擎；方向在发射时确定，不穿墙追踪。平台以玩家 eye/look 的 right/up 坐标计算，跟随转向和跳跃。客户端 24/48/72 LOD 将服务器 24 个逻辑索引映射到固定阵位，发射和归阵是原池中的同一对象。

默认延迟 5 tick 发射，约 12 tick 完成出击/归阵；下次用另一逻辑索引，可以重叠。客户端最多六组、八个玩家池和原有 quads 预算。SA 用原有六式轨迹，逐渐展开三层后阵；退出用已有 RETURN 状态回阵。客户端丢包/迟到或无空闲索引允许省略视觉，不补造装饰碎刃。资源重载/换世界仍清理原有渲染缓存。

普通单发预算是关联原生攻击最终有效伤害的 25%，上限 6；每动作一次、默认每 8 tick 一次，单玩家最多 4 个在途实体、全服最多 256 个，30 tick 强制清理。忙碌期间不补发积压队列。发射前再次检查目标和平台两段射线，飞行和伤害事件再次检查绑定/目标。意外撞到其他实体会取消并丢弃。禁止玩家、友方、驯养动物、村民、铁傀儡等受保护类别；普通 Forge 伤害取消继续生效。

SA 每招额外总预算默认 8：B1/B2 一次、B3/B4 两次、B5 三次、B6 七次均分。不按原生伤害事件数量或附近目标数量放大。只使用合法原生锁定目标，或最近真实原生命中的目标，不扫描大量实体。额外直接伤害不创建碎刃实体、不清除目标原生伤害免疫、不添加自定义眩晕。

幻影剑同时标记 SoulLegacyDamageGuard 和 InheritedCombatDamage，SA 额外伤害使用其 try/finally 作用域，阻止协奏/刀魂/自身伤害递归。Rank 上限在服务器玩家 START/END 校验，原生 Rank 能力及 HUD 是唯一资源。正向 Rank 信用可能短暂存在到同 tick 校验，不能持续资助技能。

既有 AwakenedBladeVfxPacket 继续承载动作事件，没有每 tick 位置同步或新的包类型。SWING variant 0 表示 SA，100–147 编码普通逻辑索引和左右，-1 表示回阵；协议升至 **22**，客户端与服务器需同步更新。

## 配置

| 配置 | 默认 | 作用 |
|---|---:|---|
| awakenedSakuraFlowDrainPerSecond | 120 | 保留旧配置键，仅新 SA 使用 |
| sakuraSaMaxTicks | 1200 | 安全运行上限 |
| sakuraConcertoInterval | 8 | 普通协奏最短间隔，最低 6 tick |
| sakuraConcertoDamageFraction | .25 | 关联有效伤害比例 |
| sakuraConcertoDamageCap | 6 | 单发上限 |
| sakuraSaStageDamage | 8 | 每招额外伤害总预算 |

## 验证分层

- JUnit：共享索引/原对象归阵、左右视角坐标、跳跃/转向/俯仰、并发与饱和预算、伤害上限、六式脉冲数和安全边界；保留凝散、模型及网络回归。
- Forge 专服：`./gradlew runServer -PsakuraCombatSmoke`。使用真实能力、原生 `doChargeAction`、`progressCombo`、物品 `inventoryTick`、原生范围攻击及幻影剑真实碰撞。标记为 SAKURA_SA_SMOKE_PASS、SAKURA_CONCERTO_SMOKE_PASS、SAKURA_STANCE_SMOKE_PASS、SAKURA_COMBAT_SMOKE_PASS。显式服务端时间推进与 FakePlayer 不是实际图形客户端输入验收。
- 客户端由作者验收：第一/第三人称及旁观者，原生鼠标按住/松开/取消蓄力，准星遮挡、移动跳跃与转向、六式节奏、剑阵层次、多人延迟/帧时、资源包重载和材质适配。没有宣称客户端手感或美术已通过。

执行结果和已知限制见本次 PR；不要将旧 #45 的通过记录当作 3.0 的结果。
