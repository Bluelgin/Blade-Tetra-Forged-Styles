# Mikage Challenge Update

## New endgame challenge

- Added the reusable Boundary Gate Charm. Holding it opens a temporary torii;
  every player who crosses the same gate joins the same challenge.
- Added the Thousand-Gate Mirror Realm, built from the authored Mikage Garden
  map. Player game modes are preserved and the arena is protected from block
  edits, fluids, pistons, fire placement and explosion damage.
- Added Gatekeeper Mikage, a player-shaped three-phase boss with party scaling,
  guard and counter windows, sword-ghost teleport attacks and Boundary Flash.
- Added the original battle theme *The Final Trial*. It fades in when Mikage
  formally enters combat, loops without the source track's trailing silence,
  and fades out on victory or departure. It can be disabled or adjusted in the
  client config and replaced by resource packs through
  `blade_tetra:music.mikage_battle`.
- Added a particle-free geometric arena seal when combat begins: animated
  vermilion floor inscriptions, four spectral torii anchors, height-fading
  mirror membranes and proximity ripples. The effect is client-only, batched
  into one geometry submission per frame, and configurable for accessibility
  and shader-heavy modpacks.
- Replaced Mikage's vanilla bar with an optional torii-shaped T0 boss HUD. It
  includes one continuous three-stage health bar, 2/3 and 1/3 phase markers,
  delayed damage feedback, phase names, Reminiscence styling and synchronized
  signature-technique progress. Other bosses retain their own HUDs.
- Awakened Akatsuki receives additional story dialogue without changing the
  encounter's power or rewards.
- Expanded Mikage's voice-ready script with signature-technique callouts,
  successful-guard recognition, three defeat lines and unique Reminiscence
  dialogue. All subtitles now share one timed queue, keeping future voice clips
  from overlapping or drifting away from their matching line.
- Added safe defeat and return handling. A defeated participant returns alone;
  the remaining party can continue, and interrupted sessions recover players
  from the challenge dimension on their next login.

## Rewards and graduation forging

- First clear: Secret Forging Scroll: Boundary Crossing, Sword Ghost Remnant,
  and the wearable Broken Oni Mask.
- Repeat clears grant one Sword Ghost Remnant to every successful participant.
- Wear the Broken Oni Mask and sneak while opening the Boundary Gate Charm to
  enter the harder Reminiscence encounter. Clearing it awakens a cosmetic echo
  on the mask.
- The scroll is a native Tetra schematic provider. Open it directly above a
  Tetra workbench to reveal Boundary Forging.
- Boundary Forging consumes only one Sword Ghost Remnant and requires a tier-7
  Tetra forge hammer. It is applied as a blade improvement, preserving the
  modular blade's materials, modules, enchantments, kill count and legacy.
- The completed weapon is named Sacred Blade: Boundary Crossing, or Yokai
  Blade: Akatsuki [Beyond the Gate] when the forged blade already carries the
  awakened Akatsuki legacy. Both variants have equal strength.

## Boundary Mark

- Standard: complete a four-hit native sequence.
- Iaido: land a fully prepared precision draw.
- Rengeki: land six uninterrupted hits.
- Dangaku: land the complete heavy cleave.
- Completing the active style condition forms a Boundary Mark. The next
  successful Slash Art consumes it, manifests a spectral torii and performs a
  delayed Boundary Flash. The effect uses controlled bonus damage and has a
  per-blade cooldown.

## Pack-author controls

The server config exposes Mikage's base health and damage, multiplayer health
and damage scaling, Reminiscence multipliers, soft single-hit compression and
arena placement speed.
