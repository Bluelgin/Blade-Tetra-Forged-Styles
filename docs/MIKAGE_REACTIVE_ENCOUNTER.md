# 御影重做：反应式战斗运行时

本 PR 是整只 Boss 重做的第一阶段：把战斗流程、玩家行为观察、选招、释放生命周期和清理边界接入实际御影实体。当前已加入格挡振刀与作者确认的第一个新技能“千门追斩”；其余技能通过显式 legacy adapter 运行，之后逐步替换。

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

## 格挡与振刀

这部分移植自作者的 `Bluelgin/Contract-Blade`，参考提交
`ccb41e84d4afbe088bfcc13f37613b5dd5f77e5e` 的 `BlackFoxDefense`、
`BlackFoxCounterCut`、`BlackFoxEncounters`、`BlackFoxBalance` 与
`BlackFoxAttackTrace`。代码的 MIT 版权声明随包保留在
`META-INF/licenses/contract-blade-duel-MIT.txt`。使用御影已有的特效与模型，
没有复制黑狐模型、动画、音乐或纹理。

- 一阶段御影空闲时，可以接住正面六格内的直接剑击或原生近战刀光。
  格挡保护为 8 tick，反击冷却为 60 tick；破绽、阶段保护、开场与正在释放的
  技能不触发格挡反击。移除了原先按 `tickCount % 70` 无预警减伤的逻辑。
- 反击由独立 `MikageGuardCounterExecution` 执行，目标 UUID、方向与原点在
  格挡时锁定。第 2 tick 提示，第 9 tick 起刀并排队，第 10 tick 命中，
  第 26 tick 完成；离场、阶段切换或取消会释放 scope 内的命中和视觉实体。
- 玩家在近战命中前 0–3 tick 挥刀、保持同一把剑并面向来刀，可以振刀。
  普通剑需至少 80% 攻击冷却；拔刀剑监听实际近战动作，覆盖原生及百构流派。
  一次挥刀只消费一次，同 tick 的两种事件不会重复装填。长期举刀不装填此窗口。
- 已标记的环斩、剑刃连击、踏步居合、断岳重斩、闪身反击近身段与新格挡
  反击可振刀。远程斩击、次元斩、幻影剑、结界和界焰保留原有应对机制，
  不累计这个振刀计数；新技能需显式标记近战命中。
- 成功时显示火花、音效和振刀计数，并给该玩家 24 tick 的御影攻击保护。
  五次独立接触打出 100 tick 大硬直；每次成功伤害会加快恢复 10 tick，
  最后一 tick 保留。大硬直期间关闭格挡、旧攻击适应和自动剑轮反制。
- 多人共享御影的失衡计数，但挥刀、持剑校验及保护按玩家隔离；4 tick 内
  的多次接触不会刷满计数。转阶段/结束清空，离场参战者的输入与保护及时回收。
- 原生刀光/次元斩会把伤害源改成玩家，`MikageBladeAttackTrace` 与 mixin
  保留正在执行的实体和出生持剑，避免把远程次元斩当作正面剑击。

## 新技能：千门追斩

`MikageSkillSelection` 将新技能与旧池合并后交给同一个反应式导演。千门追斩在各阶段开放，距离不超过 32 格，有独立的 400 tick 起手冷却；后退、持续攻击和滞空的已观察证据提高其选择分数。执行状态全部属于本次 `MikageThousandGatesExecution`，不往旧 technique holder 添加时钟。

