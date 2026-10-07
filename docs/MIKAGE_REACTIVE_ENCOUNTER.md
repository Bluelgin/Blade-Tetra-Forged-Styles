# 御影重做：反应式战斗运行时

本 PR 是整只 Boss 重做的第一阶段：把战斗流程、玩家行为观察、选招、释放生命周期和清理边界接入实际御影实体。新的技能内容和具体连招图留待作者讨论；当前通过显式 legacy adapter 运行已有技能，保持一个可运行的过渡版本。

## 当前实现

- `MikageEntity` 保留注册身份、属性、同步动作和 Minecraft 生命周期入口。
- `MikageEncounterSetup` 负责队伍装备评分、生命/伤害/速度校准与访客配置。
- `MikageDamageService` 负责受击过滤、软上限、阶段血量门槛和对玩家的自适应伤害。实体传入 `super::hurt`，避免再次调用实体的 `hurt` 而递归。
- `MikageLegacyCounterRuntime` 接管旧 SA/次元斩适应、锁定解除和残心反制；`MikageSwordWheelRuntime` 接管剑轮部署、破层与恢复。它们共享原有 state holders，不复制状态，也不作为新技能扩展点。
- `MikageArenaMovement` 负责场地约束与参战者查找；`MikageEncounterPresentation` 负责 HUD、特效包、音效和新追踪玩家的场地快照。内部调用直接进入对应模块，实体不再转发旧脚本 tick。
- `MikageLegacyTiming` 收容旧技能的时序、几何参数；新技能各自定义这些参数。
- `MikageEncounter` 管理开场、阶段、前台技能、击败和取消；每只实体各自拥有运行时。
- `MikagePlayerObserver` 只观察服务端参战者已经发生的操作，不读取下一次输入。
- `PlayerBehaviorMemory` 分玩家记录接近/后退、格挡、滞空、攻击压力与重复释放的 SA；样本有窗口、次数上限和退出清理。
- `ReactiveCombatDirector` 在合法技能中比较距离、行为证据和近期释放记录，并输出选择原因。多人目标切换会清掉连招历史。
- `SkillTransition` 可以表达“上一招完成后，若玩家仍在后退，则偏好某个追击”；不预先锁死一整串技能。当前没有编写新的连招边。
- `SkillRunner` 同时只允许一个前台释放。每次释放拥有独立状态、`CastScope` 和单调递增编号。
- `MikageLegacySkillExecution` 独立拥有前摇计时器和目标 UUID；目标死亡、离场或失效时取消，不偷偷换人命中。
- `MikageLegacySkillPool` 是原有技能的临时可用性和战术元数据。三阶段选招也通过反应式导演，不再使用固定三格轮转；原界斩充能、语音和演出继续保留。
- `MikageLegacySkillEffects` 收容已有技能演出与多 tick 脚本。它不是新的技能扩展点，之后随新技能替换而缩减。
- `MikageAttackTimeline` 将延迟命中归属到释放；回调前移出待执行队列，支持取消/回调追加。`MikageCastEntityEvents` 将释放期间生成的原生视觉实体归属到同一个 scope，无需全维度扫描。

## 必须保留的剧情接口

`MikageStoryBridge` 继续调用原有 `ChallengeManager.queueDialogue` 和 `ChallengeManager.onMikageDefeated`。开场、胜利、失败、访客与神域对话的文本、语音、节点、奖励和首通 NBT 没有重写。

`ChallengeSession` 的缺失 Boss 恢复改为调用 `restoreCombatHealthFraction`：恢复已有阶段，不重新触发阶段台词。阶段在一次战斗中只向前推进，治疗不会让台词来回播放。普通 discard/清理不会触发胜利。

旧技能专属语音目前由原脚本触发；替换技能时，需要由作者确认新技能与这些语音的对应关系。

## 执行和场地所有权

新技能的目标、计时、命中记录放在本次 `SkillExecution` 中。技能的延迟任务和临时实体注册到 `CastScope`；结束、打断、转阶段或战斗取消时只释放一次。正常完成也要清掉临时视觉实体。

持续场地危险与前台释放分开：当前 `boundaryWalls` 可以在生成技能结束后继续运行；完整转阶段/结束清理会移除它。新技能若要保留场地物件，需显式交给场地运行时，而不是让已关闭的 cast 继续伤害玩家。

旧防御适应、数值校准和部分多 tick 状态尚在 legacy holders 中。这是技能重做前的兼容边界，不代表它们已经变成新技能实例。新增技能不得往 `MikageTechniqueRuntime` 再加一个全局 `xxxTicks`。

## 技能池讨论前不做的内容

不新增招式、改写剧情、定义御影最终连招表，或取消后摇来追求瞬间反应。现在只在前台技能结束、冷却和阶段保护允许时决定下一招；玩家的反制窗口继续存在。

讨论技能时需确定：基础动作、前摇预警、判定范围、反制方式、后摇窗口、可衔接条件、持续场地物件和对应语音。随后由这些定义创建新的独立执行类，替换 legacy pool，而不是继续扩展旧效果 switch。

## 验证

`MikageReactiveScenarios` 覆盖玩家证据隔离/衰减、重复攻击去重、合法技能过滤、反应式选择、条件衔接重新判断、目标切换、单释放所有权、取消与资源清理、异常清理、阶段单向推进和恢复。它也由 JUnit 测试调用。

纯 Java 核心可在 Java 17 下运行：

```sh
mkdir -p build/reactive-scenarios
java -m jdk.compiler/com.sun.tools.javac.Main -d build/reactive-scenarios \
  src/main/java/dev/bladetetra/challenge/mikage/*.java \
  src/test/java/dev/bladetetra/challenge/mikage/MikageReactiveScenarios.java
java -cp build/reactive-scenarios dev.bladetetra.challenge.mikage.MikageReactiveScenarios
```

完整验证使用 `./gradlew build`，并在游戏中检查单人/多人、转阶段、反制、离场、缺失实体恢复、回忆战和战后访客对话。
