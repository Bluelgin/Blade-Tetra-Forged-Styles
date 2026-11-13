# Blade Tetra: Forged Styles

Blade Tetra: Forged Styles brings Tetra's modular craftsmanship to SlashBlade: Resharped. Forge a blade whose materials, components, visual style, and combat discipline all matter—then keep refining the same sword instead of replacing it every few hours.

Built for Minecraft 1.20.1 and Forge 47.x. One jar supports Tetra 6.3.0 and later 6.x releases; Tetra 6.9.0 remains the primary compatibility target.

## What this mod adds

- A dedicated modular SlashBlade that preserves Resharped's blade state, combos, ownership, kill count, break state and Slash Arts.
- Eight Tetra module slots: blade, tsuka, tsuba, saya, habaki, kashira, fuller and soul inscription.
- Four preset blade disciplines: Orthodox, Iaido, Rengeki and Dangaku.
- Qianmen custom styles: record up to six ordinary actions, edit and name a native Tetra scroll, then forge short, standard or long blades; compact offline sharing preserves names and action order.
- Lifeng sword aura: learned mastery enables auras from real ordinary slashes at SSS, including custom styles, with three client detail levels.
- Meaningful fittings, including quickdraw and spirit saya, defensive guards, precision or reinforced habaki, multiple handle forms and blade finishings.
- Tetra repair and honing progression using the same durability state as SlashBlade.
- Material-aware blade surfaces, fittings, slash colors and restrained emissive details for Tetra, MMT and datapack-provided materials.
- Forging and technique scrolls that turn advanced construction and combat options into discoverable survival progression.
- Hidden blade legacies, four awakened souls—Akatsuki, Kyouka Suigetsu, Senbonzakura and Raikiri—and pairwise soul fusion without stat stacking or recursive bonus attacks.
- Mikage’s challenges, rematches, and new visits are currently closed. Existing progress is saved.
- Server-side balance controls and client-side visual/audio options for modpack authors and players.

## Named blade ingots

Put a supported, unbroken named blade in the center of a crafting grid and surround it with eight Broken Proud Souls. You get one named blade ingot and the source sword back. Extraction costs 10% of the source’s maximum durability, rounded up. Repair the sword first if the cost would break it.

Use named blade ingots as materials for blades, saya (scabbards), and tsuba (guards) in the Tetra workbench. The blade can inherit its source’s registered Special Effects. A saya and tsuba from the same sword can inherit its registered Slash Art. Mix sources to discover new combinations, then preview and confirm a supported fusion at the workbench.

## Combat and blade identity

The installed blade determines the main discipline:

- **Orthodox** keeps Resharped's broad native moveset and works well in complicated fights.
- **Iaido** rewards spacing, a settled sheath and a precise draw instead of face-tanking with repeated openers.
- **Rengeki** builds fast sustained pressure across a target or group, but loses momentum when the chain breaks.
- **Dangaku** favors heavy cleaves, stance pressure and wide control with a slower rhythm.

Awakenings belong to the same physical modular blade. Replacing an inscription or breaking the weapon can make an ability dormant, but the blade remembers completed awakenings. Compatible awakened souls may be fused so both abilities remain active; their raw stats do not stack.

Detailed style, awakening and resonance explanations stay behind **[Alt]+** so ordinary tooltips remain readable.

## Materials and visual compatibility

Blade Tetra reads materials already supplied to Tetra and adapts them to SlashBlade's modular models, generated textures and slash presentation. It does not take ownership of another mod's material stats, repair items, processing requirements or special effects.

Curated appearances are included for several notable materials, while other valid Tetra metals, gems, bones, woods and stones receive category-aware generated visuals. Infinity, dragonsteel, Goety metals, Twilight Forest materials and similar integrations remain optional.

More Mod Tetra is recommended for large modpacks but is not required. Always choose an MMT release made for your installed Tetra version; for example, MMT 2.2.941 belongs with Tetra 6.9 and is not compatible with Tetra 6.10+.

## Requirements

The optional Patchouli Bladesmith’s Manual covers styles, forging, and discovered legacies in game.

Required:

- Minecraft 1.20.1
- Forge 47.4.0 or a compatible Forge 47.x release
- Tetra 6.3.0+
- mutil 6.2.0+
- SlashBlade: Resharped 1.9.63+

Optional:

- Player Animator 1.0.2-rc1+ — recommended for the intended animations
- More Mod Tetra — recommended for third-party material definitions
- Contract Blade Core 0.1.0-beta.8+ — enables Akatsuki's persistent contract spirit

## Compatibility notes

- Existing modular blades keep their Tetra modules, SlashBlade state, awakenings and historical records when updating. Older imprinting scrolls remain readable; use named blade ingots to forge new fittings. Use the same mod version on the server and all clients.
- If an addon that supplied an imprinted fitting is removed, the record is retained but the missing appearance remains unavailable until its provider returns.
- Automatic named-blade discovery does not mean every addon renderer is guaranteed to split correctly. Please include the source blade, addon version and screenshots when reporting a visual mismatch.
- Back up important worlds before changing any content mod or major dependency version.

When reporting an issue, include your Minecraft, Forge, Tetra, mutil, SlashBlade: Resharped and MMT versions, together with `latest.log` when possible.

Project source and documentation: [GitHub](https://github.com/Bluelgin/Blade-Tetra-Forged-Styles)
