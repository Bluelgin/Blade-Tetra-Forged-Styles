# Named model assembly

The server continues to resolve named-blade definitions and persist the original component identities and combat metadata. A client with the provider installed can resolve these snapshots into model and texture resources. Geometry compatibility has no effect on extracting an ingot, applying a component, inheriting a Slash Art or inheriting a Special Effect.

## Render path

1. Resolve and parse the source OBJ through SlashBlade's Wavefront constructor after checking the source texture and the OBJ byte budget.
2. Build a right-handed frame in which the longitudinal coordinate increases from tip to grip. The frame controls the cut, anchor selection, and source-to-target rotation. Separate handle groups, known cross-sections and declared anchors supply evidence for the blade/hilt boundary.
3. Partition each component into normal and luminous geometry. Each component can use several groups. A missing or ambiguous component uses the corresponding modular geometry. An unavailable damaged group uses the modular damaged section.
4. Match the source socket to the native Tetra socket. The blade and complete hilt use the same target socket. The sheath uses its mouth and a local direction. Uniform proportional fitting is bounded to 0.25–4 times authored scale. The hilt keeps its world proportions by default.
5. Validate the views and mounts required by the current display state before canceling the native event. Continue through SlashBlade's native render state for lighting and buffers. The runtime OBJ used by the backend contains final geometry during construction, including for constructor-time baking.
6. Inventory rendering uses a declared part-specific item mesh when available. Otherwise it reuses the world mesh and its texture UVs and fits it to the corresponding authored Tetra icon pose. The original Tetra atlas still identifies **target** parts. Provider UV islands are never used to guess part ownership. Oversized fittings are reduced uniformly around the socket to stay within the native icon footprint.

JOML post-multiplies the socket transformation as `targetFrame · adjustment · scale · inverse(sourceFrame)`. The renderer applies matching PoseStack operations to keep position and normal transforms synchronized. Research previews mount onto the stock iron katana using the same path.

## Adaptive world sheath fit

The blade and sheath originally stretched independently to the corresponding modular blade and sheath lengths. A successful mount only established the socket and direction. It did not compare the final curves. With the Yamato blade and Yuzuki Tsukumo sheath on the orthodox target, the outer sheath envelope missed the entire blade section near the tip.

Resharped's held renderer draws the blade and sheath using `hardpointA` and `hardpointB`. The native standby pose coincides at these hardpoints, verified both by evaluating the shipped PMD/VMD and by comparing the actual pre-draw matrices in a Forge client. The carry renderer uses one pose stack for both parts. The same sheath geometry follows the original independent hardpoint throughout a draw and return. No combo or pose rewrite, visibility switch, player-model access, blade deformation or persistent item state is required. The inventory renderer retains its authored icon fitting path.

The client samples 17 transverse envelopes from the actual mounted healthy blade and sheath. Adaptive fitting accepts up to 8,192 faces per part, independently of the existing OBJ parsing budget. Where a dominant connected sheath shell can be identified, disconnected decorations do not influence its envelope. A compatible pair keeps its original mesh. Otherwise, the fit moves the sheath centerline and allows bounded longitudinal and transverse scaling. The baked sheath is checked again against the blade envelope before replacing the native sheath pass, so sparse faces that cannot follow a proposed curve do not silently qualify. The base and luminous passes share the same warp, UVs and outward normal transformation. Named blade, hilt, damaged-blade, fragment and effect draw paths retain their existing geometry and handlers.

The fit uses sampled outer envelopes rather than a solid cavity. It handles conventional longitudinal OBJ geometry and honors the existing source calibration and resource overrides. A model whose shell cannot be measured, whose proportions exceed the limits, or whose baked faces cannot contain the sampled blade uses a verified modular sheath if that fits. If both attempts fail, the existing render path remains available and `-Dblade_tetra.sheathDiagnostics=true` logs the failed fit once per cached combination. Very sparse or highly concave models may need an authored adapter. This fallback affects visuals only.

Mounted profiles use a 384-entry LRU. Complete pair results use a 192-entry LRU with a 160,000-triangle weight limit. Keys use the source identities after resource rules and the actual Tetra target object for the current blade style and length. An accepted fit is reused in subsequent sheath passes, including when unsheathed, so it does not jump at the end of an animation. The resource reload clears the profile and pair caches together with the model caches.

