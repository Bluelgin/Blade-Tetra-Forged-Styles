package dev.bladetetra.combat;

import mods.flammpfeil.slashblade.event.client.UserPoseOverrider;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import mods.flammpfeil.slashblade.registry.combo.ComboState;
import mods.flammpfeil.slashblade.util.InputCommand;
import mods.flammpfeil.slashblade.util.TimeValueHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.RegistryObject;

/** Separate held-left graph: normal Dangaku combos and native Circle SA are untouched. */
final class DangakuSpinCombos {
    static final RegistryObject<ComboState> SPIN = ModComboStates.COMBOS.register("dangaku_spin", () -> {
        ComboState nativeCircle = ComboStateRegistry.CIRCLE_SLASH.get();
        return motion(nativeCircle, DangakuSpinRules.CIRCLE_TICKS + 4)
                .loop()
                .next(entity -> StyleBranchRuntime.commands(entity).contains(InputCommand.R_CLICK)
                        ? ComboStateRegistry.NONE.getId() : DangakuSpinCombos.SPIN.getId())
                .nextOfTimeout(entity -> DangakuSpinCombos.RECOVERY.getId())
                .clickAction(nativeCircle::clickAction)
                .addTickAction(entity -> DangakuSpinHandler.animate(entity, nativeCircle))
                // No native hitEffect: Circle Slash normally installs StunManager here.
                .build();
    });
    static final RegistryObject<ComboState> RECOVERY = ModComboStates.COMBOS.register("dangaku_spin_recovery", () ->
            motion(ComboStateRegistry.CIRCLE_SLASH_END.get(), DangakuSpinRules.RECOVERY_TICKS)
                    .next(entity -> ComboState.getElapsed(entity) >= 7
                            ? ComboStateRegistry.NONE.getId() : DangakuSpinCombos.RECOVERY.getId())
                    .nextOfTimeout(entity -> ComboStateRegistry.NONE.getId())
                    .addTickAction(UserPoseOverrider::resetRot)
                    .build());

    static void register() { /* Loading this class registers both nodes on the existing deferred register. */ }
    static boolean owns(ResourceLocation combo) { return SPIN.getId().equals(combo) || RECOVERY.getId().equals(combo); }
    private static ComboState.Builder motion(ComboState nativeMotion, int ticks) {
        int animationMs = (int) (TimeValueHelper.getMSecFromFrames(
                Math.abs(nativeMotion.getEndFrame() - nativeMotion.getStartFrame())) / nativeMotion.getSpeed());
        return ComboState.Builder.newInstance()
                .startAndEnd(nativeMotion.getStartFrame(), nativeMotion.getEndFrame())
                .motionLoc(nativeMotion.getMotionLoc()).speed(nativeMotion.getSpeed()).priority(100)
                .timeout(ticks * 50 - animationMs);
    }
    private DangakuSpinCombos() {}
}
