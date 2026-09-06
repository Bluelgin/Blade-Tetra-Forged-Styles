# Blade Tetra: Forged Styles 1.3.8

This changelog covers all player-facing changes since **1.3.5**.

## Highlights

- Added **Gatekeeper Mikage**, a new endgame boss built as a multi-phase SlashBlade duel rather than a simple damage check.
- Added the **Thousand-Gate Mirror Realm**, a rebuilt and self-contained challenge arena.
- Added **Boundary Forging**, a graduation-tier improvement that lets a completed blade call down Mikage's Boundary Flash after a successful Slash Art.
- Added a removable **tsubaless construction** for modular SlashBlades.
- Added extensive multiplayer, modpack-defense and high-end equipment scaling for the Mikage trial.

## The Mikage Final Trial

- Added the reusable **Boundary Gate Charm**. Hold it to open a temporary torii; every player who crosses the same gate joins the same challenge.
- Added the protected Thousand-Gate Mirror Realm with fixed daylight and weather. Player game modes are preserved, and ordinary players cannot damage the arena through block breaking, fluids, pistons, fire or explosions.
- Added Gatekeeper Mikage, a player-shaped, three-phase sword-ghost boss with party scaling and equipment-aware difficulty scaling.
- Mikage uses a large set of interactive techniques, including Boundary Flash, Thousand-Gate Purge, Eightfold Torii, Pursuing Phantom Sword Rain, Step Iaido, Mirror Reversal, Boundary Sever, Moonshadow and Zanshin counters.
- Correct responses create real damage windows: guard the Eightfold Torii, reflect the final crimson sword, punish a missed iaido step, win the crossing-blade clash, break the vermilion seals or identify Moonshadow's true body.
- Mikage can recognize repeatedly abused routes, rapid Judgement Cut chains and repeated identical Slash Arts, encouraging players to vary their approach.
- Added safe multiplayer defeat handling. A defeated participant returns alone while the remaining party may continue the encounter.
- Interrupted challenge sessions recover affected players from the challenge dimension when they next join the world.
- Victory and defeat now return players to the rebuilt torii entrance area.

## Boss Presentation

- Added an optional torii-shaped boss HUD with one continuous three-stage health bar, phase markers, delayed-damage feedback, technique names, hints and Reminiscence styling.
- Added a client-rendered geometric arena seal with vermilion floor inscriptions, spectral torii anchors, mirror membranes and proximity ripples.
- Added dialogue and subtitles for the encounter, including additional story lines when the challenger carries an awakened Akatsuki.
- Arena visuals and the custom boss HUD can be adjusted or disabled for accessibility and shader-heavy modpacks.
- Battle music and Japanese voice acting are provided through the separate optional **Blade Tetra: Forged Echoes** resource pack. The main mod remains fully playable without it.
- Repeated skill and guard voice lines are limited per encounter to avoid excessive audio spam; HUD hints remain available throughout the fight.

## Rewards and Reminiscence

- The first clear awards the **Secret Forging Scroll: Boundary Crossing**, a **Sword Ghost Remnant**, and the wearable **Broken Oni Mask**.
- Repeat clears award one Sword Ghost Remnant to every successful participant.
- Wear the Broken Oni Mask and sneak while opening the Boundary Gate Charm to enter the harder **Reminiscence** encounter.
- Clearing Reminiscence awakens a cosmetic echo on the mask without adding mandatory combat power.
- The Boundary Crossing scroll now opens correctly and presents a short poetic description of the secret art.
- Scrolls created by earlier development builds are repaired automatically when used.

## Boundary Forging

