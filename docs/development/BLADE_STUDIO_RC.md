# Blade Studio 2.0 · Release Candidate 验收

## 能力及依赖

| 输入 | 封存行为 | 独立接收端依赖 |
| --- | --- | --- |
| 通过原生注册与预检的双源融合 | 保留刀鞘 A → 刀镡 B 的原生 ID、事件和动作接续。如果原生 Super 选择器指向该来源不拥有的次元斩终式，沿用已有融合规则，调用该来源自己的 Normal 选择器。 | Forge 47 + SlashBlade 1.x（最低 1.9.63），以及封存时识别的 SA 提供者 |
| 常规 SlashBlade SE、SA | 保留通过预检的原生注册 ID。能力由原提供者执行。 | Forge 47、SlashBlade 和证书列出的提供者 |
| 咸鱼一文字 | 烘焙鱼骨、鱼身、鱼嘴和可审计刀镡。共享运行时沿用原有鱼拍、水花及气泡逻辑。 | Forge 47 + SlashBlade |
| 觉醒刀 | 玩家确认剥离后可封存普通版本。 | 取决于剩余能力 |
| 外部双源 SA | 已注册的常规 SA 通过共享调度并声明双方的提供者。Time Beyond 需要独立的蓄力流程。融合刀带有 Treasure Barrage 特殊效果时需要延迟回放上下文。预检会报告原因。尚未实测的第三方 SA 需要在接收端确认。 | 由 A/B 的注册命名空间识别 |
| SJAP、YakumoBlade、The Last Smith: Resharpened、Recasting 等外部模型 | 根据实际模型和资源来源检查。外部资源引用随导出声明依赖，嵌入资源需要再分发许可。 | 取决于实际资源与能力来源 |
| 锻造剑谱与源 Mod 专属组合 | 预检拦截尚无独立实现的剑谱条目、专属效果和组合。 | 待移植 |
| 新旧封存 JAR | 从 1.8.0 开始，共享运行时允许匹配的 1.x 更新。历史 JAR 若精确锁定旧版，混装前需用源武器重新封存。 | 保持旧 Mod ID，换成重新封存的 JAR |

## 依赖版本规则

封存证书保留来源 Mod 的实际版本，Forge 依赖声明使用兼容区间。SlashBlade 的区间为 `[1.9.63,2)`。其他可识别的数字版本以安装版本为最低要求，以其下一个主版本为上限。0.x 使用下一个次版本作为上限。无法判断发行系列的版本只记录最低版本。Minecraft 与 Forge 仍遵循当前运行时平台的版本要求。

接收端矩阵让 1.9.63 生成的实际 JAR 在 1.9.65 加载，也测试相反方向。区间内的其他版本仍要由对应运行环境验证。

## 中文更新日志

- 御影战斗、挑战和专属美术退役。兼容层保留旧注册、庭院区块及恢复玩家位置所需的数据。
- 工作室默认进入单页普通模式，支持共享草稿、原刀预览和高级编辑。
- 封存支持已审查的双源融合和咸鱼模型。独立样本由 Forge 实际生成。
- CI 分别使用 SlashBlade 1.9.63 与 1.9.65 生成并测试附属。旧存档夹具覆盖两种服务器的保存与重启。

## English changelog

- Retired the Mikage encounter and its art. Legacy IDs and player recovery preserve existing worlds and courtyards.
- Studio opens in a single-page Simple mode with live source preview and a draft shared with Advanced mode.
- Sealing supports audited native two-source combinations and baked Salty Fish meshes. Forge generates the receiver fixtures.
- CI generates and loads independent add-ons on SlashBlade 1.9.63 and 1.9.65. Legacy fixtures verify save and reopen on both server types.

## 作者实机验收

1. 备份并复制一份真实旧世界。检查庭院、遗留物品、原处于挑战中的角色、保存后重进。
2. 打开封存台，放入普通毕业刀。改名后封存。打开高级模式编辑双语名称、Lore、Logo 与背板，返回普通模式确认草稿仍在。
3. 对比原刀与烘焙结果。拖动、缩放，检查出鞘、收刀、损坏、发光和库存图标。
4. 在干净实例中装入实际封存 JAR，检查创造栏和 `/bladeseal give`。对照横驱 → 纵驱的事件、效果和单次耗费，再检查咸鱼的模型与命中反馈。
5. 尝试封存觉醒刀。确认剥离提示可以取消，封存失败或成功后原武器仍在。
6. 安装多个同代封存附属，分别保存和重进。将截图、日志和所用完整提交 SHA 一起提交。

`python3 tools/ci/run_suite.py legacy_world_migration` 和 `legacy_world_migration_client` 各启动 Forge 两次。夹具使用可审计的历史 NBT 记录，并在第二次进服前保留实际保存的区块和玩家文件。测试包、样本与截图以 PR 当前 HEAD 的 Actions artifacts 为准。真实历史整世界和生产环境下的性能数据仍需另行采样。

## 资源许可与通用模型封存

工作台会装配当前生效的模型与贴图，并记录资源 ID、资源包、安装版本与字节哈希。封存流程按技术可用性判断外观，不因未知命名空间拒绝。使用外部或无法判断权利的素材时，客户端先烘焙，再确认，确认后才写 JAR。取消保留原刀和草稿。用户确认只表示已阅读须知。作品仍适用素材原许可。

仅使用已验证的 MIT 输入时，产物携带原始 MIT 声明。混合素材产物额外携带来源记录和使用说明。同名资源包覆盖仍会触发保守提示。用户选取的图像目前也采用保守提示。

实机验收时请覆盖第三方与普通部件混装、咸鱼刀身和鱼鞘组合、原刀及接收端画面、许可提示取消和确认、觉醒剥离确认，以及一次性授权拒绝原因。自定义刀模偏移和现有运行时不能忠实重放的能力继续由预检保护。旗帜图案可使用普通模块刀鞘的共享贴图合成，其他刀鞘外观需要独立验证。

## Asset licensing and export review

Studio bakes the active model, atlas and supported fittings. The certificate records the resource ID, resource pack, installed version where available, and hash. Unknown namespaces are eligible when their geometry can be assembled. External artwork triggers one confirmation after baking and before writing. Cancelling keeps the draft and the deposited blade.

The generated archive includes applicable MIT notices and separately identifies external artwork. Active pack overrides and unverified local images are handled conservatively. The dialog does not grant rights to any bundled source assets. Check the original terms before public distribution.
