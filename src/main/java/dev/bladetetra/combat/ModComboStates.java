package dev.bladetetra.combat;

import dev.bladetetra.BladeTetra;
import mods.flammpfeil.slashblade.capability.inputstate.CapabilityInputState;
import mods.flammpfeil.slashblade.init.DefaultResources;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import mods.flammpfeil.slashblade.registry.combo.ComboState;
import mods.flammpfeil.slashblade.util.InputCommand;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

import java.util.EnumSet;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Style combo graphs reuse Resharped's animation and combat timelines while
 * replacing their transitions. Forged Slash Art states are stricter: they copy
 * only visual motion metadata and never copy source attack callbacks.
 */
public final class ModComboStates {
    static final int IAIDO_SHEATHE_MINIMUM_NEXT_FRAME = 7;

    public static final DeferredRegister<ComboState> COMBOS =
            DeferredRegister.create(ComboState.REGISTRY_KEY, BladeTetra.MOD_ID);

    private static final ResourceLocation IAIDO_DRAW_ID =
            new ResourceLocation(BladeTetra.MOD_ID, "iaido_draw");
    private static final ResourceLocation IAIDO_FOLLOW_ID =
            new ResourceLocation(BladeTetra.MOD_ID, "iaido_follow");
    private static final ResourceLocation IAIDO_RETURN_ID =
            new ResourceLocation(BladeTetra.MOD_ID, "iaido_return");
    private static final ResourceLocation IAIDO_FINISH_ID =
            new ResourceLocation(BladeTetra.MOD_ID, "iaido_finish");
    private static final ResourceLocation IAIDO_SHEATHE_ID =
            new ResourceLocation(BladeTetra.MOD_ID, "iaido_sheathe");
    private static final ResourceLocation DANGAKU_SWEEP_ID =
            new ResourceLocation(BladeTetra.MOD_ID, "dangaku_sweep");
    private static final ResourceLocation DANGAKU_RISE_ID =
            new ResourceLocation(BladeTetra.MOD_ID, "dangaku_rise");
    private static final ResourceLocation DANGAKU_CLEAVE_ID =
            new ResourceLocation(BladeTetra.MOD_ID, "dangaku_cleave");
    private static final ResourceLocation DANGAKU_CHARGED_SWEEP_ID =
            new ResourceLocation(BladeTetra.MOD_ID, "dangaku_charged_sweep");
    private static final ResourceLocation TWIN_PHASE_DRAW_ID =
            new ResourceLocation(BladeTetra.MOD_ID, "twin_phase_draw");

    public static final RegistryObject<ComboState> IAIDO_DRAW =
            COMBOS.register("iaido_draw", () -> copyAttack(
                    ComboStateRegistry.COMBO_C,
                    entity -> selectFollowUp(entity, IAIDO_RETURN_ID),
                    ComboStateRegistry.COMBO_C_END.getId(),
                    15,
                    0));
    public static final RegistryObject<ComboState> IAIDO_FOLLOW =
            COMBOS.register("iaido_follow", () -> copyAttack(
                    ComboStateRegistry.COMBO_A1,
                    entity -> selectFollowUp(entity, IAIDO_RETURN_ID),
                    ComboStateRegistry.COMBO_A1_END.getId(),
                    5,
                    0));
    public static final RegistryObject<ComboState> IAIDO_RETURN =
            COMBOS.register("iaido_return", () -> copyAttack(
                    ComboStateRegistry.COMBO_A2,
                    entity -> selectFollowUp(entity, IAIDO_SHEATHE_ID),
                    IAIDO_SHEATHE_ID,
                    5,
                    0));
    public static final RegistryObject<ComboState> IAIDO_FINISH =
            COMBOS.register("iaido_finish", () -> copyAttack(
                    ComboStateRegistry.COMBO_A3,
                    entity -> selectFollowUp(entity, IAIDO_DRAW_ID),
                    ComboStateRegistry.COMBO_A3_END.getId(),
                    9,
                    0));
    public static final RegistryObject<ComboState> IAIDO_SHEATHE =
            COMBOS.register("iaido_sheathe", () -> copyAttack(
                    ComboStateRegistry.COMBO_C_END,
                    entity -> selectFollowUp(entity, IAIDO_DRAW_ID),
                    ComboStateRegistry.NONE.getId(),
                    IAIDO_SHEATHE_MINIMUM_NEXT_FRAME,
                    0));