1. 御影用 12 tick 进入千门。随后门、本体、持刀层与被动剑轮显示全部消失；藏身期间关闭实体碰撞与受击，保留 Boss 血条。
2. 从锁定玩家周围采样 3.6 格的出现位置。检查场地边界、世界边界、已加载区块、实体空间、液体、站立支撑（地面目标）和至目标的无遮挡刀路；空中目标允许悬空交锋。不在其他玩家身上出现。找不到有效位置就继续藏身重试，不能攻击。
3. 位置确定后，向目标玩家发送该坐标的空间音效；没有声源箭头或提前亮门。经过提示时间，再验证该位置，显形并响起拔刀声。此时锁定方向，之后不追踪玩家转向。位置失效则重新寻找，不从另一处突然攻击。
4. 提示与显形准备分别从 16/8 tick，依连续成功次数缩短到 8/4 tick；最快三轮以后的提示仍可听辨。出刀使用服务器单目标近战锥与遮挡校验，复用现有红白斩弧贴图，视觉不生成有伤害或附带眩晕的原生实体。
5. 新输入、同一把剑、面向来刀且位于刀路内才能振刀。每轮只消费一次输入；专用确认不会增加普通失衡计数，旧保护不会自动挡住新的追斩。保护继续挡住御影的其他重复伤害。
6. 振刀成功后，御影退入千门再追；闪避或伤害未被接受会清零连续成功次数并恢复首轮节奏，追斩继续，没有次数或总时长耗尽的自动结束。仅本招实际命中目标，或连续成功五次，结束攻击循环。命中保留 6 tick 收刀演出；第五次成功直接给予 100 tick 大硬直并碎门，不受此前普通失衡次数影响。

一次技能只锁定一名参战者：旁人不参与此刀的伤害或振刀判定，也不能替目标累加连续次数。空间提示私发目标；门、斩弧和碎裂演出供参战者观看。本次未引入多人分身，其技能调度仍需后续讨论。

转阶段、死亡、离场、取消或异常恢复原先的隐身、静音、重力和碰撞标记，关闭本次 scope 并清除客户端演出。旧剑轮反击、SA 与次元斩适应不会在这段交锋中插入技能。客户端通过 `ModularTechniqueVfxPacket` 的资源 ID 注册独立演出，场景有容量上限，关闭特效、换世界和资源重载会回收；不增加旧整数包或旧渲染 switch。

## 必须保留的剧情接口

`MikageStoryBridge` 继续调用原有 `ChallengeManager.queueDialogue` 和 `ChallengeManager.onMikageDefeated`。开场、胜利、失败、访客与神域对话的文本、语音、节点、奖励和首通 NBT 没有重写。

`ChallengeSession` 的缺失 Boss 恢复改为调用 `restoreCombatHealthFraction`：恢复已有阶段，不重新触发阶段台词。阶段在一次战斗中只向前推进，治疗不会让台词来回播放。普通 discard/清理不会触发胜利。

旧技能专属语音目前由原脚本触发；替换技能时，需要由作者确认新技能与这些语音的对应关系。

## 执行和场地所有权

新技能的目标、计时、命中记录放在本次 `SkillExecution` 中。技能的延迟任务和临时实体注册到 `CastScope`；结束、打断、转阶段或战斗取消时只释放一次。正常完成也要清掉临时视觉实体。

持续场地危险与前台释放分开：当前 `boundaryWalls` 可以在生成技能结束后继续运行；完整转阶段/结束清理会移除它。新技能若要保留场地物件，需显式交给场地运行时，而不是让已关闭的 cast 继续伤害玩家。

旧防御适应、数值校准和部分多 tick 状态尚在 legacy holders 中。这是技能重做前的兼容边界，不代表它们已经变成新技能实例。新增技能不得往 `MikageTechniqueRuntime` 再加一个全局 `xxxTicks`。

## 技能池讨论前不做的内容

仅加入作者已经确认的千门追斩、千门回廊与千门剑潮，不擅自扩展技能池、改写剧情或定义御影最终连招表。现在只在前台技能结束、冷却和阶段保护允许时决定下一招；玩家的反制窗口继续存在。

讨论技能时需确定：基础动作、前摇预警、判定范围、反制方式、后摇窗口、可衔接条件、持续场地物件和对应语音。随后由这些定义创建新的独立执行类，替换 legacy pool，而不是继续扩展旧效果 switch。

## 验证

`MikageDuelScenarios` 覆盖挥刀窗口边界、单次消费、换刀、多人保护隔离、格挡冷却、多次接触去重、五次失衡、受击加速恢复和清理。

