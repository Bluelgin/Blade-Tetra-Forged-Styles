package dev.bladetetra.combat;

import mods.flammpfeil.slashblade.SlashBlade;
import mods.flammpfeil.slashblade.registry.SlashArtsRegistry;
import mods.flammpfeil.slashblade.registry.combo.ComboState;
import mods.flammpfeil.slashblade.slasharts.SlashArts;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

/**
 * Maps authored techniques onto SlashBlade's real ComboState graph and selects
 * the splice points used to turn two source arts into one continuous native
 * sequence.
 *
 * <p>The primary technique enters through its real SlashArt selector. Once its
 * recognizable attack callback has completed, the runtime jumps directly into
 * the secondary technique's signature ComboState rather than releasing a second
 * complete SlashArt. Native callbacks remain untouched, so SlashBlade owns
 * movement, effects, hit rules and damage end to end.</p>
 */
final class ForgedNativeComboFlow {
    static ResourceLocation entry(ForgedSlashArtPlan.Technique technique,
            SlashArts.ArtsType requestedType, LivingEntity user) {
        SlashArts art = nativeArt(technique);
        if (art == null) {
            return SlashBlade.prefix("none");
        }
        ResourceLocation combo = art.doArts(sourceType(requestedType), user);
        return combo == null ? SlashBlade.prefix("none") : combo;
    }

    static ResourceLocation secondaryEntry(
            ForgedSlashArtPlan.Technique technique,
            SlashArts.ArtsType requestedType,
            LivingEntity user) {
        return signatureEntry(
                technique, sourceType(requestedType), user.onGround());
    }

    static ResourceLocation signatureEntry(
            ForgedSlashArtPlan.Technique technique,
            SlashArts.ArtsType sourceType,
            boolean onGround) {
        return SlashBlade.prefix(signaturePath(technique, sourceType, onGround));
    }

    static String signaturePath(
            ForgedSlashArtPlan.Technique technique,
            SlashArts.ArtsType sourceType,
            boolean onGround) {
        return switch (technique) {
            case JUDGEMENT_CUT -> sourceType == SlashArts.ArtsType.Jackpot
                    ? "judgement_cut_slash_just"
                    : onGround
                            ? "judgement_cut_slash"
                            : "judgement_cut_slash_air";
            case SAKURA_END -> onGround
                    ? "sakura_end_right"
                    : "sakura_end_right_air";
            case VOID_SLASH -> "void_slash";
            case CIRCLE_SLASH -> "circle_slash";
            case DRIVE_VERTICAL -> "drive_vertical";
            case DRIVE_HORIZONTAL -> "drive_horizontal";
            case WAVE_EDGE -> "wave_edge_vertical";
            case PIERCING -> sourceType == SlashArts.ArtsType.Jackpot
                    ? "piercing_just"
                    : "piercing_2";
        };
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

    static boolean shouldSplice(ForgedSlashArtPlan.Technique technique,
            ResourceLocation combo,
            LivingEntity user,
            ForgedSlashArtPlan.Modifier modifier) {
        int signatureTick = signatureCompleteTick(technique, combo);
        if (signatureTick < 0) {
            return false;
        }
        long elapsed = ComboState.getElapsed(user);
        return elapsed >= signatureTick + modifier.spliceTailTicks();
    }

    /**
     * First tick after the source technique's defining native attack callback has
     * completed. Returning -1 means the current state is not yet a splice state.
     */
    static int signatureCompleteTick(
            ForgedSlashArtPlan.Technique technique,
            ResourceLocation combo) {
        if (combo == null || !SlashBlade.MODID.equals(combo.getNamespace())) {
            return -1;
        }
        String path = combo.getPath();
        return switch (technique) {
            case JUDGEMENT_CUT -> switch (path) {
                case "judgement_cut_slash", "judgement_cut_slash_air" -> 1;
                case "judgement_cut_slash_just" -> 2;
                // The Just attack state is only a few ticks long. Slower routing
                // modifiers can legitimately reach its native post-attack node
                // before their tail expires; the signature has already fired.
                case "judgement_cut_slash_just2" -> 0;
                default -> -1;
            };
            case SAKURA_END -> path.equals("sakura_end_right")
                    || path.equals("sakura_end_right_air") ? 1 : -1;
            case VOID_SLASH -> path.equals("void_slash") ? 17 : -1;
            case CIRCLE_SLASH -> path.equals("circle_slash") ? 8 : -1;
            case DRIVE_VERTICAL -> path.equals("drive_vertical") ? 4 : -1;
            case DRIVE_HORIZONTAL -> path.equals("drive_horizontal") ? 4 : -1;
            case WAVE_EDGE -> path.equals("wave_edge_vertical") ? 4 : -1;
            case PIERCING -> path.equals("piercing_2")
                    || path.equals("piercing_just") ? 3 : -1;
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
            case WAVE_EDGE -> path.startsWith("wave_edge");
            case PIERCING -> path.startsWith("piercing");
        };
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
