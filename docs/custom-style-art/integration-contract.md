# 与核心开发者的接入契约（提案，非现有 Java API）

本文件不添加类、网络包、持久化字段、采录判定或 Tetra schematic。字段名均是交接建议。
资源提供显示能力，不能成为服务端操作成功、兼容性或解锁状态的依据。

## 数据模型与交互需求

| 展示视图 | 必要字段 | 展示职责 |
|---|---|---|
| RecordingView | recordingActive, lastActionId, lastActionName: Component, lastActionRecordable, recordedActions[], maxActions, validationMessage: Component? | 驱动 HUD、计数与输入可用性 |
| ActionDisplay | id, name: Component | 只显示名称和顺序，不推导攻击流程 |
| SchematicView | styleName, author, actions[], schematicValid, compatibilityMessage: Component? | 详情、Tooltip、名称输入初值 |
| WorkbenchView | schematicAvailable, selectedStyleName, selectedLength, availableLengths[], validationMessage: Component? | 自创分支详情与三档规格选择 |
| UI 操作能力（补充请求） | canUndo, canClear, canCompile, pendingOperation | 核心提供使能状态，不用动作数量单独推断能否完成 |
| UI 事件（补充请求） | eventId, eventType, success, message: Component? | 用一次性操作回执启动动画，重绘不重复触发 |

请求动作：`requestRecordLastAction / requestUndo / requestClear / requestCompile(name) /
requestSelectLength(length)`，全部交由核心实际存在的命令通道处理。这些方法名也仅为契约符号。
管理屏幕没有“播放招式”“调整伤害”“改变 ComboState”“拖动排序”等暗含业务的控件。
每次采录键只发一次请求；UI不缓存连续点击后自动录入若干动作，也不自行选择上一个有效动作。

`lastActionId` 可为空；名称用于显示，不能回传为权威动作标识。编纂成功的图纸与数据由核心产生。
绑定键位名通过原生 KeyMapping 读取，不硬编码 R。
`eventId` 只在当前会话去重；不要求美术新增网络协议。核心可将已有结果消息适配为此事件。
等待回执时禁用相关提交；失败时保持服务端返回的序列/名称，显示原因，不自行扣除资源或生成物品。

## 状态与资源映射

路径前缀：`blade_tetra:textures/gui/custom_style/`。以下文件名省略 `.png`。

| 核心事实/事件 | 资源 | 文字键后缀 / 行为 |
|---|---|---|
| 采录关闭 | 无 HUD，物品 `shiroku_fuda` | 隐藏 HUD |
| 开启、尚无动作 | `recording_hud` + `state_waiting` | waiting |
| 有可录动作 | `state_recordable`，物品 `shiroku_fuda_active` | recordable(action) |
| 采录成功回执 | `state_added`（HUD）/ `ink_write`（纸色列表） | added(action)，更新核心快照 |
| 容量已满 | `state_full` | full，容量来自核心 |
| 动作不合法/不可录 | `state_unavailable` | unavailable 或核心具体原因 |
| 编纂成功回执 | `state_compiled` + `seal_set` | compiled，核心交付真正图纸 |
| 未识别/未展开 | `state_locked` | locked，不能客户端解锁 |
| 剑谱损坏/不兼容 | `state_unavailable` | invalid / compatibility(message) |
| 工作台识别 | `state_recognized` | recognized，无持续动画 |
| 加工成功回执 | `state_forged` + `forge_glint` | forged，结果装备由核心更新 |
| 刀身规格 | `length_short / length_standard / length_long` | short / standard / long |
| 选项入口 | `style_custom` | custom |

展示优先级：具体失败回执的短提示 > 当前核心校验错误 > 容量满 > 可录动作 > 等待动作。
若核心有单一规范状态枚举，直接映射它优于客户端重建优先级。

## 动画契约

所有 sheet 为 **16×96，6 个竖排 16×16 帧**，UV `(0, frame*16,16,16)`，完整纹理尺寸 16×96。
`export/frames/` 提供每一原始帧。每组只播放一次，无 `.mcmeta`；必须由事件选择帧，不能作为自动循环 atlas sprite 使用。

| Sheet | 触发 | 每帧时长 | 总时长 | 局部位置 |
|---|---|---|---|---|
| ink_write | 成功采录回执 | 1 tick | 6 tick / 300ms | 纸色动作行左侧；HUD 仅用明亮 state_added 短提示 |
| seal_set | 成功编纂回执 | 2 tick | 12 tick / 600ms | 完成按钮/成谱预览中心 |
| forge_glint | 成功加工回执 | 2 tick | 12 tick / 600ms | 原生结果预览边缘 |

按客户端 elapsed ticks 选帧，暂停后不集中补播；重开屏幕、资源重载不重放历史成功事件。
HUD 快速成功事件只重启最后一次局部动画，不建立无界队列。容量满/失败只切静态图标并保留提示20 tick。
末帧结束清除叠层；不隐含修改物品状态。减少动态效果时跳过播放并展示静态结果。

## 物品模型与真正 Tetra 图纸

1. `models/item/custom_style/shiroku_fuda.json` 为基础资源模型，active 为视觉变体。
   核心最终注册 ID 可不同，通过自己的根模型/属性 predicate/客户端模型选择指向它们。
   本包**不注册物品、不添加猜测 predicate**。
2. `blank_schematic.json`、`compiled_schematic.json` 是可复用的独立图标模型，供空白剑谱或详情预览使用。
   **不得覆盖 `tetra:scroll` 的全局模型**，也不把新 Item 当作已具备解锁功能的原生图纸。
3. 完成编纂应由核心创建原生 `ScrollItem` 并写入其动态 `ScrollData`。已确认本仓库
   `ForgingScrolls` 采用 `material / ribbon / glyphs / schematics` 这一表现方式；可复用的美术建议在
   `export/native-scroll-presentation.json`。这里只给配色和现有 glyph 索引，不生成 schematics ID。
4. 原生卷轴的世界展开渲染继续复用 Tetra，自定义 PNG 不会自动出现在原生 ScrollItem 上。
   若需要保留原生图纸身份又在物品栏使用自定义成谱图标，需核心提供仅对该图纸生效的模型选择桥接；
   在此之前，以原生卷轴外观为接入默认方案。不得改全局卷轴纹理或渲染器。
5. 卷轴图案不绑定短/标准/长。实际名称、作者、动作数和动作表由核心 Tooltip 动态提供。
   原生图纸说明前缀建议“自创剑谱 / Custom sword manual”，避免出现 SA 或技能卷轴描述。

## 需要协调并由核心实现

- 确认物品最终注册 ID、变体模型选择方式，以及空白物品是否实际存在。
- 确认自创图纸的 ScrollData key / schema unlock IDs；本包不替核心分配或写入。
- 绑定 GUI Components、按钮请求与已有操作结果，提供可用性字段与一次性事件。
- 确认 Tetra 版本的 glyph 控件能否接独立纹理。固定图集模式需适配现有客户端控件，不能仅靠 JSON 宣称支持。
- 在原生刀身加工详情/规格选择中接入图标；根据实际控件宽度采用横排或竖排。
- 合并翻译片段并执行真实 GUI Scale、长文本、多人状态同步及资源重载验收。

资源本身可先审查/合入，功能可见性与实机正确性不应在美术 PR 中宣称完成。
