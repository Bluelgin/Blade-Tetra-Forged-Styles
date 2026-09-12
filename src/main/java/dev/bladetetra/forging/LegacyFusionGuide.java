package dev.bladetetra.forging;

import net.minecraft.network.chat.Component;

import java.util.List;

/** Read-only player-facing view of the authored legacy-fusion catalog. */
public final class LegacyFusionGuide {
    public record Entry(String id, Component sayaName, Component hiltName,
            Component abilityName) {}

    public static List<Entry> entries() {
        return LegacyFusionCatalog.values().stream()
                .map(definition -> new Entry(
                        definition.id(),
                        namedBlade(definition.sayaId()),
                        namedBlade(definition.hiltId()),
                        ability(definition)))
                .toList();
    }

    private static Component namedBlade(String id) {
        LegacyImprintKind kind = NamedLegacyCatalog.get(id);
        return kind == null
                ? Component.literal(id)
                : Component.translatable(kind.translationKey());
    }

    private static Component ability(LegacyFusionDefinition definition) {
        String prefix = definition.abilityType()
                == LegacyFusionDefinition.AbilityType.SLASH_ART
                ? "slash_art."
                : "se.";
        return Component.translatable(prefix
                + definition.ability().getNamespace()
                + "."
                + definition.ability().getPath());
    }

    private LegacyFusionGuide() {
    }
}
