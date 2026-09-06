# Blade Tetra: Forged Styles

Blade Tetra: Forged Styles brings Tetra's modular craftsmanship to SlashBlade: Resharped. Forge a blade whose materials, components, visual style, and combat discipline all matter—then keep refining the same sword instead of replacing it every few hours.

Built for Minecraft 1.20.1 and Forge 47.x. One jar supports Tetra 6.3.0 and later 6.x releases; Tetra 6.9.0 remains the primary compatibility target.

## What this mod adds

- A dedicated modular SlashBlade that preserves Resharped's blade state, combos, ownership, kill count, break state and Slash Arts.
- Eight Tetra module slots: blade, tsuka, tsuba, saya, habaki, kashira, fuller and soul inscription.
- Four blade disciplines selected by the installed blade: Orthodox, Iaido, Rengeki and Dangaku.
- Meaningful fittings, including quickdraw and spirit saya, defensive guards, precision or reinforced habaki, multiple handle forms and blade finishings.
- Tetra repair and honing progression using the same durability state as SlashBlade.
- Material-aware blade surfaces, fittings, slash colors and restrained emissive details for Tetra, MMT and datapack-provided materials.
- Forging and technique scrolls that turn advanced construction and combat options into discoverable survival progression.
- Hidden blade legacies, four awakened souls—Akatsuki, Kyouka Suigetsu, Senbonzakura and Raikiri—and pairwise soul fusion without stat stacking or recursive bonus attacks.
- The Mikage endgame challenge, with its own arena, phases, dialogue, rewards and a rather unhealthy number of torii gates.
- Server-side balance controls and client-side visual/audio options for modpack authors and players.

## New in 1.5.0: Named Blade Imprinting

Named blades can now be studied instead of simply being replaced.

1. Place a supported named SlashBlade on the blade stand directly above a Tetra workbench.
2. Hold a Blank Imprinting Scroll and sneak-use the workbench.
3. Complete the observation, tracing and hammering trial.
4. Spread the resulting pattern near a workbench to unlock that blade's imprinted saya and complete hilt.

The original named blade is still required for study. Poor work damages it, and a severe failure can break it. Carrying a proudsoul sphere improves the attempt; a proudsoul crystal or trapezohedron also protects the source blade from being broken by the ritual.

The imprinted fittings do not replace your Tetra blade body and do not copy the source blade's Slash Art. A matching saya and hilt instead form **Imprint Affinity**, dynamically granting:

- **+10–30% attack**, based on half of the positive gap between the source and modular blade.
- **+15–35% durability**, calculated by the same principle.
- No negative bonus when the modular blade is already stronger.

Version 1.5.0 recognizes 15 named blades from SlashBlade: Resharped and can discover standard named-blade definitions from installed addons. Nonstandard OBJ groups, unusual coordinate systems and custom renderers may still require a dedicated adapter.

Fun fact: I was zoning out in physics class when this idea suddenly popped into my head.

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

- Existing modular blades keep their Tetra modules, SlashBlade state, awakenings and historical records when updating.
- If an addon that supplied an imprinted fitting is removed, the record is retained but the missing appearance remains unavailable until its provider returns.
- Automatic named-blade discovery does not mean every addon renderer is guaranteed to split correctly. Please include the source blade, addon version and screenshots when reporting a visual mismatch.
- Back up important worlds before changing any content mod or major dependency version.

When reporting an issue, include your Minecraft, Forge, Tetra, mutil, SlashBlade: Resharped and MMT versions, together with `latest.log` when possible.

Project source and documentation: [GitHub](https://github.com/Bluelgin/Blade-Tetra-Forged-Styles)
