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


## Prototype gate: actual result

The opt-in scene subsequently completed in a real Forge client after the experimental-world
confirmation was handled. All four images are original framebuffer screenshots, not simulations:
[100](evidence/kyouka-v3/mirror-100.png), [140](evidence/kyouka-v3/mirror-140.png),
[180](evidence/kyouka-v3/mirror-180.png), [220](evidence/kyouka-v3/mirror-220.png).
The red/gold arch and emerald pillar are **behind the real observer** and appear inside the
mirror. The observer's lateral movement and mirror yaw change the reflected perspective.
Main target, camera position, projection and front winding restoration assertions did not fail.
The scene log is retained alongside the images. This passes the minimum block/rotation/state gate;
the cow was not sufficiently visible to call entity reflection visually accepted.

The initial checkpoint's complete Build #710 succeeded, including the inherited Forge smoke jobs.
The implementation below is a subsequent integration and requires its own build/client checks.

## Integrated architecture and budgets (validation pending)

`MirrorRitualController` derives six presentation phases on the original 120-tick clock.
`MirrorRitualResources` selects one bounded existing session (local player first), owns one target,
freezes captures at break tick 85 and draws after particles. Non-selected mirrors use the explicitly
approximate metallic surface. No new packets, saved entities or server combat callbacks exist.

The renderer receives a callback for the real detached blade and its delayed images; ghosts are
visible through the reflection camera only. The old radial ghosts, pseudo floor, atlas planes and
magic rings are removed. The existing hand departure, native model/material adapters, mounted
sheath, HUD/name and return lifecycle remain in charge.

The same deterministic triangulation supplies dark growing seams and physical shards (32/72/128
triangles). Every shard keeps its original projective UVs and samples the single pre-break image.
No per-shard capture or framebuffer is allocated. Thin solid backs, silver edges, tumbling and
spiral gathering finish before the inherited weapon return ends.

Reflection quality 0/1/2/3 is compatibility / 128px at 8Hz / 256px at 15Hz / 512px at 30Hz.
Default is 2. Target height is capped at 1024; only one target exists globally. Frustum queries in
the render-thread capture are additionally bounded to 24/40/64 blocks. Invisible, too-close,
out-of-range and non-finite projections skip work. These are code budgets, not FPS measurements.

Fabulous, Oculus/Iris, Embeddium/Rubidium/Sodium, OptiFine, Immersive Portals and Simply Mirrors
use the labelled compatibility path. No support for those modified pipelines is claimed. Runtime
capture failure quarantines captures until world change/resource reload. The test prototype is
registered only by the explicit development launch flag, with no production tick subscriber.

Resources are released on normal end, slot/weapon mismatch, death/world replacement, reload and
capture failure using the existing ritual lifecycle. A fog mixin preserves vanilla underwater
biome-transition history; GPU uniforms, framebuffer/viewport, projection, winding, depth/blend/cull
and dispatcher cameras are restored separately. Alternate tilted-plane billboard orientation and
unknown shader/portal renderers remain outside verified compatibility.

Run `./gradlew runClient -PmirrorRitualReview` for the isolated native-blade replay matrix:
first-person, third-person, FOV/window resize, compatibility, low quality/slot abort, high quality
with resource reload, and a post-reload replay. This uses a debug-awakened actual Tetra blade and
the existing successful-presentation service; it does not test natural first unlock progression.
