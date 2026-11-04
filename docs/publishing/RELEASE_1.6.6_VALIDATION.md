# 1.6.6 release validation (isolated audit)

This validation branch is based on the unmerged, test-only #62 aggregate. **Do not merge
the aggregate into main or publish any JAR based solely on these checks.** The repository
version still reads 1.6.5 until an explicit release candidate is prepared.

## Automated release-audit jobs

The PR validation matrix runs the same combined source snapshot against Tetra
6.3.0, 6.9.0, 6.13.0 and 6.17.0, with the matching MUtil versions in
`tools/ci/run_suite.py`. Three **separate** isolated native Forge jobs per
Tetra version check:

- `custom_style`: real Tetra scroll/workbench, custom combo and normal SlashBlade.
- `forging`: 16 source ingots × 13 module variants, hammer requirements and fusion.
- `storage`: real ItemStack load/save/copy, share-tag and container NBT for 11 fixtures.

The dedicated `ae2_key` job loads actual AE2 + GuideME in a Forge server,
converts each of the 11 blade fixtures through AE2's `AEItemKey`
`toTag/fromTag/toStack`, and checks capabilities, shared tags and compact saves.
**This is not an ME network or storage-cell round trip.** Any failed job is an
unresolved finding; a green job proves only its stated assertions.

## Important outstanding tests not covered by this audit

- Launch a real **1.6.5** client, back up its world, install a final 1.6.6
  candidate, and test previously owned blades, custom scrolls and dormant items.
- Real multiplayer: dedicated Forge server + two separate clients; verify
  shared tags, GUI crafting, combat synchronization and reconnect.
- **Actual AE2 ME storage**: create an ME network with a drive and storage cell,
  insert a saved/awakened/named blade through an ME terminal, restart server,
  withdraw the same item and compare its SA, SE, NBT, damage, appearance,
  source snapshots and awakened state. Repeat with a distinct same-item variant.
  Also test inserting directly via an import bus and withdrawing through export.
- Other storage mods and unknown third-party add-ons cannot be marked compatible
  from an AE2 key test.
- Full client visual and controls sign-off (book pages, named ingots, sword models,
  awakening rituals), and gameplay/balance in ordinary non-test worlds.
- Downgrade to 1.6.5 is **not** supported for newly compacted blade NBT. Restore
  a pre-upgrade backup instead; do not advertise arbitrary downgrade support.
- Benchmark FPS, TPS, heap, and save/sync bytes separately. Storage sample-size
  reduction is not proof of runtime performance gains.

No production code was modified solely to pass a failing test.
