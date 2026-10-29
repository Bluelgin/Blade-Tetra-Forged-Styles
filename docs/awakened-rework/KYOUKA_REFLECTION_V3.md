# 镜花觉醒仪式 3.0：实现、维护与验收边界

> 历史记录：实时反射实现已被破镜归刀方案替代，以下类、着色器和配置已移除。当前行为见 [KYOUKA_SHATTER_RITUAL.md](KYOUKA_SHATTER_RITUAL.md)。

## 基线与分支

基于 PR #43 的 `codex/true-name-release-ritual`，提交
`6e2e3444538ebf34fb5c9d4d67a1b9a3215bfb06`。
本分支为 `codex/kyouka-planar-reflection-v3`，Draft PR #46 的目标仍是 #43 功能分支。
#40 美术 → #41 战斗 → #42 千本樱 → #43 通用仪式的依赖保持不变；
#44/#45 的千本樱战斗修改不混入此分支。没有合并、关闭其他 PR 或发布版本。

旧镜花使用图集平面、青蓝光圈、径向刀影和翻转的刀身模型，未捕捉游戏世界。
此版本删除这些仪式表现，复用既有原刀离手、材料/刀装适配、刀鞘留身、真名 HUD 与归刃。
服务端战斗、觉醒条件、持久化和网络协议均未改动。

## 模块职责

| 模块 | 职责 |
| --- | --- |
| MirrorPlane | 双精度镜面基底、反射坐标、手性、逆转置裁剪平面、oblique projection、可见性 |
| ReflectionRenderer | 一次 vanilla LevelRenderer 离屏绘制、虚拟 Camera、RenderTarget 与冻结投影纹理 |
| ReflectionRenderState / ReflectionPass | GPU 状态事务、递归保护、捕捉范围、原生雾过渡历史恢复 |
| MirrorSurfaceRenderer | 有边界的投影反射材质、银白高光和渐进裂纹；兼容模式为明确的近似金属面 |
| MirrorShardMesh / Renderer | 共享裂纹拓扑、原 UV、薄背面、反光边缘、三维翻转与聚拢 |
| MirrorRitualController | 纯演出状态，沿用原 120 tick 生命周期，不操作 GL |
| MirrorRitualResources | 既有客户端会话与单主镜预算的桥接；不另建实体、Tick 系统或网络队列 |

反射矩阵保留负手性，绘制时反转 front winding；只算 yaw/pitch 无法替代此变换。
反射 Camera 在独立目标中绘制真实方块、天空和可见原生实体。虚拟相机投影采用镜面裁剪，
主镜通过齐次投影坐标采样，避免把图像按普通 UV 拉伸。

底层通过回调接受原刀与延迟刀影的实际原生模型绘制，不认识镜花时间轴。
镜像刀影只在反射相机内出现，与主镜共用几何映射；没有在现实中随机摆放几把刀。
为避免递归和第三方主画面特效污染，离屏绘制不分发 Forge 主世界阶段回调。
其他模组通过这些阶段绘制的特效不保证出现在镜中。

## 演出

沿用原 120 tick，不新增等待：

- 12–32：银白边缘和真实环境映像随镜面展开，原刀按通用流程离手。
- 32–72：主镜缓慢偏转；镜中原刀出现延迟/错位映像；两侧小镜复用主镜缓存。
- 72–85：刀身向镜面运动，同一组三角拓扑的深灰裂纹从中央扩散。
- 85–100：停止世界捕捉。完整镜面变成原位置的三角镜片，短暂停顿后翻转、扩散。
- 100–112：原镜片向刀身螺旋聚拢并化为银白短光痕，武器回归继续交给既有流程。

每片保留破碎前的 UV 并采样同一张冻结反射纹理；没有为碎片另建世界捕捉任务。
几何背面/侧面和边缘给镜片厚度，不使用普通蓝色三角粒子替代实体破裂。
刀尖与镜面的视觉接触、第三方刀身长度等细节仍属于正式实机美术验收范围。

## 预算与兼容性

`visuals.mirrorReflectionQuality` 是客户端配置，默认 **2**。

| 等级 | 宽度 | 更新上限 | 捕捉范围 | 每个破碎镜面片数 |
| --- | --- | --- | --- | --- |
| 0 兼容 | 不捕捉世界 | 0 | — | 32 |
| 1 低 | 128 px | 8 Hz | 24 格 | 32 |
| 2 默认 | 256 px | 15 Hz | 40 格 | 72 |
| 3 高 | 512 px | 30 Hz | 64 格 | 128 |

全客户端只保留一个反射目标，优先本地玩家，其次近邻有效会话；其他镜面使用近似材质。
目标高度最高 1024 px。沿用最多 8 会话、距离/视锥限制；不可见、过近、退化和非有限投影
跳过捕捉。暂停时已有图像继续复用，破碎后冻结，副镜不增加世界绘制。
没有每帧分配 framebuffer、大型缓冲区、逐片 draw-world 或后台 GL 操作。
以上为代码上限，**不是实测 FPS**。多个镜面及跨区块边界的渲染缓存开销仍需实际硬件测量。

Fabulous、Oculus/Iris、Embeddium/Rubidium/Sodium、OptiFine、Immersive Portals 和 Simply Mirrors
自动走兼容路径。该路径没有真实世界反射，不算核心实时反射验收；没有宣称支持这些修改后的
管线。原生 Forge 47.4.0 是已启动的测试环境，其他 47.x 和未知渲染器未穷举。

## 状态与资源生命周期

目标在首次有效捕捉时创建，尺寸/质量变化时重建；结束、失配、死亡、退出/更换世界、资源重载
及捕捉失败时释放。复用原会话有效性判断，不扫描背包或持久化镜面。
捕捉失败后本世界停止额外世界绘制，待换世界或资源重载恢复。

