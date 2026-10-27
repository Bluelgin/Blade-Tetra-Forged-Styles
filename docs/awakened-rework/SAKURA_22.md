# 千本樱2.2：原生剑技驱动凝散

基线：#44 `c6d8dfe89cdd32798b7a6f59a0409781e2102d70`；独立分支，以#44功能分支为Draft PR目标。#43的仪式目录、时间轴、解锁成功入口及仪式包未改。

## 实际动作、输入与攻击

调查本项目依赖匹配的SlashBlade Resharped 1.9.65源码（上游提交`6e2a0a0`），而非按名称猜测：

| 动作 | 注册ID | 原生输入 | 上游动作/攻击 |
| --- | --- | --- | --- |
| 下劈起手 | `slashblade:aerial_cleave` | 空中，潜行+后退+右键 | VMD1800–1812，click维持Y速度0.1；tick0起偶数tick陨落范围攻击0.44，tick1进入下落循环；循环Y加速下落，落地仍执行原生斩击/粒子/着地节点 |
| 上挑起手 | `slashblade:upperslash` | 地面，潜行+后退+右键 | VMD1600–1659，frame7即tick4发出0.50挑飞斩击，保留15tick原生僵直、跟跳hold回调 |

来源：[ComboStateRegistry](https://github.com/0999312/SlashBlade_Resharped/blob/master/src/main/java/mods/flammpfeil/slashblade/registry/ComboStateRegistry.java)、[原生动作提交](https://github.com/0999312/SlashBlade_Resharped/blob/master/src/main/java/mods/flammpfeil/slashblade/capability/slashblade/ISlashBladeState.java)、本项目ModComboStates.selectOpener/StyleCommonCommands，以及上游player_motion.vmd映射。只调用既有MIT依赖，没有复制其模型、动画或攻击实现。

`aerial_cleave_loop`、landing/end、`upperslash_jump/end`都不单独切换形态。B1–B6、其他魂/流派、未觉醒/折断的刀均不触发新入口。遵循既有双魂主魂顺序。

## 权威与幂等

`SakuraStanceRules`仅识别动作、目标形态及提交戳。`SakuraStanceController`只观察已觉醒主魂千本樱、RENGEKI主手的BladeMotionEvent，不接管输入，不注册剑技，不改伤害或回调。

上游BladeMotionEvent实际发生于updateComboSeq写入前，而且可取消/改写。因此先保留候选，PlayerTick.END读取真正的comboSeq/lastActionTime、物品对象、槽位、维度、存活状态再确认。检查的是最终提交ID，兼容其他监听器的晚期改写；下劈自动转入tick1循环也可匹配起手。虚拟NextCombo查询没有提交事件，取消/过时/换刀动作不通过验证。

确认后仅写入原有`blade_tetra_awakened_v2_sakura_dispersed`布尔值：下劈只置true，上挑只置false。同形态幂等，不会反复toggle或重播。无新增持久NBT、切换冷却、Rank资源或伤害实体。候选逐玩家O(1)处理，不扫背包；死亡/重生/退出/跨维度清理。

AwakenedBladeCombat的成功SA处理仅对千本樱直接返回；移除releaseSenbonzakura及stanceUntil。其他刀魂的成功SA处理保留。旧环刃/扇刃/刃雨不恢复；觉醒**条件**中原本需要成功SA的部分不变。

## 真实刀身与原池过渡

SakuraStanceTransition是客户端时钟：下劈16tick（tick2开始tip→hilt分解，tick10钢刃分解完毕，碎刃逐批回阵）；上挑14tick（从原位置收束，tick2–12 hilt→tip重建）。不是服务端战斗锁，也不更改原生动作时长。

* SenbonzakuraDispersedRender读取临时bladeFraction，复用既有AwakeningBladeGeometry的16段不可变真实钢刃裁剪；原顶点/UV/法线和Tetra动态材质保留，刀鞘不参与。
* LegacyModelPartRenderer仍用既有真实继承镡柄的选择/校准/挂载；部分钢刃可渐变，镡柄嵌套pass有作用域保护，不被重复裁剪。未知模型/UV保留原外观作为安全回退。
* SakuraBladeAnchor读取原生模型钢刃根/尖端和实际绘制矩阵，第一人称按手部与世界投影校正到世界空间；端点范围不可信或暂未捕获时用有界手部提示。不生成武器模型，端点缓存LRU128。
* 同一SakuraShardPool增加TRANSITION状态及可见度。下劈从原钢刃位置逐批沿斩势回半月阵；已经可见的归阵碎片可响应后续B连舞，不等待整个动画结束。
* 上挑先保存**所有原Shard对象的当前位置、速度、可见度和短轨迹**，包括正在LAUNCH/SLASH/RETURN的刀片，取消其旧组任务，然后沿连续贝塞尔/短螺旋汇入实际刀柄与恢复中的钢刃。没有另一批装饰粒子或替换碎刃。
* NBT已变为凝刃时，Controller仍保留过渡池至动画结束；完毕后回收。快速反向切换从当前fraction和实际碎片位置继续；换刀、死亡、跨世界、资源重载、瞬移超距安全中断。GUI人物预览/图标/掉落以及觉醒仪式的浮空/镜影pass不参与战斗裁剪。

预算沿用#44：最多8玩家，24/48/72碎刃，最多6攻击组，1024/3072/6144 quads每帧、64格/视锥裁剪、同一樱色图集合批。无逐片实体、无逐tick位置包、无第二套渲染器或额外美术。

## B1–B6与Rank

保留#44完整B6七个回调、原0.85倍率、一次原生投射物反射以及燃烧速率。仅在完成tick0–6后，允许已有原生root路由选出的UPPERSLASH/AERIAL_CLEAVE优先退出B6；这不要求Rank，解决零Rank仍需上挑凝刃的问题。早按仍触发self-next guard，不能裁掉B6尾部攻击。普通B连招及其他流派不变，不引入B7循环。

下劈/上挑形态控制器不调用progressCombo、不清空combo、不扣Rank。当前原生动作合法衔接窗口照常生效；B1–B5被合法原生命令衔接的行为保持上游规则。散华仍不能回补Rank；凝刃后停止燃烧并可重新积累；耗尽不自动凝刃。

## 网络与旧存档

复用AwakenedBladeVfxPacket/SakuraCue：新增类型/阶段7=DISPERSE、8=CONDENSE，包含已有身份、槽位、维度、序号和动作时间。协议20→21，两端需同时更新。服务端视觉授权先于设备同步；客户端可跨NBT更新保留临时过渡。拒绝重复/过时/错维度/错误身份的事件；旁观者仍以同步的MAINHAND与UUID识别，不读取未知的远端选中槽。没有新C2S入口。

旧存档的布尔形态直接延续。重连为稳定凝刃/散华，不重放过渡；成长、主魂规则和首次觉醒仪式不迁移、不重做。

## 验证与待实机项

JUnit：动作ID白名单、幂等、取消/过时/覆盖验证、时间轴分段及反向连续性、真实Shard索引、下劈归阵、上挑召回空闲/正在攻击碎刃、过渡期后续攻击、协议往返/阶段类型校验，并保留#44所有测试。

Forge：扩展既有-PsakuraCombatSmoke，在原生progressCombo/输入能力路径验证下劈空挥切换、实际范围伤害/下落/着地回调，上挑空挥/零Rank凝刃及0.50原生斩击，重复/其他招式、Shift+SA失效、虚拟/被取消提交、换刀/生命周期事件清理、未觉醒与其他流派排除。同时执行原#44三轮B1–B6、完整B6尾帧、A/S/SS/SSS燃烧测试及其余四项Forge回归。最终运行结果及评审JAR链接补入PR。

**本轮尚未完成真实客户端画面验收或录制新MP4。** 当前工作区缺少先前完整Gradle/Minecraft客户端缓存、javac启动器及Xvfb显示服务；Java17构建/专服验证交由CI，未用离线动画代替游戏录像。

仍需实机：第一/第三人称（含不同FOV）、其他玩家旁观及网络延迟，Tetra各材质/特殊镡柄与发光，移动/跳跃/蹲下/游泳/±180°，上挑召回正在出击碎刃与快速反向切换，连舞尾帧/零Rank/直接近战命中，死亡/重连/跨维度/资源重载、低画质/多人帧时，以及#43的离手、刀鞘留身、真名、分解和归刃整段回归。JUnit及专服smoke不代表这些画面已通过。
