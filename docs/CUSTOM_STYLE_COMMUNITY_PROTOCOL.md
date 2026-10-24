# Qianmen offline share code (compact binary v1)

Share codes are unpadded Base64URL with **no textual prefix**, JSON, account,
network request or online lookup. Import restores the author's exact name and
ordered actions, including repeats. The unreleased `BTSTYLE1:` JSON format is
intentionally unsupported. Stored sword/scroll NBT is unchanged: this is only
the portable exchange encoding, not a save migration.

## Envelope

Decode Base64URL to bytes:

| Field | Encoding |
| --- | --- |
| Header | One byte: high nibble = version 1, bit 0 = raw DEFLATE payload, bit 1 = dependency trailer present; bits 2/3 must be zero |
| Payload | Raw bytes or raw DEFLATE (RFC 1951, no zlib/gzip wrapper) |
| Checksum | Two bytes, big-endian CRC-16/CCITT-FALSE over header and transmitted payload; polynomial 0x1021, initial 0xffff, no reflection or final XOR |

The checksum detects accidental damage, not authorship or permissions. Check
it before decompressing or interpreting the version. Export compares raw and
compressed payloads and uses compression only if strictly smaller. Import
rejects padding, whitespace, non-URL-alphabet characters, noncanonical Base64
padding bits, trailing bytes and unknown flags. Maximum input: 4096 characters;
maximum expanded payload: 2048 bytes. Truncated streams, concatenated streams
and oversized decompression are rejected.

## Payload

Strings are strict UTF-8, prefixed by their byte length as a minimal unsigned
LEB128 integer (at most two bytes). No Unicode normalization or name trimming.

1. Name string: nonblank, at most 48 Java UTF-16 code units, no ISO controls;
   maximum UTF-8 length 144 bytes.
2. Action count: one byte, 1 through 6.
3. External dictionary count: one byte, 0 through action count.
4. That many unique full `namespace:path` strings, each at most 128 bytes.
   IDs must parse canonically as Minecraft ResourceLocations. Dictionary order
   is first occurrence; repeated actions reuse one entry. Unknown/unsafe IDs
   are allowed in parse-only previews, never automatically permitted.
5. One token byte per action, in authored order. Values 0..127 reference the
   fixed dictionary below. Values 128..133 reference external entry `token-128`.
   Unassigned built-in IDs and out-of-range external indices are rejected.
   Every external entry must be referenced.
6. Only when header bit 1 is set: one-byte dependency count (1..12), then that
   many unique mod ID strings matching `[a-z][a-z0-9_]{1,63}`. No display names
   or code-supplied version policies are encoded. No remaining payload bytes.

## Fixed action dictionary

These are wire IDs, never registry ordinals. Never reorder/remove/reuse an
entry. New entries may be appended up to ID 127; readers missing those entries
must reject them rather than substitute another action. Incompatible layouts
require a new version nibble.

| IDs | Namespace | Paths in exact ID order |
| --- | --- | --- |
| 0..5 | slashblade | combo_a1, combo_a2, combo_a3, combo_a4, combo_a4_ex, combo_a5ex |
| 6..12 | slashblade | combo_b1, combo_b2, combo_b3, combo_b4, combo_b5, combo_b6, combo_b7 |
| 13..16 | slashblade | combo_c, upperslash, rapid_slash, rapid_slash_quick |
| 17..20 | blade_tetra | iaido_draw, iaido_follow, iaido_return, iaido_finish |
| 21..23 | blade_tetra | dangaku_sweep, dangaku_cleave, dangaku_charged_sweep |

`export` emits the portable name/actions without dependency hints.
`exportWithDependencies` (GUI export) adds only trusted external provider mod IDs
from installed adapters, deduplicated. Built-in SlashBlade/BladeTetra providers
are implied and never add length. Provider labels/installed versions come from
local trusted adapters. Unknown namespaces are never assumed to be mod IDs.
Hints remain untrusted declarations; missing declared mods block import but
cannot grant permissions, identify a trusted provider, or download anything.

## Import and existing interactions

`inspect` is registry-independent parsing for previews. It never mints a scroll.
`importValidated` and `CustomStyleDependencyResolver.inspect` retain server-side
registry, ordinary-action permission, trusted provider and version checks.
Finalization/forging/runtime permission checks are unchanged. SA and arbitrary
unregistered actions remain forbidden, regardless of checksum validity.

The GUI requests a server preview, shows the original name/ordered actions and
provider diagnostics, and asks before overwriting a draft. A failed import
leaves the existing draft intact. Export reads a server-approved draft or real
Tetra scroll in either hand; the complete code remains selectable/copyable.
The commands `/blade_tetra_style import <code>` and `export` continue to work;
command import grants the existing native Tetra scroll, while GUI import loads
an editable draft. No combat, reach, timing, schematic or persistence changes.

Trusted third-party adapters can register an exact action/provider mapping via
`CustomStyleDependencyResolver.registerProvider` even when that mod is absent.
Only an audited `ComboCompatibilityResolver.registerOrdinaryAction` permission
allows replay. Provider mapping alone does not. Trusted Maven version ranges
remain local adapter policies. Diagnostics continue to distinguish missing mod,
incompatible version, unregistered action and unsafe action.

The game network channel is bumped from 14 to 15 so old GUI clients cannot
connect to this incompatible export implementation. Both sides need the same
build. Scroll/NBT schema versions are independent of exchange/network versions.

## Length and validation

With a four-character Chinese name, measured uncompressed lengths including
header/checksum are 27 characters for two built-in actions, 28 for three, and
32 for six. Long names and external full IDs cost more; compression is selected
only if useful. This does not promise a fixed length for arbitrary modded IDs.

`CustomStyleExchangeTest` pins all 24 wire IDs and checks name/order/repeats,
external hints, both encoding modes, CRC corruption, malformed UTF-8/lengths,
future versions, dictionary references and bounded decompression. Existing
`runServer -PcustomStyleSmoke` verifies real draft import/finalization and failed
import protection. Final in-game clipboard/visual acceptance remains user-owned.

Local validation: build/JUnit passed (422 tests, 0 failures/errors, 1 existing skip); `CUSTOM_EXCHANGE_SMOKE_PASS`, `CUSTOM_STYLE_SMOKE_PASS` and 32-PNG resource validation passed. No game GUI session was launched for this protocol change.
