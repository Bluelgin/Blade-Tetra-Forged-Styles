package dev.bladetetra.architecture;

import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CombatBalanceArchitectureTest {
    @Test void allSevenDefaultsAreNeutralAndHaveTranslations() throws Exception {
        var config = Files.readString(Path.of("src/main/java/dev/bladetetra/config/CombatBalanceConfig.java"));
        assertTrue(config.contains("defineInRange(key, 1.0D, 0.0D, 10.0D)"));
        assertFalse(config.contains("worldRestart()"));
        for (String locale : new String[]{"en_us", "zh_cn"}) {
            var json = JsonParser.parseString(Files.readString(Path.of(
                    "src/main/resources/assets/blade_tetra/lang/" + locale + ".json"))).getAsJsonObject();
            for (String key : new String[]{"global", "standard", "iaido", "dangaku", "rengeki", "slashArt", "summonedSword"}) {
                String translation = "config.blade_tetra.combatBalance." + key + "DamageMultiplier";
                assertTrue(json.has(translation), translation);
                assertTrue(json.has(translation + ".tooltip"), translation);
            }
        }
    }
    @Test void newValuesBelongToTheExistingServerSpec() throws Exception {
        var registration = Files.readString(Path.of("src/main/java/dev/bladetetra/BladeTetra.java"));
        var gameplay = Files.readString(Path.of("src/main/java/dev/bladetetra/config/GameplayConfig.java"));
        assertTrue(registration.contains("ModConfig.Type.SERVER"));
        assertTrue(gameplay.contains("CombatBalanceConfig.define(builder)"));
        var runtime = Files.readString(Path.of("src/main/java/dev/bladetetra/combat/CombatBalanceRuntime.java"));
        assertTrue(runtime.contains("shot.getShooter() instanceof Player"));
        assertTrue(runtime.contains("entity.getPersistentData().putString(STYLE"));
        assertTrue(runtime.contains("InheritedCombatDamage.isInherited"));
    }
}