    public static final RegistryObject<ComboState> DANGAKU_SWEEP =
            COMBOS.register("dangaku_sweep", () -> copyAttack(
                    ComboStateRegistry.COMBO_A1,
                    ModComboStates::selectDangakuFollowUp,
                    ComboStateRegistry.COMBO_A1_END.getId(),
                    5,
                    0));
    public static final RegistryObject<ComboState> DANGAKU_RISE =
            COMBOS.register("dangaku_rise", () -> copyAttack(
                    ComboStateRegistry.COMBO_A3,
                    ModComboStates::selectDangakuFollowUp,
                    ComboStateRegistry.COMBO_A3_END.getId(),
                    9,
                    450));
    public static final RegistryObject<ComboState> DANGAKU_CLEAVE =
            COMBOS.register("dangaku_cleave", () -> copyAttack(
                    ComboStateRegistry.COMBO_A4_EX,
                    ModComboStates::selectDangakuFollowUp,
                    ComboStateRegistry.COMBO_A4_EX_END.getId(),
                    22,
                    0));
    /** Charged Dangaku uses the native sweep motion but owns its hit logic. */
    public static final RegistryObject<ComboState> DANGAKU_CHARGED_SWEEP =
            COMBOS.register("dangaku_charged_sweep", () -> visualMotion(
                    ComboStateRegistry.COMBO_A1, 18));

    /** Native SlashBlade draw motion with every attack callback deliberately removed. */
    public static final RegistryObject<ComboState> TWIN_PHASE_DRAW =
            COMBOS.register("twin_phase_draw", () -> visualMotion(
                    ComboStateRegistry.COMBO_C, 18));

    // Forged Slash Arts copy native motion only. Normal and Haste states are
    // separate registry entries so animation speed remains state-machine native.
    public static final RegistryObject<ComboState> FORGED_JUDGEMENT =
            COMBOS.register("forged_judgement", () -> forgedVisual(
                    ComboStateRegistry.JUDGEMENT_CUT, false));
    public static final RegistryObject<ComboState> FORGED_JUDGEMENT_HASTE =
            COMBOS.register("forged_judgement_haste", () -> forgedVisual(
                    ComboStateRegistry.JUDGEMENT_CUT, true));
    public static final RegistryObject<ComboState> FORGED_SAKURA =
            COMBOS.register("forged_sakura", () -> forgedVisual(
                    ComboStateRegistry.SAKURA_END_LEFT, false));
    public static final RegistryObject<ComboState> FORGED_SAKURA_HASTE =
            COMBOS.register("forged_sakura_haste", () -> forgedVisual(
                    ComboStateRegistry.SAKURA_END_LEFT, true));
    public static final RegistryObject<ComboState> FORGED_VOID =
            COMBOS.register("forged_void", () -> forgedVisual(
                    ComboStateRegistry.VOID_SLASH, false));
    public static final RegistryObject<ComboState> FORGED_VOID_HASTE =
            COMBOS.register("forged_void_haste", () -> forgedVisual(
                    ComboStateRegistry.VOID_SLASH, true));
    public static final RegistryObject<ComboState> FORGED_CIRCLE =
            COMBOS.register("forged_circle", () -> forgedVisual(
                    ComboStateRegistry.CIRCLE_SLASH, false));
    public static final RegistryObject<ComboState> FORGED_CIRCLE_HASTE =
            COMBOS.register("forged_circle_haste", () -> forgedVisual(
                    ComboStateRegistry.CIRCLE_SLASH, true));
    public static final RegistryObject<ComboState> FORGED_DRIVE_VERTICAL =
            COMBOS.register("forged_drive_vertical", () -> forgedVisual(
                    ComboStateRegistry.DRIVE_VERTICAL, false));
    public static final RegistryObject<ComboState> FORGED_DRIVE_VERTICAL_HASTE =
            COMBOS.register("forged_drive_vertical_haste", () -> forgedVisual(
                    ComboStateRegistry.DRIVE_VERTICAL, true));
    public static final RegistryObject<ComboState> FORGED_DRIVE_HORIZONTAL =
            COMBOS.register("forged_drive_horizontal", () -> forgedVisual(
                    ComboStateRegistry.DRIVE_HORIZONTAL, false));
    public static final RegistryObject<ComboState> FORGED_DRIVE_HORIZONTAL_HASTE =
            COMBOS.register("forged_drive_horizontal_haste", () -> forgedVisual(
                    ComboStateRegistry.DRIVE_HORIZONTAL, true));
    public static final RegistryObject<ComboState> FORGED_WAVE_EDGE =
            COMBOS.register("forged_wave_edge", () -> forgedVisual(
                    ComboStateRegistry.WAVE_EDGE_VERTICAL, false));
    public static final RegistryObject<ComboState> FORGED_WAVE_EDGE_HASTE =
            COMBOS.register("forged_wave_edge_haste", () -> forgedVisual(
                    ComboStateRegistry.WAVE_EDGE_VERTICAL, true));
    public static final RegistryObject<ComboState> FORGED_PIERCING =
            COMBOS.register("forged_piercing", () -> forgedVisual(
                    ComboStateRegistry.PIERCING, false));
    public static final RegistryObject<ComboState> FORGED_PIERCING_HASTE =
            COMBOS.register("forged_piercing_haste", () -> forgedVisual(
                    ComboStateRegistry.PIERCING, true));

