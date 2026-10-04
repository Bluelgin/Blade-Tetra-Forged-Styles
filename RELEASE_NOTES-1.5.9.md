# Blade Tetra: Forged Styles 1.5.9

Unbroken circles. Reforged fittings.

## Styles and combat

- Dangaku's right-click route is sweep → return sweep → Circle Slash. Pause briefly after the second swing to branch into a two-hit heavy slash. Circles cover nearby enemies on all sides; heavy slashes retain a short defensive window.
- Hold left attack on the ground to enter sustained Dangaku circles after approximately 0.3 seconds. Each circle lasts 0.6 seconds and flows directly into the next. Aim freely with a 25% movement slowdown. Each circle shares 40% of an ordinary circle's damage budget, without adding stun or knockback. Taking damage does not cancel the action, but still damages and knocks back the player. Costs and projectile reflection remain native.
- Release finishes the current circle before recovery. Leaving the ground prevents further repeats; right use and weapon changes exit the stance. Re-equipping a blade does not resume unattended spinning.
- Rengeki uses native ground B combos and aerial attacks, without automatic pursuit or kill teleports. Sprinting blade trails remain; each sprint hit uses half the base damage of an ordinary B-series slash and scales with the weapon rather than a fixed numerical damage cap.
- Fixed repeated aerial right-click attacks for Rengeki and Dangaku. Aerial Dangaku can use its sweep, return, circle or paused heavy route with native-style slow falling. Landing does not add an automatic attack.
- All styles retain native forward sneak-dashes, backward sneak-uppercuts and follow-up jumps, aerial cleaves and held-right Slash Arts. Dangaku no longer adds a separate charged-right sweep or formation-break marker.

## Fittings and pack configuration

- Tetra attack speed improves ground-dodge recovery, special summoned-sword preparation, normal SA charge and Super SA preparation across all styles, rather than becoming extra damage. Default benefits scale from 1.6 to 3.2 attack speed, with respective time-reduction caps of 20%, 40%, approximately 33% and 30%. Ordinary right-click attack progression remains native.
- Socketed kashira, hilt wraps and applicable blade coatings reuse loaded Tetra sword materials, requirements, integrity costs, attributes and effects rather than separate material-stat tables.
- Supports MMT material-coating improvements and applicable dragon-blood coatings. Recipes still depend on the provider, installed integrations and unlock requirements. Only one coating may be installed at a time.
- Added seven server-owned damage multipliers: global, Standard, Iaido, Dangaku, Rengeki, Slash Arts and non-SA summoned swords. All default to **1.0**. Configured is an optional in-game editor, not a required dependency.
- Settings live in the world's `serverconfig/blade-tetra-server.toml`. They affect supported player-owned modular-blade damage, not material attributes, other blades, Mikage or pets.

## Dead Thought removal and compatibility

- Blades injected with a Dead Thought Soul Seal can use **Remove Dead Thought** in the workbench blade slot for one vanilla amethyst shard. Materials, improvements, enchantments and challenge progress remain. The seal is not refunded; uninjected blades do not show the operation.
- Existing ability reconciliation restores underlying SA/SE sources. Fitting-granted Dead Thought remains. Fixed empty ability records interfering with original SA preservation and restoration; older blades without a saved SA are reconciled from their current sources.
- Improved modular-blade recognition for selected RevelationFix Tetra hit effects, VVAddon hit/kill effects and MMT's Freeze/Mana Siphon effect entry points. Providers retain their probabilities, damage and activation conditions. Dedicated right-click abilities are not transplanted; full compatibility with unknown or custom provider versions is not guaranteed.

## Art and performance

- Corrected modular-blade inventory presentation and guard extrusion while preserving the existing frame, hand and world presentation.
- Remade ordinary, light and protective guards with real apertures, bevels and clearer metallic detail. Material palettes and third-party named blades' unique models remain intact.
- Refined collars, habaki, leather and fabric wraps, faceted sockets, subtle coating sheen, saya details and soul inscriptions, with distinct dragonsteel blade motifs.
- Updated awakened Akatsuki with oxblood bindings, black lacquer and golden moon inlay, coordinated metal fittings and red soul glow. Removed the floating charm while preserving actual enchantments.
- Optimized full-inventory texture caching and guard geometry, reducing repeated texture generation when opening inventories. Resource reloads still release generated textures correctly.

## Divine Domain, Mikage and text

- Existing Divine Domain difficulties, waves and rewards remain. In Hundred Ghosts and higher trials, Mikage provides one divine-fire rescue per player when health reaches 30% or below. The ring follows and heals the player while holding approaching ritual enemies at bay for six seconds. It cannot revive players or protect against ranged attacks and ground hazards.
- The first successful clear grants **Beyond the Domain**, closing the current chapter and leaving a hint of the story ahead.
- Integrated Mikage's updated model and single drawn sword with a more prominent red blade and improved poses, avoiding duplicate sword displays.
- Updated Karmic Mirror states, the Dead Thought Soul Seal and Sword Ghost Remnant with pixel-art textures. Visitor dialogue omits portraits by default and retains an integration interface; default portrait assets are no longer bundled.
- Added Chinese/English support for easter-room books, improved journal pagination and item text, and supplied missing modular-blade holosphere titles. Selected fitting-effect values and activation conditions can be inspected at the workbench.

## Installation

- Minecraft **1.20.1**, Forge **47**, Tetra **6.3.0–6.x**, Mutil **6.2.0–6.x**, SlashBlade: Resharped **1.9.63–1.x**.
- MMT, Configured and other integrations are optional and are not bundled.
- Update both clients and the server to **1.5.9** for multiplayer. Back up your world before upgrading; existing modular blades do not need to be recrafted.
