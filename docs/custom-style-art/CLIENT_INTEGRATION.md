# 千门剑谱客户端美术整合交付

> 此页是美术整合时的交付记录，所述基线、网络协议与验证结果属于当时版本；相关 PR 现已合入 main。后续录制流程与分享 GUI 见[功能完善记录](../CUSTOM_STYLE_FINALIZATION.md)，现行分享格式见[社区协议](../CUSTOM_STYLE_COMMUNITY_PROTOCOL.md)。下文 BTSTYLE1 等旧格式引用仅用于说明原整合范围。

## 开发基线与资源来源

- 功能基线：PR #30，`ebd3e72ff26e2e03a8ddc57986ba5a5298f979fb`。
- 美术来源：PR #31，`27ae38011e5570700eecf1e6b1dfe6ba6e6efdd7`。
- 独立分支：`codex/qianmen-art-integration`，目标分支 `codex/custom-style-schematics`。
- #31 原始资源提交经过范围审查后 cherry-pick；没有复制第二套源图目录或覆盖旧 main 的功能代码。原始 PNG、SVG 与资源摘要保持原样。后续合入 #31 时相同资源应自然合流。

## 已接通

- `style_recorder` 根模型转接 `item/custom_style/shiroku_fuda`，注册 ID 不变，物品栏和手持共用正式模型；不注册额外物品。
- 编纂屏幕使用 editor_panel、两态动作行、三段水平切片四态按钮、undo/clear/finish 图标。名称继续使用原生 EditBox。
- 保留 `/blade_tetra_style edit`，命名、选择、上移、下移、删除、撤销末招、清空、完成。清空有原生确认框。
- 等待现有服务器快照期间禁用重复提交；100 tick 未收到回执时仅允许重试，绝不宣称成功。完成仍随当前输入名称一起发送原有 FINISH 请求。
- 高度充足时显示六招。320×240 逻辑画布显示四行，鼠标滚轮查看其余；焦点在列表时方向键选择并滚动。动作名称按字体像素宽度省略，Tooltip 保留完整名称和真实 ID。没有动作名称接口，默认显示真实动作 ID；资源包可提供 `action.blade_tetra.<namespace>.<path>` 翻译，不影响提交。
- 式录札在手中时左上角显示静态使用提示，来自真实使用键设置。**这不表示正在采录或已有可录动作。**
- HUD 只适配 `ClientChatReceivedEvent.System` 中现有、精确匹配的结构化服务器翻译消息，不解析聊天字符串、不监听挥刀猜测成功。采录回执携带的数量只短暂显示，不当作持续同步的草稿状态。
- ADDED 启动 ink_write（6帧，每帧1 tick）；物品／命令成谱消息和编辑器 `finished=true` 启动 seal_set（6帧，每帧2 tick）。只播放一次，不排队，不因重绘重启；反馈60 tick 后消失。屏幕效果强度为0时仅保留静态成功图标。打开界面或隐藏HUD时动画仍会正常到期，之后不补播。
- Tetra `GlyphDeserializer.textureLocation` 支持自定义纹理，Mutil GuiTexture 默认 UV 尺寸为256×256。`workbench_glyphs.png` 直接复制四张16×16正式图标到该尺寸图集，不重绘、不缩放。三个自创刀身 schematic 使用短／标准／长图标，module extract 使用自创流派图标。仅 glyph 字段变化，原生工作台布局、配方、材料、模块属性和解锁条件全部保留。
- 中英文片段增量合并，保留核心使用的 screen/message/tooltip 键以及分享码提示，不覆盖整份语言文件。空列表继续说明真实式录札操作，不使用概念图中的虚构 R 键。

## 保留原生外观／待核心显示接口