    public static final RegistryObject<ComboState> IAIDO_ROOT =
            COMBOS.register("iaido_root", () -> root(ModComboStates::selectIaidoOpener));
    public static final RegistryObject<ComboState> RENGEKI_ROOT =
            COMBOS.register("rengeki_root", () -> root(ModComboStates::selectRengekiOpener));
    public static final RegistryObject<ComboState> DANGAKU_ROOT =
            COMBOS.register("dangaku_root", () -> root(ModComboStates::selectDangakuOpener));

    public static ResourceLocation getRoot(BladeStyle style) {
        return switch (style) {
            case IAIDO -> IAIDO_ROOT.getId();
            case RENGEKI -> RENGEKI_ROOT.getId();
            case DANGAKU -> DANGAKU_ROOT.getId();
            default -> ComboStateRegistry.STANDBY.getId();
        };
    }

    public static ResourceLocation getForgedMotion(
            ForgedSlashArtPlan.Technique technique, boolean haste) {
        return switch (technique) {
            case JUDGEMENT_CUT -> haste
                    ? FORGED_JUDGEMENT_HASTE.getId() : FORGED_JUDGEMENT.getId();
            case SAKURA_END -> haste
                    ? FORGED_SAKURA_HASTE.getId() : FORGED_SAKURA.getId();
            case VOID_SLASH -> haste
                    ? FORGED_VOID_HASTE.getId() : FORGED_VOID.getId();
            case CIRCLE_SLASH -> haste
                    ? FORGED_CIRCLE_HASTE.getId() : FORGED_CIRCLE.getId();
            case DRIVE_VERTICAL -> haste
                    ? FORGED_DRIVE_VERTICAL_HASTE.getId() : FORGED_DRIVE_VERTICAL.getId();
            case DRIVE_HORIZONTAL -> haste
                    ? FORGED_DRIVE_HORIZONTAL_HASTE.getId() : FORGED_DRIVE_HORIZONTAL.getId();
            case WAVE_EDGE -> haste
                    ? FORGED_WAVE_EDGE_HASTE.getId() : FORGED_WAVE_EDGE.getId();
            case PIERCING -> haste
                    ? FORGED_PIERCING_HASTE.getId() : FORGED_PIERCING.getId();
        };
    }

    private static ComboState root(
            java.util.function.Function<LivingEntity, ResourceLocation> opener) {
        return ComboState.Builder.newInstance()
                .startAndEnd(0, 1)
                .loop()
                .motionLoc(DefaultResources.ExMotionLocation)
                .next(opener)
                .nextOfTimeout(entity -> ComboStateRegistry.NONE.getId())
                .build();
    }

    /**
     * Copies a native attack node, including every slash timeline, movement
     * action and hit effect. Only its click and timeout destinations change.
     */
    private static ComboState copyAttack(
            Supplier<ComboState> source,
            Function<LivingEntity, ResourceLocation> next,
            ResourceLocation nextOfTimeout) {
        return copyAttack(source, next, nextOfTimeout, 0, 0);
    }

