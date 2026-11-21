# Blade Seal: explicit awakened-content omission (PR #76)

## Product boundary

An awakened sword may be sealed as an **ordinary, unawakened version of its construction**, with the owner's informed consent. No awakened-only skills, soul forms, rituals, persistent counters, presentation, or generated awaken-state assets are ported to `bt_export_runtime` by this feature. The original ItemStack is never modified.

This is *not* a general permission to downgrade a sword or silently discard any unsupported feature. All other source-only SA/SE, forged abilities, boundary forging, traditions, fish/banners, art licensing and resource checks retain existing strict denial behavior.

## Server-authoritative path

1. Capture and fingerprint the original player-held sword; never mutate it.
2. Detect awakened-soul inscription, unlocked/progress state or selected-soul identity.
3. Make a separate ItemStack copy. Remove the awakened inscription module and its own material/improvement tags; remove only recognized awakening-scoped NBT, leaving other modules, materials, visual provenance, names, recipes and custom combos intact.
4. Akatsuki's built-in Power I grant has no individual provenance marker. An awakened Akatsuki sword with Power I therefore loses that **ambiguous** Power I on the projected copy. Other enchantments stay, and higher-level Power is retained. This is part of the consent warning; we must not claim per-enchantment origin fidelity.
5. Re-synchronize Tetra construction -> SlashBlade state on the same projected copy: base damage, durability, root combo, enchantments, soul contract/default bewitched state, inherited SA/SE. Re-capture all attributes from that recomputed copy, not from the awakened weapon's live state.
6. Run the **existing, strict** full preflight on the normalized blade and normalized snapshot. Distinct unsupported non-awakening abilities still block export.
7. Send preview with a dedicated `omitsAwakening` boolean. The confirmation UI is disabled until the user explicitly acknowledges the presented omission notice, and the server refuses `Confirm(acknowledgeAwakeningLoss=false)` for this draft even if the client is modified.
8. On success, return only the server-authorized normalized snapshot and projection to the client. The resulting independent JAR has no awakening runtime or soul state payload.

The single-use source fingerprint and operator/creative authorization remain enforced. An old packet or an altered original cannot bypass server verification. The authorized normalized image remains immutable across retry for a resource or image-file packaging error.

## Explicit non-goals and caveats

- This change does **not** implement or bundle awakening abilities, even when the source weapon still has unlocked state.
- Other source-only skills and special traditions remain blockers. Awakening acknowledgement must not waive their failure.
- Third-party OBJ/PNG license restrictions are unchanged.
- The source blade keeps all achievements, unlocks, NBT, model, SA and visual state.
- A previously granted Akatsuki Power I may be indistinguishable from manually applied Power I; this projection omits it by design. In future, introduce explicit origin metadata in the authoring mod if exact separation becomes possible.
- The mod author must still test source/client and clean-client visuals; compile and source-free server acceptance alone are insufficient.

## Smoke test coverage

`BladeExportPackageSmoke.verifyAwakeningOmission` tests every single-soul inscription and a paired inscription, original-item immutability, unacknowledged denial, acknowledged single-use approval, ordinary enchantment preservation, Akatsuki's Power I removal, retained construction and refusal of another non-portable tradition. The real `export_client` path uses an awakened source + named mixed fittings + PNG and checks that the GUI requires acknowledgement before sealing.

A build/CI test is evidence only for the exact checked commit. Preserve Draft status until clean-client image parity and full integration checks complete.
