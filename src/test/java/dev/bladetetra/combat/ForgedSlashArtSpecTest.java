package dev.bladetetra.combat;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ForgedSlashArtSpecTest {
    @Test
    void versionedSnapshotRoundTripsWithoutTetraModuleState() {
        ForgedSlashArtSpec spec = new ForgedSlashArtSpec(
                "sa_core/diamond",
                ForgedSlashArtPlan.Technique.PIERCING,
                ForgedSlashArtPlan.Technique.CIRCLE_SLASH);

        ForgedSlashArtSpec restored = ForgedSlashArtSpec.fromTag(spec.toTag());

        assertEquals(spec, restored);
        assertEquals(spec.key(), restored.key());
    }

    @Test
    void unknownSnapshotVersionIsRejected() {
        ForgedSlashArtSpec spec = new ForgedSlashArtSpec(
                "sa_core/iron",
                ForgedSlashArtPlan.Technique.JUDGEMENT_CUT,
                ForgedSlashArtPlan.Technique.SAKURA_END);
        CompoundTag tag = spec.toTag();
        tag.putInt("version", 99);

        assertNull(ForgedSlashArtSpec.fromTag(tag));
    }

    @Test
    void oldEchoInscriptionKeepsItsTechniquesWithoutRequiringAModifier() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("version", 1);
        tag.putString("core", "sa_core/iron");
        tag.putString("primary", "judgement_cut");
        tag.putString("secondary", "circle_slash");
        tag.putString("modifier", "echo");
        ForgedSlashArtSpec migrated = ForgedSlashArtSpec.fromTag(tag);
        assertEquals(ForgedSlashArtPlan.Technique.JUDGEMENT_CUT, migrated.primary());
        assertEquals(ForgedSlashArtPlan.Technique.CIRCLE_SLASH, migrated.secondary());
        assertEquals(2, migrated.toTag().getInt("version"));
        assertFalse(migrated.toTag().contains("modifier"));
        tag.remove("modifier");
        assertEquals(migrated, ForgedSlashArtSpec.fromTag(tag));
    }
}
