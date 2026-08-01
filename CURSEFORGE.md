# Blade Tetra: Forged Styles

Blade Tetra: Forged Styles brings Tetra's modular craftsmanship to
SlashBlade: Resharped. Forge a blade whose materials, components, visual style,
and combat discipline all matter.

## Features

- A dedicated modular SlashBlade item that preserves SlashBlade's blade state,
  combo system, ownership, kill count, break state, and special attacks.
- Eight Tetra module slots: blade, tsuka, tsuba, saya, habaki, kashira,
  fuller, and soul inscription.
- Three blade disciplines derived directly from the installed blade module:
  Iaido, Rengeki, and Dangaku.
- Combat-relevant components, including quickdraw and spirit scabbards,
  defensive guards, precision or reinforced habaki, and distinct handle and
  fuller choices.
- Tetra repair and honing progression, sharing the same durability value used
  by SlashBlade: Resharped.
- Dynamic material-aware blade textures, decorative material patterns, slash
  trails, and ranged-art colors.
- A 512px high-detail modular atlas with layered forged grain, improved hamon,
  crystal facets, obsidian fracture planes, arcane inlays, and dragon-scale
  forging patterns.
- Distinct standard, swift, and stable tsuka wrapping, with ray-skin grain,
  layered cord shadows, material-aware collars, and restrained menuki details.
- Cleaner blade faces: isolated speckles were removed from ordinary metals and
  replaced by continuous forged grain, polish, facets, fractures, and inlays.
- Curated and automatic compatibility for Tetra materials and populated Forge
  ingot tags.
- The hidden Akatsuki awakening: forge a blood-crystal soul inscription, apply
  the Akatsuki saya artwork, reach 50 kills, then finish an enemy with a Slash
  Art beneath an open full moon.
- Four blade-history secrets: Hundred-Forged for repeated break-and-reforge
  mastery, Myriad Forms for teaching one blade all three disciplines, Raikiri
  for binding lightning into a conductive purple-lightning build, and
  Beginner's Heart for fifty victories with the starter iron katana build.
- Smithing-note fragments can be discovered in village weaponsmith chests.
  Reading them permanently records poetic hints and completed legacies in a
  player-bound Journal of Forged Blades.
- Conductive copper, silver, gold, electrum, brass, bronze, aluminum, signalum,
  and lightning-aligned components can attract real lightning in open storms.
  Conductive blades can also call a controlled visual bolt and 4-7 lightning
  damage onto exposed targets, with a seven-second cooldown.
- Optional Contract Blade integration gives an awakened Akatsuki a real,
  persistent contract spirit with ownership, resonance, deployment, recall,
  broken-blade dormancy, and a dedicated HUD emblem.

## Requirements

- Minecraft 1.20.1
- Forge 47.4.0 or compatible Forge 47.x release
- Tetra 6.10.0+
- mutil 6.2.0+
- SlashBlade: Resharped 1.9.63+

Player Animator 1.0.2-rc1+ is optional but recommended.
Contract Blade Core 0.1.0-beta.8+ is optional and enables Akatsuki's spirit.

## Stable release notice

Version 1.0.0 is the first feature-frozen stable release. Backing up a world
before adding or updating any content mod is still recommended. When reporting
issues, include the Minecraft, Forge, Tetra, mutil, and SlashBlade: Resharped
versions used.

## 1.0.0 — First stable release

- Promoted the complete Alpha 31 feature set to the first stable release.
- Includes modular forging, three construction-derived combat disciplines,
  dynamic material visuals and emission, the Journal of Forged Blades, eight
  hidden legacies, and optional Contract Blade integration.
- Fixed a severe held-item performance issue where ordinary non-luminous
  modular blades regenerated and scanned a 512px emission atlas every render
  pass. Empty emission results are now bounded, cached, and cleared safely on
  resource reload.
- Expanded the command-only Legacy Debug Talisman to all eight legacies. It
  remains absent from recipes and creative tabs, and packaged games require
  both the exact player name `Dev` and `enableDeveloperTools=true`.
- Feature development is frozen for 1.0.0; only release blockers, crashes,
  save-safety issues, and severe performance defects are in scope.

## Alpha 31 — Senbonzakura and legacy developer tooling

