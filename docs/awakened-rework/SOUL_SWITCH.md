# Fusion soul selection and compact HUD

Fusion inscriptions select one complete combat soul at a time. The stable primary
soul still determines the item name and model/backplate palette. Single-soul
admission and legacy-mode combat are retained.

- Default input: the grave/tilde key, without Shift, configurable in Controls.
  Only a fresh key press in gameplay sends a request; menus and key repeats do not.
- The request includes the held slot, server-assigned blade UUID and previous soul.
  The server checks all three, life/spectator/menu state, awakening and cooldown.
  Only a server success changes selection and plays the switch pulse/particles/sound.
- Switching has a 20-tick cooldown on both player and blade. It does not alter Rank,
  native combo state, awakening, equipment appearance or existing skill cooldowns.
- For fusion skills, Kyouka requires Iaido and Senbonzakura requires Rengeki.
  Akatsuki and Raikiri retain their existing style range. Selection remains possible
  in an incompatible style; the selected seal is dimmed/locked and skills stay off.
- Active executions, Sakura SA, pending mirror attacks and scheduled/flying shard
  attacks temporarily block switching. This allows accepted attacks to finish.
  A successful switch releases water-mirror armor and cancels Raikiri field sessions and thunder armor.
  Existing lifecycle handlers continue to handle death, logout and dimension changes.
- Retired automatic fusion procs stay disabled in V2; two full skill sets never
  run simultaneously. Akatsuki's optional intrinsic-spirit bridge follows selection.

The HUD uses two fixed soul seals, a small selection marker and optional key hint.
There is no energy bar or permanent label. Single-soul blades show one seal.
`soulSwitchHud` client settings configure visibility, key hint, reduced animation,
16–32px icon size (22px default), and horizontal/vertical offsets. Default placement
is above the center of the survival HUD, above Contract Blade's normal resonance bar.
Both HUDs retain their own meaning and can be moved independently.

Editable artwork is in `art/soul-switch/`. Export with Node and `sharp` 0.34.5:

```text
node tools/art/export_soul_switch.cjs
node tools/art/export_soul_switch.cjs --check
node tools/art/export_soul_switch.cjs --preview
```

The exported textures are 64px; GUI size is independent of texture resolution.
The preview shows enlarged artwork, 22px seals and examples of two-soul states.

Validation: `gradlew test build` covers selection fallback and wire-field validation.
The existing `runServer -PsakuraCombatSmoke` also runs `SoulSwitchSmoke`, checking all
six pairs through the real server request path, saved selection, cooldown/stale/wrong
blade rejection, style/controller admission, stable primary art, unchanged native
Rank/combo state, and broken-blade exclusion. This is not graphical-client or complete
Contract Blade integration QA. Network protocol is `awakened2-soul-switch-1`; install
the same build on both client and server.
