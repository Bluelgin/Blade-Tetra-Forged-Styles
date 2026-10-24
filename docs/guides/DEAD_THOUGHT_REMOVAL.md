# 解除死念 / Remove Dead Thought

将已通过无生残印注入死念的模块化拔刀剑放入 Tetra 改装台，选择刀身，再选择「解除死念」。放入 **1 个原版紫水晶碎片**并确认操作。

- 未注入刻印的刀不显示此选项。
- 预览不消耗材料，也不改变原刀。生存模式确认后消耗一枚碎片，创造模式不消耗。
- 仅移除注入的无生残印；由现有能力同步逻辑重新计算 SA/SE，不强制指定某个技能。
- 材料、模块、强化、附魔和其他改装保留。刻印不返还，之后可以重新注入。
- 解除后选项消失，重新注入后再次出现。
- 玩家挑战完成/领取奖励记录保留，不会因为解除刻印再次获得奖励。
- 若刀装有赋予死念的对应部件组合，界面会提示：解除注入刻印不会移除部件的死念。需要另外更换对应部件。未注入刻印的部件组合不会显示解除选项。
- 解除不是回滚此前对怪物产生的命蚀，也不取消已经发动的攻击。
- 修复了空技能记录被当作有效 ID 的问题，新注入会正确保存被替换的 SA。旧存档若从未保存原 SA，无法凭空恢复该 ID；优先按现存部件/自定义构装重新计算，没有其他来源时退回原生无 SA 状态。

Put an injected Blade Tetra modular blade in a Tetra workbench, select its blade slot and **Remove Dead Thought**, then supply one vanilla amethyst shard. The operation is visible only while the injected seal is present. Previews are non-destructive. Survival crafting consumes one shard; creative crafting does not. The original seal is not refunded.

Only the injected marker is removed. Existing ability reconciliation restores the underlying SA/SE while retaining materials, improvements, enchantments and player progress. Fitting-granted Dead Thought remains; the description warns about matching fittings. Re-injection makes the operation available again.

Development verification: `./gradlew runServer -PdeadThoughtRemovalSmoke` uses an isolated world under `build/dead-thought-removal-smoke`.
