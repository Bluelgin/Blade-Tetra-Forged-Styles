# Internal Kyouka V-teleport renderer probe (not a public release)

Built from main as version `1.6.7-kyouka-vprobe1`. Replace the installed Blade Tetra 1.6.7 JAR with this JAR; do **not** install both.
Use the same Forge 1.20.1 modpack as the affected player and back up the world first.

1. Open Creative Inventory > Blade Tetra main tab and take `[TEST] 镜花水月 · V残影诊断`. The existing OP command `/blade_tetra_test_kyouka` also gives the same initialized sword.
2. Test V teleport with an empty area, then near monsters with aggro, first and third person, 5-10 attempts each.
3. Collect `logs/latest.log`, `logs/debug.log`, and any `crash-reports/crash-*.txt`, noting exact world/time, mod list and whether the afterimage was visible.
4. Search `[KYOUKA-V]` for `CAPTURE`, `SERVER_SPAWN`, `CLIENT_JOIN`, `RENDERER_OK`, `MISSING_RENDERER` or `SKIP`.

Interpretation:
- `SERVER_SPAWN` but no `CLIENT_JOIN`: client tracking/sync issue, or crash before join.
- `CLIENT_JOIN renderer=NULL` or `MISSING_RENDERER type=blade_tetra:kyouka_afterimage`: missing Kyouka renderer mapping.
- `MISSING_RENDERER` naming another mod's entity: look at that renderer/compatibility.
- `RENDERER_OK` followed by a new crash: examine new stacktrace; not a missing renderer at shouldRender.
- `SKIP`: the candidate teleport failed eligibility/displacement checks, so no echo should spawn.

The test-only mixin deliberately skips drawing entities with missing renderers so the log can be saved. Remove it before release. Existing combat, aggro and decay behavior is otherwise unchanged.