`MikageThousandGatesScenarios` 覆盖五次连续成功、逐轮加速、闪避重置与上千轮仍继续追击、每轮命中结束、失效位置重试、普通失衡计数隔离、保护内仍需新输入，以及释放类型查找和取消清理。游戏内还需实测立体声定位、瞬移插值、空战、两名玩家互相穿过刀路、地形变化和关闭 VFX。

`MikageReactiveScenarios` 覆盖玩家证据隔离/衰减、重复攻击去重、合法技能过滤、反应式选择、条件衔接重新判断、目标切换、单释放所有权、取消与资源清理、异常清理、阶段单向推进和恢复。它也由 JUnit 测试调用。

纯 Java 核心可在 Java 17 下运行：

```sh
mkdir -p build/reactive-scenarios
java -m jdk.compiler/com.sun.tools.javac.Main -d build/reactive-scenarios \
  src/main/java/dev/bladetetra/challenge/mikage/*.java \
  src/test/java/dev/bladetetra/challenge/mikage/MikageReactiveScenarios.java \
  src/test/java/dev/bladetetra/challenge/mikage/MikageDuelScenarios.java \
  src/test/java/dev/bladetetra/challenge/mikage/MikageThousandGatesScenarios.java \
  src/test/java/dev/bladetetra/challenge/mikage/MikageCorridorScenarios.java \
  src/test/java/dev/bladetetra/challenge/mikage/MikageGateBarrageScenarios.java \
  src/test/java/dev/bladetetra/challenge/mikage/MikageSpacingScenarios.java
java -cp build/reactive-scenarios dev.bladetetra.challenge.mikage.MikageReactiveScenarios
java -cp build/reactive-scenarios dev.bladetetra.challenge.mikage.MikageDuelScenarios
java -cp build/reactive-scenarios dev.bladetetra.challenge.mikage.MikageThousandGatesScenarios
java -cp build/reactive-scenarios dev.bladetetra.challenge.mikage.MikageCorridorScenarios
java -cp build/reactive-scenarios dev.bladetetra.challenge.mikage.MikageGateBarrageScenarios
java -cp build/reactive-scenarios dev.bladetetra.challenge.mikage.MikageSpacingScenarios
```

完整验证使用 `./gradlew build`，并在游戏中检查单人/多人、转阶段、反制、离场、缺失实体恢复、回忆战和战后访客对话。


## 千门回廊 / Gate Corridor

`MikageGateCorridorExecution` is a complete foreground release: six cueable torii, three sword-riding passes, registered SlashBlade B1–B7 on every pass, exit traversal, landing and recovery. The target UUID stays fixed for the release. The director can select it within 48 blocks, with its own 480-tick cooldown. It cannot overlap Thousand Gates Pursuit.

Deployment lasts 24 ticks. Entrance cues last 16/14/12 ticks, with a 10-tick interval between passes. Each approach may adjust toward the target while the complete replacement route remains clear. Upon entering the attack segment, its direction and height are committed. B ends through the native registry's next-input gate, including the complete B7 finisher; no authored timer truncates the combo. Uninterrupted releases typically last roughly 12–15 seconds, depending on approach and landing height.

Native slash entities supply original timing, shapes and ratios. A scoped native TargetSelector adapter resolves only the locked participant, checks native bounds and line of sight, and applies trial damage before returning an empty native target list. This prevents native forceHit/stun/knockback and duplicate damage from overriding encounter rules. Two confirmed damage contacts per pass, at least ten ticks apart. Native slash visuals follow the rider; the enlarged summoned sword beneath her feet is decorative and has no separate hit box. Other participants may damage Mikage but cannot receive or parry this release's slashes.

A fresh sword parry during any native contact cancels all remaining passes and native slashes. The sword flies forward then breaks; Mikage descends. Landing awards one shared parry-meter contact and a 40-tick stagger, or the existing 100-tick full balance break when the meter reaches five. These use one recovery clock, retaining the existing hit-accelerated recovery. Ordinary dodges and damage contacts do not cancel the release.

