package dev.bladetetra.combat;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ForgedSlashArtSpecTest {
    @Test
    void versionedSnapshotRoundTripsWithoutTetraModuleState() {
        ForgedSlashArtSpec spec = new ForgedSlashArtSpec(
                "sa_core/diamond",
                ForgedSlashArtPlan.Technique.PIERCING,
                ForgedSlashArtPlan.Technique.CIRCLE_SLASH,
                ForgedSlashArtPlan.Modifier.ECHO);

        ForgedSlashArtSpec restored = ForgedSlashArtSpec.fromTag(spec.toTag());

        assertEquals(spec, restored);
        assertEquals(spec.key(), restored.key());
    }

    @Test
    void unknownSnapshotVersionIsRejected() {
        ForgedSlashArtSpec spec = new ForgedSlashArtSpec(
                "sa_core/iron",
                ForgedSlashArtPlan.Technique.JUDGEMENT_CUT,
                ForgedSlashArtPlan.Technique.SAKURA_END,
                ForgedSlashArtPlan.Modifier.BALANCED);
        CompoundTag tag = spec.toTag();
        tag.putInt("version", 99);

        assertNull(ForgedSlashArtSpec.fromTag(tag));
    }
}
