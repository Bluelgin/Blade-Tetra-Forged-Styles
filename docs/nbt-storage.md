# Modular blade NBT storage, version 1

The storage boundary now writes a compact, self-contained modular blade. Runtime
tags, SlashBlade capabilities, Tetra module APIs and share tags retain their
standard schemas. No world database, extra mod or compression library is needed.

## Measured result

The regression fixture contains the same 11 real awakened/fusion items used in
the earlier size survey, with the inventory Slot field removed. Both sides are
serialized with Minecraft's NbtIo. These are full item compounds, including
capability data, not just the inner tag or a textual SNBT character count.

| Sample | Before (bytes) | After (bytes) | Reduction |
| --- | ---: | ---: | ---: |
| Raikiri | 2,998 | 1,214 | 59.5% |
| Kyouka × Raikiri | 3,091 | 1,307 | 57.7% |
| Akatsuki | 3,205 | 1,291 | 59.7% |
| Senbonzakura | 3,075 | 1,280 | 58.4% |
| All 11 items | 34,545 | 14,649 | 57.6% |

Individual results range from 1,214 to 1,549 bytes, down from 2,998–3,351 bytes.
Separately gzip-compressing each item totals 13,113 bytes before and 8,605 bytes
after (34.4% less). This latter comparison is illustrative: an actual player file,
chunk or packet has its own compression boundaries and other contents. Neither
measurement establishes an FPS/TPS improvement or reduced runtime heap usage.

Run `gradlew test --tests dev.bladetetra.storage.BladeStackStorageTest` to regenerate
`build/reports/nbt-storage-comparison.csv` and an optimized NBT sample archive.
The original fixture and a frozen version-one archive are checked in, so future
encoder changes must still decode previously written items correctly.

## Layout and compatibility

Only saved items whose id is `blade_tetra:modular_slashblade` are transformed.
The `tag.blade_tetra:storage` compound includes an explicit format version.
Frozen field tables encode known field names with short, stable identifiers;
they are independent of registry registration order.

* The complete native blade state is stored once. If tag.bladeState and
  ForgeCaps.Parent differ, the tag snapshot's changed and missing fields are
  stored as a patch. Loading reconstructs both exact views, including nested
  unknown values. It never guesses which state is newer from counters.
* Known awakening/history keys use shorter names only on disk. Values and NBT
  types remain exact, including zero, false, cooldowns and UUIDs.
* Standard module/material pairs store their common path prefix once. Unusual,
  third-party or ambiguous/shared layouts remain in their original form.
* Other capability entries, custom styles, imprints, enchantments, appearance
  choices and unknown fields are retained. No item data is replaced by a
  reference into world storage.
* ItemStack.of expands the saved copy before Forge initializes capabilities or
  Tetra/SlashBlade inspects the tag. Old items already in the standard format
  continue through their original load path. A normal subsequent save converts
  them; no bulk rewrite of player worlds is performed.
* ItemStack.save still fills the supplied destination compound, even when a
  caller ignores its return value. The live stack and the source archive are
  not mutated by conversion.
* Unknown storage versions/reserved payloads pass through without overwrite.
  This is forward-data preservation, not support for playing a newer-format
  item in an older mod. Downgrading to a release without this decoder is not
  supported; keep a world backup when changing versions.

The existing typed combat/VFX packets are retained. Shared item tags are not
stripped: external storage implementations such as AE2 can depend on their
complete contents. An actual AE2 installation and a remote multiplayer session
were not part of this verification.

## Verification

Unit coverage checks exact round trips of all 11 real items, frozen version-one
files, legacy tag-only/capability-only/divergent layouts, unknown nested data,
ambiguous modules, source immutability and repeated conversion.

`gradlew runServer -PbladeStorageSmoke` enables an isolated Forge server check.
Its working directory is `build/blade-storage-smoke`; it needs an accepted EULA
and a free server port there. It loads all 11 fixtures and exercises the actual
Mixin hooks, capabilities, durability, copy(), share-tag read/write and a
SimpleContainer save/load, then shuts down. Success logs
`BLADE_STORAGE_SMOKE_PASS`. It never opens the user's test world.
