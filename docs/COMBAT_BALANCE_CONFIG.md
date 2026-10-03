# 战斗平衡配置 / Combat balance

所有新倍率默认 **1.0**，保持现有伤害。不强制安装配置菜单模组。

## 配置位置

- 单人：`saves/<存档>/serverconfig/blade-tetra-server.toml`
- 专用服务器：`<世界目录>/serverconfig/blade-tetra-server.toml`，世界目录通常为 `world`。
- 整合包的新存档模板：`defaultconfigs/blade-tetra-server.toml`。不会覆盖已有存档。

这是 SERVER 配置，由服务器决定实际伤害；客户端的视觉配置不能改变它。

```toml
[combatBalance]
globalDamageMultiplier = 1.0
standardDamageMultiplier = 1.0
iaidoDamageMultiplier = 1.0
dangakuDamageMultiplier = 1.0
rengekiDamageMultiplier = 1.0
slashArtDamageMultiplier = 1.0
summonedSwordDamageMultiplier = 1.0
```

依次为总伤害、本传、居合、断岳、连舞、SA、非 SA 幻影剑倍率。范围为 **0～10**：0 不造成受支持的伤害，1 保持原样，0.75 降低 25%。不改变材料属性、动作、机动性、资源消耗或冷却。

## 结算规则

伤害请求 × 总倍率 × 流派倍率 × 攻击类型倍率。普通攻击的类型倍率为 1；SA 的幻影剑只使用 SA 倍率，不再乘普通幻影剑倍率。例如总倍率 0.75、居合倍率 0.8，居合普通攻击得到原伤害的 0.6 倍。多段攻击逐次结算一次。

这不是自动把输出调整到普通武器的 125%：实际输出还受材料、招式、护甲与其他模组影响。

仅处理玩家的 Blade Tetra 模块刀，不全局修改其他拔刀剑、御影、宠物或自然雷击。原生斩波、次元斩和幻影剑生成时保存原流派与攻击类型，换刀不会改变其归属，但命中时读取最新配置。普通攻击会清除 SA 状态；自动延续的 SA 招式保留分类。旧的未标记攻击实体不会按玩家当前武器补算。

镜花/水月、赤月脉冲、千本樱与雷切中继承已结算伤害预算的效果不会再次叠乘倍率。独立主动伤害正常缩放；残心延迟伤害保存原流派。主动导电雷击单独缩放，自然雷击不受影响。护甲和其他伤害上限仍参与结算，最终生命损失未必严格线性。

未知第三方自定义攻击实体、直接修改生命值、灵魂状态和处决机制不在通用伤害倍率的保证范围内。

## 修改与配置菜单

成功重新载入后影响后续命中，不追溯已经结算的伤害预算。配置项不要求重启；Forge 的文件监视器负责读取外部 TOML 修改，不必使用 `/reload`。

可选使用 [Configured 2.2.0（Forge 1.20.1）](https://www.curseforge.com/minecraft/mc-mods/configured/files/5096828)。配置采用标准 Forge SERVER 规格，提供中英文名称、说明和数值校验。开发验证包含 Configured 后端识别，并非所有图形界面的逐项人工操作测试。

多人修改服务器配置需要相应权限和服务端支持；普通客户端不能自行改变服务器伤害。直接编辑服务器文件后，客户端菜单显示可能需要重新打开或重新连接。

## English

All seven multipliers default to **1.0** and accept **0–10**. The world-owned SERVER file is `<world>/serverconfig/blade-tetra-server.toml`; place a template in `defaultconfigs` for new worlds only.

Supported damage is multiplied by global × originating style × attack kind. Ordinary attacks have a kind multiplier of 1. SA projectiles use the SA multiplier instead of the non-SA summoned-sword multiplier. Native projectiles retain their origin across weapon switches while reading current values on impact. Effects based on an already-scaled damage budget are not scaled again.

Only player-owned Blade Tetra modular-blade attacks are covered. Other blades, bosses, pets, natural lightning, direct health changes and unknown third-party attack entities are excluded. Materials, animation, mobility, costs and cooldowns stay unchanged. This is not automatic DPS normalization.

Changes affect subsequent hits after Forge reloads the file; no restart flags are required. Configured is optional. Editing multiplayer SERVER configs requires server support and permission. English and Chinese labels/tooltips are provided.

## 开发验证

`./gradlew runServer -PcombatBalanceSmoke -PconfiguredTest` 在 `build/combat-balance-smoke` 的独立世界验证伤害分类、继承预算、Configured 配置识别和实际文件重载，不修改常规测试存档。测试配置会恢复到 1.0。

已验证：328 项回归测试通过；Tetra 6.3 基础环境和 Tetra 6.9 + MMT 环境均通过运行检查，包含普通幻影剑/SA 互斥、换刀后的延迟命中、继承伤害、零倍率、非玩家攻击隔离、实际伤害事件与文件自动重载。Configured 2.2.0 后端识别七项配置、翻译键、范围校验及无需重启标记。

MMT 测试环境存在其可选联动配方缺少外部物品的报错（例如未安装 Iron's Spellbooks 时的 `irons_spellbooks:arcane_essence`）；不影响本次战斗配置检查通过，不代表这些外部配方已修复。