- Added the Sakura Soul Crystal, crafted from a proudsoul sphere, amethyst
  shards, and pink petals, as a dedicated awakened-inscription material.
- Added the hidden Senbonzakura legacy. Its candidate requires a wakizashi,
  Sakura Crystal inscription, and Sakura Fubuki saya artwork.
- Awakening requires sixteen uninterrupted Rengeki hits without taking damage
  in a cherry grove, followed by a successful dawn Slash Art kill.
- Every four Rengeki hits after awakening creates one petal mark, up to three.
  The next successful Slash Art hit turns each mark into two pink summoned
  swords, with an eight-second cooldown.
- Added Senbonzakura's journal fragment, hidden advancement, completed record,
  pink-white branch emission, and original cherry-petal presentation.
- Removing a required component or breaking the blade makes the legacy dormant;
  restoring the same physical blade reactivates its inherited identity.
- Added the command-only Legacy Debug Talisman for materials, candidates,
  near-complete rituals, awakening, dormancy, break/restore, and safe legacy
  reset testing. It has no recipe or creative-tab entry.
- The talisman requires the exact player name `Dev`. Development runs allow it
  automatically; packaged games additionally require `enableDeveloperTools`.

## Alpha 30 — Material emission and soul glow

- Added a real full-bright luminous pass for arcane, fiery, lightning, icy,
  ender, crystal, obsidian-fracture, dragonsteel, and compatible discovered
  materials.
- Emission is generated as a transparent companion to each existing 512px
  material atlas. It changes no models, combat values, recipes, or item NBT.
- Different material families illuminate only authored surface details such as
  crystal facets, heat seams, lightning branches, runes, and obsidian cracks
  instead of making the entire blade uniformly bright.
- Awakened inscriptions gain restrained purple-gold veins. Akatsuki replaces
  them with blood-red crystal veins, while Kyouka Suigetsu uses cyan mirror
  lines.
- Broken or dormant legacy blades retain a faint identifying afterglow and
  recover their full emission after repair and reactivation.
- Added `config/blade-tetra-client.toml` controls for enabling the emissive
  layer and independently scaling material and soul-glow intensity.
- Disabling emission cleanly falls back to the ordinary Alpha 29.2 dynamic
  material textures.

## Alpha 26 — Server configuration release candidate

- Added a per-world `serverconfig/blade-tetra-server.toml` gameplay config.
- Server owners can control smithy clue chance and pause all new easter-egg
  progress without deleting existing blade identities or player discoveries.
- Optional Contract Blade integration can be disabled without making Contract
  Blade Core a required dependency or deleting spirits owned by that mod.
- Conductivity now exposes separate controls for real player-attracting
  lightning, incoming lightning mitigation, offensive lightning, proc chances,
  Raikiri chance, damage curve/cap, and cooldown.
- Defaults exactly preserve Alpha 25 behavior: 32% clue chance, 20-minute
  one-point attraction average, 35% lightning mitigation, 2% offensive chance
  per point capped at 8%, 12% for Raikiri, 4–7 damage, and a seven-second
  cooldown.
- Configuration changes are server-authoritative and do not modify recipes,
  module NBT, durability, or existing save data.
- Full setting documentation is included in `CONFIGURATION.md` inside the
  project and release source.

## Alpha 25 — Journal of Forged Blades

### Alpha 25.4 — Secret progression cleanup

- Removed all pre-unlock easter-egg counters and instructions from blade
  tooltips, including Beginner's Heart kills, Myriad Forms mastery count,
  Hundred-Forged repair/honing progress, Raikiri charge instructions, and
  Akatsuki ritual readiness.
- Forging clues now live exclusively in smithing-note fragments and the Journal
  of Forged Blades, preserving the intended discovery loop.
- Post-unlock identity lines remain visible, as do functional state messages
  such as awakened/dormant Akatsuki, soul inscription status, conductivity,
  combat discipline, and saya appearance.
- SlashBlade's ordinary soul and kill statistics are unchanged.

### Alpha 25.3 — Journal reader fix

- Fixed the Journal of Forged Blades doing nothing when used. Minecraft's
  vanilla reader only recognizes the exact vanilla written-book item ID, even
  when a custom item subclasses `WrittenBookItem`.