- The Boundary Crossing scroll provides the Tetra schematic for **Boundary Forging**.
- Boundary Forging requires a tier-7 Tetra forge hammer and consumes one Sword Ghost Remnant.
- The improvement preserves the blade's materials, modules, enchantments, kill count, durability state and existing legacy data.
- A completed weapon is named **Sacred Blade: Boundary Crossing**, or **Yokai Blade: Akatsuki [Beyond the Gate]** when applied to an awakened Akatsuki. Both names have equal combat strength.
- Any successful Slash Art hit with the forged blade can activate the secret art; no style-specific mark-building sequence is required.
- Four crimson phantom blades descend from above the target before a vermilion torii manifests and falls.
- The final strike deals blade-scaled damage plus **10% of the target's maximum health**, with a shared **10-second player cooldown**.
- Immediate, rapid-charge, unlocked, multi-hit and projectile-based Slash Arts are supported.
- One Slash Art can activate Boundary Flash only once, even when it hits repeatedly or strikes several targets.
- Ordinary attacks can no longer consume a pending Slash Art trigger.
- Boundary damage no longer disappears inside normal damage-immunity frames. The final strike bypasses armor and damage cooldown frames while still respecting damage events, totems, boss phases and mod-specific protections.
- Passive animals, villagers, golems, pets, teammates, creative players and spectators are excluded from targeting and area damage.
- Phantom blades now spawn above the victim and travel slowly enough for the sequence to remain readable.

## Tsubaless Construction

- Added **Remove Tsuba** to the Tetra workbench for modular SlashBlades.
- Removing a tsuba costs no material and does not return the previous tsuba material.
- The internal tsubaless fitting keeps the existing Tetra slot occupied, preventing empty-slot migration and rendering problems.
- Tsubaless blades gain **+0.12 attack speed** and lose **1 integrity**.
- Guard, light and material effects from the removed tsuba are fully removed rather than merely hidden.
- Traditional constructions that explicitly require a complete fitting, including Shoshin, do not accept the tsubaless state.
- The complete potato construction retains its existing automatic saya and tsuba hiding behavior.

## Modpack Compatibility and Balance

- Mikage now evaluates each participant independently using actual health and absorption loss instead of relying only on displayed armor attributes.
- Repeated ineffective hits gradually correct Mikage's outgoing damage against unusual modded defenses.
- After several confirmed low-damage hits, Mikage may briefly test a boundary/magic damage source to handle defenses that cancel ordinary damage entirely.
- Successful SlashBlade guards, intended defensive mechanics and invulnerability frames are excluded from adaptive-damage learning.
- Per-hit health-fraction limits and phase gates prevent the adaptive system or extremely powerful weapons from producing uncontrolled one-shots.
- Party size, offensive power, maximum health, armor and toughness can influence trial scaling, but configured safety ceilings remain in effect.
- Server pack authors can configure Mikage's health, damage, party scaling, Reminiscence multipliers, phase protection, adaptive damage and arena placement speed.
- Third-party material statistics and special effects remain the responsibility of Tetra/MMT and their integration providers. Blade Tetra reads those materials for SlashBlade visuals without redefining their gameplay values.
- Legacy material identifiers remain available as a hidden compatibility layer so existing modular blades do not lose their materials.

## Fixes and Polish

- Fixed the Boundary Crossing scroll displaying an untranslated localization key.
- Fixed Boundary Forging activating after the next normal attack instead of the Slash Art that actually hit.
- Fixed occasional loss of the delayed seal or final Boundary Flash damage during multi-hit Slash Arts.
- Fixed several target, party and phase-transition edge cases in the Mikage encounter.
- Replaced the earlier challenge map with the rebuilt original arena used by this release and removed the unused sign/easter egg.
- Updated the Boundary Gate Charm icon to depict a torii on a scroll.

## Compatibility

- Minecraft **1.20.1**
- Forge **47.x** (built with 47.4.0)
- Tetra **6.3.0–6.x**
- mutil **6.2.0–6.x**
- SlashBlade: Resharped **1.9.63–1.x**
- Player Animator remains optional.

Existing worlds and previously forged blades remain compatible. Back up important worlds before changing any mod list.
