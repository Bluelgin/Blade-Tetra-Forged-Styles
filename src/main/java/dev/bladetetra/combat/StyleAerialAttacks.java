package dev.bladetetra.combat;

import mods.flammpfeil.slashblade.event.client.UserPoseOverrider;
import mods.flammpfeil.slashblade.event.handler.FallHandler;
import mods.flammpfeil.slashblade.registry.combo.ComboState;
import net.minecraft.world.entity.LivingEntity;

import static dev.bladetetra.combat.StyleBranchRules.Phase;

/** Air is the ground attack timeline with Resharped's own falling behavior. */
final class StyleAerialAttacks {
    static void start(LivingEntity entity, Phase phase, ComboState original) {
        if (StyleBranchRuntime.finishIfLanded(entity, phase)) return;
        if (phase.recovery()) UserPoseOverrider.resetRot(entity);
        else original.clickAction(entity);
    }

    static void tick(LivingEntity entity, Phase phase, ComboState original) {
        if (StyleBranchRuntime.finishIfLanded(entity, phase)) return;
        if (phase.recovery()) UserPoseOverrider.resetRot(entity);
        else original.tickAction(entity);
        // Active nodes copy ground motions. Legacy rise/drop nodes already
        // call this inside their original aerial callback: never apply twice.
        if (!original.isAerial()) FallHandler.fallDecrease(entity);
    }
    private StyleAerialAttacks() {}
}
