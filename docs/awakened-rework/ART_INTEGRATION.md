# 觉醒刀 2.0 · 真名解放 / Art integration

**状态：美术资源交付；未接入游戏。** 基线 `72331ebbf5f0f62f2aded0cf28a713ca3a146fb7`（main，源码 1.6.5）。本目录内的图和 MP4 全部是离线 SVG / 图集合成，绝不是 Minecraft 实机截图。方块人只表示玩家模型的位置，不是交付的人物资产。

本 PR 仅新增资源、SVG 源、工具、文档，以及允许跟踪这些文件的 `.gitignore` 规则。不修改 Java、网络、SA、觉醒条件、刀身模块、旧资源或现有资源引用。当前 main 不会自动播放新资源；可以独立合入，代码开发者随后接入。

## 1. 已交付与尚需实现

| 类别 | 已交付的实际文件 | 仍需代码实现 |
|---|---|---|
| 赤月 | 6 个方向 × 出现/稳定/崩解刀痕、交点、裂隙、残月、十字终斩、刀身红纹、符纹/HUD、16 帧刀痕生命周期 | 命中方向与目标锚点、因果斩线组合、逐线点亮和收束 |
| 镜花 | 三层水镜、4 种碎片、虚斩、独立水波、3 张模型用材质、符纹/HUD、16 帧镜面生命周期 | 真实动作记录、玩家模型与动画重演、镜面中的人物；不要求截图或反射缓冲 |
| 千本樱 | 小/中碎刃、长幻影剑、樱瓣刃、消散碎屑、樱瓣、五瓣阵心、符纹/HUD、16 帧分解/逆放聚合 | 刀身隐藏时机、批量顶点、剑阵与攻击时序 |
| 雷切 | 可拉伸雷步带、方向电弧、断续闪光、落点脉冲、雷刃、贯穿光、3 张模型材质、符纹/HUD、8 帧闪烁 | 真实位移路径/模型残影、路径逐段爆发 |
| 共用 | 光爆、透明度坡度、刀光转换遮罩、12 帧符纹折叠 | 专属符纹与折叠遮罩合成、真名文本、本地化、成功事件 |

共 **62 个独立组件、68 帧材质动画、130 个 SVG、5 张组件图集 + 5 张动画图集**。全部为手工定义的贝塞尔曲线/矢量路径；没有 AI 图片、固定人物、外部位图或字体依赖。自有美术采用仓库 MIT 许可。

三种剑阵编队、镜中模型、空间中交错斩线、巨大樱花剑阵均是**动态组合设计**，不是一张可直接播放的全屏图。离线预览的空间布局是参考；实际 PNG 素材已制作，不把布局示意冒充游戏实现。

## 2. 目录、导出与校验

- 可编辑源：[`../../art/svg/awakened/`](../../art/svg/awakened/)，按四刀与 shared 分类；动画帧在其子目录。
- 游戏 PNG：[`../../src/main/resources/assets/blade_tetra/textures/effect/awakened/`](../../src/main/resources/assets/blade_tetra/textures/effect/awakened/)。本节末尾资产表和 [`assets.json`](assets.json) 给出每个完整路径及 UV。
- 导出：[`../../tools/art/export_awakened.cjs`](../../tools/art/export_awakened.cjs)。**只读取现有 SVG**，可以手改 SVG 后导出，不必先运行设计生成器。
- 设计生成器：[`../../tools/art/build_awakened.py`](../../tools/art/build_awakened.py)。重新生成最初 SVG 及 manifest，**会覆盖 SVG 手工编辑**；仅在有意重置源设计时运行。
- 预览：[`../../tools/art/preview_awakened.py`](../../tools/art/preview_awakened.py)。从实际 PNG 图集裁片，不使用替代图形伪造刀光。
- 检查：[`../../tools/art/validate_awakened.py`](../../tools/art/validate_awakened.py)，报告 [`validation.json`](validation.json)。检查 PNG 格式/尺寸/透明度、UV、重叠、2px 边界、68 帧与未用格、SVG 外部依赖、11 个视频编码、Java 修改边界。报告包含源文件 SHA-256 与解码 RGBA 摘要，跨 PNG 压缩实现可比较像素。

复现环境：Python 3.11+，Pillow **12.3.0**；Node 22+，sharp **0.35.5**（librsvg 2.63.2）；ffmpeg / ffprobe，libx264；预览字体 DejaVu Sans。依赖仅用于开发，不打入 JAR。可在临时 venv 安装 Pillow，在临时 Node 目录安装 sharp 并设置 `NODE_PATH`，不必改项目构建文件。

```sh
# 从已编辑好的 SVG 导出；不先重置源图
node tools/art/export_awakened.cjs
node tools/art/export_awakened.cjs --check
python tools/art/preview_awakened.py
python tools/art/validate_awakened.py
```

动画预览为 960×540、20 fps、H.264/yuv420p，无声音。时间轴统一按 **20 tick/s**；预览不会拖长一闪的突进来营造慢动作。颜色为 sRGB RGBA，直通 alpha；输出不是预乘 alpha。预览器使用普通 alpha 合成，是结构与节奏检查，不模拟游戏曝光、真正的加色混合或光照。