    private static ComboState copyAttack(
            Supplier<ComboState> source,
            Function<LivingEntity, ResourceLocation> next,
            ResourceLocation nextOfTimeout,
            int minimumNextFrame,
            int additionalTimeout) {
        ComboState original = source.get();
        Function<LivingEntity, ResourceLocation> gatedNext =
                minimumNextFrame > 0
                        ? ComboState.TimeoutNext.buildFromFrame(
                                minimumNextFrame, next)
                        : next;
        ComboState.Builder builder = ComboState.Builder.newInstance()
                .startAndEnd(original.getStartFrame(), original.getEndFrame())
                .priority(original.getPriority())
                .speed(original.getSpeed())
                .timeout(original.timeout + additionalTimeout)
                .motionLoc(original.getMotionLoc())
                .next(gatedNext)
                .nextOfTimeout(entity -> nextOfTimeout)
                .addHoldAction(original::holdAction)
                .addTickAction(original::tickAction)
                .addHitEffect(original::hitEffect)
                .clickAction(original::clickAction)
                .releaseAction(original::releaseAction);

        if (original.getLoop()) {
            builder.loop();
        }
        if (original.isAerial()) {
            builder.aerial();
        }
        return builder.build();
    }

    private static ComboState visualMotion(Supplier<ComboState> source,
            int timeout) {
        ComboState original = source.get();
        return ComboState.Builder.newInstance()
                .startAndEnd(original.getStartFrame(), original.getEndFrame())
                .priority(original.getPriority())
                .speed(original.getSpeed())
                .timeout(timeout)
                .motionLoc(original.getMotionLoc())
                .next(entity -> ComboStateRegistry.NONE.getId())
                .nextOfTimeout(entity -> ComboStateRegistry.NONE.getId())
                .build();
    }

    private static ComboState forgedVisual(Supplier<ComboState> source,
            boolean haste) {
        ComboState original = source.get();
        float speedScale = haste ? 1.25F : 1.0F;
        float timeoutScale = haste ? 0.75F : 1.0F;
        ComboState.Builder builder = ComboState.Builder.newInstance()
                .startAndEnd(original.getStartFrame(), original.getEndFrame())
                .priority(original.getPriority())
                .speed(original.getSpeed() * speedScale)
                .timeout(Math.max(0, Math.round(original.timeout * timeoutScale)))
                .motionLoc(original.getMotionLoc())
                .next(entity -> ComboStateRegistry.NONE.getId())
                .nextOfTimeout(entity -> ComboStateRegistry.NONE.getId());
        if (original.getLoop()) {
            builder.loop();
        }
        if (original.isAerial()) {
            builder.aerial();
        }
        return builder.build();
    }

    private static ResourceLocation selectIaidoOpener(LivingEntity entity) {
        return selectOpener(entity, IAIDO_DRAW.getId());
    }

    private static ResourceLocation selectRengekiOpener(LivingEntity entity) {
        return selectOpener(entity, ComboStateRegistry.COMBO_B1.getId());
    }

    private static ResourceLocation selectDangakuOpener(LivingEntity entity) {
        return selectDangakuGroundChoice(entity);
    }

    private static ResourceLocation selectDangakuFollowUp(LivingEntity entity) {
        return selectDangakuGroundChoice(entity);
    }

    /**
     * Dangaku leaves ordinary right-click in NONE so ItemSlashBlade can enter
     * its native held-use state without committing an attack. Release is owned
     * by DangakuFormationHandler: tap -> sweep, hold -> charged sweep. Left-click
     * remains the deliberate cleave. Directional and aerial commands stay native.
     */
    private static ResourceLocation selectDangakuGroundChoice(LivingEntity entity) {
        EnumSet<InputCommand> commands = entity.getCapability(CapabilityInputState.INPUT_STATE)
                .map(state -> state.getCommands(entity))
                .orElseGet(() -> EnumSet.noneOf(InputCommand.class));

        if (commands.contains(InputCommand.ON_GROUND)) {
            if (commands.containsAll(EnumSet.of(
                    InputCommand.SNEAK, InputCommand.FORWARD, InputCommand.R_CLICK))) {
                return ComboStateRegistry.RAPID_SLASH.getId();
            }
            if (commands.containsAll(EnumSet.of(
                    InputCommand.SNEAK, InputCommand.BACK, InputCommand.R_CLICK))) {
                return ComboStateRegistry.UPPERSLASH.getId();
            }
            if (commands.contains(InputCommand.L_CLICK)) {
                return DANGAKU_CLEAVE_ID;
            }
            if (commands.contains(InputCommand.R_CLICK)) {
                return ComboStateRegistry.NONE.getId();
            }
        }

        if (commands.contains(InputCommand.ON_AIR)) {
            if (commands.containsAll(EnumSet.of(
                    InputCommand.SNEAK, InputCommand.BACK, InputCommand.R_CLICK))) {
                return ComboStateRegistry.AERIAL_CLEAVE.getId();
            }
            return ComboStateRegistry.AERIAL_RAVE_A1.getId();
        }

        return ComboStateRegistry.NONE.getId();
    }

