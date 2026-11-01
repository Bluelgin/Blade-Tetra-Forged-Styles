# Fusion soul selection and compact HUD

Fusion inscriptions select one active-art soul at a time. Custom Kyouka/Senbonzakura
blades can additionally route ordinary actions by family (see below). The stable primary
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
  A successful switch releases water-mirror armor, except on a custom linked Kyouka/Senbonzakura blade,
  and cancels Raikiri field sessions and thunder armor.
  Existing lifecycle handlers continue to handle death, logout and dimension changes.
- Retired fusion procs stay disabled in V2. Each custom ordinary action belongs to
  at most one soul. Akatsuki's optional intrinsic-spirit bridge follows selection.

The HUD uses two fixed soul seals, a small selection marker and optional key hint.
There is no energy bar or permanent label. Single-soul blades show one seal.
`soulSwitchHud` client settings configure visibility, key hint, reduced animation,
16–32px icon size (22px default), auto-hide, and horizontal/vertical offsets. Default placement
is above the center of the survival HUD, above Contract Blade's normal resonance bar.
Both HUDs retain their own meaning and can be moved independently.

## Custom action linkage

Eligibility uses the **installed, playable** `CustomStyleSpec`, not the player's
recording history or the blade-length module. Its existing version-1 NBT and
exchange codes are unchanged. `SoulActionAffinity` explicitly classifies
`blade_tetra:iaido_draw/follow/return/finish` as Kyouka and
`slashblade:combo_b1` through `combo_b7` as Senbonzakura. Shared movement,
generic A combos, unknown addon actions and Slash Arts grant neither family.
An invalid/unplayable installed sequence grants neither eligibility.

`SoulSkillState` separates `allowsStyle` (installed qualification), `enabled`
(selected active art), `available` (persistent controller binding), and
`acceptsAction` (ordinary source attribution). On a Kyouka/Senbonzakura fusion,
both qualified, awakened souls stay available independently of active selection.
Other fusion pairs retain selected-soul exclusivity. Custom single souls and
selected Kyouka/Senbonzakura in other pairs also gain installed-family eligibility.

For example, Iaido follow -> B1 -> B2 -> Iaido draw routes ordinary effects to
Kyouka -> Senbonzakura -> Senbonzakura -> Kyouka. Delayed attacks retain their
captured action: water impacts and shard volleys finish across family changes,
while fracture/back-shard state persists. Sakura dispersed reduction and Rank
anti-credit follow Sakura damage sources; they do not clamp legitimate Kyouka
Rank gains. The existing broad Rank ceiling remains for native Rengeki and during
Sakura SA. Tilde selects the active art; it does not reassign ordinary actions.

Ordinary actions do not reveal or pulse HUD seals. Skill activity follows the
server-confirmed notifications below; the underline still marks the selected
active soul when its seal is visible. No shader, resource counter or animation
system is introduced.

## Skill-driven HUD visibility

With `soulSwitchHud.enabled=true` and `autoHide=true` (both defaults), equipping,
walking, aiming, ordinary committed actions and ordinary misses leave seals hidden.
Only controller acceptance reveals the relevant soul: Akatsuki execution start;
Kyouka armor activation or a reserved water/recall attack; Sakura actual fracture,
committed stance change, reserved concerto or committed SA; Raikiri armor activation,
planted sword, executed trail strike or successful discharge. Failed/cancelled
admission and fracture cooldown denial emit no activity. A skill can execute
without a hit; this HUD confirms the accepted skill, not guaranteed damage.

Each soul has its own 60-tick hold followed by an 8-tick fade. Armor, executions,
Sakura SA/reassembly, and accepted mirror/shard work keep the relevant seal visible.
When sustained work ends, its 60-tick hold starts then. Static dispersed back shards
never keep the HUD awake. Switching souls previews both seals for 40 ticks plus fade,
without clearing existing skill activity. The key hint appears during the preview.
`autoHide=false` restores always-visible seals; `enabled=false` hides the HUD entirely.

`SoulActivityService` collects accepted cues per held weapon and merges them at
player tick END. Only changed sustained masks or new triggers are sent to that player;
there is no idle heartbeat, nearby-player broadcast, or client combo inference.
`SoulActivityPacket` is clientbound message 28 (four-bit trigger and sustained masks),
bound to server-assigned blade UUID, held slot and dimension. Both single and fusion
blades are supported. Changing equipment, dimension, death or disconnect clears the
client timeline; server bindings also clear on logout, death and dimension changes.
The timeline stores presentation time only, independently of gameplay controllers.


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
Contract Blade integration QA. The same smoke also runs `CustomSoulSmoke` for real
native mixed actions, delayed source attribution, state coexistence and active-art
selection. Network protocol is `awakened2-soul-activity-1`; install
the same build on both client and server.

HUD validation adds timeline regressions for idle/dual-soul/hold/fade/sustained/preview
and reset behavior, packet identity/mask round trips, `SoulActivitySmoke` in the
existing Sakura server smoke, and actual native armor cue checks in the Raikiri smoke.
Graphical fade/pulse appearance still requires in-game acceptance.
