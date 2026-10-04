# Modular blade art review

This update is visual only. It does not change material stats, effects, recipes,
combat timing or item progression. Materials and effects remain provider-owned.

## Improvements

- Metal collars and habaki: dark bevels, bright lips and broad restrained polish.
- Guards: remade thin oval, pierced four-lobed and chamfered octagonal meshes,
  including actual blade slot/window walls and bevels. Materials still own their
  palettes. Icon presentation exposes the face slightly; world mounts stay perpendicular.
- Installed cloth/leather wraps: woven fibres versus overlapping straps/stitches;
  the actual metal end collars remain visible. Unknown wraps keep their palette.
- Installed sockets: bounded pommel inset, metal bezel, split gem facets and highlight.
- Coatings: ingredient-derived colour in a narrow double sheen, not an opaque recolour.
- Quickdraw/spirit saya: draw-mouth seam versus a small lacquer inlay.
- Fire/ice/lightning dragonsteel: separate restrained heat/crystal/electric blade motifs.
- Soul inscriptions: small root seal; existing awakening state, colours and glow preserved.
- Active Akatsuki: coordinated oxblood wrap, dark lacquer/gold moon saya, warm metal
  fittings and blade moon mark. No hanging charm or cord is added. No new unlock,
  item state or special guard is required; all three ordinary guards use the same design.
  Dormant Akatsuki does not receive the activated finish.
  The active finish uses its localized red soul-glow pass instead of the full-surface
  purple foil overlay. Actual enchantments and the stack's enchanted identity remain unchanged.

## Real in-game acceptance gallery

Launch the development client with `-PartPreview -PmmtTest -PiceAndFireTest`.
The profile uses `build/art-preview` and a separate creative flat world. It never
loads or modifies the player's regular development saves or external modpack.

Three pages show 36 actual ItemStacks through Minecraft's GUI item renderer, with
enlarged and normal-size icons. Wrap/socket samples use native upgrade recipes.
Coating samples also use a loaded provider's applicable native upgrade recipe,
and fail visibly if none is loaded. Sample construction never invents item improvements.
Sample labels are inspection-only custom names; they are not release item names.

Screenshots are exported to `art-previews/inventory-update/inventory-page-1.png`
through `inventory-page-3.png`. These are captures of the actual game, not mockups.
ESC/Enter acceptance returns to the isolated world; its inventory contains the
same sample stacks. F8 reopens the gallery in this opt-in profile.
The "刀镡细看" button opens frontal views of the actual world guard meshes through
the same material-render hook, exported as `guard-fronts.png` during the preview run.
This makes the real pierced windows and bevels inspectable without an edge-on icon.

Without the launch property the gallery, hotkey, world creation and inventory
setup are all inactive. None of the inspection dependencies are bundled in releases.

## Verification

Pixel tests cover unchanged empty attachments, collar protection, distinct wraps,
bounded sockets, restrained edge tint and distinct dragonsteel patterns. The icon
geometry regression suite covers all 108 modular model resources and preserves
world faces and the corrected guard extrusion.

Tests also cover all guard profiles, all 108 model variants, unchanged non-guard
geometry, the absence of a guard on tsubaless blades, cache reuse
and localized crescent/glow motifs. Runtime captures include native wool/leather
wraps, diamond socket and fire-dragon-blood coating.

Final baseline and MMT/Tetra 6.9 runs each completed 359 tests, zero failures/errors
and one intentionally skipped test. All three inventory pages and the world-guard
front-view page were captured and visually inspected. External named-blade legacy
parts retain their own authored models; this redesign replaces the mod's ordinary
simple/light/guard tsuba meshes, not other mods' unique hilts.

## Full-inventory performance and Akatsuki simplification

The 12-item gallery did not exercise the 36-slot inventory's working set. The
48-entry material/negative-emission caches could evict GUI/world layout pairs on
every frame. Their bounded capacity is now 128. Per-bake debug atlas PNG exports
were removed as well. Resource reload still releases the generated textures.

The forged guard mesh now uses 16 outline segments, with two radial bands for
oval/octagonal guards and three for pierced guards. The regression budget is at
most 288 triangles per icon guard, retaining bevels, blade slots and actual
windows. The old mesh spent most sampled render-thread time in vertex submission.

Akatsuki's hanging moon-seal charm and cord were removed at the user's request.
The existing lacquer, gold moon inlay, red binding band and blade finish remain
unchanged. No extra charm geometry is added in the GUI, hand or world render paths.

Run `-PartPreview -PartInventoryBenchmark -PmmtTest -PiceAndFireTest` to populate
the isolated world's real 36-slot survival inventory. After 60 warm-up frames,
the probe records 300 screen renders and texture-generation deltas. It exports
`inventory-performance.json` and `inventory-performance.png` beside the gallery.
The 2026-10-04 MMT/Tetra 6.9 run, with Ice and Fire, Configured and ItemZoom loaded,
recorded 14.956 ms mean / 15.927 ms p95 inventory-screen rendering and **zero**
material or emission rebakes after warm-up. These are screen render timings,
not overall game FPS or a guarantee for every modpack. The probe is inactive
without its explicit launch property.
