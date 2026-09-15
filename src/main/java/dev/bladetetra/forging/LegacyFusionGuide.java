package dev.bladetetra.forging;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** Read-only player-facing view of the authored legacy-fusion catalog. */
public final class LegacyFusionGuide {
    public record Entry(String id, Component sayaName, Component hiltName,
            Component abilityName) {}

    public static List<Entry> entries() {
        return entries(LegacyFusionCatalog.values(), LegacyFusionGuide::namedBlade);
    }

    static List<Entry> entries(List<LegacyFusionDefinition> definitions,
            Function<String, Component> nameResolver) {
        return definitions.stream()
                .map(definition -> new Entry(
                        definition.id(),
                        nameResolver.apply(definition.sayaId()),
                        nameResolver.apply(definition.hiltId()),
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
        List<Component> abilities = new ArrayList<>();
        if (definition.slashArt() != null) {
            abilities.add(ability("slash_art.", definition.slashArt()));
        }
        for (ResourceLocation effect : definition.specialEffects()) {
            abilities.add(ability("se.", effect));
        }
        if (abilities.size() == 1) return abilities.get(0);
        MutableComponent combined = Component.empty();
        for (int index = 0; index < abilities.size(); index++) {
            if (index > 0) combined.append(Component.literal(" + "));
            combined.append(abilities.get(index));
        }
        return combined;
    }

    private static Component ability(String prefix, ResourceLocation ability) {
        return Component.translatable(prefix + ability.getNamespace() + "." + ability.getPath());
    }

    private LegacyFusionGuide() {
    }
}
