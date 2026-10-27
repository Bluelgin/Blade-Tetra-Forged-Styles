# 千本樱 2.1：真实镡柄与实时散华剑阵

基线：PR #43 `6e2e3444538ebf34fb5c9d4d67a1b9a3215bfb06`。本分支堆叠在 #43 上，不合并、不发布。首次觉醒的120tick时间轴、原刀离手、刀鞘留身、真名和归刃没有改动；战斗剑阵在仪式有效期间暂停。

## 刀柄修复

旧 `sakura_hilt.obj` 是一个固定、粗略的独立柄模型。它拿当前刀的动态材质作贴图，不能继承锻造模块、原始UV、镡型、法线或映锻刀装，而且旧钩子会隐藏刀鞘。

现在删除该文件。`SenbonzakuraDispersedRender` 只处理真正主手对象的战斗刀身组：玩家层/第一人称手持pass确认身份，LOWEST阶段处理已经完成材质适配的模型，不恢复任何先前取消的事件。GUI/item_*、掉落物、刀鞘、浮空仪式与镜影均不经过此裁剪。

* 普通模块刀：`DispersedBladeGeometry` 从 `ForgedGuardModel` 处理后的实际模型保留所有非钢刃UV岛的面，使用现有 `RuntimeWavefrontViewFactory` 建立不可变模型视图。没有缩放、重定位、重新生成柄、改变UV或物品NBT。动态材质、发光和原生光照照常应用。
* 映锻刀装：继续使用 `LegacyModelPartRenderer` 的真实几何选择、校准与挂载，仅跳过 BLADE 部件。TSUBA/TSUKA/HILT 的嵌套原生绘制有 try/finally 深度保护，避免把已经切好的完整镡柄再次裁剪。原生刀鞘全部保留。
* 未知第三方OBJ：不猜测其坐标或UV语义，保留完整模型作为安全回退。希望只保留柄的特殊外观应通过既有映锻适配器描述柄组/校准，而不是依赖本功能复制一份武器渲染器。
* 缓存按实际模型引用/目标组键控，LRU128；资源重载时清空。

## B1–B6 与原生时序

原生 B6 的 next 从 frame6（4 tick）开放，但其伤害回调一直到 tick6；默认动画超时又只有333ms。在 NextComboEvent 中直接于最早窗口跳回 B1 会截掉尾部攻击；等到 tick7又已进入恢复节点。

`SakuraSixthCombo` 因此只在散华千本樱的 B5→B6 路由中替换 B6 的**路由节点**。它直接引用原生 B6 的 motion、帧区间、速度、优先级及 tick/hold/hit/click/release 回调，仅多给100ms输入尾窗。tick0–6全部执行后，合法原生输入才允许 tick7/8→B1。早按、无输入、无燃烧资格都返回当前节点，触发 upstream 的 self-next guard。停止输入走原生 B_END 恢复，不强行进 B7。其他 Rengeki 仍走未改动的原生B6/B7。

保持原有Rengeki伤害/刀光配置对该节点的识别，避免包装ID漏掉原来的0.85倍率；不新增伤害/僵直/命中实体。可选Player Animator通过上游公开动画Map复用B6动画，无该Mod时仍用原生MMD。Rank继续存于SlashBlade能力：入口A+、起始等级决定燃烧速率、10tick扣费、禁止散华增益。路由和虚拟查询不扣费。零Rank立即禁止续连，死亡/换刀/跨维度会清除燃烧会话。

## 一组碎刃，而非两套装饰

* `SakuraShardFormation`：稳定索引的背后半月坐标，眼高适配姿态；纯数学最短角插值可测试±180°边界。
* `SakuraShardPool`：固定24/48/72个Shard，最多6组出击，IDLE→LAUNCH→SLASH→RETURN→IDLE。出击占用同一索引，因此阵列实际缺位。忙碌索引不会复制/抢占，归阵才可复用。
* `SakuraShardTrajectory`：原创三维贝塞尔切入、倾斜椭圆回旋、切线引导归阵。B1–B6决定左右交叉、倾斜与半径节奏，不改变攻击逻辑。
* `SakuraCombatVisualController`：绑定玩家UUID、已有Tetra id与热键槽；协调原生动作预测、服务端确认、目标修正、暂停/换世界/卸载/换刀清理。不负责绘制细节。
* `SakuraShardRenderer`：追加到现有 `AwakenedBladeVfxClient` 的樱色图集批次，不创建第二渲染引擎。24格内标准/高画质绘制有尖端和厚度的六面薄刃；远处/低画质使用沿轨迹朝向的图集刀片。短淡粉光轨最多保留6个tick样本。金属色通过分面明暗和原图集白色高光采样实现，不引入Shader或AI贴图。

