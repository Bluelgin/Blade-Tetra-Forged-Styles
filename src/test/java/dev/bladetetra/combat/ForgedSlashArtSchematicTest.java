package dev.bladetetra.combat;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class ForgedSlashArtSchematicTest {
    @Test
    void BothSlotsEnforceTheSameNativeHammerTierWithoutChangingTheirCost() throws Exception {
        for (String slot : new String[]{"primary", "secondary"}) {
            for (ForgedSlashArtPlan.Technique technique : ForgedSlashArtPlan.Technique.values()) {
                String tier = switch (technique) {
                    case DRIVE_VERTICAL, DRIVE_HORIZONTAL, WAVE_EDGE -> "iron";
                    case CIRCLE_SLASH, PIERCING -> "diamond";
                    default -> "netherite";
                };
                var root = JsonParser.parseString(Files.readString(Path.of(
                        "src/main/resources/data/tetra/schematics/slashblade/forged_"
                                + slot + "_" + technique.id() + ".json"))).getAsJsonObject();
                var outcome = root.getAsJsonArray("outcomes").get(0).getAsJsonObject();
                assertEquals("minecraft:" + tier,
                        outcome.getAsJsonObject("requiredTools").get("hammer_dig").getAsString());
                assertEquals("slashblade:proudsoul_ingot", outcome.getAsJsonObject("material")
                        .getAsJsonArray("items").get(0).getAsString());
                assertEquals("sa_" + slot + "/" + technique.id(), outcome.get("moduleVariant").getAsString());
            }
        }
    }
}