Source references for the original renderer: [Resharped 1.9.63 LayerMainBlade](https://github.com/0999312/SlashBlade_Resharped/blob/0c1034e886ca1e76421ead8d82704bde739edd2e/src/main/java/mods/flammpfeil/slashblade/client/renderer/layers/LayerMainBlade.java), [Resharped 1.9.65 LayerMainBlade](https://github.com/0999312/SlashBlade_Resharped/blob/6e2a0a0/src/main/java/mods/flammpfeil/slashblade/client/renderer/layers/LayerMainBlade.java).

## Provider and resource pack settings

Use a client resource named `assets/<model namespace>/bladetetra/legacy_models/<model path>.json`. Include the original `.obj` extension in the model path. For example, the declaration for `some_addon:model/katana.obj` goes in `assets/some_addon/bladetetra/legacy_models/model/katana.obj.json`.

The resource manager picks the highest-priority resource for each path. An optional variant declaration at `<model path>/<named blade namespace>/<named blade path>.json` replaces individual fields from the model declaration. Use the same resource path to override a supplied declaration in a higher-priority resource pack. Malformed files are logged and ignored. Older provider declarations in `data/blade_tetra/legacy_adapters` still supply defaults. Conflicting legacy declarations still fall back to standard conventions.

```json
{
  "axis": "z",
  "reversed": true,
  "width_axis": "y",
  "blade_groups": ["edge", "inlay"],
  "blade_luminous_groups": ["edge_emission"],
  "hilt_groups": ["guard", "grip"],
  "hilt_luminous_groups": ["guard_emission"],
  "saya_groups": ["scabbard_shell", "cord"],
  "blade_anchor": [0, 4, -52],
  "saya_anchor": [0, 4, -48],
  "saya_direction": [0, 0, -1],
  "saya_fit": "preserve",
  "hilt_transform": {"offset_y": 0.025},
  "blade_damaged_blade_groups": ["broken_edge"],
  "item_blade_blade_groups": ["inventory_edge"]
}
```

- Axes take `x`, `y` or `z`. Anchors are absolute OBJ points; directions point from the tip toward the socket. `width_axis` fixes the transverse orientation. Per-component `blade_anchor`, `hilt_anchor`, `saya_anchor` and corresponding `*_direction` fields are supported.
- Base and luminous groups can be declared independently. An empty array disables that channel. Existing `*_luminous` entries in legacy base lists are treated as luminous groups.
- Named `blade_groups` describe an already isolated edge unless an anchor or a separate handle defines a cut. Set `split_hilt` to `false` if material to the rear of the blade socket cannot provide a complete hilt.
- `geometry_model` can name a different OBJ for assembly while preserving the source identity and texture. The alternative OBJ must use that texture's UV layout.
- `unit_scale` converts source OBJ units to SlashBlade model units independently of the blade length. It does not affect explicitly declared item meshes.
- `*_fit` accepts `proportional` or `preserve`. A `*_transform` may specify `scale`, `rotation` in degrees, `offset_x`, `offset_y`, `length` and `width` as a deliberate override. Components use the target frame's axes for these adjustments.
- State-specific names start with the native group, then the component, followed by `_groups` or `_luminous_groups`. Supported state prefixes are `blade_damaged`, `blade_fragment`, `item_blade`, `item_bladens` and `item_damaged`. Declared icon meshes use native icon units and XY projection.

SlashBlade 1.9.63 and 1.9.65 share mutable OBJ regex matchers. The two constructor paths serialize calls to the native parser across reload workers and the render thread. Constructor-return hooks still see the complete mesh. The OBJ grammar remains the one supported by the installed SlashBlade version, including triangles and quads. A fused source with no reliable boundary may need an explicit anchor or an independently authored alternate OBJ. A luminous-only blade keeps its own luminous layer. Decorations on the selected component stay with it, and unassigned groups are ignored.

## Budgets and diagnostics

Source OBJ input is limited to 8 MiB, 2,048 groups and 32,768 source triangles. Derived model views triangulate and discard degenerate triangles before writing indexed OBJ positions, UVs and normalized normals. Mesh caches limit both entry counts and retained triangle counts. Parsed sources, partitions, compiled views, mount results and unsuccessful resolutions are invalidated on resource reload. The advanced visual diagnosis reports source capability per component independently of the ingot's gameplay eligibility.

## On-demand verification

```sh
./gradlew test --tests '*NamedLegacyGeometryTest' --tests '*RuntimeWavefrontViewFactoryTest' --tests '*NamedBladeSheathAlignmentTest'
./gradlew test --tests '*NamedLegacyGeometryTest' -PsjapModelFixtures=/path/to/extracted/assets/slashblade_addon/model
./gradlew runServer -PnamedIngotSmoke
./gradlew runClient -PnamedIngotArtReview
./gradlew runClient -PnamedIngotArtReview -PsheathReview
```

Use the OBJ files extracted from the actual add-on JAR for the second command. The client review uses its existing isolated world and writes screenshots into `build/named-ingot-art-review`. Geometry tests validate OBJ parsing, view construction and transforms. Screenshots from a Forge client are needed to verify final appearance, including with client optimization mods.

The sheath review uses the native Yamato edge, Yuzuki Tsukumo sheath and Black Fox hilt on an orthodox blade, then a long all-Yamato comparison. It logs the held hardpoint matrices agreeing at rest, separating in the native A3 blade pose and agreeing again after the return sequence. Screenshots are written into `build/sheath-review`. The forced A3 sequence drives the blade motion for visual diagnosis without issuing combat input to the test world.

Forge 47.4.0 / Resharped 1.9.63 evidence: [mixed blade at rest](../testing/evidence/named-sheath/mixed-closed.png) and [exposed blade at the forced A3 diagnostic frame](../testing/evidence/named-sheath/mixed-draw-diagnostic.png).

Forge 47.4.0 / Resharped 1.9.65 evidence: [same-source long Yamato at rest](../testing/evidence/named-sheath/yamato-long-closed.png). The diagnostic completed in first person and both third-person cameras for both fixtures, then regenerated its sources after a resource reload.
