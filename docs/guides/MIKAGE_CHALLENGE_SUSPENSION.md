# 御影挑战退役与旧存档迁移

御影战斗、回忆战、界门、访客与神域演出已经退役。旧存档的三个维度继续注册以保留区块。存放在区块里的庭院建筑会留在原地。

| 兼容数据 | 处理方式 |
| --- | --- |
| `mikage`、`mikage_echo`、`mikage_phantom_sword`、`mikage_divine_companion`、`mikage_gate_core`、`mikage_gate_sword` | 六个 `blade_tetra` 实体 ID 使用同一个占位类型，服务端在加入世界前清除 |
| 寄存在区块里的旧原生攻击和试炼怪物 | 只清理带御影攻击标记的旧实体，或神域维度内带试炼标记的旧实体 |
| 镜界、神域、彩蛋房 | 保留 `mirror_realm`、`divine_domain`、`easter_realm` 的维度和维度类型 |
| 玩家仍在挑战区 | 优先返回旧入口坐标，否则返回主世界出生点。成功返回后清理已退役的活动会话字段 |
| `boundary_gate_charm`、`broken_oni_mask`、`sword_ghost_remnant`、`karmic_puppet`、`dead_thought_soul_seal` | 保留原物品 ID 和物品 NBT。门符、面具、神域镜面失去挑战交互 |
| 已有界断锻造和死念印记 | 保留刀上已有能力及印记。在旧存档里已解锁界断锻造的玩家仍可使用持有的剑鬼残魂完成锻造 |

## Verification

`Native / legacy_world_migration` exercises legacy-format entity, item and player NBT against the live Forge registries in all three retirement dimensions on a dedicated server. It checks the saved return position, an existing courtyard block, unrelated player data, registered item round trips and repeat migration. It uses equivalent SNBT fixtures. A historical world directory upgraded and reopened on an integrated server remains a separate acceptance case.

The production JAR audit rejects old challenge classes, art, models, templates and Mixins. Akatsuki's execution tint, the native slash source tracking used by Habaki, and the shared weapon backplate render type live outside the retired challenge.
