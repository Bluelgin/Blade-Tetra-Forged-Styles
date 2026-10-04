package dev.bladetetra.combat;

import dev.bladetetra.BladeTetra;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import mods.flammpfeil.slashblade.registry.combo.ComboState;
import mods.flammpfeil.slashblade.util.TimeValueHelper;
import mods.flammpfeil.slashblade.event.client.UserPoseOverrider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.registries.RegistryObject;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;

import static dev.bladetetra.combat.StyleBranchRules.*;

/**
 * Dangaku ground/air share authored timelines with native slow falling.
 * Rengeki nodes remain for old action IDs only; its live root uses the native B/air tree.
 */
public final class BranchingStyleCombos {
    private static final Map<Phase, RegistryObject<ComboState>> NODES = new EnumMap<>(Phase.class);
    public static void register() {
        if (!NODES.isEmpty()) return;
        for (Phase phase : Phase.values()) {
            NODES.put(phase, switch (phase) {
                case D_SWEEP -> ModComboStates.DANGAKU_SWEEP;
                case D_HEAVY -> ModComboStates.DANGAKU_CLEAVE;
                default -> ModComboStates.COMBOS.register(path(phase), () -> create(phase));
            });
        }
    }
    public static ResourceLocation id(Phase phase) {
        return phase == null ? ComboStateRegistry.NONE.getId() : NODES.get(phase).getId();
    }
    public static Phase phase(ResourceLocation id) {
        if (id == null || !BladeTetra.MOD_ID.equals(id.getNamespace())) return null;
        for (Phase phase : Phase.values()) if (path(phase).equals(id.getPath())) return phase;
        return null;
    }
    private static String path(Phase phase) {
        return switch (phase) {
            case D_SWEEP -> "dangaku_sweep"; // Preserve saved IDs and legacy technique checks.
            case D_HEAVY -> "dangaku_cleave";
            default -> (phase.rengeki() ? "rengeki_" : "dangaku_") + phase.name().substring(2).toLowerCase(java.util.Locale.ROOT);
        };
    }

    static ResourceLocation opener(LivingEntity entity, BladeStyle style) {
        Intent intent = StyleBranchRuntime.intent(entity, false);
        if (intent == Intent.NONE) return id(null);
        ResourceLocation common = StyleCommonCommands.resolve(intent, entity.onGround(),
                StyleBranchRuntime.commands(entity).contains(mods.flammpfeil.slashblade.util.InputCommand.R_CLICK));
        if (common != null) return common;
        boolean rengeki = style == BladeStyle.RENGEKI;
        if (!entity.onGround()) {
            return id(rengeki ? Phase.R_AIR_FIRST : Phase.D_AIR_FIRST);
        }
        return id(rengeki ? Phase.R_FIRST : Phase.D_SWEEP);
    }

    static ResourceLocation next(LivingEntity entity, Phase phase) {
        return nextFor(phase, ComboState.getElapsed(entity), StyleBranchRuntime.intent(entity, false),
                entity.onGround(), StyleBranchRuntime.commands(entity).contains(mods.flammpfeil.slashblade.util.InputCommand.R_CLICK));
    }

    static int minimumInputTick(Phase phase, Intent intent, boolean grounded, boolean rightClick) {
        return commonMove(intent, grounded, rightClick) != CommonMove.NONE
                ? phase.commonMinimumTick() : phase.minimumTick();
    }

    static ResourceLocation nextFor(Phase phase, long elapsed, Intent intent, boolean grounded, boolean rightClick) {
        if (grounded && phase.airAttack()) return id(StyleBranchRules.groundedRecovery(phase));
        ResourceLocation common = StyleCommonCommands.resolve(intent, grounded, rightClick);
        if (common != null) return elapsed >= phase.commonMinimumTick() ? common : id(phase);
        return id(StyleBranchRules.next(phase, elapsed, intent, grounded));
    }