- The journal now opens its synchronized dynamic pages through a dedicated
  client bridge while retaining the vanilla book reading screen.
- No Patchouli dependency was added, and existing journals, clues, player
  discoveries, and completed legacy pages remain compatible.

### Alpha 25.2 — NBT Sage

- Added the final meta secret, NBT Sage, as an unlisted sixth journal chapter.
- Its cryptic page appears only after the player inherits three ordinary blade
  legacies; it is never placed in village loot and does not alter the five-page
  collection counter.
- The same physical blade must experience all eight component slots (including
  fuller and inscription), three distinct blade materials, a saya skin, and five
  honing steps while carrying both Hundred-Forged and Myriad Forms.
- Progress uses a fixed module bitmask, four bounded milestone fields, and a
  material list capped at three entries. Raw NBT key count and serialized size
  never contribute, preventing arbitrary tag bloat from solving the secret.
- Unlocking adds the hidden NBT Sage advancement, a completed journal appendix,
  the tooltip “This blade's NBT is staring back at you,” and a restrained
  purple-gold tint blended into Myriad Forms' existing style trail.
- NBT Sage is cosmetic and grants no attack, durability, or reach bonus.

### Alpha 25.1 — Completed legacies and journal artwork

- Journal pages now read the player's real hidden advancements. A poetic clue
  becomes an exact completed record after its blade legacy is inherited.
- Added separate collected and completed counters to the journal tooltip.
- Completing a known legacy updates the journal with an anvil chime, a restrained
  gold notification, and a small end-rod flourish.
- Reading a new fragment now has page-turn audio.
- Replaced the temporary vanilla paper/book icons with six authored 16px assets:
  five color-coded manuscript fragments and a dark indigo smithing journal.
- Existing Alpha 25 fragments repair their visual model data automatically when
  carried, so old saves remain compatible.

- Added five collectible smithing-note fragments for Beginner's Heart,
  Hundred-Forged, Myriad Forms, Raikiri, and Akatsuki.
- Village weaponsmith chests have a 32% chance to contain one fragment;
  advanced secrets are deliberately rarer.
- Reading a new fragment permanently records its clue for that player and
  grants a Journal of Forged Blades if needed.
- The journal opens as a real book, updates its pages as clues are discovered,
  survives player death, and can also be crafted from a book, paper, and an
  iron nugget.
- Duplicate fragments are not consumed, allowing them to be shared with other
  players.

## Alpha 24 — Blade legacies and Iaido rebalance

- Added four manually awarded hidden advancements and permanent blade-history
  identities: Hundred-Forged, Myriad Forms, Raikiri, and Beginner's Heart.
- The same physical item now remembers break/restore transitions, blade
  material changes, and signature hits completed in each combat discipline.
- Iaido draw reach increased from 3.25 to 4.25 blocks.
- Iaido draw damage is now 1.60x normally and 2.00x after completing the
  resheathe rhythm; follow-up cuts deal 1.15x and the early draw receives 25%
  incoming-damage reduction.
- Iaido remains a narrow single-target discipline; its changes do not widen
  the attack into Rengeki or Dangaku territory.
- Alpha 24.1 changes Beginner's Heart to count only qualifying kills made with
  the starter iron katana construction. Repairs and honing are allowed;
  incompatible module configurations simply pause its dedicated counter.
- Added a six-level conductivity display, rare player-attracting lightning,
  35% held-blade lightning mitigation, and controlled offensive lightning.

## Alpha 18 — Construction-derived blade names

- Blade names now update automatically from the installed Tetra construction.
- Blade material supplies the prefix, while the blade module selects Katana,
  Wakizashi, or Nodachi.
- Distinct component pairings unlock authored epithets such as Flash, Gale,
  Soulwarden, Immovable, Flying Swallow, Mountainbreaker, Phantom, and Autumn
  Water.
- Names are derived without additional NBT, and anvil custom names still take
  precedence.

### Also included from Alpha 17

- Added native Tetra honing progression for the blade and tsuka slots.
- Hones are applied to the same combat stats used by ordinary attacks, combat
  disciplines, and special attacks.
- Preserved SlashBlade durability and break-state behavior while Tetra derives
  maximum durability from installed modules.
