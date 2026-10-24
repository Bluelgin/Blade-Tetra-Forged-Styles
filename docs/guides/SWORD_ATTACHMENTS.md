# 剑改装复用：镶嵌、裹柄与涂层

## 玩家侧

- **镶嵌：柄头位。** 制作“镶嵌式柄头”会替换现有柄头，不额外保留旧柄头的数值。不增加第九个槽位，也不占用灵魂铭刻。原生剑的镶嵌材料、完整度成本、属性、效果与加工要求直接复用。
- **裹柄：刀柄位。** 三种刀柄共用原生剑的裹柄改良，柄芯材质不变；选择皮革、纤维或织物后，柄卷纹理/主色跟随材料。已有柄卷染色继续作为颜色覆盖，不参与数值计算。
- **涂层：刀身位。** 接入当前已加载、适用于 `sword/blade` 的涂层改良。基础 Tetra 没有通用涂层，因此未安装相应提供者或未满足提供者加载条件时不会凭空出现配方。沿用原配方的材料、工具、经验、卷轴解锁与打磨条件，只为拔刀剑增加同一时间一种涂层的限制。
- 涂层显示为轻微色泽，颜色取提供者配方原料的游戏贴图；无法获得可靠颜色时保持金属原色。觉醒刀纹/颜色仍在涂层之后绘制，优先保留原特色。
- **MMT 材料涂层也在刀身位。** 将普通剑 `sword/fuller` 的 `material_fuller` 配方适配为 MMT 原生的共享刀身改良 `improvements_material_fuller/`，保留原刀身。材料列表、材料加工系数和改良数据由 MMT 提供；不要求冰火传说或 `more_tetra_tools`。这是 MMT 的刀身改良形式，不额外叠加剑槽模块本体的耐久倍率。更换材料按 MMT 的原生改良分组替换，不能同时堆叠多种材料涂层或与龙血涂层叠加。
- 通用涂层以改良键包含 `coating` 为约定，另明确适配 MMT 的 `improvements_material_fuller/`；未知命名、替换刀身的配方或非数据驱动改装不盲目接入。

## 数值与效果的所有权

这里没有新的伤害/攻速/耐久或触发概率表。镶嵌从当前 `tetra:sword/socket` 模块取得已经展开的材料变体；裹柄和涂层继续使用 Tetra 的 `ImprovementData`，属性聚合走现有 `IModularItem`。

成功近战命中的 `hurtEnemy` 路径委托原生 `ItemEffectHandler.applyHitEffects`，例如原生流血、束缚、眩晕；没有同时在 `HitEvent` 再调用一次。右键近战沿用 SlashBlade 自己的近战/命中流程，不加攻速冷却或独立伤害处理。远程斩波、幻影剑、SA 不被当成近战再次补算这些命中效果。

**兼容边界：材料/数值兼容不等于所有行为兼容。** 某些附属的事件只识别 `ItemModularHandheld` 或自家武器类，而本物品继承 `ItemSlashBlade`。这些特殊行为仍需提供者支持 `IModularItem` 或后续单独桥接，不能宣称全部自动生效。

## 架构

- `item/BladeComponentLayout`：现有物理槽位与界面位置，保持存档槽位不变。
- `compat/attachments/SwordAttachmentSchematics`：只映射槽位与原生镶嵌产物；原配方不被替换为自定义平衡表。
- `compat/attachments/NativeSocketKashira`：原生镶嵌模块的柄头安装形式。
- `compat/attachments/MaterialFullerCoating`：MMT 剑槽材料涂层到共享刀身改良的独立适配，不硬编码材料数值。
- `compat/attachments/BladeCoatingPolicy`：拔刀剑的一种涂层限制。
- `compat/attachments/NativeSwordHitEffects`：原生命中效果委托。
- `visual/BladeAttachmentAppearance`：读取现有模块/改良身份，不额外持久化材质 NBT。
- `client/MaterialTextureCompositor`：从原大类抽出的部件绘制流程。
- `client/AttachmentFinishPainter`：裹柄、镶嵌和涂层的外观，不读取附属实现类或判断附属是否安装。
- `MaterialTextureManager`：继续负责渲染入口、缓存与图集生命周期。

仅在 `SchematicStore.processData` 结束时进行一次数据映射，发生在原生配方展开与注册之前。服务端重载和客户端数据包解析共用此路径，保留原始材料结果类型，交由 Tetra 展开。原始 JSON 同步流程不变。

## 验证

`-PattachmentSmoke` 在独立 `build/attachment-smoke-*` 世界运行自动诊断，正常服务器不会执行。诊断验证原生镶嵌、皮革裹柄、源改良数据身份和重复映射。涂层测试夹具只放在测试世界的数据包中，不在模组资源内发布。

已验证：Tetra 6.3.0、Tetra 6.9.0 + MMT、Tetra 6.17.0（Mutil 6.3.0）的实际服务端加载、镶嵌与裹柄。6.17.0 的独立测试数据包额外验证两种涂层配方、禁止叠加、原改良数据身份，以及带改装的原生右键近战命中。MMT 测试环境没有加载冰火传说，因此没有宣称实测其龙血涂层特殊行为。完整自动测试为 319 项，全部通过。

新版本 Tetra 要求新版 Mutil，测试时通过 `-Pmutil_version` 选择相应依赖，正式开发默认依赖版本没有改变。

MMT 材料涂层补充验证：Tetra 6.9.0 + MMT 2.2.941，未安装冰火传说和 `more_tetra_tools`，实际安装材料涂层、保留刀身、使用原生共享改良数据，以及更换材料不叠加均通过。自动测试更新为 320 项。该验证不代表所有 MMT 自定义事件效果已桥接。
