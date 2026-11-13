package dev.bladetetra.client;

import dev.bladetetra.combat.ProgrammaticFusionPlan;
import net.minecraft.Util;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.Locale;
import java.util.function.Predicate;

/** Uses addon art names where available and Blade Tetra's vocabulary otherwise. */
public final class ProgrammaticFusionNameGrammar {
    private static final String PREFIX = "name.blade_tetra.fusion.";

    public static Component name(ProgrammaticFusionPlan plan) {
        String release = termKey(plan.releaseAbility(), "entry", plan.release().entry(),
                ProgrammaticFusionNameGrammar::hasSourceName);
        String response = termKey(plan.responseAbility(), "response", plan.response().response(),
                ProgrammaticFusionNameGrammar::hasSourceName);
        return Component.translatable(PREFIX + "format",
                Component.translatable(release), Component.translatable(response));
    }

    static String termKey(ResourceLocation ability, String role, Enum<?> semantic,
            Predicate<String> hasTranslation) {
        if (ability != null && !"slashblade".equals(ability.getNamespace())) {
            String key = Util.makeDescriptionId("slash_art", ability);
            if (hasTranslation.test(key)) {
                return key;
            }
        }
        return PREFIX + role + "." + semantic.name().toLowerCase(Locale.ROOT);
    }

    private static boolean hasSourceName(String key) {
        return I18n.exists(key) && !I18n.get(key).isBlank();
    }

    private ProgrammaticFusionNameGrammar() {
    }
}