`MikageCorridorRoute` checks arena bounds, loaded chunks, world border, blocks and fluids along the entire swept body before cue/arrival and before every movement. Entrances cannot overlap a player. Invalid terrain aborts safely; no silent relocation to a different cued entrance. Target loss, phase changes, defeat, normal completion and cancellation restore gravity/visibility/silence, remove owned native slashes and fade the cast's client scenes. A 600-tick watchdog prevents indefinitely chasing a continually moving target. Persistent six-gate scenes are bounded and cleared on world change or resource reload, and honor the combat VFX settings.

Client Combo B rotations are retargeted from Contract-Blade's native VMD samples onto Mikage's arm rig; legs retain the sword-riding stance. Code adaptation is covered by the existing Contract-Blade MIT notice. Animation attribution is bundled separately in `META-INF/licenses/SlashBlade-animation-MIT.txt` (Mysterious Mountain Forging-shop Group, 2024). Story/dialogue/voice/reward and visitor flows are unchanged.

Validation: `MikageCorridorScenarios` adds 44 assertions covering all three passes, native completion authority, the two-contact cap/interval/reset, parry after the damage cap, irreversible cancellation, shared short/full stagger and exactly-once cleanup. Focused modules have architecture budgets without increasing existing budgets.

Manual game checks still required: ground and airborne targets; movement during approach versus committed B flight; torii cue/exit alignment and sampled hand/sword poses; parry at each B stage; normal and fifth-contact knockdowns; block edits across the route; two participants with the bystander swinging; target disconnect/dimension change and phase transition mid-flight; low VFX settings/resource reload; latency and native slash attachment. Automated builds do not establish visual quality or in-game timing.


## 千门剑潮 / Gate Barrage

御影在目标前方约 18 格选择可步行到达的门心位置；门宽约 14 格、高约 11 格。先显示 4 tick 竖线，再用 6 tick 拉开，复用千门追斩的鸟居几何形状。30 tick 蓄剑后开始连续扫射。技能进入反应式技能池，范围 0–48 格、仅地面目标可选，有独立 720 tick 冷却，不与其他前台技能同时释放。

每 4 tick 发射五把原生幻影剑，分布在门内五条剑道，逐渐调整瞄准点；射出后沿直线飞行，无追踪。玩家用拔刀剑原生挥刀反弹流程抵挡，保留 `ArrowReflector.doReflect` 的原有速度和方向。反弹剑变为金色，并不再命中参战者、Boss 或核心：推进与破门仍需玩家近身完成。原生 TargetSelector 的 PvP 开关不应禁用 Boss 攻击，因此投射物使用独立 caster/cast 所有权、不设置 native shooter；命中只授权当前参战者，试炼伤害保留无敌帧和每人 10 tick 接触间隔，禁用原生 forceHit、眩晕与销毁范围药水效果。

门心是脚下约 1.5 格高的可攻击独立目标。黑洞调用本体 `JudgementCutRenderer`，直接读取依赖里的 `slashblade:model/util/slashdim.obj` 和 `slashdim.png`，不复制或重画本体资源。客户端代理只用于渲染，从不加入世界或 tick；没有次元斩伤害、拉扯或额外碰撞。动画按本体 15/28 tick 周期的公倍数循环，持续战斗不会因 native lifetime 耗尽而变透明。核心与鸟居使用实体同步数据，支持后来加入和重新进入视距，无需累计演出包；关闭装饰 VFX 仍显示破门目标与门框。

破核需要三次独立持剑输入，且玩家距门心水平不超过 2.75 格、垂直不超过 2 格、视线通畅。仅接收直接近战或玩家原生近战斩击；远程次元斩、幻影剑、换刀、过期输入、自动连击阶段推进和同次挥刀的多段伤害不会额外计数。输入最长有效 20 tick，同 tick 事件去重，两次核心伤害至少间隔 12 tick。每次命中核心变色、播放破裂音和进度提示，并暂停新剑发射 12 tick；已射出的剑继续飞行。第三次立即清除本次全部幻影剑，鸟居在 16 tick 内收成竖线消失，御影进入共用恢复时钟的 60 tick 硬直；普通失衡计数保留，受击仍可加速恢复。

