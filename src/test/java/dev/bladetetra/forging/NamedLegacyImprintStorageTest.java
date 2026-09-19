package dev.bladetetra.forging;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
    void unrelatedNonReplacingCraftPreservesInstalledImprintIdentity() {
        CompoundTag tag = new CompoundTag();
        tag.putString("slashblade/saya", "slashblade/legacy_saya");
        tag.putString("slashblade/legacy_saya_material", "legacy_saya/imprinted");
        NamedLegacyImprintStorage.putSource(tag, "saya", "yakumoblade/fox");

        assertFalse(NamedLegacyImprintStorage.applyCraftResultTag(tag,
                "slashblade/saya", "thirdparty:saya_polish"));
        assertEquals("yakumoblade/fox", NamedLegacyImprintStorage.sourceId(tag, "saya"));
    }

    @Test
    void namedSchematicCannotAttachIdentityIfItsModuleOutcomeDidNotStick() {
        CompoundTag tag = new CompoundTag();
        tag.putString("slashblade/tsuba", "slashblade/basic_tsuba");
        tag.putString("slashblade/basic_tsuba_material", "basic_tsuba/iron");
        NamedLegacyImprintStorage.putSource(tag, "tsuba", "removed_addon/old");

        assertTrue(NamedLegacyImprintStorage.applyCraftResultTag(tag,
                "slashblade/tsuba", "slashblade/legacy_auto/sjap/yamato/tsuba"));
        assertNull(NamedLegacyImprintStorage.sourceId(tag, "tsuba"));
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

    @Test
    void snapshotRoundTripsCompleteKindWithoutCatalog() {
        CompoundTag tag = new CompoundTag();
        tag.putString("slashblade/saya", "slashblade/legacy_saya");
        tag.putString("slashblade/legacy_saya_material", "legacy_saya/imprinted");
        NamedLegacyImprintStorage.putSource(tag, "saya", "testaddon/blade");

        LegacyImprintKind kind = new LegacyImprintKind(
                "testaddon/blade",
                new ResourceLocation("testaddon", "blade"),
                new ResourceLocation("testaddon", "model/blade.obj"),
                new ResourceLocation("testaddon", "textures/blade.png"),
                "testaddon:material",
                LegacyCalibrationProfile.DEFAULT,
                12.5D,
                321,
                new ResourceLocation("slashblade", "piercing"),
                List.of(new ResourceLocation("testaddon", "effect")));

        assertTrue(NamedLegacyImprintStorage.putSnapshot(tag, "saya", kind));
        LegacyImprintKind restored = NamedLegacyImprintStorage.snapshot(tag, "saya");
        assertNotNull(restored);
        assertEquals(kind.id(), restored.id());
        assertEquals(kind.name(), restored.name());
        assertEquals(kind.model(), restored.model());
        assertEquals(kind.texture(), restored.texture());
        assertEquals(kind.material(), restored.material());
        assertEquals(kind.defaultProfile(), restored.defaultProfile());
        assertEquals(kind.baseAttack(), restored.baseAttack());
        assertEquals(kind.maxDamage(), restored.maxDamage());
        assertEquals(kind.slashArt(), restored.slashArt());
        assertEquals(kind.specialEffects(), restored.specialEffects());
    }

    @Test
    void changingOrClearingSourceInvalidatesSnapshot() {
        CompoundTag tag = new CompoundTag();
        tag.putString("slashblade/tsuba", "slashblade/legacy_tsuba");
        tag.putString("slashblade/legacy_tsuba_material", "legacy_tsuba/imprinted");
        NamedLegacyImprintStorage.putSource(tag, "tsuba", "testaddon/first");
        LegacyImprintKind first = new LegacyImprintKind(
                "testaddon/first",
                new ResourceLocation("testaddon", "first"),
                new ResourceLocation("testaddon", "model/first.obj"),
                new ResourceLocation("testaddon", "textures/first.png"),
                "testaddon:material",
                LegacyCalibrationProfile.DEFAULT,
                8.0D, 200, null, List.of());
        assertTrue(NamedLegacyImprintStorage.putSnapshot(tag, "tsuba", first));
        assertNotNull(NamedLegacyImprintStorage.snapshot(tag, "tsuba"));

        assertTrue(NamedLegacyImprintStorage.putSource(tag, "tsuba", "testaddon/second"));
        assertNull(NamedLegacyImprintStorage.snapshot(tag, "tsuba"));
        assertTrue(NamedLegacyImprintStorage.clearSource(tag, "tsuba"));
        assertNull(NamedLegacyImprintStorage.sourceId(tag, "tsuba"));
        assertNull(NamedLegacyImprintStorage.snapshot(tag, "tsuba"));
    }
}
