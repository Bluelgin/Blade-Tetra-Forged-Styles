package dev.bladetetra.combat;

import mods.flammpfeil.slashblade.SlashBlade;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import mods.flammpfeil.slashblade.registry.SlashArtsRegistry;
import mods.flammpfeil.slashblade.slasharts.SlashArts;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

/**
 * Maps a forged technique to SlashBlade's real SlashArt entry and owns the
 * corresponding native ComboState graph.
 *
 * <p>Forged Slash Arts no longer clone a single visual node and guess the rest
 * of the timeline. The source SlashArt selects its normal entry state and
 * SlashBlade itself progresses the native graph. Blade Tetra only observes graph
 * ownership so it can hand off A -> B without stealing intermediate states.</p>
 */
final class ForgedNativeComboFlow {
    static ResourceLocation entry(ForgedSlashArtPlan.Technique technique,
            SlashArts.ArtsType requestedType, LivingEntity user) {
        SlashArts art = nativeArt(technique);
        if (art == null) {
            return ComboStateRegistry.NONE.getId();
        }

        // The authored SA has one power budget for normal/super casts. Preserve
        // native failure/just semantics, but deliberately normalize Super to the
        // source art's ordinary graph so SlashArts' default
        // "Super -> Judgement Cut End" cannot leak into unrelated techniques.
        ResourceLocation combo = art.doArts(sourceType(requestedType), user);
        return combo == null ? ComboStateRegistry.NONE.getId() : combo;
    }

    static SlashArts.ArtsType sourceType(SlashArts.ArtsType requestedType) {
        if (requestedType == null) {
            return SlashArts.ArtsType.Fail;
        }
        return switch (requestedType) {
            case Fail -> SlashArts.ArtsType.Fail;
            case Jackpot -> SlashArts.ArtsType.Jackpot;
            case Success, Super -> SlashArts.ArtsType.Success;
        };
    }

    static boolean owns(ForgedSlashArtPlan.Technique technique,
            ResourceLocation combo) {
        if (combo == null || !SlashBlade.MODID.equals(combo.getNamespace())) {
            return false;
        }
        String path = combo.getPath();
        return switch (technique) {
            case JUDGEMENT_CUT -> path.startsWith("judgement_cut");
            case SAKURA_END -> path.startsWith("sakura_end");
            case VOID_SLASH -> path.startsWith("void_slash");
            case CIRCLE_SLASH -> path.startsWith("circle_slash");
            case DRIVE_VERTICAL -> path.startsWith("drive_vertical");
            case DRIVE_HORIZONTAL -> path.startsWith("drive_horizontal");
            case WAVE_EDGE -> path.startsWith("wave_edge")
                    || path.startsWith("drive_vertical_end");
            case PIERCING -> path.startsWith("piercing");
        };
    }

    static boolean isRecovery(ResourceLocation combo) {
        return ComboStateRegistry.NONE.getId().equals(combo)
                || ComboStateRegistry.STANDBY.getId().equals(combo);
    }

    private static SlashArts nativeArt(ForgedSlashArtPlan.Technique technique) {
        return switch (technique) {
            case JUDGEMENT_CUT -> SlashArtsRegistry.JUDGEMENT_CUT.get();
            case SAKURA_END -> SlashArtsRegistry.SAKURA_END.get();
            case VOID_SLASH -> SlashArtsRegistry.VOID_SLASH.get();
            case CIRCLE_SLASH -> SlashArtsRegistry.CIRCLE_SLASH.get();
            case DRIVE_VERTICAL -> SlashArtsRegistry.DRIVE_VERTICAL.get();
            case DRIVE_HORIZONTAL -> SlashArtsRegistry.DRIVE_HORIZONTAL.get();
            case WAVE_EDGE -> SlashArtsRegistry.WAVE_EDGE.get();
            case PIERCING -> SlashArtsRegistry.PIERCING.get();
        };
    }

    private ForgedNativeComboFlow() {
    }
}
