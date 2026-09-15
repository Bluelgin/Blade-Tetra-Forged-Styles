# 血樱终景「无生」 / Dead Thought

本轮使用 Blockbench MCP 制作并导出。可编辑工程按资源拆分，运行时使用同名 OBJ 和一张 256×256 黑红色板贴图；不打包 Blockbench 工程进模组 JAR。

| 工程 | 用途 | 三角形数 |
| --- | --- | ---: |
| `dead_thought_domain.bbmodel` | 分层显现的地面刻阵 | 1476 |
| `dead_thought_rifts.bbmodel` | 左右独立分组的三维裂口 | 256 |
| `dead_thought_final_wheel.bbmodel` | 断环、五瓣刀片、内环与无生核 | 1856 |
| `dead_thought_execution_line.bbmodel` | 黑／红／淡红三层细切线 | 60 |
| `dead_thought_life_remnant.bbmodel` | 六块抽象生命残影 | 72 |
| `dead_thought_branch_cage.bbmodel` | 枯枝簇 | 156 |
| `dead_thought_scars.bbmodel` | 斩痕与残缺轮片 | 336 |

Blockbench 工程坐标为 16 单位/方块，运行时 OBJ 已换算为方块单位。左右裂缝在编辑工程中重叠，检查时隐藏另一侧分组；运行时只绘制对应侧。

导出脚本：`tools/art/build_dead_thought.js`。在 Blockbench MCP `risky_eval` 中先设置 `globalThis.DEAD_THOUGHT_ROOT` 为仓库绝对路径，再执行脚本。需要先有一个打开的 Blockbench 工程。每次执行会新建七个工程，不覆盖其他打开的工程；磁盘上本目录的生成资源会更新。

OBJ 每个组只声明一次，凹多边形用三角化处理，UV 保持在色板格内。资源回归测试检查分组、索引、UV 和总面数预算。修改工程后需按同样的单位／分组约定重新导出，不能直接把编辑单位当作方块单位。
