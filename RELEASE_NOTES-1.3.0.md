# 拔刀剑：百构流派 1.3.0

适用于 Minecraft 1.20.1、Forge 47.x、Tetra 6.3.0–6.x 与拔刀剑：重锋 1.9.63–1.x。

## 材料职责重构

- Blade Tetra 不再扫描 `forge:ingots/*` 或为其他模组的锭生成材料数值。第三方材料的强度、耐久、完整度、加工要求、修复来源与特殊效果统一交由 Tetra 材料提供方管理。
- More Mod Tetra（MMT）现在是推荐的可选材料提供方。安装后，其金属、宝石、木材、骨材与其他 Tetra 材料会直接用于模块化拔刀剑，无需逐项兼容。
- MMT 与 Tetra 必须使用彼此兼容的版本组合；本版已验证 MMT 2.2.941＋Tetra 6.9 与 MMT 2.4.17＋Tetra 6.17。
- Blade Tetra 专注于拔刀剑的特殊视觉适配：读取 Tetra 材料颜色与表面分类，生成刀身、刀柄、刀镡、刀鞘、刀光和纯视觉粒子，不复制或覆盖材料数值。
- 旧版提供的 35 种第三方金属定义保留为隐藏兼容层。已有武器继续保持原材料键、属性与修复能力，新工坊不再提供这些重复材料。
- 移除 `blade_tetra-common.toml` 中的自动材料开关和黑名单；马铃薯仍是本模组原创材料，薯条兼定的外观、炸脆与回潮机制保持不变。

## Material provider boundary

- Blade Tetra no longer creates Tetra materials from generic Forge ingot tags.
- Tetra and providers such as More Mod Tetra now exclusively own third-party material stats, processing, repairs, and effects.
- Blade Tetra consumes those materials only for modular SlashBlade construction and SlashBlade-specific visual generation.
- Previous curated metals remain hidden compatibility aliases for existing weapons; potato remains a native Blade Tetra material.