所有出击从当前槽位出发，回到同一槽位；返回终点跟随移动中的玩家。目标最多每tick修正0.55格；死亡/卸载/离开12格范围则提前收束归阵。玩家瞬移超过8格时安全取消，避免画出横穿世界的轨迹。未命中也有前方短程回旋；只有真实伤害确认才增加3tick细小命中高光。

## 网络与预算

复用 `AwakenedBladeVfxPacket` 和 `TRACKING_ENTITY_AND_SELF`，增加可选SakuraCue（玩家、Tetra id、热键槽、维度、阶段、序号、服务端动作时间、目标ID）。协议19→20，客户端和服务端需一起更新；没有新的C2S入口。

服务端 `SakuraCombatVisuals` 从真实 DoSlashEvent 为每次动作发送一次SWING；同一次原生动作的多段slash共享序号。原生slash与动作关联，实际伤害结果至多发送一次HIT。客户端按阶段/动作时间把及时的本地预测与权威事件合并，HIT只修正现有组，不另生成一批刀。重复、过期、错误归属/维度、非有限坐标和非法阶段均被拒绝。未同步装备的事件最多暂存32条/6tick。无碎刃逐tick同步、无服务器碎刃实体。

每客户端最多8个近邻玩家池；只在客户端tick检查当前主手，不扫完整背包。最大视距64格，整组视锥裁剪（包含出击目标），优先近邻。低/标准/高：24/48/72碎刃，2/4/6轨迹样本，总1024/3072/6144 quads每帧上限；近处刀片6个quads、远处1个；所有碎刃共用已有一次樱色批次，无逐刀draw call。固定池在帧间复用，轨迹计算和历史更新在tick执行。以上为代码上限，不是多人帧时实测。

## 参考与资源

参考 [Terraria官方Wiki Zenith](https://terraria.wiki.gg/wiki/Zenith) 的高速往返/环绕视觉语言。Wiki正文抓取被403拒绝，未取得或复制Terraria实现；运动曲线和薄刃几何为本项目原创。没有搬运Terraria贴图/代码/音效。直接复用已有MIT依赖 [SlashBlade Resharped](https://github.com/0999312/SlashBlade_Resharped) 的ComboState、NextComboEvent、DoSlashEvent、原生模型/渲染接口；复用本仓库#40原创樱色图集。无新增二进制美术或运行前置，移除固定柄OBJ。

## 测试与人工验收

新增：真实标准模型各镡型的坐标/UV/法线保留；未知UV回退；手持/GUI/掉落/仪式可见性策略；B6尾帧门限；碎刃索引、真实空缺、归阵、组数上限、长时间连续动作、预测合并、重复确认、失去目标、轨迹端点/曲率和±180°；协议往返与非法数据。`./gradlew build` 运行全部JUnit。

`./gradlew runServer -PsakuraCombatSmoke`：独立Forge假玩家场景，通过原生progressCombo实际执行三轮B1–B6，验证B6七个原生slash、原伤害倍率、早按/无输入、虚拟查询不扣Rank、零Rank停止、散华保留、普通B6/B7不变。CI同时保留#43的四项Forge回归。

**本轮没有完成新的Minecraft客户端录像或画面验收。** 当前工作区没有先前完整Gradle/Minecraft客户端依赖缓存，只有Java17运行时及旧录像；构建与Forge验证交给GitHub CI，不以旧#43录像冒充本次散华效果。待CI结束在PR补充真实运行结果。

需玩家实机验收：第一/第三人称各材料及镡/缠柄、特殊映锻刀装、其他玩家旁观；凝刃/散华反复切换；移动/跳跃/蹲下/转向/游泳及移动/死亡目标；多人并发帧时和网络延迟；换刀/断线/重连/资源包重载；#43四刀仪式及千本樱分解/真名/归刃后进入战斗。自然觉醒条件、双魂主魂规则与旧档保持既有实现。不能把数学/Forge通过写成客户端画面通过。
