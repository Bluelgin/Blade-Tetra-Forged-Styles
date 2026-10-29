# 镜花水月 4.0：水镜衣、三镜追忆与水镜凝斩

本文件取代 KYOUKA_30.md 的玩法说明。PR #49 仍以 #46 为 base，不合并或发布其他 PR。3.0 的历史调查与测试记录不能作为 4.0 的验证证据。

## 原生事实与观察

本次实际下载 CurseMaven file 8018222 的 SlashBlade Resharped 1.9.63 JAR，使用 Java17 javap 核查 AttackManager、EntitySlashEffect、IConcentrationRank。Forge 来源为 `forge-1.20.1-47.4.0-sources.jar`，核查 LivingBreatheEvent、ForgeHooks.onLivingBreathe、LivingEntity/Player 补丁和 LivingDamageEvent。

沿用 NativeBladeCommitBridge 的提交观察：最终 setLastActionTime 后、原生 clickAction 前记录动作身份。候选路由和取消事件不提交。

NativeBladeActionObserver 现在包装 doManagedAttack 的原生 Consumer，try/finally 还原嵌套观察上下文，始终只执行一次原回调。LivingAttackEvent 只在此实际调用域内捕捉原生伤害源及原始基准；回调正常完成且目标实际失血，才提交快照。保护取消、无敌抵消、挥空、单纯查询和完整吸收不产生记录。观察器不再调用 calculateTotalDamage，因此不会额外重发原生暴击计算事件。

一个已提交动作只接受第一个实际成功目标/窗口，2 tick 最小授权间隔。此保守约束让多段和范围剑技共享一份额外预算，不按每个目标或每把剑气倍增。独立特殊 SA/未知攻击源不猜测补伤。原生直伤使用当前已提交动作；间接剑气使用生成时的动作身份。不得宣称完整复刻所有第三方特殊攻击。

## 常态追忆

每玩家只保留 LastMirrorSlash：动作和武器身份、目标 UUID/ID、实际时间、固定空间锚点、朝向、原生基准、已有平衡分类。新记录覆盖旧记录，60 tick 过期，不采样移动、旋转或骨骼历史。

原生 ConcentrationRank.addRankPoint(user, amount) 前后比较真实 grade，只有 `<SSS → SSS` 记录晋升事实。tick END 等待同 tick 的实际命中完成，再选择最近快照。装备/登录本身不会触发；直接修改 raw rank 的第三方实现不保证触发。

10 tick 后结算一次合计75%伤害，三把视觉镜刃共享此数值。600 tick 冷却保存在玩家 Forge PlayerPersisted 数据中，以主世界 gameTime 计时，换刀、换维度、重登不刷新。水镜衣期间忽略晋升；激活水镜衣清除尚未完成的追忆及历史。结束形态不补发追忆。

三面银蓝水膜与无鞘 Tetra 镜刃短暂显现，共享既有 NativeBladeMotionSampler 和 FloatingBladeRenderer；只有刀的原生 VMD 矩阵，没有完整玩家录像。20 tick 后客户端自行过期，停止包和短期 tombstone 防延迟旧包复活。观察者开始追踪时补发仍有效的短演出。

## 水镜衣与氧气

合法原生 Spiral Swords 批次在实际加入世界后激活注册 MobEffect `blade_tetra:water_mirror_armor`。无需锁定，原生环剑寿命、伤害、飞行和耀魂开销不改。重复释放不刷新任何防御预算。

统一 armored() 校验 Buff、真实主手 ItemStack、武器 UUID、觉醒主魂、存活、维度、功能开关与原版 airSupply。死亡、换刀、离线、维度/世界卸载清理预约任务。登录清除无合法会话的崩溃遗留无限 Buff。

Forge LivingEntity 在呼吸事件前调用 decreaseAirSupply；事件消费量已包括 Respiration 随机结果。HIGHEST 呼吸监听仅在形态有效且没有 WATER_BREATHING/CONDUIT_POWER/原生无敌能力时把 canBreathe 改为 false，不直接每 tick 写空气。保留原生 consumeAirAmount，其他 Mod 可在后续事件优先级修改呼吸或在自身逻辑恢复 airSupply。海龟壳通过实际 WATER_BREATHING 状态联动。

