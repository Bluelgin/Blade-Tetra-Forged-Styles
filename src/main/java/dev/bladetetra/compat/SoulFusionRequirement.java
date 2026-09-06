package dev.bladetetra.compat;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import dev.bladetetra.easteregg.SoulLegacyState;
import se.mickelus.tetra.module.schematic.CraftingContext;
import se.mickelus.tetra.module.schematic.requirement.CraftingRequirement;
import se.mickelus.tetra.module.schematic.requirement.CraftingRequirementDeserializer;

import java.util.Locale;

/** Makes fusion schematics visible only for legacies learned by the target blade. */
public final class SoulFusionRequirement implements CraftingRequirement {
    public static final String TYPE = "blade_tetra:soul_fusion";

    private final SoulLegacyState.Legacy host;
    private final SoulLegacyState.Legacy partner;

    private SoulFusionRequirement(
            SoulLegacyState.Legacy host,
            SoulLegacyState.Legacy partner) {
        this.host = host;
        this.partner = partner;
    }

    public static void register() {
        CraftingRequirementDeserializer.registerSupplier(TYPE,
                SoulFusionRequirement::fromJson);
    }

    @Override
    public boolean test(CraftingContext context) {
        return context != null
                && SoulLegacyState.hasSingleInscription(context.targetStack, host)
                && SoulLegacyState.isUnlocked(context.targetStack, host)
                && SoulLegacyState.isUnlocked(context.targetStack, partner);
    }

    private static SoulFusionRequirement fromJson(JsonObject json) {
        return new SoulFusionRequirement(
                readLegacy(json, "host"), readLegacy(json, "partner"));
    }

    private static SoulLegacyState.Legacy readLegacy(JsonObject json, String key) {
        if (!json.has(key) || !json.get(key).isJsonPrimitive()) {
            throw new JsonParseException("Missing soul fusion legacy: " + key);
        }
        try {
            SoulLegacyState.Legacy legacy = SoulLegacyState.Legacy.valueOf(
                    json.get(key).getAsString().toUpperCase(Locale.ROOT));
            if (legacy == SoulLegacyState.Legacy.NONE) throw new IllegalArgumentException();
            return legacy;
        } catch (IllegalArgumentException exception) {
            throw new JsonParseException("Unknown soul fusion legacy in " + key, exception);
        }
    }
}
