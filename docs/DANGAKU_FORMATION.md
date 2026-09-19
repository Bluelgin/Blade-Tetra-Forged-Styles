# 断岳流 · 阵型操纵与蓄力横扫

## 1. 核心定义

断岳不再是“系统自动交替纵劈/横扫”的重攻击流派，而是：

`左键断阵 + 右键整阵 + 长按右键开岳`

玩家的主要资源不是层数或标记，而是**敌人的实际位置**。

- 左键：纵劈 / Cleave，窄、重、有霸体；
- 右键短按：普通横扫 / Sweep，低伤害，快速把敌人整理到玩家前方；
- 右键蓄力：按当前蓄力值扩大横扫；
- 刀满足原生 Slash Art 条件时：**横扫与 SA 同时释放**，SA 不再替代断岳动作；
- 右键蓄满：大范围“开岳”横扫，同时仍允许原生 SA 正常释放；
- 敌群被压紧后，纵劈读取当前阵型并获得小幅群体收益。

本传与居合不变；连舞仍按 `RENGEKI_SHORT_STEP.md` 独立运行。

## 2. 输入分工

断岳普通地面输入不再自动 `Cleave ↔ Sweep`。

- `L_CLICK`：选择 `dangaku_cleave`；
- 普通 `R_CLICK`：在 `ItemSlashBlade.use()` 之前由断岳截获，只进入 held-use，不执行原生 `progressCombo()`；
- 松手由 `DangakuFormationHandler` 结算断岳横扫：
  - 未达到刀本身的原生 SA 蓄力阈值：普通横扫；
  - 达到蓄力横扫阈值后：按当前蓄力值排一次断岳横扫；
  - 如果刀同时满足原生 SA 条件：**不取消 Stop 事件**，让 Resharped 的 `releaseUsing()` 继续释放 SA；
  - 达到 `44 tick`：横扫达到满蓄大范围状态，但 SA 仍可同时发生；
  - 如果刀本身不满足原生 SA 条件，则只释放断岳自己的横扫。

原生 SA 的 Jackpot / Success 判定、Proud Soul 消耗、`ChargeActionEvent` 与第三方兼容都继续由 Resharped 自己负责；断岳不调用、不复制 `doChargeAction()`。

潜行方向技与空中技仍走 Resharped 原生分支。潜行右键没有被断岳的普通右键拦截器占用，因此仍可作为纯原生输入路径。

普通断岳蓄力是**地面动作**：如果玩家在已捕获的右键蓄力过程中离地，这次 charge 会永久 disarm；之后即使重新落地，松手也只会被吃掉，不会把旧蓄力带到空中，也不会漏回原生 `releaseUsing()`。

`StyleInputBuffer` 不再保存断岳的自动纵横切换，避免旧逻辑抢掉右键蓄力。

## 3. 蓄力曲线

### 3.1 时间

- 满蓄：`44 tick`（约 `2.2 s`）；
- 满蓄宽限：`5 tick`；
- 超过宽限后：蓄力值按固定速度单向衰减；
- 最低：`25%`；
- 不会重新循环回 `100%`。

第一次达到满蓄仍然是最佳“开岳”窗口。SA 是否同时发生只取决于刀本身的原生条件，不会改变断岳的蓄力曲线。

### 3.2 HUD

客户端直接读取原生 `getTicksUsingItem()`，使用与服务器相同的 `DangakuChargeMath`：

- 普通蓄力：显示百分比进度条；
- 满蓄宽限：显示 `◆`；
- 过蓄衰减：显示 `↓`，进度条同步回落。

HUD 没有战斗权威，也不新增同步包。它只表示断岳横扫本身的蓄力程度；SA 仍由 Resharped 原生逻辑判定。

## 4. 面板动态缩放

面板伤害使用柔性平方根缩放：

`panelPower = clamp(sqrt(panelDamage / 24), 0.75, 1.50)`

因此高面板仍能扩大断岳的战场控制能力，但不会线性失控。

### 4.1 满蓄范围

满蓄大横扫范围：

- 低面板基线：约 `8.5` 格；
- 高面板硬上限：`12.0` 格。

未满蓄时，实际范围从 `5.5` 格向对应满蓄范围插值。

### 4.2 伤害

断岳进一步明确为“阵型控制流派”，整体伤害下调。

需要区分**额外 style factor**和**整招最终 ratio**：

- 普通 Sweep 复用原生 A1。A1 本身约 `0.44x`，再经过旧断岳 `0.92` 与新 `0.68` factor，单次刀光实际约 **`0.275x`**；
- Cleave 复用 A4_EX。每一道原生刀光仍是 `1.0x`，每道再乘旧断岳 `1.12` 与新 `0.95` factor，约 **`1.064x / 道`**；A4_EX 有两道有效刀光，因此同一目标两道都命中时总量约 **`2.13x`**，再视阵型决定是否获得 `+6%`；
- 紧密敌群的断阵奖励：约 **`+6%`**；
- 蓄力横扫 ratio：约 **`0.42x → 0.58x~0.68x`**，随蓄力和面板柔性变化；
- 当横扫与 SA 同时释放时，横扫伤害再乘 **`0.72`**，最高大约 **`0.49x`**。

这里的重点是：Sweep / charged Sweep 都是控制优先；Cleave 仍是断岳的重击兑现按钮。SA 是额外爆发来源，而不是替代断岳动作。

## 5. 整阵