## 3. 纹理契约

组件图集紧凑分配：赤月 **1024×512 / 8×4格**；镜花、千本樱、雷切 **512×512 / 4×4格**；shared **256×256 / 2×2格**。格子统一128×128；每个图案至少有 2px 完全透明边界。空格保持全透明。动画图集 **512×512**，4×4 格；按行从左到右，从上到下。动画不是 Minecraft 地形 atlas 的自动 `.mcmeta` 动画，必须由客户端按帧选择 UV。

`assets.json` 包含 `id/source/texture/rect/canvas_size/uv/pivot/size_blocks/blend/interface/stage/tier/status`；`texture` 是完整 ResourceLocation；`rect` 是像素矩形。`uv` 已内缩 0.5px，原点在左上，V 向下；默认 pivot 是单格中心 `(0.5,0.5)`。`size_blocks` 是**整张格子的宽、高**（不是半径），不要误当作绘制函数的 halfSize。微小图形在格内有留白，缩放以格子为准。

建议线性过滤、禁用 mipmap、clamp-to-edge；禁止对图集整体 repeat。需要重复条带时逐段重复该 tile 的 UV。使用工具函数时注意现有 `rotatedAtlasBillboard` 采用未内缩格边；接入者应通过 `texturedQuad` 传入清单 UV，或扩展该工具的 UV 参数。

### 混合、遮挡与可读性

- `alpha`：`SRC_ALPHA / ONE_MINUS_SRC_ALPHA`，保留墨色外轮廓以适配雪地和白墙；纹理本身有明暗细节。
- `additive`：推荐先用同一纹理普通 alpha 绘制轮廓，再以 `SRC_ALPHA / ONE`、alpha × 0.25–0.4 叠加高光。预算不足只保留 alpha 轮廓；不要在白天仅绘制加色层。
- 世界特效 depth-test 开、depth-write 关；不允许透墙高亮。透明平面按距离处理；一组同图集/同混合模式集中提交，结束后恢复渲染状态。
- 常规攻击最多一个 2–3 格刀光；中型技能优先地面/目标边缘；终结仅十字与贯穿光短时加亮，禁止全屏白闪和常驻巨阵。近相机将 alpha 降至 0.25，裁掉贴脸平面；不要让背后残月盖住第一人称视野。
- 日常/中型/终结档由 `tier` 提供资源建议，服务端不需要为每一片材质建立实体。

### 空间定位与方向

| 资源族 | 推荐锚点/方向 | 尺寸与寿命 |
|---|---|---|
| 断命线 | 目标胸口外侧 0.02–0.05 格；局部 XY 平面；scar_0 沿 +X，其他方向已预旋转，代码可继续绕法线旋转 | 2.8×0.7 格只是普通示例；存活由战斗系统决定，淡入3t，结束6t |
| 因果交点/裂隙/十字 | 真实命中交点；裂隙沿局部 +Y，十字可绕法线旋转 | 0.3 / 1.2×4 / 5×5 格；十字亮峰2t |
| 水镜/水波 | 镜面竖立，玩家周围1.5–3格；脚下水镜水平放置，离地0.035格避免闪烁 | 1.4×2.1；脚下可改为2×2；远层透明度0.35 |
| 镜片 | 原镜面平面的顶点位置出发，法线方向小幅速度，禁止随机塞满全屏 | 0.2×0.35；终结后16t淡出 |
| 虚斩 | 跟随记录动作的实际攻击平面；双轨虚线是镜花辨识点，不是伤害范围提示 | 2.6×2.6；6–10t |
| 散华碎刃 | 本地 +Y 朝刃尖，轴向缩放；批量 Billboard 或定向小平面 | small 0.12×0.4；长刃可0.18×1.1；独立寿命 |
| 雷步带 | +X 从起点指向终点；以真实路径分段，+Y 为横向厚度；每2–3格一段以免电弧拉糊 | 长度随路径变化，厚0.18–0.3；尾迹最多12t |
| 雷步落点/雷刃/贯穿光 | 落点水平；雷刃用真实挥刀平面；贯穿光沿实际位移+X | 脉冲1.3×1.3；雷刃2.7；贯穿光仅3t峰值 |
| 背后残月/专属符纹 | 背后0.6格，胸口以上0.4格；跟随玩家朝向，避免跟相机贴脸 | 残月2.5；符纹1.2，持续≤44t |
| HUD | 快捷栏外侧；不得挡准星和原有流派HUD；像素位置由GUI scale决定 | 将128px格缩为20–24 GUI px；同一时刻只显激活刀魂 |

刀光的长宽分开缩放；赤月斜线优先使用 scar_0 后旋转，以免预旋转方向在非等比缩放下变形。六张方向版本可用于等比目标印记；appearance/break 静态状态也可以采用独立 alpha/scale 插值。

## 4. 材质动画

