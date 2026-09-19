# 连舞流追刃、击杀续舞与疾走 B / Rengeki Flow

## 设计目标

连舞继续使用《拔刀剑：重锋》的原生 B 系地面连击，不注册新的真实 ComboState，也不加入新按键、资源条或持久化状态。

定位：**牺牲一部分 B 系单段伤害，换取追身、击杀续舞和疾走高频压迫感。**

核心定义：

`原生 B 连击 × 0.85 + 右键主动追身 + 击杀自动续舞 + 高频疾走 B`

本传与居合行为不变。

---

## 1. 原生 B 系伤害取舍

- `combo_b1` 到 `combo_b7` 以及原生 B 收势刀光统一乘 `0.85`。
- 只影响连舞的 native B flow。
- 方向技、空中技、SA、SE、融合结构不受影响。
- 追身与击杀续舞本身不追加伤害。
- 疾走 B 使用独立的低倍率面板伤害路径，不再额外乘 `0.85`。

---

## 2. 普通命中追刃

B1-B6 的有效非致死命中会生成一次 `10 tick` 追刃窗口。

只有真实原生推进：

`B1→B2 → ... → B6→B7`

可以消费该窗口。

操作语义：

- **右键 B→B**：继续原生 combo，并尝试追身。
- **左键 B→B**：继续原生 combo，但不追身；旧窗口同时被消费。
- 挥空、超时、方向技、空中技、SA 等不会延迟触发旧追身。

参数：

- 搜索距离：`5.5` 格。
- 前方锥角：约 `±50°`。
- 单次移动上限：`2.75` 格。

右键判定来自 Resharped 在 `progressCombo()` 同步调用期间临时写入的 `INPUT_STATE / R_CLICK`，并在 `BladeMotionEvent` 中读取，不使用 tick 猜测。

---

## 3. 击杀续舞

原生 B flow 的合法命中确认击杀后，生成一次独立的 kill hand-off。

它与普通右键追刃是两套机制：

- 不要求右键；
- B1-B7 均可触发；
- 原生 B recovery 中的延迟刀光仍可触发；
- 主动切出 B flow 后，旧刀光不再抢夺玩家位置。

时序：

1. 若玩家立刻推进下一段真实 B→B，优先在这次推进中消费 hand-off；
2. 否则服务器下一 tick 自动尝试一次。

参数：

- 搜索距离：`6.5` 格。
- 前方锥角：约 `±80°`。
- 移动上限：`4.5` 格。
- 不强制旋转镜头。
- 不自动追加下一刀。

---

## 4. 疾走 B

### 4.1 触发条件

必须同时满足：

- 主手为连舞刀；
- 当前真实 combo 为 `NONE`；
- 正在 sprint；
- 实际着地；
- 未骑乘、未飞行、未持续使用物品；
- 当前没有待处理的击杀续舞；
- 连续服务器 tick 的真实水平位移至少 `0.12 block/tick`。

速度使用位置差，而不是 END tick 已被摩擦衰减的 `deltaMovement`：

`speed = sqrt((x_now-x_prev)^2 + (z_now-z_prev)^2)`

因此正常 Ctrl / 双击 W 疾跑即可触发，不依赖 V 瞬步。

停止疾跑、速度不足、换刀或进入正式 combo 时，疾走链重置。

### 4.2 视觉节拍

视觉保持独立 B1→B7 节奏：

- 新视觉 beat：每 `10 tick`；
- 每个 beat 的 burst：`7 tick`；
- B1：`-30° / 145°` 交叉开场；
- B2-B6：正负 roll 家族交替的 rush slash；
- B7：追加小型收束双斩。

视觉全部使用 ownerless `EntitySlashEffect`：

- 不设置 shooter / owner；
- 不执行原生 broad `areaAttack`；
- 视觉数量不会复制为同数量真实伤害；
- 不再维护额外静音分支，声音交回原生/Resharped 路径。

### 4.3 真实命中节拍

真实命中与视觉完全解耦：

- `SPRINT_HIT_INTERVAL_TICKS = 4`；
- 理论上限约 `5 hit/s`；
- 每个 pulse 最多选择一名合法目标；
- 前方约 `±55°`；
- 不做多目标被动 AoE；
- 不推进真实 ComboState；
- 不触发追刃或击杀续舞。

### 4.4 速度、范围和伤害

速度输入：

- 最低：`0.12 block/tick`；
- 缩放上限：`0.36 block/tick`。

真实范围：

- `1.35 → 2.75` 格。

视觉 BaseSize：

- `0.28 → 0.58`。

每次真实 hit 的 combo ratio：

- 低速：`0.03x`；
- 满速：`0.07x`；
- 中间线性插值；
- 超过 `0.36` 后不再继续提高。

真实伤害仍走 Resharped 的正常近战面板路径，因此武器面板、附魔、Rank、SlashBlade damage scale 与整合包整体倍率仍然生效。

### 4.5 hurt i-frame 边界

高频疾走不能使用 Resharped 的 `resetHit=true`。

原因是 `resetHit` 会在攻击调用结束后直接把 `target.invulnerableTime` 清零，**即使这一刀因为目标原本已有 hurt window 而没有成功造成伤害**。这会意外清掉其他攻击来源留下的保护时间。

