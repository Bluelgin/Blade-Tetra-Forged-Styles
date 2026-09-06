# Blade Tetra: Forged Styles 1.4.0

Version 1.4.0 completes the story surrounding Gatekeeper Mikage and adds a peaceful postgame route for players who have overcome her final Reminiscence.

## Highlights

- Added Mikage's complete postgame visitor route.
- Added a visual-novel-style dialogue interface with character portrait and expression changes.
- Added the secret room beyond the gate, containing developer notes, community acknowledgements, displays and completion gifts.
- Added a crafting recipe for the Boundary Gate Charm.
- Improved multiplayer challenge membership and re-entry handling.
- Fixed several scroll, schematic, resource-pack reminder and map-entity issues.

## After the Final Reminiscence

- An Oni Mask awakened by completing Reminiscence can now open a peaceful visitor gate.
- Sneak-use the Boundary Gate Charm while wearing the awakened mask to return without beginning another boss battle.
- Mikage now appears as a peaceful visitor NPC after the final trial.
- Added a dedicated dialogue interface inspired by visual novels, including a full character portrait and contextual expressions.
- Players can ask Mikage about Akatsuki, Mikage's past, the Reminiscence battle and the awakened mask.
- Dialogue was rewritten to use clearer, more natural language while preserving Mikage's restrained tone.
- The visitor encounter can be left normally through the return torii.

## The Place Beyond the Gate

- Added a secret postgame room accessible through Mikage's visitor dialogue.
- This room is an epilogue and developer space rather than another combat arena; no boss or NPC spawns inside it.
- Added six written displays covering the mod's origin, the Potato Blade, Akatsuki's empty seat, community contributions and a final message to the player.
- Each display now includes a fixed-direction floating English summary while preserving the original lectern and full written book.
- Written books are signed by `_Cazs_`.
- Preserved the room's Tetra workbench, tool rack, SlashBlade stands, displayed Potato Blade, player heads and decorative details.
- Added a completion gift barrel containing all seven smithing clues, seven saya patterns and several decorative materials.
- Added a dedicated exit marker and return torii so players can safely return to their original world.
- The bundled room format now preserves blocks, block entities and independent entities. One-time migration markers prevent displays from duplicating when an existing room is upgraded.

## Boundary Gate and Challenge Flow

- Added the missing crafting recipe for the Boundary Gate Charm.
- Strengthened multiplayer challenge ownership and re-entry rules.
- Players who were defeated or removed from an active encounter can no longer bypass challenge separation by teleporting back to a surviving participant.
- Returning to the challenge space after defeat now creates or follows the appropriate separate challenge state instead of silently rejoining the previous battle.
- Improved recovery for players who disconnect or leave while a challenge is active.
- Visitor, normal and Reminiscence gates now keep their participants and destinations isolated from one another.

## Scrolls, Schematics and Interface Fixes

- Corrected the Boundary Crossing scroll's integration with Tetra's native schematic system.
- Fixed the workbench showing unavailable scroll knowledge as a phantom entry when the player did not possess the corresponding boss reward.
- Fixed an incorrect or untranslated schematic name appearing in the Tetra workbench.
- The Boundary Crossing scroll continues to open with its poetic description and correctly grants access to its intended forging knowledge.
- Fixed the optional Forged Echoes reminder buttons failing even though the same commands worked when typed manually.
- Resource-pack reminder actions now open the download page or resource-pack folder correctly and can be dismissed normally.

## Map and Display Fixes

- Restored SlashBlade stand entities that were missing from the first exported version of the secret room.
- Preserved the Potato Blade and all item data stored on display entities.
- Preserved Tetra rack inventories, lectern books, signs, containers and player-head data.
- Added separate one-time restoration markers for blade stands and floating text displays, preventing duplicate entities on repeated visits.

## Compatibility

- Minecraft **1.20.1**
- Forge **47.x** (built with 47.4.0)
- Tetra **6.3.0–6.x**
- mutil **6.2.0–6.x**
- SlashBlade: Resharped **1.9.63–1.x**
- Player Animator remains optional.

Existing worlds and previously forged blades remain compatible. As always, back up important worlds before changing a modpack.
