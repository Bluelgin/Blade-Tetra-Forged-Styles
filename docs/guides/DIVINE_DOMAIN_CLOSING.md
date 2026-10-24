# 神域序章收尾 / Divine Domain prologue

本次替代旧共斗援助，保留祭坛、业镜杀业快照、五档挑战、波次、增援、最终敌人的血量阶段/地面预警、奖励和撤离。百鬼及以上沿用原有支援门槛；残响、怨聚不额外增加援助。

## 神火救援

- 每位参与者每场一次：存活、非创造/旁观、生命值不高于最大值 30% 时触发。
- 随玩家移动，持续 120 tick（6 秒）；每秒回复一次，共六次。总治疗为最大生命的 60%，上限 12 点（6 颗心）。不能复活，也不赋予全伤害免疫。
- 半径 2.5 格、高差 3.5 格内只推开本场仪式敌人；火焰持续时拦截它们的直接近战。远程、环境和地面预警仍可伤害玩家。
- 无御影 NPC 常驻、自动代打、伤害倍率、三剑法阵或最终束缚。守业不再依赖已移除的祓印窗口，避免永久高额减伤。
- 按 Session 保存次数；波次不重置。成功/失败/离场依照原挑战清理。新网络协议 12，客户端与服务器需同版。
- 首次通关赠送双语《神域之外》，提示未来独立模组承载后续故事；不锁重复挑战，不承诺模组名称或发布日期。

## 可选对话立绘资源接口

核心默认不显示立绘，文字框不保留空白立绘占位；御影 Boss 实体模型不受影响。
未来故事模组可在自己的 jar 中提供如下资源（允许跨命名空间纹理）：

`assets/blade_tetra/dialogue/mikage_portraits.json`

```json
{
  "width": 512,
  "height": 560,
  "expressions": {
    "neutral": "your_story_mod:textures/gui/mikage_neutral.png",
    "soft": "your_story_mod:textures/gui/mikage_soft.png",
    "distant": "your_story_mod:textures/gui/mikage_distant.png",
    "serious": "your_story_mod:textures/gui/mikage_serious.png"
  }
}
```

`your_story_mod` 只是示例，不是预定 mod ID。安装含该资源的模组后自动启用；不需核心硬依赖或反射。资源包同样可使用此接口。每次打开/初始化对话重新读取，资源重载后重新打开即可更新。未知表情回落 neutral；缺失/无效描述或纹理安全回落无立绘。宽高必须与整张 PNG 一致，范围 1–4096。
原四张立绘资源仅保留在本地源目录作备用；Gradle 资源集排除 `assets/blade_tetra/textures/gui/mikage_dialogue/**`，不进入测试运行资源或发布 jar。核心保留读取外部资源描述的接口，不再打包或自动启用这些 PNG；御影实体模型与其纹理不受影响。

## 美术

神火本体复用御影现有立体火舌；状态图标采用白芯、朱红火、金环的 SVG，并导出 PNG供游戏读取（Minecraft 不原生加载 SVG）。
SVG 在 `art/svg/divine_domain/`，预览在 `art/divine-fire-rescue-preview.png`。

业镜另有无铭、残响、怨聚、百鬼、修罗、无间六套 SVG，以镜心/裂纹/魂纹区分。沿用原 `CustomModelData` 1–5，不改变杀业快照或旧存档物品 ID。无生残印采用碎裂印石，刀下亡魂残念采用飘散魂火，不再与业镜共用原版碎片图标。物品纹理导出 32×32 硬边像素 PNG（无渐变、无抗锯齿）；整套预览为 `art/divine-domain-items-preview.png`，可用 `tools/render_divine_domain_items.cjs` 重新导出。

## 实机验收

1. 百鬼/修罗/无间：没有常驻御影；血量 30% 阈值触发，六次治疗、6 秒后消失，再残血不触发。
2. 多人：各自一次，救援不串场；旁观者/宠物不被击退，箭矢与地面预警仍有威胁。
3. 致命伤直接战败返回；断线返回/通关清理没有残留神火。
4. 中英切换、首次通关书、背包满时掉落、重复通关不重复赠书。
5. 无描述文件时只有对话；用资源包测试立绘启用、缺少表情/纹理及损坏 JSON 的回落。