多人共用门心和三次破坏进度，各玩家输入/伤害保护独立。主目标离场会将扫射转向另一位合法参战者，剩余参战者仍可破门；无合法参战者则取消。没有普通时限、闪避次数或波次耗尽结束条件。无有效门位则放弃该次释放；完整门框和通往中心的步行路径检查场地范围、区块、世界边界、障碍、液体、脚下支撑，持续每 20 tick 复检地形。转阶段、战斗结束、异常、实体丢失、地形失效或取消均回收核心与所有剑。每次最多 64 把活剑，寿命 80 tick；清理保存有界活集合，不为无限剑潮积累清理回调。存档重载后不存在的 cast 不会恢复攻击，孤儿实体首 tick 自行消失。

`MikageGateBarrageScenarios` 新增 53 个断言，覆盖预警、长期不断流、实体预算、三次多人独立输入、重复事件、多段伤害、暂停边界、换刀/过期/未来输入、接触间隔与离场隔离、破门演出结束、阶段/目标丢失/取消的一次性清理。加上此前四组，纯 Java 战斗场景共 219 个断言。独立模块设有架构预算；MikageEntity 保持 385 行。剧情、对话、语音、奖励与访客接口保持原样。

## 常态间合

间合属于普通战斗移动，不占技能池。正常围绕玩家保持约 6–8 格；玩家接近到 5.5 格以内时，以面向玩家的斜后退短步拉开；远到 8.5 格外时追近到 7.2 格。距离阈值带滞回，避免在边缘逐 tick 来回切换。

每个目标最多连续退让两次。每次最多 12 tick，拉开至 6.5 格可以提前结束；第一次结束后停顿 8 tick。第二次退让结束后，继续靠近至近战范围，下一次选招优先使用已有 BLADE_COMBO；原有技能冷却、前摇、刀路和振刀规则保留。已释放的近战或格挡反击结束（包括被振刀打断）后才恢复退让额度；取消前摇、施放远程技能、路线受阻都不能刷新额度。路线受阻时停步，把接触机会留给追近的玩家。

常态移动只在战斗空闲、脚踩地面时执行。开场、阶段保护、技能前摇/释放/收刀、失衡、互动破绽与剑轮破层期间暂停，不刷新退让额度。技能开始前释放常态拥有的导航与侧移；释放导航时核对 Path 身份，避免下一 tick 清掉技能刚接管的路径。Goal 在战斗期间保留 MOVE/LOOK 标志，阻止低优先级闲逛和看人 Goal 干扰已锁定的技能方向。普通移动没有瞬移或额外伤害。

路线是短距离地面步行/侧移，沿完整实体体积检查场地、区块、世界边界、障碍、液体、地面支撑、玩家与界斩火墙；一侧受阻尝试另一侧，两侧都不安全就停步。开放的火墙缺口必须容纳整个实体。空中和水中暂停这套地面脚步，继续使用现有反应式选招。目标切换、离场以及阶段/死亡/取消清理会移除旧状态。

`CombatSpacingState` 只管理决定与额度，`MikageCombatFootwork` 管理移动命令，`MikageFootworkRoutes` 管理安全路线，`MikageSpacingGoal` 只负责 AI 调度；MikageEntity 仍为 385 行，独立模块增加行数预算而不提高旧预算。

`MikageSpacingScenarios` 新增 42 个断言，覆盖距离滞回、短步/停顿边界、两次额度、2000 tick 持续追击、技能暂停、阻路、多人回调隔离、接刀恢复、目标切换、离场、阶段清理和无效距离。六组纯 Java 场景共 261 个断言，JUnit 调用同一套场景。游戏内仍需检查实际步幅和追击速度、玩家绕圈、贴身追击是否能接刀、墙角/台阶/火墙缺口、空战、技能锁向和多人延迟；这些手感不能由状态测试或编译结果确认。

仍需游戏内验证：本体不同普攻连击的反弹体验、PvP 关闭时伤害与反弹、输入冷却及多段斩击、门心可达性、门框/黑洞/金色反弹剑演出、迟加入与延迟、多人转移目标及共同破门、地形改动、转阶段/退出/重载和低特效设置。自动化场景与完整构建不替代游戏内视觉和手感测试。