普通300空气、无呼吸增强约维持15秒。空气即将达到0时先移除形态，不改变本次原始呼吸条件；陆地恢复原版补气，水下仍走原版潜水/溺水。结束不补满、不改最大容量。无限氧气可以无限续航；不硬编码饰品或扫描背包。第三方若在我们之前改 canBreathe，随后被形态的陆地耗气覆盖，则需其在更晚优先级参与；非标准氧气系统不承诺完全兼容。

## 水斩和防御

水镜衣每个有效已提交动作最多预约一份14 tick、40%基准的凝斩；每玩家最多8份。固定区域水滴汇聚、压缩，结算成功产生蓝白水爆/环纹。结算前重新检查 UUID、维度、加载、存活、敌我、原始区域3格容差、32格主人距离、视线和 AttackEntityEvent。敌人离开可落空，不移动目标，不执行任何 ComboState 或 areaAttack。

伤害复用 authoredDamage、InheritedCombatDamage、SoulLegacyDamageGuard 及既有镜花伤害来源；原生Rank的DamageSource入口在组合解析前拒绝继承/二次伤害，避免追加伤害改变原生动作。已有平衡倍率只应用一次。

防御在 LivingDamageEvent LOWEST 提议扣减：护甲、药水、吸收已经处理，取消事件不参与。公式 `0.30 × clamp(air/maxAir)`；60 tick 间隔，200 tick 滚动总减免≤6 HP，成功时另付20空气。虚空等 BYPASSES_INVULNERABILITY/BYPASSES_EFFECTS 来源不保护，不加无敌帧，不取消击退或僵直。

Forge 没有公开的“玩家健康写入后”事件，因此增加仅观察 Player.actuallyHurt 返回的窄 Mixin：重查最终事件未取消且确实失血后才提交预算/氧气，避免更晚取消、吸收和保护机制被收费。没有重写伤害算法。短期账本独立于 Buff/武器保留，重启形态不重置；服务器进程重启会清空此200 tick短期账本。

## 客户端与网络

水膜直接绘制已经摆好姿态的原生玩家模型，不新建玩家模型或实体。第三人称半透明覆膜、第一人称原生手臂覆膜随原版空气比例变薄；共享 water_film 材质用于镜面。凝水、冲击、卸力扩散复用 KyoukaVfxClient 的有界瞬时队列。没有反射、场景捕捉、独立VFX引擎或逐tick同步。

协议升级为 `kyouka4-water-1`，要求两端版本一致。MobEffect 和原版空气使用游戏自身同步；短演出只发必要事件。新水系事件含维度、时间和随机事件身份，用于跨维度、重复和延迟包过滤。三镜包附固定区域锚点，不追着已逃离敌人的位置移动。客户端世界切换/资源重载清理或重建资源。

## 文件职责

- NativeBladeActionObserver / NativeBladeAttackObserverMixin：原生实际命中观察。
- NativeBladeCommitBridge：继续提供已提交动作身份。
- KyoukaMirrorSessions：单条历史、SSS边界后的调度、武器会话与Buff生命周期。
- KyoukaMirrorDamageService：一份额外伤害及目标/保护复核。
- WaterMirrorEffect：真实MobEffect注册。
- WaterMirrorCombat / WaterMirrorDefenseBudget：原版呼吸接入、最终伤害防御和短期账本。
- AwakenedRankConstraintMixin：已取消正收益锁，现仅观察真实晋升并阻止二次伤害递归Rank。
- WaterMirrorDamageCommitMixin：公开事件之后的实际健康写入确认。
- WaterMirrorLayer / KyoukaMirrorRenderer / KyoukaVfxClient：身体水膜、短暂三镜、瞬时水流。

旧 KyoukaMirrorSession、AwakenedRankConstraintService、常驻三镜、持续Rank支付、逐动作费用和80tick三镜心跳已删除。旧配置 cost 键仅保留为明确弃用项，不再读取。共享双魂、赤月、原生输入和120tick觉醒仪式未删除。

## 验证与限制

见 KYOUKA_40_QA.md。构建成功不等于世界内视觉验收；独立其他PR没有合入，不能宣称其功能已在此分支验证。没有新建PR、合并、发布或改动其他PR。