| 图集（完整资源前缀为 blade_tetra:textures/effect/awakened/） | 有效帧 / 播放 | 帧段 |
|---|---|---|
| akatsuki/scar_lifecycle.png | 16帧，1t/帧，单次 | 0–3出现，4–9稳定，10–15崩解；持续印记停在6帧，再播10–15 |
| kyouka/mirror_lifecycle.png | 16帧，2t/帧，单次 | 0–3生成，4–7水波，8稳定，9–11裂纹，12–15破碎；稳定停8 |
| senbonzakura/blade_disperse.png | 16帧，1t/帧 | 正放分解，逆放15→0聚合；更大碎刃运动由同一基础碎刃动态图组装 |
| raikiri/step_flicker.png | 8帧，1t/帧，单次 | 0–2闪光，3–7残留；空余8–15全透明，不播放 |
| shared/release_fold.png | 12帧，1t/帧 | 0–5展开，6–11收束；与各自专属符纹叠加，非共用魔法阵；空余12–15全透明 |

模型材质 `echo_wash`、`echo_edge`、`echo_gradient` 是 **UV中性的条纹/边缘色/透明度坡度**，不是皮肤或身体轮廓。可以重复渲染玩家原模型，保留原动画并整体着色/淡出；`echo_edge` 的视觉轮廓必须由模型顶点法线外扩或轻微模型缩放产生，不能直接把此纹理当作任意玩家皮肤UV的描边。无描边接口时省略 edge，使用原皮肤模型乘蓝银/白蓝颜色和alpha。这是需要实现的普通模型 pass，不要求特殊 shader。

刀身纹路 `blade_trace` 也不能直接覆盖未知的 Tetra 刀身 UV；建议沿刀刃画一条很窄的定向平面。精确贴合刀面属于后续代码/材质映射工作，本 PR 不改刀身贴图。

## 5. 演出时间轴（tick，相邻区间边界共享）

时间轴是建议客户端演出，不定义伤害或无敌帧。T=0 应来自服务端确认事件；不得根据挥刀猜测觉醒/终结成功。迟到事件用 age 跳过已过阶段，不把伤害等到演出结束才执行。离线 MP4 以相同 tick 编排，结尾有少量保持帧。

### 四种觉醒（0–44t，2.2s）

| 刀魂 | 时间 | 资源与动作 |
|---|---|---|
| 赤月 | 0–6 / 6–23 / 23–32 / 32–44 | 残月淡入；刀身红纹亮起；专属符纹/真名文字显现并向刀身收束；残月与红纹淡出，在32t接刀鸣（音频未制作，复用现有声音由开发者选择） |
| 镜花 | 0–8 / 8–18 / 18–26 / 26–44 | 脚下镜面生成；倒映原模型/刀影；镜裂与碎片；真名文字/符纹显现并淡出。镜面材质按16帧生命周期缩到约26t，稳定段可略过 |
| 千本樱 | 0–16 / 16–27 / 27–32 / 32–44 | 樱瓣绕玩家；半径收束并聚成刀；刀形和专属符纹短停；樱瓣重新散开，alpha归零 |
| 雷切 | 0–12 / 12–17 / 17–26 / 26–44 | 沿刀身小电弧；竖向雷光峰值；雷光淡出、专属符纹/真名短停；归静 |

真名用游戏本地化文本渲染“妖刀·赤月 / 镜花水月 / 千本樱 / 雷切”，不把中文字烘焙进纹理。离线预览只用拉丁名称代替文本占位，不是最终游戏字体。

### 残月·命断（0–56t，2.8s）

| 时间 | 演出 |
|---|---|
| 0–12 | 六个方向斩线每2t依次点亮；位置来自已有真实印记 |
| 10–18 | 独立 causal_node 在交点淡入 |
| 14–22 | 黑红 rift 在交点张开；透明边缘不遮挡全部目标 |
| 24–32 | 斩线局部长度由1收至0.35；节点保持 |
| 32–34 | execution_cross 快速到峰值，沿攻击方向偏转；禁止全屏闪白 |
| 34–43 | 十字衰减，节点熄灭，裂隙闭合 |
| 40–56 | 原斩线改用break帧并逐渐归零 |

### 无相·万镜归一（0–56t，2.8s）

| 时间 | 演出 |
|---|---|
| 0–18 | 8片镜面环绕展开（低3片），按序淡入 |
| 14–24 | 镜内以模型pass显示记录动作的残影，远层透明度更低 |
| 24–38 | 每面镜延迟1t点亮，replay_cut 按真实攻击平面向目标收束 |
| 36–41 | 汇聚斩短暂叠亮；多斩可合批，不能合并为一张固定方向大图 |
| 40–50 | 全镜同步裂纹/碎开；每镜3–4片即可表达，不需要物理刚体 |
| 48–56 | 蓝白碎片消散；低画质直接淡出，不画次级光屑 |

### 终景·千樱葬（0–64t，3.2s）

| 时间 | 演出 |
|---|---|
| 0–16 | 客户端已有碎刃停止、重排为五瓣巨阵；每瓣用同一基础碎刃，144片为标准示例 |
| 16–24 | 阵形固定，array_core 淡亮；阵心不替代真实碎刃 |
| 24–28 | 玩家挥刀提示（动作由真实状态驱动，美术无新攻击动画） |
| 28–42 | 五瓣每2t一批突进；按镜头远近、距离和玩家数进一步减片 |
| 42–48 | 刀刃淡出，改为 falling_petal |
| 48–64 | 樱瓣飘落并淡出，无高开销实体 |

