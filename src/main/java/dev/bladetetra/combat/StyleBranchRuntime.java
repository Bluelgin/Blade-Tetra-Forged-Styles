package dev.bladetetra.combat;

import dev.bladetetra.BladeTetra;
import dev.bladetetra.item.ModularSlashBladeItem;
import mods.flammpfeil.slashblade.capability.inputstate.CapabilityInputState;
import mods.flammpfeil.slashblade.event.client.UserPoseOverrider;
import mods.flammpfeil.slashblade.registry.combo.ComboState;
import mods.flammpfeil.slashblade.util.InputCommand;
import mods.flammpfeil.slashblade.util.TimeValueHelper;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.EnumSet;

import static dev.bladetetra.combat.StyleBranchRules.*;

/** Direction input and landing safety; ordinary aerial attacks have no per-jump quota. */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID)
public final class StyleBranchRuntime {

    static EnumSet<InputCommand> commands(LivingEntity entity) {
        return entity.getCapability(CapabilityInputState.INPUT_STATE)
                .map(input -> input.getCommands(entity)).orElseGet(() -> EnumSet.noneOf(InputCommand.class));
    }
    static Intent intent(LivingEntity entity, boolean bufferedClick) {
        EnumSet<InputCommand> commands = commands(entity);
        if (!bufferedClick && !commands.contains(InputCommand.R_CLICK) && !commands.contains(InputCommand.L_CLICK)) return Intent.NONE;
        if (bufferedClick || commands.contains(InputCommand.R_CLICK)) {
            if (commands.contains(InputCommand.SNEAK) && commands.contains(InputCommand.BACK)) return Intent.BACK;
            if (commands.contains(InputCommand.SNEAK) && commands.contains(InputCommand.FORWARD)) return Intent.FORWARD;
        }
        return Intent.ATTACK;
    }

    static boolean finishIfLanded(LivingEntity entity, Phase phase) {
        if (!entity.onGround()) return false;
        entity.getMainHandItem().getCapability(ModularSlashBladeItem.BLADESTATE).ifPresent(state -> {
            if (BranchingStyleCombos.id(phase).equals(state.getComboSeq())) {
                UserPoseOverrider.resetRot(entity);
                state.updateComboSeq(entity, BranchingStyleCombos.id(StyleBranchRules.groundedRecovery(phase)));
            }
        });
        return true;
    }

    static void holdUpper(LivingEntity entity, Phase phase) {
        if (ComboState.getElapsed(entity) != (int) TimeValueHelper.getTicksFromFrames(9)
                || !entity.onGround() || !commands(entity).containsAll(EnumSet.of(InputCommand.BACK, InputCommand.R_DOWN))) return;
        entity.getMainHandItem().getCapability(ModularSlashBladeItem.BLADESTATE).ifPresent(state ->
                state.updateComboSeq(entity, BranchingStyleCombos.id(phase.rengeki() ? Phase.R_JUMP : Phase.D_JUMP)));
    }

    static void tickDive(LivingEntity entity, Phase phase) {
        if (entity.onGround()) {
            entity.getMainHandItem().getCapability(ModularSlashBladeItem.BLADESTATE).ifPresent(state ->
                    state.updateComboSeq(entity, BranchingStyleCombos.id(phase.rengeki() ? Phase.R_LAND : Phase.D_LAND)));
            return;
        }
        if (ComboState.getElapsed(entity) >= 2) {
            var movement = entity.getDeltaMovement();
            entity.setDeltaMovement(movement.x * .8, Math.max(-1.2, movement.y - .25), movement.z * .8);
            entity.hasImpulse = true;
            entity.fallDistance = 1; // Native cleave's fall protection, no altitude damage scaling.
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void tick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.onGround() && entity.getMainHandItem().getItem() instanceof ModularSlashBladeItem) {
            var combo = entity.getMainHandItem().getCapability(ModularSlashBladeItem.BLADESTATE)
                    .map(state -> state.getComboSeq()).orElse(null);
            Phase phase = BranchingStyleCombos.phase(combo);
            if (phase != null && phase.airAttack()) finishIfLanded(entity, phase);
        }
    }
    private StyleBranchRuntime() {}
}
