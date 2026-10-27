# Kyouka 3.0: planar reflection implementation record

## Baseline and scope

Base: `codex/true-name-release-ritual`, PR #43, commit
`6e2e3444538ebf34fb5c9d4d67a1b9a3215bfb06`.
#40 supplies artwork, #41 awakening runtime, #42 Sakura runtime, #43 shared ritual.
#44/#45 build Sakura combat on #43 and do not change the Kyouka presentation files.
This branch is a sibling of #44, not a replacement for it. No upstream PR is merged or edited.

The old `KyoukaAwakeningVisual` uses atlas planes/rings; `FloatingBladeRenderer` draws unrelated
radial blade ghosts and a vertically flipped sword. Neither captures the game world.

## Stage B gate

The first deliverable is an opt-in **real Minecraft client** planar-reflection prototype.
Do not equate its source code, math tests, compilation or startup with a visual pass.
`./gradlew runClient -PmirrorPrototype` creates/loads its own flat test world. It builds
colored reference blocks behind the observer, translates the observer and rotates a vertical
mirror. Four screenshots are written to `build/mirror-prototype/evidence/`.
`MIRROR_PROTOTYPE_SCENE_FINISHED` means the scenario ended, not that it passed visual review.

No ordinary ritual is switched to this renderer until the geometric/state-restoration gate
has actual client evidence. Stage C–E progress and final results are recorded below as work proceeds.

## Pipeline under evaluation

- `MirrorPlane`: double-precision world basis, reflected eye, handedness-reversing view,
  inverse-transpose clip plane and oblique near-plane projection. No ritual or GL dependency.
- `ReflectionRenderer`: one persistent `TextureTarget`, mirrored vanilla `Camera`, one extra
  vanilla `LevelRenderer.renderLevel`, and projective plane-to-texture coordinates.
- `ReflectionRenderState`: scoped restoration of target binding, viewport, projection,
  inverse view, front winding, depth/blend/cull, shader and shader parameters.
- `MirrorSurfaceRenderer`: explicit bounded surface with homogeneous texture projection;
  target aspect ratio and FOV are not approximated with a flat image stretch.
- `ReflectionPass`: render-thread recursion guard. Forge stage effects are suppressed only
  during the secondary pass; the ordinary pass dispatches normally.
- Client-only mixins: wrap the vanilla world invocation, swap the main target for the capture,
  and suppress secondary stage callbacks. No server combat, persistent entities or packets.

The main render follows the secondary pass and re-establishes terrain setup. The actual
observer's culling frustum and entity dispatcher camera are explicitly restored beforehand.
Unsupported renderer combinations must opt out before world capture; porting an entire portal
or shader engine is outside scope.

## References and license review

- Minecraft 1.20.1 and Forge 47.4.0: use the existing project dependency, `RenderTarget`,
  `Camera`, `LevelRenderer` and Forge stage hooks; no vanilla binaries are committed.
- [Water Shader Mod](https://github.com/MahoganyTown/Water-Shader-Mod), main branch,
  CC0-1.0: examined its `GameRendererMixin`, framebuffer and clipping approach. It targets
  Fabric 1.19.4, modifies shaders and player invisibility and recursively calls GameRenderer.
  Those hooks and assets are not transplanted.
- [Simply Mirrors 0.1.1, Forge 1.20.1](https://www.curseforge.com/minecraft/mc-mods/simply-mirrors/files/9239970):
  inspected the published JAR (CFR 0.152) and bundled MIT license, copyright 2026 NsTut.
  Its MirrorRenderer reuses LevelRenderer, mirrored camera and oblique clipping; it also
  disables smart culling and restores entity dispatcher state. Its shader adaptation is
  substantial and is not assumed compatible here. No code/assets are copied into this branch;
  this record credits the implementation investigation. The project has no verified source
  repository linked on the inspected listing; do not claim source-level upstream compatibility.

## Results

- `./gradlew compileJava`: passed locally on Java 17 (cold setup: 20m58s).
- Targeted `MirrorPlaneTest`: 7 tests, zero failures/errors, including conservative visibility
  and transient non-finite projection rejection.
- First actual client attempt: an early capture before the test world was ready hit a
  degenerate clip plane. Capture failed closed; this was **not** accepted as a mirror pass.
  World-readiness and visibility gating were added. A new real-client run is in progress.
- Mesa llvmpipe / OpenGL 4.5. No shader pack or alternate terrain renderer installed.
- Stages C–E are deliberately gated on actual prototype screenshots and state checks.
  The ordinary Kyouka ritual has not been replaced yet. No real-time-reflection acceptance claim.
