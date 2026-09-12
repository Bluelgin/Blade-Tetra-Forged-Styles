package dev.bladetetra.forging;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyFusionTest {
    @Test
    void orderedFoxPartsResolveToDifferentFusions() {
        LegacyImprintKind black = kind("fox_black");
        LegacyImprintKind white = kind("fox_white");

        assertEquals(LegacyFusion.BLACK_SAYA_WHITE_HILT,
                LegacyFusion.installed(new NamedLegacyParts(black, white, white)));
        assertEquals(LegacyFusion.WHITE_SAYA_BLACK_HILT,
                LegacyFusion.installed(new NamedLegacyParts(white, black, black)));
    }

    @Test
    void sameSourceAndIncompletePartsDoNotResolve() {
        LegacyImprintKind black = kind("fox_black");
        LegacyImprintKind white = kind("fox_white");

        assertNull(LegacyFusion.installed(new NamedLegacyParts(black, black, black)));
        assertNull(LegacyFusion.installed(new NamedLegacyParts(black, null, white)));
    }

    @Test
    void sameSourceFittingsResolveOrthodoxLegacyWithoutBecomingFusion() {
        LegacyImprintKind black = kind("fox_black");
        LegacyImprintKind white = kind("fox_white");

        assertEquals(black, new NamedLegacyParts(black, black, black).completeSet());
        assertTrue(black.supportsOrthodoxInheritance());
        assertEquals("slashblade:piercing", black.slashArt().toString());

        assertEquals(white, new NamedLegacyParts(white, white, white).completeSet());
        assertFalse(white.supportsOrthodoxInheritance());
    }

    @Test
    void yashaSayaAndKikoukuHiltResolveOnlyInAuthoredOrder() {
        LegacyImprintKind yasha = named("slashblade/yasha", "yasha");
        LegacyImprintKind kikouku = named("slashblade/yasha_true", "yasha_true");

        assertEquals(LegacyFusion.YASHA_SAYA_KIKOUKU_HILT,
                LegacyFusion.installed(new NamedLegacyParts(yasha, kikouku, kikouku)));
        assertNull(LegacyFusion.installed(
                new NamedLegacyParts(kikouku, yasha, yasha)));
    }

    @Test
    void sealedAgitoSayaAndOrotiagitoHiltResolveToRustRelease() {
        LegacyImprintKind sealed = named(
                "slashblade/orotiagito_sealed", "orotiagito_sealed");
        LegacyImprintKind released = named(
                "slashblade/orotiagito", "orotiagito");

        assertEquals(LegacyFusion.SEALED_AGITO_SAYA_OROTIAGITO_HILT,
                LegacyFusion.installed(
                        new NamedLegacyParts(sealed, released, released)));
        assertNull(LegacyFusion.installed(
                new NamedLegacyParts(released, sealed, sealed)));
        assertEquals(LegacyFusion.Ability.SPECIAL_EFFECT,
                LegacyFusion.SEALED_AGITO_SAYA_OROTIAGITO_HILT.ability());
        assertEquals("blade_tetra:snake_molt",
                LegacyFusion.SEALED_AGITO_SAYA_OROTIAGITO_HILT
                        .abilityId().toString());
    }

    @Test
    void muramasaSayaAndDoutanukiHiltResolveOnlyToDouwari() {
        LegacyImprintKind muramasa = named("slashblade/muramasa", "muramasa");
        LegacyImprintKind doutanuki = named("slashblade/doutanuki", "doutanuki");

        assertEquals(LegacyFusion.MURAMASA_SAYA_DOUTANUKI_HILT,
                LegacyFusion.installed(
                        new NamedLegacyParts(muramasa, doutanuki, doutanuki)));
        assertNull(LegacyFusion.installed(
                new NamedLegacyParts(doutanuki, muramasa, muramasa)));
        assertEquals(LegacyFusion.Ability.SLASH_ART,
                LegacyFusion.MURAMASA_SAYA_DOUTANUKI_HILT.ability());
        assertEquals("blade_tetra:douwari",
                LegacyFusion.MURAMASA_SAYA_DOUTANUKI_HILT
                        .abilityId().toString());
    }

    private static LegacyImprintKind kind(String id) {
        return id.equals("fox_black")
                ? LegacyImprintKind.BLACK_FOX
                : LegacyImprintKind.WHITE_FOX;
    }

    private static LegacyImprintKind named(String id, String path) {
        return new LegacyImprintKind(id,
                new ResourceLocation("slashblade", path),
                new ResourceLocation("slashblade", "model/test.obj"),
                new ResourceLocation("slashblade", "model/test.png"),
                "slashblade:proudsoul_ingot", LegacyCalibrationProfile.DEFAULT,
                6.0D, 70, null, List.of());
    }
}
