# 觉醒魂铭残片

四个独立物品：`akatsuki_soul_fragment`、`senbonzakura_soul_fragment`、
`raikiri_soul_fragment`、`kyouka_soul_fragment`。只用于悬停阅读，不消耗、不开界面。

普通悬停只显示礼记式短文与 Shift 提示；按住 Shift 显示完整解读，包括操作、
技能、代价与限制、融合／自创流派、适用流派和补制材料，不再分用 Ctrl／Alt。
按键名称读取当前绑定，包括拔刀剑本体两种按键与魂铭切换键。
长段落按实际字体换行，中英文均提供完整说明；数值明确按默认规则介绍。
完整说明超过屏幕高度时，保持 Shift 并滚动鼠标滚轮阅读，右侧显示进度。
切换物品、界面或阅读模式会重置滚动位置。仅在正在悬停的完整指南可滚动时接管滚轮。

标题沿用游戏字体以支持中文，额外加入加粗斜体、暗色描边、轻微逐字起伏和掠光；
正文排版保持静止。操作前缀浅金、技能名刀魂色、收益青绿、代价暖红；数值和限制文字不跳动。
礼记关键词与技能名称使用小范围动态：赤月呼吸明暗、千本樱掠光、雷切短促电光、镜花淡色偏移重影。
滚动后的 300ms 暂停正文动态，段落末补充一行礼记口吻的余韵；完整技能、代价与数值仍直接说明。
客户端配置 `visuals.animateSoulFragmentTitles` 与 `visuals.animateSoulFragmentText` 分别关闭标题和正文关键词动画，
关闭后保留静态配色。文字样式在本地化后按语义分段，布局缓存静态字宽与分段，仅绘制可见行。
暗底边框和标题点缀按四刀分别配色，未增加全局字体替换、着色器或粒子。

内容以当前实际规则为准：赤月血刃、归血、血丝、旧终月与契约剑灵；千本樱破碎、
常态花剑、散华协奏、无限连舞、燃尽凝华樱爆；雷切迟雷、雷针、聚针雷爆、雷铠和雨中传导；
镜花残影诱敌、破镜、SSS 三镜追忆、水镜衣、凝水斩与氧气卸力。
旧版已停用的融合共鸣不作为现行技能介绍。

## 发放与补领

- 资格直接读取现有四个觉醒成就，不要求重新觉醒或手持旧刀。
- 获得成就时尝试发放；登录时补查老存档；每 100 tick 重试尚未成功领取的物品。
- 每刀的领取回执独立存于 `PlayerPersisted/BladeTetraAwakenedGuides`，死亡克隆时保留。
- 单件不可堆叠，只有背包有空位且实际入包成功才写回执；满背包不掉落、不提前登记已领。
- 丢弃或丢失不会反复赠送，遗失者可用无序合成补制：纸＋红石／粉红色花簇／紫水晶碎片／海晶碎片。
- 撤销后重新授予成就也不会清除领取回执；教程物品不参与技能解锁或战斗状态。

## 资源与验证

当前采用用户选定的 B2「刀魂余响」：原 B 赤月、雷切保留，千本樱与镜花水月统一为
细长光核、横向开放碎片。四件分别表现血色裂隙、樱瓣碎刃、雷电残响和破镜水影。
配色参考本模组血晶、樱魂晶，形态参考
[灾厄 Lore 物品](https://github.com/CalamityTeam/CalamityModPublic/tree/1.4.4/Items/LoreItems)，资源为自行绘制。

`art/soul-fragments` 内的 32px SVG 像素母版为正式美术来源，不依赖候选预览脚本。
`python tools/art/export_soul_fragments.py` 将其导出为四张 32×32 透明 PNG；
`--check` 逐像素核对母版与游戏贴图，无额外运行依赖。预览位于 `art-previews/soul-fragments/fragments.png`。
母版使用整数像素矩形，保留选定版本的全部像素；后续导出不会恢复旧版金属遗物。
Tooltip 的装饰分层参考 [Legendary Tooltips](https://github.com/AHilyard/LegendaryTooltips/blob/1.20/src/main/java/com/anthonyhilyard/legendarytooltips/LegendaryTooltips.java)
与 [Obscure Tooltips](https://github.com/ObscuriaLithium/obscure-tooltips)。

接入已有 `runServer -PsakuraCombatSmoke` 的 `AwakenedGuideSmoke`，使用真实 ServerPlayer 的
成就与背包流程，仅网络连接用空实现：验证即时赠送、四刀补发、满背包延后、部分空位重试、
重登持久化与死亡复制不重领。Forge 会拒绝向 FakePlayer 授予成就，因此不使用 FakePlayer 作为
成就验证主体。测试结果看日志 `AWAKENED_GUIDE_SMOKE_PASS`。

`runClient -PsoulGuideReview` 使用隔离运行目录 `build/soul-guide-review`，等待加载遮罩消失后，
通过真实 Forge Tooltip 收集、组件工厂、字体排版与滚轮处理依次展示四刀普通／Shift 顶部／
Shift 技能段／Shift 底部，共 16 张小窗口截图；验证末尾可达后输出 `SOUL_GUIDE_REVIEW_PASS` 并退出。
不进入世界、不接触玩家存档。截图在该目录的 `evidence` 下；未验证与第三方 Tooltip 模组同时安装的显示效果。