三类常用编队参考见 [`previews/sakura_formations.png`](previews/sakura_formations.png)：扇形沿±1.2rad排布；护阵绕玩家1.2–2格；空中阵在目标上方2.5–4格。均复用 blade_small，可混入每8片一把长刃强化节奏。示意图是编队构图参考，不代表攻击判定形状。

### 神鸣·一闪（0–40t，2.0s）

| 时间 | 演出 |
|---|---|
| 0–12 | 刀鞘周围汇聚少量 direction_arc；5–12t出现落点式细地纹 |
| 12–16 | 最短蓄势保持，电弧贴近刀鞘，不召唤落雷 |
| 16–20 | 真实高速突进，ribbon跟路径更新；模型残影最多3个 |
| 20–30 | 尾迹最多12t；路径上6个段落从22t起逐个爆发，每段亮2–4t |
| 29–32 | piercing_light 沿路径贯穿，白色核心峰值仅2–3t |
| 32–40 | 所有电弧和轨迹熄灭，保留新落点玩家模型 |

普通雷步示意0–32t：4–14t位移，12–18t落点脉冲，14–30t尾迹衰减。镜像重演示意0–40t：0–6t开镜，10–12t虚斩出现，12–24t重演移动，25–36t关镜与刀光淡出。凝刃转换示意0–32t：0–15正放，16–31逆放。

## 6. 质量分级与客户端预算

| 内容 | 低 | 标准 | 取舍 |
|---|---|---|---|
| 同一终结镜面 | 3 | 8 | 保留近处镜面，远层省略 |
| 同一剑阵碎刃 | 32 | 144 | 仍保留五瓣轮廓；不改变伤害/范围 |
| 每玩家残影模型 | 1 | 3 | 低省略描边pass，模型自身着色 |
| 每条雷步路径平面 | 1 | 2 | 标准交叉平面；低只保留面向相机的一带 |
| 次级镜片/樱瓣 | 0–8 | 24–48 | 距离远、多人同屏优先减装饰片 |
| 材质动画 | 同一纹理，隔帧读或稳定帧 | 全帧 | 不复制一整套低清纹理，节省显存 |
| 光照 | alpha轮廓一次 | alpha + 可选加色高光 | 不要求bloom或后处理 |

这是**推荐效果预算，尚未经过 GPU 实测**。建议客户端总预算：低192张/标准512张透明小平面（所有玩家合计），超过后先减次级片、远处镜面和残影；不是每个技能都可同时占满上表。距离裁剪沿用 `BLADE_COMBAT_VFX_DISTANCE`，总效果数沿用 `BLADE_COMBAT_VFX_MAX_EFFECTS`，等级映射与强度沿用现有配置。更远的效果只保留核心刀光与符纹，绝不生成数百个实体。

标准纹理显存：1024×512×4 + 3×512²×4 + 256²×4 + 5×512²×4 ≈ **10.25 MiB**（不含mipmaps）；实际 PNG 压缩体积很小并不等于显存小。低画质可将每张图集在GPU上传前缩到宽高各一半（UV不变），约2.56 MiB；这一步需要代码支持，当前仅提供同图集减片方案。图集不用的空格是留给未来扩展，不用于多层全屏透明平面。

[`previews/*_quality.png`](previews/README.md) 左侧低、右侧标准，上深下浅，展示减片结果；雪地可读性依赖alpha轮廓，不能仅加色。

## 7. 与当前代码的接口及冲突

| 审阅文件 | 当前实现事实 | 接入要求 / 本PR边界 |
|---|---|---|
| `AkatsukiAwakening` | 现有满月夜间、可见天空、SA击杀窗口等校验，成功后粒子/声音/成就 | 成功位置发送视觉事件；不重复判断条件，不重做觉醒 |
| `AkatsukiExecution` | 现有低血量处决及融合协同 | 本PR不替换处决；因果印记状态和终结是否共存由代码开发者处理 |
| `KyoukaAwakening` | **当前是夜间、露天、玩家在水中，不要求雨**；还有有效攻击/击杀校验 | 任务所述“雨中”与main不符，保留main事实；不增加雨条件 |
| `SenbonzakuraAwakening` | 现有樱花林相关觉醒、印记与剑刃释放 | 只消费真实成功事件；凝刃/散华状态需新增同步 |
| `BladeLegacyEasterEggs` | 雷切觉醒在此处理引雷充能及SA击杀；不存在独立RaikiriAwakening文件 | 在现有成功路径追加VFX事件，保留原校验 |
| `RaikiriChainHandler` | 当前雷切是确定性连锁导电等逻辑 | 本PR不会把连锁伪装成雷步；新位移和路径数据由战斗代码负责 |
| `SoulLegacyState` | 支持单/双魂铭；active()取固定优先级的主魂 | 传明确soul id；不依赖主魂色覆盖所有融合技能的特效 |
| `SoulResonance` | 现有魂铭共鸣/协同规则 | 不引入新的消费、倍率或冷却 |
| `KyoukaVfxClient` | 已有地面镜池、4×3镜片图集及透明/加色分批绘制 | 新图集不是旧4×3布局；必须新ResourceLocation与UV，不能覆盖旧文件 |
| `RaikiriVfxClient` | 已有纹理带、程序化电核、质量配置 | 可复用Ribbon几何；旧packet不含雷步路径/模型姿态，不足以完整驱动新系统 |
| `BladeTechniqueVfxClient` | 通用战技演出入口与现有特效 | 可借用生命周期/裁剪机制；不要把预览中的运动写入占位Java |
| `VfxPrimitives` | billboard、rotatedAtlasBillboard、texturedQuad/Plane、texturedRibbon | Billboard可直接复用；图集Ribbon/Plane需接入自定义UV；现有整图工具不能直接采样组件图集 |
| `MaterialTextureManager` | 当前雷切刀身色偏紫、其他三刀已具专属配色 | 新雷切战斗图按白蓝制作；刀身改色不属于本PR，必要时由开发者统一 |

