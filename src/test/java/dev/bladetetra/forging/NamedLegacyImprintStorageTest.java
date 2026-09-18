package dev.bladetetra.forging;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NamedLegacyImprintStorageTest {
    @Test
    void migratesPerBladeVariantsToGenericVariantsAndKeepsSoftIds() {
        CompoundTag tag = new CompoundTag();
        tag.putString("slashblade/saya", "slashblade/legacy_saya");
        tag.putString("slashblade/legacy_saya_material", "legacy_saya/sjap/yamato");
        tag.putString("slashblade/tsuba", "slashblade/legacy_tsuba");
        tag.putString("slashblade/legacy_tsuba_material", "legacy_tsuba/removed_addon/old_blade");

        assertTrue(NamedLegacyImprintStorage.migrateStackTag(tag));
        assertEquals("legacy_saya/imprinted",
                tag.getString("slashblade/legacy_saya_material"));
        assertEquals("legacy_tsuba/imprinted",
                tag.getString("slashblade/legacy_tsuba_material"));
        assertEquals("sjap/yamato", NamedLegacyImprintStorage.sourceId(tag, "saya"));
        assertEquals("removed_addon/old_blade",
                NamedLegacyImprintStorage.sourceId(tag, "tsuba"));
        assertFalse(NamedLegacyImprintStorage.migrateStackTag(tag));
    }

    @Test
    void craftingIdentityComesFromSchematicAndOrdinaryReplacementClearsIt() {
        CompoundTag tag = new CompoundTag();
        // Tetra applies the schematic/module outcome first, then crafting effects run.
        tag.putString("slashblade/saya", "slashblade/legacy_saya");
        tag.putString("slashblade/legacy_saya_material", "legacy_saya/imprinted");
        assertTrue(NamedLegacyImprintStorage.applyCraftResultTag(tag,
                "slashblade/saya", "slashblade/legacy_auto/yakumoblade/fox/saya"));
        assertEquals("yakumoblade/fox", NamedLegacyImprintStorage.sourceId(tag, "saya"));

        tag.putString("slashblade/saya", "slashblade/basic_saya");
        tag.putString("slashblade/basic_saya_material", "basic_saya/oak");
        assertTrue(NamedLegacyImprintStorage.applyCraftResultTag(tag,
                "slashblade/saya", "slashblade/basic_saya"));
        assertNull(NamedLegacyImprintStorage.sourceId(tag, "saya"));
    }

    @Test
    void parsesOnlyMatchingNamedImprintSchematics() {
        assertEquals("slashblade_addon/sange",
                NamedLegacyImprintStorage.sourceFromSchematic(
                        "tetra:slashblade/legacy_auto/slashblade_addon/sange/tsuba", "tsuba"));
        assertNull(NamedLegacyImprintStorage.sourceFromSchematic(
                "slashblade/legacy_auto/slashblade_addon/sange/tsuba", "saya"));
        assertNull(NamedLegacyImprintStorage.sourceFromSchematic(
                "slashblade/basic_saya", "saya"));
        assertNull(NamedLegacyImprintStorage.sourceFromSchematic(null, "saya"));
    }

    @Test
    void staleIdentityIsRemovedWhenGenericModuleIsNoLongerInstalled() {
        CompoundTag tag = new CompoundTag();
        tag.putString("slashblade/saya", "slashblade/basic_saya");
        tag.putString("slashblade/basic_saya_material", "basic_saya/oak");
        NamedLegacyImprintStorage.putSource(tag, "saya", "removed_addon/ghost");

        assertTrue(NamedLegacyImprintStorage.migrateStackTag(tag));
        assertNull(NamedLegacyImprintStorage.sourceId(tag, "saya"));
        assertFalse(tag.contains(NamedLegacyImprintStorage.ROOT));
    }
}
