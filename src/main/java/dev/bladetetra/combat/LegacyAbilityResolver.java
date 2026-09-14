package dev.bladetetra.combat;

import mods.flammpfeil.slashblade.registry.SlashArtsRegistry;
import mods.flammpfeil.slashblade.registry.SpecialEffectsRegistry;
import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

/** Resolves data-driven legacy abilities against the live SlashBlade registries. */
public final class LegacyAbilityResolver {
    public static ResourceLocation registeredSlashArt(ResourceLocation id) {
        return isSlashArtRegistered(id) ? id : null;
    }

    public static List<ResourceLocation> registeredSpecialEffects(
            Collection<ResourceLocation> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<ResourceLocation> registered = new LinkedHashSet<>();
        for (ResourceLocation id : ids) {
            if (isSpecialEffectRegistered(id)) {
                registered.add(id);
            }
        }
        return List.copyOf(registered);
    }

    public static boolean isSlashArtRegistered(ResourceLocation id) {
        return id != null && SlashArtsRegistry.REGISTRY.get().containsKey(id);
    }

    public static boolean isSpecialEffectRegistered(ResourceLocation id) {
        return id != null && SpecialEffectsRegistry.REGISTRY.get().containsKey(id);
    }

    private LegacyAbilityResolver() {
    }
}