| 项目 | 本次处理及原因 |
| --- | --- |
| 式录札 active 模型、state_recordable | 正式资源保留；缺少最近有效动作、可录性、采录开关的客户端同步，不猜测 predicate。 |
| 原生卷轴的专属物品栏 PNG 与建议配色 | 保留 #30 的 Tetra ScrollItem 原生外观及展开/收回。配色与 glyph 序列目前写在 ScrollData；遵守本任务不修改剑谱 NBT 的限制，不改构造器或全局模型。blank/compiled 图标作为备用资源随原包保留。导入和自录均走同一原生创建路径。 |
| 工作台 state_locked/recognized/unavailable | 保留原生可用性、锁定和原因提示；缺少独立工作台只读状态，不把“持有卷轴”解释为“工作台已识别”。正式状态图可供后续小接口复用。 |
| forge_glint / state_forged | 无可靠的客户端锻造成功事件。资源保留但不播放，不以打开工作台或查看结果作为成功。 |
| 最新动作本地化名、持续HUD数量 | 当前消息只有结果与数量，编辑器快照只有动作ID；未新增网络框架。状态和数量按已收到的消息显示，不假造最近动作名。 |
| 详细编辑失败原因 | 原快照没有操作ID或错误枚举。FINISH 返回未完成时显示检查提示，原聊天失败提示保留；迟到回执沿用现有快照语义，不宣称具备请求关联能力。 |

没有修改 ComboState、攻击判定、伤害、距离、Tetra 加工规则、服务器存储验证、BTSTYLE1 协议、网络包或四种预设流派。

## 轻量验证

在 Windows / Java 17 下执行：

```
python docs/custom-style-art/export_assets.py --check
python -X utf8 docs/custom-style-art/validate_assets.py --integration-base ebd3e72ff26e2e03a8ddc57986ba5a5298f979fb
python docs/custom-style-art/export_workbench_glyphs.py --check
python -X utf8 docs/custom-style-art/check_integration.py
./gradlew.bat compileJava processResources jar --offline
git diff --check
```

美术校验覆盖32张PNG、54个原始产物SHA-256、RGBA像素、透明度、尺寸、模型引用、18帧、双语键。导出检查兼容 Windows CRLF 与不同平台 zlib 压缩差异，仍检查准确像素及现有文件摘要，不重新编码原图。整合范围检查将三个 schematic / module 的 glyph 排除后逐字段比较基线，保证其余加工字段相同。

静态布局检查覆盖普通320×240及更高逻辑画布：可见行、滚动范围、工具栏、数量和底部按钮均不相交。长文本使用Font裁切与Tooltip；中文输入法及最终字体显示留给用户实测。低于Minecraft普通最小逻辑尺寸的窗口不作为本次验收目标。

Java编译与资源处理通过；现有弃用警告未扩展修复。没有启动Forge实机、大型回归、截图自动化或录像。最终视觉、交互手感和Tetra具体图标尺寸由用户验收。

## 用户实机验收清单

- [ ] 安装本整合分支构建，核对式录札物品栏和双手持握无紫黑贴图。
- [ ] 挥刀后使用式录札，确认真实成功才出现墨迹、计数；无有效动作、重复采录、已满时没有成功动画。
- [ ] 普通挥刀、反复刷新界面、打开工作台均不触发成功动画；HUD不遮挡战斗中心和快捷栏。
- [ ] `/blade_tetra_style edit`：中文/英文长名称输入正常；命名和直接完成均使用当前文字。
- [ ] 录满六招：滚轮/键盘能查看并选择第六招，底部按钮无重叠；长动作名悬停显示完整内容。
- [ ] 依次尝试上移、下移、删除；核对服务器返回顺序、选择跟随及完成卷轴的Tooltip顺序。撤销只移除末招。
- [ ] 清空取消不修改草稿，确认才清空；空草稿禁用完成；等待回执时不能重复提交。
- [ ] 通过式录札潜行右键进入编纂界面，在界面中完成后获得真实 Tetra 卷轴，印章只播一次；原生展开/收回、工作台识别和三档自创刀身加工仍可用。
- [ ] 工作台保留原生布局，三档长度图标与自创模块图标清晰，原有四流派未变化。
- [ ] 导出分享码再导入：卷轴外观与自录一致，分享码及错误提示可读。
- [ ] 任选常用GUI Scale和中英文查看纸色、字宽、按钮状态、焦点框与HUD位置；将审美或位置调整反馈给本PR。

## 后续功能开发

本文件记录 PR #32 的纯美术接入边界。后续流程修复、刀身节奏和导入／导出 GUI 见
[千门剑谱功能交付说明](../CUSTOM_STYLE_FINALIZATION.md)，不再受本文件的纯客户端修改范围限制。


Protocol update: the unreleased JSON share code has been replaced by the prefix-free compact format. The historical integration scope above describes the original art-only work. See [current exchange contract](../CUSTOM_STYLE_COMMUNITY_PROTOCOL.md).