普通 Sweep 命中后，目标不会单纯从玩家身边炸开，而是朝玩家前方约 `2.7` 格的收束点移动。

蓄力横扫使用更远的动态收束点：范围越大，收束点越靠前。

位移考虑目标的击退抗性；高抗性/Boss 不会被无条件强拉。

服务端对普通实体设置 `hurtMarked` 以同步新速度；如果目标是 `ServerPlayer`，额外发送原版 `ClientboundSetEntityMotionPacket`，避免 PvP 中出现服务端已聚怪、客户端仍保持旧运动状态导致的回弹/纠正。

## 6. 蓄力横扫与 SA 共存

蓄力横扫使用一个短命 pending strike，在松手后约 `4 tick` 结算：

- 如果没有 SA：断岳进入自己的 visual-only `dangaku_charged_sweep` Combo；
- 如果同时有 SA：**不切换到断岳 Combo**，避免覆盖原生 SA Combo；
- pending sweep 不要求当前 Combo 仍是 `dangaku_charged_sweep`，因此不会因为 SA 切换 Combo 而被自己取消；
- 横扫仍生成独立横向刀光、执行范围选敌和聚怪；
- SA 继续完全走 Resharped 原生释放路径。

大横扫参数：

- 约 `±135°` 大扇区；
- 最多 `24` 个合法目标；
- 要求 LOS；
- 使用 `SlashBladeTargetingConditions + AttackablePredicate`；
- `AttackManager.doMeleeAttack(..., false, false, ratio)`；
- 不 `forceHit/resetHit`，不会主动清除其他攻击留下的 hurt window。

### 6.1 Sidecar 命中隔离

大横扫的 melee 只是断岳自己的 sidecar，不允许借用“4 tick 后当前 Combo”的额外行为。

每个 charged-sweep melee 调用都建立一个只在同步调用栈内存在的 `ChargedSweepHitContext`。在 Resharped 已经完成伤害、但尚未执行 `ItemSlashBlade.hurtEnemy()` 的当前 Combo hitEffect / 耐久分支时，断岳只取消**这一笔精确匹配的 HitEvent**。

因此：

- SA 自己的命中、Proud Soul、ComboState 与事件不被修改；
- charged Sweep 仍使用 Resharped 的正常面板/附魔/伤害事件路径；
- charged Sweep 不会因为当前 Combo 已经切成 SA，就额外执行一次 SA hitEffect；
- charged Sweep 不会按最多 24 个目标额外磨损 24 点刀耐久；
- 不新增 packet、NBT 或长期 combat context。

视觉刀光为 ownerless `EntitySlashEffect`，只负责表现，不复制成额外 areaAttack。

## 7. 断阵

旧断岳是：

`Cleave 命中 → 给目标写 BROKEN_STANCE_* NBT → 下一次本人攻击 +5% → 清理`

新断岳不再依赖这个标记作为玩法。

纵劈命中时直接读取目标周围 `1.8` 格内的合法敌群：

- 至少 `3` 个目标形成紧密群体时，本次纵劈获得约 `6%` 的阵型奖励；
- 没有层数；
- 没有计时器；
- 不需要记住“是不是刚刚被横扫过”。

位置本身就是状态。

旧 `StyleCombatHandler` 已经完全删除 `BROKEN_STANCE_*` 的写入、+5% 消费以及 tick 清理。`DangakuFormationHandler` 只保留旧世界/旧实体上遗留 tag 的被动清除逻辑，不再产生新的破势 NBT。

## 8. Runtime 检查

1. 地面左键能直接进入纵劈，不再被强制安排下一刀为横扫。
2. 普通地面右键按下时不立即挥刀，也不会穿回原生 `progressCombo()`。
3. 未达到原生 SA 阈值时松开触发普通 Sweep。
4. 达到蓄力横扫阈值后，松手一定会排一次断岳横扫。
5. 具备 SA 条件的刀在同一次松手中同时释放断岳横扫与原生 SA。
6. 满蓄 `44 tick` 时仍可同时释放大横扫与 SA，不再二选一。
7. 原生 SA 仍负责 Proud Soul 消耗、Jackpot / Success 和 `ChargeActionEvent`，断岳代码不调用 `doChargeAction()`。
8. SA 共存时不会用 `dangaku_charged_sweep` 覆盖原生 SA Combo。
9. SA 共存时 charged sidecar 不执行当前 SA Combo 的 hitEffect，也不追加 sidecar 耐久消耗。
10. 长按 HUD 从 0% 向 100% 上升。
11. 约 2.2 秒进入断岳满蓄，保持约 0.25 秒宽限。
12. 继续按住后进度条单向下降，不重新循环到 100%。
13. 蓄力过程中离地会永久取消本次断岳 charge，不会在空中松手开岳或漏出原生 SA。
14. 满蓄低面板范围约 8.5+，高面板不超过 12 格。
15. 大横扫最多处理 24 个合法目标，不能隔墙命中。
16. 普通/蓄力 Sweep 都能明显把敌群压向玩家前方收束区。
17. 高击退抗性的目标受到较弱位移。
18. PvP 目标能够收到聚怪后的原版 motion 同步。
19. 聚成至少 3 个目标后纵劈获得较小的断阵收益。
20. 大横扫不会 `forceHit/resetHit` 清掉已有 i-frame。
21. 旧 `StyleCombatHandler` 不再写/消费 `BROKEN_STANCE_*`。
22. 潜行方向技、空中技仍保持原生路径。
23. 不新增永久 charge NBT，也不新增自定义网络包。
