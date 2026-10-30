# Progressive blade release

Condensed successful native melee/slash attack actions roll once at 25%, not once per target or
hit. Four persistent item-NBT stages remove a quarter of the steel each and deploy the same
quarter of the existing bounded back shard pool. Partial shards only follow; they do not attack.
Stage four changes to dispersed at PlayerTick.END, after the native damage window. No durability,
module, enchantment or item destruction occurs. Stage changes synchronize through existing equipment NBT.

Full reassembly uses the existing native upper-slash transition and resets stages after 14 ticks
of holding the same blade/slot. Switching, dying or dimension change cancels reset. Interrupted
reassembly retains fracture progress. No resets through quick stance toggles.

Ordinary dispersed native blade damage is multiplied by .70. The default associated projectile
budget is 25% of that reduced hit, yielding a theoretical .875 of condensed per successful proc;
intervals, caps, misses and armor affect actual output. Infinite Dance SA remains unchanged.

Normal concerto reuses the same shard pool: six ticks flying toward an authorized enemy overhead
anchor, two ticks poised, firing at tick eight and returning from tick nine. Headroom blocked:
try a side-upper anchor; if either route is obstructed, do not emit. Server rechecks the live
anchor, legality and paths before spawning a real native projectile. No damage on cosmetic travel.
Clients track a target-relative authorized offset. Existing owner/group/shard/vertex limits apply.

Automated checks cover progressive pool identity/count, native projectile origin and collision,
four-stage NBT progression and delayed reassembly. First/third-person visual quality still needs
human playtesting, particularly blade clipping and fast enemy movement near ceilings.
