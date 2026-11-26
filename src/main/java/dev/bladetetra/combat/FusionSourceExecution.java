package dev.bladetetra.combat;

import dev.bladetetra.compat.fusion.RecastingFusionCompatibility;
import mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import mods.flammpfeil.slashblade.registry.SlashArtsRegistry;
import mods.flammpfeil.slashblade.registry.combo.ComboState;
import mods.flammpfeil.slashblade.slasharts.SlashArts;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.Supplier;

/** Exposes the source object during synchronous callbacks without changing the serialized key. */
final class FusionSourceExecution {
    private static final ResourceLocation FORGED_ART = ResourceLocation.parse("blade_tetra:forged_slash_art");
    private static final ThreadLocal<Source> SOURCE = new ThreadLocal<>();

    static SlashArts registeredArt(ResourceLocation id) {
        if (id == null || id.equals(SlashArtsRegistry.NONE.getId())
                || id.equals(SealedFusionSources.DISPATCHER)
                || id.equals(SealedFusionSources.SOURCE_DISPATCHER)
                || id.equals(FORGED_ART)
                || !SlashArtsRegistry.REGISTRY.get().containsKey(id)) return null;
        return SlashArtsRegistry.REGISTRY.get().getValue(id);
    }

    static Selection select(ResourceLocation id, SlashArts.ArtsType type,
            ServerPlayer player, ISlashBladeState state) {
        SlashArts art = registeredArt(id);
        if (art == null || !RecastingFusionCompatibility.canSelect(id)
                || !RecastingFusionCompatibility.ready(id, art, player)) return null;
        return withSource(state, art, () -> {
            ResourceLocation combo = art.doArts(type, player);
            SlashArts.ArtsType effective = type;
            // Resharped gives every art Judgement Cut End by default. That entry
            // belongs to Judgement Cut itself, not to the other inherited selectors.
            if (type == SlashArts.ArtsType.Super
                    && ComboStateRegistry.JUDGEMENT_CUT_END.getId().equals(combo)
                    && !SlashArtsRegistry.JUDGEMENT_CUT.getId().equals(id)) {
                effective = SlashArts.ArtsType.Success;
                combo = art.doArts(effective, player);
            }
            return neutral(combo) || registeredCombo(combo) == null
                    ? null : new Selection(id, art, combo, effective);
        });
    }

    static ComboState registeredCombo(ResourceLocation id) {
        return id != null && ComboStateRegistry.REGISTRY.get().containsKey(id)
                ? ComboStateRegistry.REGISTRY.get().getValue(id) : null;
    }

    static boolean neutral(ResourceLocation id) {
        return ComboStateRegistry.NONE.getId().equals(id)
                || ComboStateRegistry.STANDBY.getId().equals(id);
    }

    static SlashArts sourceArt(ISlashBladeState state) {
        Source source = SOURCE.get();
        return source != null && source.state() == state ? source.art() : null;
    }

    static <T> T withSource(ISlashBladeState state, SlashArts art, Supplier<T> action) {
        Source previous = SOURCE.get();
        SOURCE.set(new Source(state, art));
        try {
            return action.get();
        } finally {
            if (previous == null) SOURCE.remove();
            else SOURCE.set(previous);
        }
    }

    record Selection(ResourceLocation id, SlashArts art, ResourceLocation combo,
            SlashArts.ArtsType type) {}
    private record Source(ISlashBladeState state, SlashArts art) {}
    private FusionSourceExecution() {}
}
