# 美术资源索引

完整机器清单见 `export/asset-manifest.json`；校验摘要见 `export/sha256.json`。所有 SVG 为可编辑源。

| 资源名 | 尺寸 | 资源路径（assets/blade_tetra/ 下） |
|---|---|---|
| `shiroku_fuda` | 32×32 | `textures/item/custom_style/shiroku_fuda.png` |
| `shiroku_fuda_active` | 32×32 | `textures/item/custom_style/shiroku_fuda_active.png` |
| `blank_schematic` | 32×32 | `textures/item/custom_style/blank_schematic.png` |
| `compiled_schematic` | 32×32 | `textures/item/custom_style/compiled_schematic.png` |
| `recording_hud` | 160×36 | `textures/gui/custom_style/recording_hud.png` |
| `editor_panel` | 248×220 | `textures/gui/custom_style/editor_panel.png` |
| `action_row` | 224×22 | `textures/gui/custom_style/action_row.png` |
| `action_row_selected` | 224×22 | `textures/gui/custom_style/action_row_selected.png` |
| `button_normal` | 24×20 | `textures/gui/custom_style/button_normal.png` |
| `button_hover` | 24×20 | `textures/gui/custom_style/button_hover.png` |
| `button_disabled` | 24×20 | `textures/gui/custom_style/button_disabled.png` |
| `button_pressed` | 24×20 | `textures/gui/custom_style/button_pressed.png` |
| `length_short` | 16×16 | `textures/gui/custom_style/length_short.png` |
| `length_standard` | 16×16 | `textures/gui/custom_style/length_standard.png` |
| `length_long` | 16×16 | `textures/gui/custom_style/length_long.png` |
| `style_custom` | 16×16 | `textures/gui/custom_style/style_custom.png` |
| `state_waiting` | 12×12 | `textures/gui/custom_style/state_waiting.png` |
| `state_recordable` | 12×12 | `textures/gui/custom_style/state_recordable.png` |
| `state_added` | 12×12 | `textures/gui/custom_style/state_added.png` |
| `state_full` | 12×12 | `textures/gui/custom_style/state_full.png` |
| `state_unavailable` | 12×12 | `textures/gui/custom_style/state_unavailable.png` |
| `state_compiled` | 12×12 | `textures/gui/custom_style/state_compiled.png` |
| `state_locked` | 12×12 | `textures/gui/custom_style/state_locked.png` |
| `state_recognized` | 12×12 | `textures/gui/custom_style/state_recognized.png` |
| `state_forged` | 12×12 | `textures/gui/custom_style/state_forged.png` |
| `undo` | 12×12 | `textures/gui/custom_style/undo.png` |
| `clear` | 12×12 | `textures/gui/custom_style/clear.png` |
| `finish` | 12×12 | `textures/gui/custom_style/finish.png` |
| `arrow` | 12×12 | `textures/gui/custom_style/arrow.png` |
| `ink_write` | 16×96 | `textures/gui/custom_style/ink_write.png` |
| `seal_set` | 16×96 | `textures/gui/custom_style/seal_set.png` |
| `forge_glint` | 16×96 | `textures/gui/custom_style/forge_glint.png` |

## 附加交付

- 4 个 generated item model JSON，只在新增 `models/item/custom_style/` 下，不替换原模型。
- 3 组 sheet，各6帧，共18张原始帧：`export/frames/`。
- 中英文六张界面概念图及资源与动画两张总览，PNG + 可编辑SVG：`mockups/`。
- 34 对（实际以文件为准）中英文文本：`export/ui-strings.json`，待核心合并。
- 原生 Tetra 图纸表现提案：`export/native-scroll-presentation.json`。
- 动态名称/数量/作者不在贴图中；所有条目映射、UV和播放时序见接入契约。

## 原创与外部资源

本任务新增几何、图标、纹理均为原创像素 SVG；只参考仓库风格，没有复制第三方贴图。
Tetra 原生 glyph 与卷轴渲染仅在原生 API 中复用，不打包其纹理。概念图渲染字体由使用者本地提供，不随仓库分发。
