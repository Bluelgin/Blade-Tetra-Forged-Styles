package dev.bladetetra.mixin;

import dev.bladetetra.combat.SpeedEfficiencyRuntime;
import mods.flammpfeil.slashblade.ability.SuperSlashArts;
import mods.flammpfeil.slashblade.capability.inputstate.IInputState;
import mods.flammpfeil.slashblade.event.Scheduler;
import mods.flammpfeil.slashblade.event.handler.InputCommandEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.timers.TimerCallback;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Keep native validation/cost/release; shift only its windup and matching preparation VFX. */
@Mixin(value = SuperSlashArts.class, remap = false)
public abstract class SpeedEfficiencySuperSaMixin {
    @Redirect(method = "lambda$onInputChange$5", at = @At(value = "INVOKE",
            target = "Lmods/flammpfeil/slashblade/event/Scheduler;schedule(Ljava/lang/String;JLnet/minecraft/world/level/timers/TimerCallback;)V"),
            remap = false, require = 2, allow = 2)
    private static void bladeTetra$superPreparation(Scheduler scheduler, String name, long due,
            TimerCallback<LivingEntity> callback, long pressedAt, RandomSource random,
            ServerPlayer player, InputCommandEvent event, IInputState input) {
        SpeedEfficiencyRuntime.schedule(scheduler, name, due, callback, player, pressedAt);
    }
}
