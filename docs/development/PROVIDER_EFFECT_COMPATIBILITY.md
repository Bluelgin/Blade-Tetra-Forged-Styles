# Tetra 附属效果兼容检查（2026-10-04）

## 范围与证据

只读检查玩家整合包中的 Tetra 6.3.0、Tetranomicon 1.5.3、VVAddon alpha3.0.1、
以及 GoetyRevelation 内嵌的 RevelationFix 4.4。另检查开发环境的标准 MMT
2.2.941（CurseForge 文件 7867317）。不把不同版本或特供版视为同一个实现。

玩家导出的刀上存在：

```text
slashblade/kashira = slashblade/socket_kashira
slashblade/socket_kashira_material = sword_socket/socket_gr_ominous_orb
```

RevelationFix 的原始材料定义提供 `goety_revelation.vizir: 25`，以及
`**generic.attack_damage: 0.066`。数据安装成功与事件触发兼容是两件事。

## 已确认的问题与本次处理

| 来源 | 已确认的限制 | 本次处理 |
| --- | --- | --- |
| RevelationFix：维吉尔、别西卜、诅咒利刃、寒芒、蛛毒侵蚀、玄妙之刃、弑神、末日、泡影、暗夜主宰、融身入影 | 效果方法只接受 `ModularItem`；我们的刀继承 `ItemSlashBlade`、实现 `IModularItem` | 为审查过的 11 个效果类增加模块刀接入，继续执行提供者自己的逻辑 |
| VVAddon：命中、击杀效果 | 相同的具体类判断，实际只需要接口的效果查询 | 接入这两个事件方法，保留原来的命中条件、经验处理和效果逻辑 |
| VVAddon：周期增益、右键技能 | 还向 `Homology`、`Honor`、`Evil` 传递具体 `ModularItem`，涉及耐久操作 | **未修复**；整个方法保持原样，不做一半的类型替换，后续需要单独的辅助方法适配 |
| 标准 MMT 2.2.941 的通用效果入口 | `MMTEffectHelper` 使用 `IModularItem` 查询效果；伤害入口通过自身 `EffectLevelEvent` 分发 | 不修改其通用计算或重复分发事件；不是所有 MMT 版本均已验证 |
| 标准 MMT 2.2.941 的 Iron's Spells 联动：冻结、法力虹吸 | 这两个独立事件方法仍只接受 `ModularItem` | 同样接入接口效果查询；仍由提供者判断法术模组、概率和法力条件 |
| 原生 Tetra 效果 | 效果读取可使用模块接口；投掷、弓和专属武器功能仍有自身条件 | 保留现有原生近战分发，不把每一类功能强行附加到斩波或幻影剑 |

## 其他边界

- `sword/socket` 的材料通过原生数据挂到柄头，不复制提供者的数值。
- 材料自带的 `attributes` 会被直接继承，即使部件没有伤害 `extract`；
  本整合包的下界合金被数据包加入 1.5 点攻击属性，并非材料读取失败。
- 裹柄、涂层配方以已支持的剑部件槽适配。某个附属若在自己的运行时代码里硬编码
  `sword/blade`、`sword/hilt` 或特定武器类，不能仅凭材料/配方显示成功就宣称其技能兼容。
- 对提供者混入 `ModularSwordItem` 的专属右键技能，没有统一迁移：拔刀剑已有自己的右键流程。
- 本次不修改概率、伤害、冷却、召唤物寿命，也不重新广播命中事件；不额外增加触发次数。

## 架构与安全

`ProviderEffectCompatMixin` 只列出审查过的可选类。Mixin 插件把这几个方法中
具体类的效果查询接到 Tetra 接口，保持原来的事件处理方法和数值不变。
资格判断只新增本 mod 的模块刀；原来的 Tetra 武器继续符合条件，其他接口实现者不自动放行。

适配器同时更新类型判断、转换、接口调用和栈帧。遇到未知具体类方法、字段、参数传递，
整个方法跳过并报告日志，防止未来版本出现半适配或错误强制转换。
不加载或打包可选附属类；没有安装附属时不添加任何新效果。

## 验证

- 全部 JUnit 测试，以及真实 RevelationFix/VVAddon 类文件的审查与改写回读。
- 独立 Forge 服务端：没有启示录时正常启动；不使用玩家存档。
- 独立 Forge 服务端搭配 GoetyRevelation：原生材料读取为 25%；
  直接调用经过接入的原始维吉尔事件方法，实际产生召唤实体；原有特殊伤害标签
  能阻止再次触发；普通钻石剑不产生召唤。
- 11 个启示录效果类的加载检查不等于 11 个效果逐个战斗测试，不能混为一谈。
- VVAddon 的命中/击杀方法已完成真实类文件测试，尚未完成该附属的游戏内战斗验证。
- MMT 的两个法术联动方法已完成真实类文件测试，尚未完成搭配法术模组的战斗验证。

开发诊断开关：`-PproviderEffectSmoke`，启示录组合另加 `-PlargeCompatTest`。
类文件审查可使用 `-PproviderEffectAuditJars=<RevelationFix.jar>;<VVAddon.jar>`。
这些开关及诊断不会在普通游戏启动时运行。

玩家正在运行的整合包没有被写入；原有测试包未被替换。
