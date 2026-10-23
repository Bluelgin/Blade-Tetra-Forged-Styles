# 千门剑谱 — future community exchange contract (v1)

This is a stable **offline** import/export boundary; no network calls, accounts,
upload APIs, community moderation, remote execution or code download are implemented.

## Data flow

- Recorded ordinary slash combo → `CustomStyleSpec` → native Tetra `ScrollData`.
- Completed scroll in either hand → `/blade_tetra_style export` → clickable/copyable code.
- Another player uses `/blade_tetra_style import <code>` → server decodes
  data, checks every move with `ComboCompatibilityResolver.playable`, then
  grants the same genuine reusable Tetra schematic scroll as manual recording.
- The future creator/editor UI and web community API should depend on
  `CustomStyleExchange.export`, `inspect` (preview only), and
  `importValidated` (server authoritative), **never directly mint from inspect**.

## Version 1 wire format

Prefix: `BTSTYLE1:`

The remainder is unpadded Base64URL of UTF-8 JSON. Example JSON before encoding:

```json
{
  "format": "blade_tetra:custom_style",
  "version": 1,
  "name": "飞燕",
  "actions": ["slashblade:combo_a1", "slashblade:combo_b1"]
}
```

The format and integer version make the contract independent from the
implementation-specific binary Tetra scroll payload (`BTCS1:`).
The JSON may be extended with optional metadata in future versions, without
allowing client payloads to alter damage, reach, permission or attack effects.
Do not publish personally identifying author data by default.

Limits: <=4 KiB encoded string, <=2 KiB decoded JSON, <=6 actions,
<=48 Java chars for the name, <=128 chars per action ID. Unknown/unregistered
or unauthorized ordinary actions reject at **import time**. Invalid and
future-version formats fail closed with explicit statuses. A share code is
untrusted editable data, not a signature or provenance assertion.

## In-game draft management (placeholder for Astra editor)

`/blade_tetra_style show` — read-only ordered list of drafted actions.
`/blade_tetra_style rename <name>` — name the current recording.
`/blade_tetra_style undo` and `clear` — edit a draft without new hit logic.
`/blade_tetra_style finish` — finalize as native Tetra scroll.
The physical Style Recorder remains the main interaction: right-click records
only the most recently performed valid move, and sneak-use finishes.

Future UI should display `StyleRecordingSession.snapshot` and ask the server
to mutate that session, never directly rewrite sword NBT or scroll contents.

Current scope does not upload/download, search community styles, display
published likes/rankings, or allow import of privileged Slash Arts.
