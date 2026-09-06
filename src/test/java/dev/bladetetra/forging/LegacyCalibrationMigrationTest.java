package dev.bladetetra.forging;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyCalibrationMigrationTest {
    @Test
    void removesEveryObsoletePerBladeProfileWithoutTouchingOtherData() {
        CompoundTag stack = new CompoundTag();
        CompoundTag obsolete = new CompoundTag();
        obsolete.put("slashblade:fox_black", LegacyCalibrationProfile.DEFAULT.write());
        obsolete.put("some_addon:retired_blade", LegacyCalibrationProfile.DEFAULT.write());
        stack.put(LegacyCalibration.STACK_ROOT, obsolete);
        stack.putString("slashblade/saya", "slashblade/legacy_saya");
        stack.putString("slashblade/legacy_saya_material",
                "legacy_saya/slashblade:fox_black");

        assertTrue(LegacyCalibration.migrateStackTag(stack));
        assertFalse(stack.contains(LegacyCalibration.STACK_ROOT));
        assertTrue(stack.contains("slashblade/saya"));
        assertTrue(stack.contains("slashblade/legacy_saya_material"));
    }

    @Test
    void migrationIsSafeAndIdempotentForNewAndEmptyStacks() {
        CompoundTag current = new CompoundTag();
        current.putBoolean("blade_tetra_akatsuki_unlocked", true);

        assertFalse(LegacyCalibration.migrateStackTag(current));
        assertFalse(LegacyCalibration.migrateStackTag(current));
        assertTrue(current.getBoolean("blade_tetra_akatsuki_unlocked"));
        assertFalse(LegacyCalibration.migrateStackTag(null));
    }
}
