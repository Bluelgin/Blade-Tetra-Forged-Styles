package dev.bladetetra.mixin;

import dev.bladetetra.combat.SpeedEfficiencyInputHandler;
import dev.bladetetra.combat.SpeedEfficiencyRuntime;
import mods.flammpfeil.slashblade.ability.SummonedSwordArts;
import mods.flammpfeil.slashblade.capability.inputstate.IInputState;
import mods.flammpfeil.slashblade.event.Scheduler;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.timers.TimerCallback;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Resharped 1.9.63: reschedule its four ORIGINAL callbacks, not additional summons. */
@Mixin(value = SummonedSwordArts.class, remap = false)
public abstract class SpeedEfficiencySummonedSwordMixin {
    @Redirect(method = "lambda$onInputChange$4", at = @At(value = "INVOKE",
            target = "Lmods/flammpfeil/slashblade/event/Scheduler;schedule(Ljava/lang/String;JLnet/minecraft/world/level/timers/TimerCallback;)V"),
            remap = false, require = 4, allow = 4)
    private void bladeTetra$swordPreparation(Scheduler scheduler, String name, long due,
            TimerCallback<LivingEntity> callback, long pressedAt, int power, IInputState input) {
        SpeedEfficiencyRuntime.schedule(scheduler, name, due, callback,
                SpeedEfficiencyInputHandler.owner(input), pressedAt);
    }
}
