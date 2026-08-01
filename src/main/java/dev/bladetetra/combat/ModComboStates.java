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
 * replacing their transitions. This keeps hit timing compatible with
 * Resharped, but prevents a style root from replaying one signature attack for
 * every click.
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
                    entity -> selectFollowUp(entity, DANGAKU_CLEAVE_ID),
                    ComboStateRegistry.COMBO_A1_END.getId(),
                    5,
                    0));
    public static final RegistryObject<ComboState> DANGAKU_RISE =
            COMBOS.register("dangaku_rise", () -> copyAttack(
                    ComboStateRegistry.COMBO_A3,
                    entity -> selectFollowUp(entity, DANGAKU_CLEAVE_ID),
                    ComboStateRegistry.COMBO_A3_END.getId(),
                    9,
                    450));
    public static final RegistryObject<ComboState> DANGAKU_CLEAVE =
            COMBOS.register("dangaku_cleave", () -> copyAttack(
                    ComboStateRegistry.COMBO_A4_EX,
                    entity -> selectFollowUp(entity, DANGAKU_SWEEP_ID),
                    ComboStateRegistry.COMBO_A4_EX_END.getId(),
                    22,
                    0));

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

    private static ResourceLocation selectIaidoOpener(LivingEntity entity) {
        return selectOpener(entity, IAIDO_DRAW.getId());
    }

    private static ResourceLocation selectRengekiOpener(LivingEntity entity) {
        return selectOpener(entity, ComboStateRegistry.COMBO_B1.getId());
    }

    private static ResourceLocation selectDangakuOpener(LivingEntity entity) {
        return selectOpener(entity, DANGAKU_CLEAVE.getId());
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