创建失败时先保留目标所有权，以便释放部分分配的原生资源。销毁也在状态事务中执行，
不会将已删除的纹理 ID 重新绑定。保存/恢复包括 framebuffer、viewport、矩阵与排序、
front winding、深度/混合/裁剪/颜色写入、活动纹理与绑定、纹理矩阵、光照、雾和着色参数。
水下雾的生物群系过渡历史另行保存；主视锥和实体/方块实体 dispatcher 相机重新建立。
不修改 Minecraft 的主 Camera 对象。

`-PmirrorRitualReview` / `-PmirrorPrototype` 打开状态恢复断言与目标计数。
普通游戏不会注册开发测试场景的 Tick 监听器。

## 已执行验证

- Java 17 本地重新编译和资源处理通过；生产重映射 JAR 已生成。
- 本地 486 项 JUnit：485 通过、0 失败、0 错误、1 项原有跳过。
  跳过项为未提供外部审计 JAR 的 ProviderEffectBytecodeBridgeTest。
- 新增 13 项针对性测试覆盖反射/FOV/裁剪、世界边界、不可见/非法投影、连续阶段、
  镜片拓扑面积、相同 UV 的运动/归聚和质量更新预算。
- [CI #713](https://github.com/Bluelgin/Blade-Tetra-Forged-Styles/actions/runs/40533395213)
  在提交 `2af6727` 上完整通过 build、JUnit 与四项继承的 Forge smoke。
  后续增加主相机调度限制、重播场景时钟同步和验证记录，最终提交的 Checks 独立显示，不挪用旧提交结果。

### 真实反射最小原型

Minecraft 1.20.1 / 项目 Forge、Tetra、SlashBlade 依赖，Mesa llvmpipe / OpenGL 4.5，
无额外光影。原始 framebuffer 截图：
[100](evidence/kyouka-v3/mirror-100.png)、[140](evidence/kyouka-v3/mirror-140.png)、
[180](evidence/kyouka-v3/mirror-180.png)、[220](evidence/kyouka-v3/mirror-220.png)。

彩色拱门与绿宝石柱实际位于观察者身后；镜中随视点横移与镜面旋转改变映像与透视。
主目标、Camera、投影与 front winding 断言未失败，场景结束日志已归档。
这证明最小世界反射路线可运行，**不代表正式四阶段演出已通过视觉验收**。
该轮牛的映像不够清楚，不宣称实体反射已完成视觉检查。

首轮过早捕捉曾遇到退化裁剪平面；已加入世界就绪、可见性和非法矩阵门控。
后续测试还处理了隔离世界的实验性设置确认页；没有把停在确认页视作引擎障碍。

### 正式仪式的最小运行检查

同一原生 Forge 环境中，第一人称、默认质量的真实刀具仪式已完整重播。
[原始日志摘录](evidence/kyouka-v3/ritual-native.txt)记录 23 次世界反射捕捉、114 次状态恢复审计，结束时 `targets=0, ready=false`，
未触发恢复断言。仪式截图确认主镜显示附近方块，刀身继续沿用原生模型。
这只覆盖一轮正常结束，不表示下面的完整验收矩阵已经执行通过。

首次自动重播因隔离客户端时钟落后而被既有展示包的时效校验拒绝，捕捉为零。
测试场景现在先发送原版时间同步包，再调用原有展示服务；未更改正式网络协议或时效校验。

## 正式实机验收与复现

**正式仪式画面与整合包验收由作者接手，当前保持 Draft，未标记视觉验收通过。**
手持已觉醒镜花，用 OP 命令 `/blade_tetra_ritual_preview` 重播，不改变觉醒/战斗状态。
验收真实反射请使用原生渲染路径；兼容模式单独核对降级与稳定性。

自动场景：`./gradlew runClient -PmirrorRitualReview`，隔离目录 `build/mirror-ritual-review`。
它使用 debug 觉醒的真实 Tetra 刀和既有 S2C 服务，覆盖第一/第三人称、FOV/窗口变化、
兼容/低/高质量、换槽中止、资源重载与再次重播；检查真实捕捉计数、兼容模式零捕捉和目标释放。
这不是自然首次解锁流程。截图在该目录 `evidence/`，状态断言不替代画面检查。

仍需正式确认：四阶段美术连贯性、刀影/真实刀身/接触点、镜片冻结映像、移动视点、
第一/第三人称与不同刀装、死亡/跨维度/断线、多玩家/延迟、长期重复使用和真实硬件帧时。
任意倾斜镜面的 billboard 方向、未知 shader/portal 管线不属于本轮已验证兼容范围。

## 技术调研与许可

- Minecraft / Forge：直接复用项目现有依赖的 Camera、RenderTarget、LevelRenderer 和事件设施，
  不打包原版二进制，不另做世界渲染引擎。
- [Water Shader Mod](https://github.com/MahoganyTown/Water-Shader-Mod)，
  `b42c4997a8e39ea9418a1a4260225bf59856b0a2`，CC0-1.0：调查其 Fabric 1.19.4
  多 pass/裁剪思路，未移植递归 GameRenderer、玩家隐藏、Shader 或资产。
- [Simply Mirrors 0.1.1 Forge 1.20.1](https://www.curseforge.com/minecraft/mc-mods/simply-mirrors/files/9239970)：
  实际下载 JAR 并以 CFR 0.152 调查；包内 MIT，Copyright 2026 NsTut。
  SHA256 `5fa726cdd871f7ea550ef3c0cd6fdd88ab24924c02772455e968ed4f58c6139f`。
  核对了重用 LevelRenderer、关闭 smart culling、相机与状态恢复的路径。
  没有复制其源码/Shader/资源，复杂光影适配未搬运，也未声称上游兼容。
