package dev.bladetetra.client;

import dev.bladetetra.combat.ProgrammaticFusionHandler;
import dev.bladetetra.combat.ProgrammaticFusionPlan;
import net.minecraft.Util;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Presents the original registered arts in casting order. */
public final class ProgrammaticFusionNameGrammar {
    public static Component name(ProgrammaticFusionPlan plan) {
        return Component.translatable("name.blade_tetra.fusion.format",
                term(plan.releaseAbility()), term(plan.responseAbility()));
    }

    private static Component term(ResourceLocation id) {
        if (!ProgrammaticFusionHandler.available(id)) {
            return Component.translatable("name.blade_tetra.fusion.unavailable");
        }
        String key = Util.makeDescriptionId("slash_art", id);
        return I18n.exists(key) ? Component.translatable(key) : Component.literal(id.toString());
    }

    private ProgrammaticFusionNameGrammar() {}
}
