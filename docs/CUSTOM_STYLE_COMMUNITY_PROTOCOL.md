# 千门剑谱 — offline community exchange contract (v1)

This is a stable **offline** import/export boundary; no network calls, accounts,
upload APIs, community moderation, remote execution or code download are implemented.

## Data flow

- Recorded ordinary slash combo → `CustomStyleSpec` → native Tetra `ScrollData`.
- Completed scroll in either hand → `/blade_tetra_style export` → clickable/copyable code.
- Another player uses `/blade_tetra_style import <code>` → server decodes
  data, checks every move with `ComboCompatibilityResolver.playable`, then
  grants the same genuine reusable Tetra schematic scroll as manual recording.
- The editor UI and any future web community API depend on
  `CustomStyleExchange.export`, `inspect` (preview only), and
  `importValidated` (server authoritative), **never directly mint from inspect**.
- GUI import previews a code on the server, then loads it into the player's
  editable draft only after explicit overwrite confirmation. It does not mint
  a scroll or modify a weapon. Existing command import still grants a scroll.
- GUI export reads either a server-validated saved draft or a genuine Tetra
  scroll in either hand and offers complete selectable text and clipboard copy.

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

## In-game draft management

`/blade_tetra_style show` — read-only ordered list of drafted actions.
`/blade_tetra_style rename <name>` — name the current recording.
`/blade_tetra_style undo` and `clear` — edit a draft without new hit logic.
`/blade_tetra_style finish` — finalize as native Tetra scroll.
The physical Style Recorder remains the main interaction: right-click records
only the most recently performed valid move, and sneak-use opens the editor.
`/blade_tetra_style edit` remains available.

The UI displays `StyleRecordingSession.snapshot` and asks the server
to mutate that session, never directly rewrite sword NBT or scroll contents.

Current scope does not upload/download, search community styles, display
published likes/rankings, or allow import of privileged Slash Arts.

## Optional v1 dependency hints

Old codes are unchanged. GUI export may append:

```json
"requirements": [{"modId":"slashblade","displayName":"SlashBlade: Resharped"}]
```

At most 12 entries; mod IDs match `[a-z][a-z0-9_]{1,63}`, display labels are
at most 96 characters without controls. The 2 KiB JSON limit still applies.
Export omits these optional hints if they would exceed that limit.
`CustomStyleExchange.export` remains the deterministic legacy export;
`exportWithDependencies` adds trusted installed-adapter provider hints.

Hints are editable, untrusted dependency declarations. They never whitelist
an action, supply a trusted provider name/version policy, download code, or
grant SA, damage, reach, timing or crafting permissions. Missing declared mod
IDs block import and remain visible in previews. Declared display names are
not used as authoritative action attribution. An unknown action namespace
does not imply a mod with that ID is missing.

Parsing (`CustomStyleExchange.inspect`) needs no installed combo registry;
server diagnostics (`CustomStyleDependencyResolver.inspect`) retain parsed
content even if dependencies are unavailable. Formal import revalidates each
action against the actual registry and ordinary-action allowlist. Finalizing
a draft and forging its native scroll also retain runtime permission checks.
Invalid UTF-8, fractional format versions, corrupt/oversized JSON and invalid
requirement types fail closed. Future format versions remain unsupported.

## Trusted provider adapters

Core explicitly maps `slashblade:*` to mod ID `slashblade` and
`blade_tetra:*` to `blade_tetra`. Third-party namespaces are never guessed.
An adapter may register an exact ID mapping even when the optional mod is
absent, allowing useful missing-mod diagnostics:

```java
CustomStyleDependencyResolver.registerProvider(
    new ResourceLocation("different_namespace", "ordinary_cut"),
    new CustomStyleDependencyResolver.Provider("actual_mod_id", "Actual Mod", ""));
// Only after auditing click/tick effects, prerequisites and free normal execution:
ComboCompatibilityResolver.registerOrdinaryAction(
    new ResourceLocation("different_namespace", "ordinary_cut"));
```

Provider mapping alone grants nothing. Conflicting mappings are rejected.
Only a real compatibility adapter may supply a Maven version range, such as
`[1.2,2)`; the default empty range makes no minimum-version claim.
Code-supplied version-like fields cannot become trusted policy.

Diagnostics distinguish `AVAILABLE`, `MISSING_MOD`, `INCOMPATIBLE_VERSION`,
`UNREGISTERED_ACTION`, and `UNSAFE_ACTION`; malformed/unsupported formats
retain the separate exchange format status. Unknown attribution is shown
explicitly, without invented names or download links.

No timing metadata is read from share codes. Unadapted external actions keep
their native transition gate and standard melee reach. See
[implementation, timing table and acceptance checklist](CUSTOM_STYLE_FINALIZATION.md).

GUI requests use the mod's existing server channel (protocol 14); the
portable share prefix/JSON version remain `BTSTYLE1:` / `1`. Both game sides
must run the same mod build, independently of portable share-code compatibility.