    static ComboState create(Phase phase) {
        ComboState original = source(phase).get();
        int animationMs = (int) (TimeValueHelper.getMSecFromFrames(
                Math.abs(original.getEndFrame() - original.getStartFrame())) / original.getSpeed());
        // Resharped adds timeout to animation length. Signed correction makes the
        // total tick window explicit without speeding up the authored hit timeline.
        ComboState.Builder builder = ComboState.Builder.newInstance()
                .startAndEnd(original.getStartFrame(), original.getEndFrame())
                .motionLoc(original.getMotionLoc()).speed(original.getSpeed()).priority(100)
                .timeout(phase.duration() * 50 - animationMs)
                .next(entity -> next(entity, phase))
                .nextOfTimeout(entity -> id(StyleBranchRules.timeout(phase)));
        if (phase.aerial()) builder.aerial();
        if (original.getLoop()) builder.loop();
        if (phase.airAttack()) {
            builder.clickAction(entity -> StyleAerialAttacks.start(entity, phase, original))
                    .addTickAction(entity -> StyleAerialAttacks.tick(entity, phase, original))
                    .addHitEffect(original::hitEffect);
        } else if (phase.dive()) {
            builder.clickAction(entity -> {
                        UserPoseOverrider.resetRot(entity);
                        entity.setDeltaMovement(entity.getDeltaMovement().multiply(1, 0, 1));
                    })
                    .addTickAction(entity -> StyleBranchRuntime.tickDive(entity, phase));
        } else if (phase.recovery()) {
            builder.addTickAction(UserPoseOverrider::resetRot);
        } else if (!phase.recovery()) {
            builder.clickAction(original::clickAction)
                    .addTickAction(original::tickAction).addHitEffect(original::hitEffect);
            // Rapid Slash's hold callback owns a looping areaAttack and native-ID
            // transitions. Upper Slash's hold callback also hardcodes native IDs.
            if (phase == Phase.R_UPPER || phase == Phase.D_UPPER) {
                builder.addHoldAction(entity -> StyleBranchRuntime.holdUpper(entity, phase));
            }
        }
        // Deliberately keep Builder's Fail releaseAction: no quick-charge SA is
        // borrowed from recovery poses; normal native long-charge SA is untouched.
        return builder.build();
    }

    static Supplier<ComboState> source(Phase phase) {
        return switch (phase) {
            case R_FIRST, D_SWEEP, R_AIR_FIRST, D_AIR_FIRST -> ComboStateRegistry.COMBO_A1;
            case R_SECOND, D_RETURN, R_AIR_SECOND, D_AIR_SECOND -> ComboStateRegistry.COMBO_A2;
            case R_CHASE -> ComboStateRegistry.RAPID_SLASH;
            case R_FLURRY, R_AIR_FLURRY -> ComboStateRegistry.COMBO_B1;
            case R_FINISH, R_AIR_FINISH -> ComboStateRegistry.COMBO_A3;
            case D_FINISH, D_AIR_FINISH -> ComboStateRegistry.CIRCLE_SLASH;
            case D_HEAVY, D_AIR_HEAVY -> ComboStateRegistry.COMBO_A4_EX;
            case R_RECOVERY, R_AIR_RECOVERY -> ComboStateRegistry.COMBO_B_END2;
            case D_RECOVERY, D_AIR_RECOVERY -> ComboStateRegistry.COMBO_A4_EX_END2;
            case D_CIRCLE_RECOVERY, D_AIR_CIRCLE_RECOVERY -> ComboStateRegistry.CIRCLE_SLASH_END;
            case R_UPPER, D_UPPER -> ComboStateRegistry.UPPERSLASH;
            case R_JUMP, D_JUMP -> ComboStateRegistry.UPPERSLASH_JUMP;
            case R_AIR_RISE -> ComboStateRegistry.AERIAL_RAVE_B3;
            case R_AIR_DROP -> ComboStateRegistry.AERIAL_RAVE_B4;
            case R_DIVE, D_DIVE -> ComboStateRegistry.AERIAL_CLEAVE_LOOP;
            case R_LAND, D_LAND -> ComboStateRegistry.AERIAL_CLEAVE_LANDING;
        };
    }

    public static boolean rengekiFinisher(ResourceLocation combo) {
        Phase phase = phase(combo);
        return phase == Phase.R_FINISH || phase == Phase.R_AIR_FINISH || phase == Phase.R_AIR_DROP;
    }
    private BranchingStyleCombos() {}
}