    /**
     * A style chain still yields to the standard directional and aerial
     * commands. Ordinary grounded clicks advance to the next style beat.
     */
    private static ResourceLocation selectFollowUp(
            LivingEntity entity,
            ResourceLocation groundNext) {
        return selectOpener(entity, groundNext);
    }

    public static boolean isDangakuRise(ResourceLocation combo) {
        return DANGAKU_RISE_ID.equals(combo);
    }

    public static boolean isDangakuCleave(ResourceLocation combo) {
        return DANGAKU_CLEAVE_ID.equals(combo);
    }

    public static boolean isDangakuSweep(ResourceLocation combo) {
        return DANGAKU_SWEEP_ID.equals(combo);
    }

    public static boolean isDangakuChargedSweep(ResourceLocation combo) {
        return DANGAKU_CHARGED_SWEEP_ID.equals(combo);
    }

    public static boolean isIaidoSheathe(ResourceLocation combo) {
        return IAIDO_SHEATHE_ID.equals(combo);
    }

    public static boolean isIaidoDraw(ResourceLocation combo) {
        return IAIDO_DRAW_ID.equals(combo);
    }

    public static boolean isIaidoFinish(ResourceLocation combo) {
        return IAIDO_FINISH_ID.equals(combo);
    }

    public static boolean isIaidoAttack(ResourceLocation combo) {
        return IAIDO_DRAW_ID.equals(combo)
                || IAIDO_FOLLOW_ID.equals(combo)
                || IAIDO_RETURN_ID.equals(combo)
                || IAIDO_FINISH_ID.equals(combo);
    }

    public static ResourceLocation getIaidoDrawId() {
        return IAIDO_DRAW_ID;
    }

    public static ResourceLocation getDangakuCleaveId() {
        return DANGAKU_CLEAVE_ID;
    }

    public static ResourceLocation getDangakuSweepId() {
        return DANGAKU_SWEEP_ID;
    }

    public static ResourceLocation getDangakuChargedSweepId() {
        return DANGAKU_CHARGED_SWEEP_ID;
    }

    /**
     * Retains Resharped's directional and aerial commands while replacing the
     * ordinary grounded opener with the active style.
     */
    private static ResourceLocation selectOpener(
            LivingEntity entity,
            ResourceLocation groundOpener) {
        EnumSet<InputCommand> commands = entity.getCapability(CapabilityInputState.INPUT_STATE)
                .map(state -> state.getCommands(entity))
                .orElseGet(() -> EnumSet.noneOf(InputCommand.class));

        if (commands.contains(InputCommand.ON_GROUND)) {
            if (commands.containsAll(EnumSet.of(
                    InputCommand.SNEAK, InputCommand.FORWARD, InputCommand.R_CLICK))) {
                return ComboStateRegistry.RAPID_SLASH.getId();
            }
            if (commands.containsAll(EnumSet.of(
                    InputCommand.SNEAK, InputCommand.BACK, InputCommand.R_CLICK))) {
                return ComboStateRegistry.UPPERSLASH.getId();
            }
            if (commands.contains(InputCommand.L_CLICK)
                    || commands.contains(InputCommand.R_CLICK)) {
                return groundOpener;
            }
        }

        if (commands.contains(InputCommand.ON_AIR)) {
            if (commands.containsAll(EnumSet.of(
                    InputCommand.SNEAK, InputCommand.BACK, InputCommand.R_CLICK))) {
                return ComboStateRegistry.AERIAL_CLEAVE.getId();
            }
            return ComboStateRegistry.AERIAL_RAVE_A1.getId();
        }

        return ComboStateRegistry.NONE.getId();
    }

    private ModComboStates() {
    }
}
