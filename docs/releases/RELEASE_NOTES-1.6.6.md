# Blade Tetra: Forged Styles 1.6.6

**From wielding a named blade to forging one of your own.**

This update focuses on named-blade ingots, awakened-blade combat, the bladesmith handbook, and compact storage. Qianmen custom styles and Lifeng sword aura were already introduced in 1.6.5.

## Named blade ingots

- Surround an eligible named SlashBlade with **8 Tiny Proud Souls** on a crafting table to extract its corresponding ingot. The original sword is returned after losing roughly **10% of maximum durability**; it must have durability remaining.
- Use ingots for Tetra blade, saya and guard modules. Source base attack contributes **50% / 25% / 25%** respectively, with bounded secondary stats. Mix sources freely.
- Required hammer tier now scales with source base attack: **tier 3 below 12; tier 4 at 12 or higher**.
- Matching saya + guard inherit the source's registered **Slash Art (SA)**; the blade independently inherits its own registered **Special Effects (SE)**. Third-party effects still depend on their registration and compatibility.
- Ingot appearances use the source's backplate inside an ingot silhouette, with safe fallbacks for unsupported third-party artwork.

## Fusion and legacy migration

- Eligible source combinations expose **Fuse SA** at the Tetra workbench. Preview first and explicitly confirm; preview does not alter the original. Changing a participating source invalidates that confirmation, while repairing/reforging the same source retains it. Signature fusions still take precedence.
- The old imprinting mini-game, blank-scroll recipes and outputs have been retired. Existing blank scrolls keep their item ID but become **Lost Knowledge**, a hidden, unobtainable-in-normal-play, nonfunctional keepsake. Finished old manuals retain their item data but lose their retired purpose.
- Previously converted blades remain in existing saves. Old built-in conversions from non-modular SlashBlades or Tetra swords are no longer available as recipes; use the revised Iron Katana / Tetra crafting route. Existing world items are **not** scanned, deleted or reset.

## Four awakened blades

- **Akatsuki:** improved blood blade, debt and recovery, plus blood-thread SA execution. Native orbiting-sword hits and up to two hostile-arrow deflections can replenish the channel's shared fuel budget. Final Moon and Contract-Blade integration remain.
- **Senbonzakura:** progressive scattering, trailing blade fragments, scattered attacks, Endless Rengeki and gathered-blade Sakura Burst visuals.
- **Raikiri:** lightning armor, rain conduction, delayed strikes along native Rapid Slash movement, persistent ground needles and needle detonations.
- **Kyouka Suigetsu:** movement decoys that shatter when struck, together with Three Mirrors, Water Mirror Armor and condensed-water slashes under existing limits.
- Switch the active soul on fused soul imprints; eligible custom styles can invoke installed soul skills. Four soul relics provide scrollable skill guidance and legacy-owner claim recovery.

## Handbook, artwork and maintenance

- Optional bilingual **Patchouli Bladesmith Handbook** covering crafting, styles and scrolls, named blades and fusions, awakening clues, fittings and compatibility. Undiscovered story clues stay hidden; Patchouli is **not** a hard dependency.
- Shorter standard tooltips, with extended guidance in the handbook and expanded views.
- Reduced awakened backplate textures and removed an unused Mikage portrait texture to shrink shipped resources. Mikage's normal fights, rematches and new visitor entry remain closed; existing progress is retained.

## Storage and verification

- Versioned compact NBT for saved modular blades; regular Tetra/SlashBlade runtime data and unknown fields are preserved on load.
- Across 11 real awakened/fusion blade fixtures, complete **uncompressed item NBT** decreased from **34,545 to 14,649 bytes (57.6%)**. This is a storage-size measurement, **not** a proven FPS/TPS or heap improvement.
- Automated native Forge coverage includes representative Tetra releases **6.3.0, 6.9.0, 6.13.0 and 6.17.0**, along with forging, styles, combat, storage and handbook suites. AE2 was tested at the real item-storage-cell data layer, **not** as a full in-world ME network.

## Critical update and rollback notice

**⚠ Back up your entire world, including player inventories, before upgrading.**

- 1.6.6 can read legacy blade data, but blades saved using its compact format **cannot be reliably read by 1.6.5 or earlier**. To downgrade, restore your **pre-upgrade world backup**; do not just swap the JAR back.
- Full historical-world migration, actual two-client multiplayer, complete restarted AE2 ME networks and all third-party integrations remain outside the verified scope.
- Requires Minecraft **1.20.1**, Forge **47.x**, Tetra **6.3.0–6.x**, MUtil **6.2.0–6.x**, SlashBlade: Resharped **1.9.63–1.x**. Keep client/server mod versions in sync.
- The shipping JAR does not bundle development/testing tools, dependencies or separate model packs.

> Prepared on the 1.6.6 release-candidate branch. Public availability begins only after an actual platform release.
