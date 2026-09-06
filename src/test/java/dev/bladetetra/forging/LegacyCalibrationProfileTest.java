package dev.bladetetra.forging;

import dev.bladetetra.forging.LegacyCalibrationProfile.PartTransform;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LegacyCalibrationProfileTest {
    @Test void independentDimensionsAndBladeTweakRoundTrip() {
        var t = new PartTransform(1, .02F, -.01F, 4, 1.25F, .75F);
        var blade = new PartTransform(1, 0, 0, 0, 1.10F, .95F);
        var p = new LegacyCalibrationProfile(.77F, .025F, .90F, .1F,
                t, t, t, blade, false, 15);
        assertEquals(p, LegacyCalibrationProfile.read(p.write()));
        assertEquals(blade, p.withRevision(16).bladeTransform());
    }

    @Test void nanCannotReachRenderingAndBladeTweakHasNarrowBounds() {
        var evil = new PartTransform(Float.NaN, Float.POSITIVE_INFINITY, Float.NaN, Float.NaN,
                Float.NaN, Float.NaN);
        assertEquals(LegacyCalibrationProfile.IDENTITY, evil.normalized());
        var p = new LegacyCalibrationProfile(Float.NaN, .02F, .9F, .1F,
                evil, evil, evil, new PartTransform(2, 1, 1, 20, 2, .1F), false, 0).normalized();
        assertEquals(LegacyCalibrationProfile.DEFAULT.tsubaCenter(), p.tsubaCenter());
        assertEquals(1.2F, p.bladeTransform().length());
        assertEquals(.85F, p.bladeTransform().width());
        assertEquals(0, p.bladeTransform().offsetX());
    }
    @Test
    void profileRoundTripsThroughCompactNbt() {
        LegacyCalibrationProfile input = new LegacyCalibrationProfile(.71F, .04F, .88F, .12F,
                new PartTransform(1.1F, .03F, -.04F, 3F),
                new PartTransform(.9F, -.02F, .01F, -2F),
                new PartTransform(1.02F, 0F, .02F, 1F), true, 42L);
        assertEquals(input, LegacyCalibrationProfile.read(input.write()));
    }

    @Test
    void hostileValuesAreBounded() {
        LegacyCalibrationProfile value = new LegacyCalibrationProfile(-4F, -8F, 20F, 9F,
                new PartTransform(20F, 7F, -7F, 80F), null, null, false, -3L).normalized();
        assertEquals(.50F, value.tsubaCenter(), .0001F);
        assertEquals(.012F, value.tsubaRadius(), .0001F);
        assertEquals(1F, value.tsukaCenter(), .0001F);
        assertEquals(.30F, value.tsukaRadius(), .0001F);
        assertEquals(1.35F, value.tsubaTransform().scale(), .0001F);
        assertEquals(.30F, value.tsubaTransform().offsetX(), .0001F);
        assertEquals(-.30F, value.tsubaTransform().offsetY(), .0001F);
        assertEquals(30F, value.tsubaTransform().rotation(), .0001F);
        assertEquals(0L, value.revision());
    }

    @Test
    void oldOrMissingProfilesUseStableDefaults() {
        assertEquals(LegacyCalibrationProfile.DEFAULT, LegacyCalibrationProfile.read(new CompoundTag()));
    }

    @Test
    void oldGlobalTransformMigratesToEveryPart() {
        CompoundTag old = new CompoundTag();
        old.putFloat("scale", 1.1F);
        old.putFloat("offset_x", .03F);
        old.putFloat("offset_y", -.04F);
        LegacyCalibrationProfile value = LegacyCalibrationProfile.read(old);
        assertEquals(value.tsubaTransform(), value.tsukaTransform());
        assertEquals(value.tsukaTransform(), value.sayaTransform());
    }

    @Test
    void boundaryPrototypeProfilesMigrateToSelectionRegions() {
        CompoundTag old = new CompoundTag();
        old.putFloat("blade_end", .75F);
        old.putFloat("guard_end", .80F);
        LegacyCalibrationProfile value = LegacyCalibrationProfile.read(old);
        assertEquals(.775F, value.tsubaCenter(), .0001F);
        assertEquals(.025F, value.tsubaRadius(), .0001F);
        assertEquals(.90F, value.tsukaCenter(), .0001F);
        assertEquals(.10F, value.tsukaRadius(), .0001F);
    }
}
