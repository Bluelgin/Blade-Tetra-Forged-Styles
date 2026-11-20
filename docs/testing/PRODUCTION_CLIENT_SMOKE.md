# On-demand production JAR smoke

This is a deliberately **manual** diagnostic. It does **not** run on normal
pushes, PRs, or the existing suite planner. It complements, not replaces,
`verifyProductionJar`, userdev `runClient`, and in-world acceptance.

## What the two modes actually prove

| Mode | Checks | Does **not** prove |
| --- | --- | --- |
| `artifact` | Build and reobfuscate the release JAR; verify its `mods.toml`, Mixin manifest, no development-only files, probe classes, exactly one Blade Tetra JAR, and stage the **original** Tetra/SlashBlade/mutil/player-animation JARs | That a real Minecraft client can boot |
| `client` | All artifact checks, then use an isolated Minecraft 1.20.1 Forge **production** installation, with the release JAR in `mods/`. On the Forge mod event bus, inspect the **built renderer map** for all eight Blade Tetra entity types, including `blade_tetra:kyouka_afterimage`. Wait until a real client screen is open. Require both proof markers in the game's own `latest.log` and no crash for five more seconds | Full V-key teleport/combat animation, multiplayer, a player modpack, shader compatibility, or every Tetra version |

The two mandatory game markers in `client` mode are:

- `BLADE_TETRA_PRODUCTION_RENDERERS_PASS`: Forge's `EntityRenderersEvent.AddLayers`
  provides the completed renderer map; all eight actual renderers are present.
- `BLADE_TETRA_PRODUCTION_CLIENT_PASS`: client menu/UI is ready with renderer
  registration already verified.

The script itself prints `BLADE_TETRA_PRODUCTION_SMOKE_PASS` only after both
game-origin markers and a short stability window. The `artifact` mode emits
`BLADE_TETRA_PRODUCTION_ARTIFACT_PASS` instead; these are intentionally
different. If Forge crashes, exits prematurely or never reaches the menu,
the check **fails** (no silent skip/false-green).

## GitHub Actions (manual)

Once `.github/workflows/production-smoke.yml` exists on the default branch:

1. **Actions → Production JAR smoke (manual) → Run workflow**.
2. Select `mode = client` (or the cheaper `artifact` audit).
3. Select Forge `47.4.13` or `47.4.23`. Tests default to the former because
   the reported client crash used that version.
4. Download `production-smoke-<mode>-<forge>` to inspect the report, launcher
   log and game `latest.log`/crash reports.

This is an explicit owner-triggered run; it does not increase everyday PR cost.
GitHub only lists a new `workflow_dispatch` workflow for manual use after
the workflow file reaches the repository's default branch.

CI installs Forge only on explicit request. Respect Forge's distribution
preferences and avoid running this as a frequent scheduled job.

## Developer command (Linux / Xvfb)

```bash
./gradlew --no-daemon stageProductionSmoke
python3 tools/ci/production_smoke.py --mode artifact
python3 -m pip install minecraft-launcher-lib==8.0
xvfb-run -a python3 tools/ci/production_smoke.py --mode client --forge 47.4.13
```

Use Java **17**. Nothing in `sourceSets.integrationTest`, `runClient`,
`-PdevTools` or the development Mixin config is added to the real instance.
The game is isolated under `build/production-smoke` so local saves/modpacks
are not touched. All runtime dependencies are copied from their original Maven
artifacts, not ForgeGradle's userdev-remapped copies. The script prints the
exact release JAR SHA-256 for traceability.

The probe is compiled into the release JAR but is dormant unless the JVM
property `-Dblade_tetra.productionSmoke=true` is explicitly present. Its
normal-play cost is just a constant boolean guard on two client events.

## Limitations and next gates

- This is a **packaged startup + renderer-registration** test. It is not an
  automated end-to-end V-key movement or in-world residual-entity render test.
  Perform those in an actual test world and player-representative modpack.
- The quick runtime test uses the dependency versions in `gradle.properties`.
  Test newer Tetra/SlashBlade releases separately; do not claim 6.9.0 / 1.9.65
  compatibility solely from this profile.
- An isolated clean client intentionally excludes player add-ons and
  rendering optimization mods; use the reproducing modpack for final compatibility.
- The repo's ordinary `Validation` CI already guards against another Forge
  EventBus listener wrapper-name collision. This manual test verifies that
  release-JAR renderer registration is functional under the production loader.
