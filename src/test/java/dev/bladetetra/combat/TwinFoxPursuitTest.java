package dev.bladetetra.combat;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TwinFoxPursuitTest {
    @Test void ordinaryTargetsRequireARealFlank() {
        Vec3 first = new Vec3(0, 0, 1);
        assertFalse(LegacyFusionHandler.formsPincer(first,
                new Vec3(0.8, 0, 0.6), 0.6F));
        assertFalse(LegacyFusionHandler.formsPincer(first,
                new Vec3(1, 0, 0), 0.6F));
        assertTrue(LegacyFusionHandler.formsPincer(first,
                new Vec3(0, 0, -1), 0.6F));
    }

    @Test void largeBossesUseTheRelaxedEightyDegreeThreshold() {
        Vec3 first = new Vec3(0, 0, 1);
        Vec3 eightyFiveDegrees = new Vec3(Math.sin(Math.toRadians(85)), 0,
                Math.cos(Math.toRadians(85)));
        assertFalse(LegacyFusionHandler.formsPincer(first, eightyFiveDegrees, 1.0F));
        assertTrue(LegacyFusionHandler.formsPincer(first, eightyFiveDegrees, 3.0F));
    }

    @Test void missingLegacyDirectionNeverTriggers() {
        assertFalse(LegacyFusionHandler.formsPincer(Vec3.ZERO,
                new Vec3(0, 0, -1), 3.0F));
    }
}