建议客户端视觉事件包含：明确的刀魂/技能/阶段、世界维度、源/目标实体ID、触发tick、持续tick、随机种子、命中点/法线、方向、强度；雷步增加路径点及起止时间；镜像重演需要有效动作ID/玩家模型姿态与采样时间。具体协议由代码开发者定义，`assets.json` 仅供素材校验注册，**不是已经可用的网络协议**。

所有效果进入当前世界后有上限、换世界/断线清空；不可跨世界复用旧实体ID。不需要特殊shader、屏幕抓取、深度采样、噪声通道、SDF、反射FBO或复杂模型。镜面中的角色是着色的玩家模型pass，光学真实反射不在交付范围。

## 8. 预览、验收与已知限制

完整浏览入口：[`previews/README.md`](previews/README.md)。含四刀总览、四段觉醒、四段终结、形态切换、雷步和镜像重演、三阵形、五组图集接触表、四组质量对比。

资源检查已通过，见 `validation.json`；`export_awakened.cjs --check` 逐像素核对SVG重新导出结果与全部PNG。尝试 `./gradlew processResources --offline` 时，环境缺少Gradle 8.1.1发行包，Wrapper下载被网络阻止，未能执行Gradle任务；不把它标为构建通过。没有运行Minecraft，也没有宣称验证游戏帧率、多人遮挡、实际玩家模型UV或Java战斗系统；代码接入后由开发者/用户实机验收。原有 main 的 Java、构建脚本、模型、现有资源文件保持不变；新增PNG未被旧渲染器引用。

尚未完成的工作均为代码接入：真实事件/状态同步、模型残影与姿态缓存、刀身纹路UV适配、GPU批量渲染/预算、GUI位置、真名本地化文本、演出与实际命中的对应。音效未新增，复用原有声音由接入者安排。没有未完成的承诺PNG或SVG资源；无新刀身模型、人物贴图或音频。

## 9. 完整组件清单

下表由 `assets.json` 列出；路径为实际资源文件，格号以左上为(0,0)。同一路径的组件共享一张PNG，单独采样对应格。

