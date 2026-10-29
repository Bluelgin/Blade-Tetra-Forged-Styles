# 镜花水月4.0 验证记录

## 调查快照

2026-10-29 开始时获取的真实 PR 元数据与逐文件列表；均为 Draft，未视作已合并：

| PR | HEAD | base |
| --- | --- | --- |
| #40 | 9f7fa9e9389fd3b297334b1aa74fda28b6ec9e75 | main |
| #41 | a60b4ab834f56ff4883336746e53f15ce0057570 | art/awakened-blades-v2 |
| #43 | 6e2e3444538ebf34fb5c9d4d67a1b9a3215bfb06 | codex/senbonzakura-endless-flow-v2 |
| #46 | 10e5aced2e74da408537390faa98b61334505bd1 | codex/true-name-release-ritual |
| #47 | ca0123131f6173fbd95bebbc7862a08f805d5c81 | codex/sakura-22-native-stance |
| #48 | dbcabd47dab34878e99a8b8cd5fe6d194cbb7e0e | codex/awakened-blades-v2-runtime |
| #49（起点） | ed9ff2dac1bb8af816262665c774a7a28fd5f2a2 | codex/kyouka-planar-reflection-v3 |

工作区初始干净，没有用户未提交更改。#49 的 #46 base 未改变。额外核查实际 Gradle 配置与上游 #41 赤月回退已经在 #49 起点中的状态。

## 自动化证据

首个4.0校验提交 `aa7ef67c04b4baf7b5fd0a8bc05935d8b4a59ed2`：[CI](https://github.com/Bluelgin/Blade-Tetra-Forged-Styles/actions/runs/40856312233)。完整 Gradle build/JUnit/实际JAR javap审计通过；真实Forge专服在“已有原生环剑尚未发射时再次施法”的测试条件处失败。此前合法施法、无锁定Buff、正常Rank收益、陆地呼吸/水下呼吸/低氧退出、取消/虚拟动作、实际原生命中、单动作预算、真实SSS边界、75%共享伤害及保护管线检查已执行。此失败不能写成整组专服通过。

测试随后正确清理已有原生环剑再施法，并补入真实14 tick水斩、40%伤害与玩家实际受伤后的防御支付检查。Forge FakePlayer 默认 isInvulnerableTo 始终为true，因此最终伤害测试使用仅测试用子类，显式调用原生 Player.actuallyHurt，不伪称真人完整 hurt/联网验收。

提交 `c9c33277fd04dcc4bacdf15676e4a0fb0e66b34d` 的 [CI 40857600568](https://github.com/Bluelgin/Blade-Tetra-Forged-Styles/actions/runs/40857600568) 已完整通过：Gradle build / JUnit / 实际JAR审计、独立图形客户端VMD/三把Tetra刀 **53 checks**、真实Forge专服 **32 checks**。两端协议为 kyouka4-water-2；包往返测试已包含维度、时间和事件UUID。

提交 `df90d69baa897e05735502ee7c2571e391cf8a6f` 的 [CI 40858108468](https://github.com/Bluelgin/Blade-Tetra-Forged-Styles/actions/runs/40858108468) 同样完整通过，专服扩展为 **39 checks**：补齐追忆10刻调度、600刻冷却波动保护、60刻历史过期、重装备不触发、持久化冷却及普通受击不取消预约水斩；独立客户端仍为53 checks。最终提交的校验结果见 [PR #49 检查](https://github.com/Bluelgin/Blade-Tetra-Forged-Styles/pull/49/checks) 与PR交付说明，不将前一提交的通过状态直接冒充最终HEAD结果。

本地实际下载并检查：SlashBlade file 8018222 JAR、Forge 47.4.0 sources JAR、Java17 javap输出。本地纯WaterMirrorDefenseBudget经Java17编译。完整本机构建因ForgeGradle依赖网络解析失败，使用仓库CI校验，不将失败写成通过。

## 跨PR审查

对 #47/#48 使用隔离 `git merge-tree --write-tree --name-only` 进行冲突探测，没有修改正式工作区或产生合并提交。

#47 实际冲突：AwakenedBladeVfxClient、GameplayConfig、AwakenedBladeCombat、ModNetwork、两种语言资源。

#48 实际冲突：构建工作流、CODE_INTEGRATION/QA_MATRIX、AwakenedBladeVfxClient、AwakenedBladeCombat、AwakenedCombatMath、AwakenedRankCost、ModNetwork及相应数学测试。

因此不能声称千本樱3.0/雷切3.1联合运行验证完成。正式分支未合入或cherry-pick它们。原生观察没有改它们的专用路由；继承与二次伤害护栏复用，后续合并仍需解决上述真实冲突并运行相关Smoke。

#43/#46已经属于#49的既有历史；本次不改觉醒仪式120tick时间轴、隐藏原刀/留鞘/归刃或世界反射代码。赤月旧终月及共享双魂代码保留；镜花主魂3.0已退役的历史锚点双魂协奏没有凭空恢复，不能把完整双魂战斗效果称作本次新增验收。

## 人工实机验收（均待验收）

- 第一人称水膜、原生拔刀剑动画及手臂覆膜深度关系。
- 第三人称皮肤/盔甲与水膜、行走/攻击贴合和氧气变薄。
- 凝水、压缩、猛击、水爆的可读性，以及目标逃离时落空。
- 水镜卸力的水幕扩散与回聚；普通受击不取消已预约水斩。
- SSS三镜短演出、水面与真实Tetra无鞘刀、原生VMD姿态及特殊动作降级。
- 原生环剑/三镜/觉醒仪式同屏，120tick仪式所有模型隐藏与归手行为。
- 真正多人观察、迟加入/退出范围、网络延迟、资源重载及实体ID复用。
- 多特效/低画质帧时间和服务器压力；未提供实测FPS数据。
- 真实水下、Respiration/潮涌能量/海龟壳，以及第三方无限氧气饰品。
- 千本樱/雷切独立PR整合后、赤月旧终月、完整双魂、Tetra自创流派及原生剑气/Rank联合回归。

## 明确限制

- 当前为每已提交动作第一个成功目标/窗口的一份追加预算；不会复制范围多目标或每段剑气伤害。
- 完整吸收但没有扣血的攻击不创建快照，避免把无实际伤害的窗口当成历史攻击。
- 自定义Rank capability实现和直接raw字段修改不属于原生晋升观察保证。
- 防御短期账本在同一服务器进程中跨Buff/武器/重登保留；服务器进程重启会清空短期账本。
- 水膜是轻量透明覆绘，第一人称、盔甲外观及真实联网需要人工判断画面质量。
- 没有高成本反射、完整分身、录像、逐tick网络动画或背包饰品扫描。