现在采用：

- `forceHit = false`；
- `resetHit = false`；
- 攻击前记录目标已有的 `invulnerableTime`；
- 只有本次精确匹配的 `HitEvent` 证明疾走 melee 确实成功后，才认为这一 pulse 命中；
- 若攻击前目标没有已有 hurt window，则把这次新产生的窗口最多缩短到 `SPRINT_HIT_INTERVAL_TICKS`，即 `4 tick`；
- 若目标攻击前已经处于其他来源的 hurt window，则疾走不会清除或缩短它。

这样可以让连续疾走命中达到高频节奏，同时避免一次失败的 sprint pulse 顺手清掉别的攻击来源的无敌帧。

### 4.6 疾走不消耗耐久

疾走真实 hit **完全不消耗刀耐久**。

不再维护“4 hit / 10 hit 相位”等计数状态。

实现利用 Resharped 已有的成功命中边界：

- melee 伤害成功后才进入 `SlashBladeEvent.HitEvent`；
- 该事件发生在后续 native hit-effect / `hurtAndBreak` 之前；
- 疾走使用一个只在同步 melee 调用期间存在的 hit context；
- context 同时绑定 **玩家、刀 ItemStack、目标实体**，避免嵌套 HitEvent 被误认；
- 在 `LOWEST` 阶段确认精确匹配后取消事件，从而跳过后续耐久分支；
- 更高优先级的兼容监听器仍有机会先观察这次成功命中。

这比维护耐久相位表更简单，也不会因为疾走频率提高而让武器快速损耗。

### 4.7 声音与疾跑连续性

疾走不再额外拦截或重映射声音事件，也不再给视觉刀光强制设置 mute。声音直接沿用原版/Resharped 的实际攻击表现，避免为一个非核心表现目标维护独立声音事件链。

真实命中前保存玩家：

- `deltaMovement`；
- sprint flag。

攻击调用期间临时关闭 sprint，结束后在 `finally` 恢复二者，避免 melee knockback 分支中断疾跑。

---

## 5. 目标与移动安全

追刃、击杀续舞和疾走 B 均继续遵守 Resharped 的：

`SlashBladeTargetingConditions + AttackablePredicate`

同时保留：

- PvP / friendly 规则；
- allied/team 排除；
- 目标存活和可攻击状态；
- LOS，不隔墙生效。

普通追刃与击杀续舞还要求玩家着地且未骑乘。

位移路径每约 `0.35` 格采样一次完整玩家碰撞箱，并检查：

- 碰撞；
- 脚下支撑；
- 涉及区块是否已加载。

不安全时只取消位移，不取消原生 combo。

---

## 6. 瞬时状态

服务器内存只保存必要状态：

- 追刃窗口；
- 击杀续舞请求；
- 上一 tick 位置样本；
- 疾走 B1→B7 visual/hit cadence；
- 当前同步 sprint-hit context。

**没有疾走耐久计数状态。**

退出、换维度、死亡/Clone 时清理对应瞬时状态。不新增玩家 NBT、刀 NBT 或网络协议。

---

## 7. 明确边界

本轮不做：

- 新真实连舞 ComboState；
- 新按键或 HUD；
- 左键普通 B 自动追身；
- 360° 自动索敌；
- 疾走多目标大 AoE；
- 疾走强制穿透已有 hurt window；
- 疾走耐久消耗；
- 独立疾走静音系统；
- 穿墙/跨沟瞬移；
- 强制镜头旋转；
- 修改本传、居合、断岳。

---

## 8. 实机验收

1. B1-B7 与原生 B recovery 约为未削弱版 `85%`，其他攻击不受影响。
2. B1→B7 的普通追刃只有右键推进时移动，左键只继续 combo。
3. B1-B7 击杀均可自动续舞，尾势延迟击杀同样有效。
4. 主动切出 B flow 后，旧 B 刀光不再强制换目标。
5. 普通 Ctrl / 双击 W 疾跑可直接启动疾走，不要求先按 V。
6. 视觉按 `10 tick` beat / `7 tick` burst 循环 B1→B7。
7. 持续接触合法目标时，真实 hit 约每 `4 tick` 尝试一次。
8. 每个 hit pulse 最多伤害一名目标。
9. 范围始终不超过 `2.75`，visual BaseSize 不超过 `0.58`。
10. 每 hit ratio 保持在 `0.03x → 0.07x`。
11. 目标若在疾走前已有外部 hurt window，失败 pulse 不得把它清零。
12. fresh sprint hit 可把自己新产生的 hurt window 缩到最多 `4 tick`，支持后续 pulse。
13. 疾走不维护额外声音拦截，声音沿用原版/Resharped 行为。
14. **连续疾走命中不消耗刀耐久。**
15. 疾走命中后玩家 sprint flag 和 movement vector 保持连续。
16. 正式 combo 期间不叠加疾走 hit。
17. 待处理 kill hand-off 时不抢先生成疾走攻击。
18. PvP、友军、LOS 与 Resharped 目标规则一致。
19. 追刃/续舞不穿墙、不跨沟、不进未加载碰撞区域。
20. 退出、换维度和死亡后不残留旧的疾走/追刃状态。