| ID | 完整 ResourceLocation | 格(x,y) | SVG 源 | 用途 |
|---|---|---|---|---|
| `akatsuki/scar_0_appear` | `blade_tetra:textures/effect/awakened/akatsuki/atlas.png` | 0,0 | [scar_0_appear.svg](../../art/svg/awakened/akatsuki/scar_0_appear.svg) | 断命线 0° / appear |
| `akatsuki/scar_0_stable` | `blade_tetra:textures/effect/awakened/akatsuki/atlas.png` | 1,0 | [scar_0_stable.svg](../../art/svg/awakened/akatsuki/scar_0_stable.svg) | 断命线 0° / stable |
| `akatsuki/scar_0_break` | `blade_tetra:textures/effect/awakened/akatsuki/atlas.png` | 2,0 | [scar_0_break.svg](../../art/svg/awakened/akatsuki/scar_0_break.svg) | 断命线 0° / break |
| `akatsuki/scar_1_appear` | `blade_tetra:textures/effect/awakened/akatsuki/atlas.png` | 3,0 | [scar_1_appear.svg](../../art/svg/awakened/akatsuki/scar_1_appear.svg) | 断命线 30° / appear |
| `akatsuki/scar_1_stable` | `blade_tetra:textures/effect/awakened/akatsuki/atlas.png` | 4,0 | [scar_1_stable.svg](../../art/svg/awakened/akatsuki/scar_1_stable.svg) | 断命线 30° / stable |
| `akatsuki/scar_1_break` | `blade_tetra:textures/effect/awakened/akatsuki/atlas.png` | 5,0 | [scar_1_break.svg](../../art/svg/awakened/akatsuki/scar_1_break.svg) | 断命线 30° / break |
| `akatsuki/scar_2_appear` | `blade_tetra:textures/effect/awakened/akatsuki/atlas.png` | 6,0 | [scar_2_appear.svg](../../art/svg/awakened/akatsuki/scar_2_appear.svg) | 断命线 60° / appear |
| `akatsuki/scar_2_stable` | `blade_tetra:textures/effect/awakened/akatsuki/atlas.png` | 7,0 | [scar_2_stable.svg](../../art/svg/awakened/akatsuki/scar_2_stable.svg) | 断命线 60° / stable |
| `akatsuki/scar_2_break` | `blade_tetra:textures/effect/awakened/akatsuki/atlas.png` | 0,1 | [scar_2_break.svg](../../art/svg/awakened/akatsuki/scar_2_break.svg) | 断命线 60° / break |
| `akatsuki/scar_3_appear` | `blade_tetra:textures/effect/awakened/akatsuki/atlas.png` | 1,1 | [scar_3_appear.svg](../../art/svg/awakened/akatsuki/scar_3_appear.svg) | 断命线 90° / appear |
| `akatsuki/scar_3_stable` | `blade_tetra:textures/effect/awakened/akatsuki/atlas.png` | 2,1 | [scar_3_stable.svg](../../art/svg/awakened/akatsuki/scar_3_stable.svg) | 断命线 90° / stable |
| `akatsuki/scar_3_break` | `blade_tetra:textures/effect/awakened/akatsuki/atlas.png` | 3,1 | [scar_3_break.svg](../../art/svg/awakened/akatsuki/scar_3_break.svg) | 断命线 90° / break |
| `akatsuki/scar_4_appear` | `blade_tetra:textures/effect/awakened/akatsuki/atlas.png` | 4,1 | [scar_4_appear.svg](../../art/svg/awakened/akatsuki/scar_4_appear.svg) | 断命线 120° / appear |
| `akatsuki/scar_4_stable` | `blade_tetra:textures/effect/awakened/akatsuki/atlas.png` | 5,1 | [scar_4_stable.svg](../../art/svg/awakened/akatsuki/scar_4_stable.svg) | 断命线 120° / stable |
| `akatsuki/scar_4_break` | `blade_tetra:textures/effect/awakened/akatsuki/atlas.png` | 6,1 | [scar_4_break.svg](../../art/svg/awakened/akatsuki/scar_4_break.svg) | 断命线 120° / break |
| `akatsuki/scar_5_appear` | `blade_tetra:textures/effect/awakened/akatsuki/atlas.png` | 7,1 | [scar_5_appear.svg](../../art/svg/awakened/akatsuki/scar_5_appear.svg) | 断命线 150° / appear |
| `akatsuki/scar_5_stable` | `blade_tetra:textures/effect/awakened/akatsuki/atlas.png` | 0,2 | [scar_5_stable.svg](../../art/svg/awakened/akatsuki/scar_5_stable.svg) | 断命线 150° / stable |
| `akatsuki/scar_5_break` | `blade_tetra:textures/effect/awakened/akatsuki/atlas.png` | 1,2 | [scar_5_break.svg](../../art/svg/awakened/akatsuki/scar_5_break.svg) | 断命线 150° / break |
| `akatsuki/causal_node` | `blade_tetra:textures/effect/awakened/akatsuki/atlas.png` | 2,2 | [causal_node.svg](../../art/svg/awakened/akatsuki/causal_node.svg) | 因果交错的独立交点 |
| `akatsuki/crescent` | `blade_tetra:textures/effect/awakened/akatsuki/atlas.png` | 3,2 | [crescent.svg](../../art/svg/awakened/akatsuki/crescent.svg) | 觉醒背后残月 |
| `akatsuki/rift` | `blade_tetra:textures/effect/awakened/akatsuki/atlas.png` | 4,2 | [rift.svg](../../art/svg/awakened/akatsuki/rift.svg) | 命断黑红裂隙 |
| `akatsuki/execution_cross` | `blade_tetra:textures/effect/awakened/akatsuki/atlas.png` | 5,2 | [execution_cross.svg](../../art/svg/awakened/akatsuki/execution_cross.svg) | 命断十字终斩 |
| `akatsuki/blade_trace` | `blade_tetra:textures/effect/awakened/akatsuki/atlas.png` | 6,2 | [blade_trace.svg](../../art/svg/awakened/akatsuki/blade_trace.svg) | 刀身红纹的独立条带 |
| `kyouka/water_mirror` | `blade_tetra:textures/effect/awakened/kyouka/atlas.png` | 0,0 | [water_mirror.svg](../../art/svg/awakened/kyouka/water_mirror.svg) | 稳定水镜 |
| `kyouka/mirror_cracked` | `blade_tetra:textures/effect/awakened/kyouka/atlas.png` | 1,0 | [mirror_cracked.svg](../../art/svg/awakened/kyouka/mirror_cracked.svg) | 裂纹镜面 |
| `kyouka/mirror_ghost` | `blade_tetra:textures/effect/awakened/kyouka/atlas.png` | 2,0 | [mirror_ghost.svg](../../art/svg/awakened/kyouka/mirror_ghost.svg) | 远层水镜 |
| `kyouka/replay_cut` | `blade_tetra:textures/effect/awakened/kyouka/atlas.png` | 3,0 | [replay_cut.svg](../../art/svg/awakened/kyouka/replay_cut.svg) | 重演的双轨虚斩 |
| `kyouka/ripple` | `blade_tetra:textures/effect/awakened/kyouka/atlas.png` | 0,1 | [ripple.svg](../../art/svg/awakened/kyouka/ripple.svg) | 独立水波圈 |
| `kyouka/shard_0` | `blade_tetra:textures/effect/awakened/kyouka/atlas.png` | 1,1 | [shard_0.svg](../../art/svg/awakened/kyouka/shard_0.svg) | 镜面可复用碎片 |
| `kyouka/shard_1` | `blade_tetra:textures/effect/awakened/kyouka/atlas.png` | 2,1 | [shard_1.svg](../../art/svg/awakened/kyouka/shard_1.svg) | 镜面可复用碎片 |
| `kyouka/shard_2` | `blade_tetra:textures/effect/awakened/kyouka/atlas.png` | 3,1 | [shard_2.svg](../../art/svg/awakened/kyouka/shard_2.svg) | 镜面可复用碎片 |
| `kyouka/shard_3` | `blade_tetra:textures/effect/awakened/kyouka/atlas.png` | 0,2 | [shard_3.svg](../../art/svg/awakened/kyouka/shard_3.svg) | 镜面可复用碎片 |
| `kyouka/echo_wash` | `blade_tetra:textures/effect/awakened/kyouka/atlas.png` | 1,2 | [echo_wash.svg](../../art/svg/awakened/kyouka/echo_wash.svg) | 模型残影材质；不是人物图 |
| `kyouka/echo_edge` | `blade_tetra:textures/effect/awakened/kyouka/atlas.png` | 2,2 | [echo_edge.svg](../../art/svg/awakened/kyouka/echo_edge.svg) | 模型残影材质；不是人物图 |
| `kyouka/echo_gradient` | `blade_tetra:textures/effect/awakened/kyouka/atlas.png` | 3,2 | [echo_gradient.svg](../../art/svg/awakened/kyouka/echo_gradient.svg) | 模型残影材质；不是人物图 |
| `raikiri/echo_wash` | `blade_tetra:textures/effect/awakened/raikiri/atlas.png` | 0,0 | [echo_wash.svg](../../art/svg/awakened/raikiri/echo_wash.svg) | 模型残影材质；不是人物图 |
| `raikiri/echo_edge` | `blade_tetra:textures/effect/awakened/raikiri/atlas.png` | 1,0 | [echo_edge.svg](../../art/svg/awakened/raikiri/echo_edge.svg) | 模型残影材质；不是人物图 |
| `raikiri/echo_gradient` | `blade_tetra:textures/effect/awakened/raikiri/atlas.png` | 2,0 | [echo_gradient.svg](../../art/svg/awakened/raikiri/echo_gradient.svg) | 模型残影材质；不是人物图 |
| `senbonzakura/blade_small` | `blade_tetra:textures/effect/awakened/senbonzakura/atlas.png` | 0,0 | [blade_small.svg](../../art/svg/awakened/senbonzakura/blade_small.svg) | 散华独立碎刃 / blade_small |
| `senbonzakura/blade_medium` | `blade_tetra:textures/effect/awakened/senbonzakura/atlas.png` | 1,0 | [blade_medium.svg](../../art/svg/awakened/senbonzakura/blade_medium.svg) | 散华独立碎刃 / blade_medium |
| `senbonzakura/phantom_blade` | `blade_tetra:textures/effect/awakened/senbonzakura/atlas.png` | 2,0 | [phantom_blade.svg](../../art/svg/awakened/senbonzakura/phantom_blade.svg) | 散华独立碎刃 / phantom_blade |
| `senbonzakura/petal_blade` | `blade_tetra:textures/effect/awakened/senbonzakura/atlas.png` | 3,0 | [petal_blade.svg](../../art/svg/awakened/senbonzakura/petal_blade.svg) | 散华独立碎刃 / petal_blade |
| `senbonzakura/dissolve_chip` | `blade_tetra:textures/effect/awakened/senbonzakura/atlas.png` | 0,1 | [dissolve_chip.svg](../../art/svg/awakened/senbonzakura/dissolve_chip.svg) | 散华独立碎刃 / dissolve_chip |
| `senbonzakura/falling_petal` | `blade_tetra:textures/effect/awakened/senbonzakura/atlas.png` | 1,1 | [falling_petal.svg](../../art/svg/awakened/senbonzakura/falling_petal.svg) | 终景后飘落樱瓣 |
| `senbonzakura/array_core` | `blade_tetra:textures/effect/awakened/senbonzakura/atlas.png` | 2,1 | [array_core.svg](../../art/svg/awakened/senbonzakura/array_core.svg) | 五瓣阵心；外围由独立剑刃组成 |
| `raikiri/step_ribbon` | `blade_tetra:textures/effect/awakened/raikiri/atlas.png` | 3,0 | [step_ribbon.svg](../../art/svg/awakened/raikiri/step_ribbon.svg) | 雷步定向条带 |
| `raikiri/direction_arc` | `blade_tetra:textures/effect/awakened/raikiri/atlas.png` | 0,1 | [direction_arc.svg](../../art/svg/awakened/raikiri/direction_arc.svg) | 雷步定向条带 |
| `raikiri/dash_strobe` | `blade_tetra:textures/effect/awakened/raikiri/atlas.png` | 1,1 | [dash_strobe.svg](../../art/svg/awakened/raikiri/dash_strobe.svg) | 雷步定向条带 |
| `raikiri/landing_pulse` | `blade_tetra:textures/effect/awakened/raikiri/atlas.png` | 2,1 | [landing_pulse.svg](../../art/svg/awakened/raikiri/landing_pulse.svg) | 雷刃 / 神鸣终结层 |
| `raikiri/thunder_cut` | `blade_tetra:textures/effect/awakened/raikiri/atlas.png` | 3,1 | [thunder_cut.svg](../../art/svg/awakened/raikiri/thunder_cut.svg) | 雷刃 / 神鸣终结层 |
| `raikiri/piercing_light` | `blade_tetra:textures/effect/awakened/raikiri/atlas.png` | 0,2 | [piercing_light.svg](../../art/svg/awakened/raikiri/piercing_light.svg) | 雷刃 / 神鸣终结层 |
| `akatsuki/true_name_seal` | `blade_tetra:textures/effect/awakened/akatsuki/atlas.png` | 7,2 | [true_name_seal.svg](../../art/svg/awakened/akatsuki/true_name_seal.svg) | 专属真名符纹，不含文字 |
| `akatsuki/hud_corner` | `blade_tetra:textures/effect/awakened/akatsuki/atlas.png` | 0,3 | [hud_corner.svg](../../art/svg/awakened/akatsuki/hud_corner.svg) | HUD 真名角标 |
| `kyouka/true_name_seal` | `blade_tetra:textures/effect/awakened/kyouka/atlas.png` | 0,3 | [true_name_seal.svg](../../art/svg/awakened/kyouka/true_name_seal.svg) | 专属真名符纹，不含文字 |
| `kyouka/hud_corner` | `blade_tetra:textures/effect/awakened/kyouka/atlas.png` | 1,3 | [hud_corner.svg](../../art/svg/awakened/kyouka/hud_corner.svg) | HUD 真名角标 |
| `senbonzakura/true_name_seal` | `blade_tetra:textures/effect/awakened/senbonzakura/atlas.png` | 3,1 | [true_name_seal.svg](../../art/svg/awakened/senbonzakura/true_name_seal.svg) | 专属真名符纹，不含文字 |
| `senbonzakura/hud_corner` | `blade_tetra:textures/effect/awakened/senbonzakura/atlas.png` | 0,2 | [hud_corner.svg](../../art/svg/awakened/senbonzakura/hud_corner.svg) | HUD 真名角标 |
| `raikiri/true_name_seal` | `blade_tetra:textures/effect/awakened/raikiri/atlas.png` | 1,2 | [true_name_seal.svg](../../art/svg/awakened/raikiri/true_name_seal.svg) | 专属真名符纹，不含文字 |
| `raikiri/hud_corner` | `blade_tetra:textures/effect/awakened/raikiri/atlas.png` | 2,2 | [hud_corner.svg](../../art/svg/awakened/raikiri/hud_corner.svg) | HUD 真名角标 |
| `shared/success_burst` | `blade_tetra:textures/effect/awakened/shared/atlas.png` | 0,0 | [success_burst.svg](../../art/svg/awakened/shared/success_burst.svg) | 终结成功短光爆 |
| `shared/alpha_ramp` | `blade_tetra:textures/effect/awakened/shared/atlas.png` | 1,0 | [alpha_ramp.svg](../../art/svg/awakened/shared/alpha_ramp.svg) | 透明度渐变辅助纹理 |
| `shared/switch_slash` | `blade_tetra:textures/effect/awakened/shared/atlas.png` | 0,1 | [switch_slash.svg](../../art/svg/awakened/shared/switch_slash.svg) | 普通到解放刀光过渡遮罩 |

动画SVG完整路径：

- `blade_tetra:textures/effect/awakened/akatsuki/scar_lifecycle.png` ← `art/svg/awakened/akatsuki/scar_lifecycle/00.svg` 至 `15.svg`。
- `blade_tetra:textures/effect/awakened/kyouka/mirror_lifecycle.png` ← `art/svg/awakened/kyouka/mirror_lifecycle/00.svg` 至 `15.svg`。
- `blade_tetra:textures/effect/awakened/senbonzakura/blade_disperse.png` ← `art/svg/awakened/senbonzakura/blade_disperse/00.svg` 至 `15.svg`。
- `blade_tetra:textures/effect/awakened/raikiri/step_flicker.png` ← `art/svg/awakened/raikiri/step_flicker/00.svg` 至 `07.svg`。
- `blade_tetra:textures/effect/awakened/shared/release_fold.png` ← `art/svg/awakened/shared/release_fold/00.svg` 至 `11.svg`。
