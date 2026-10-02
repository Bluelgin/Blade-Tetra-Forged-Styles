package dev.bladetetra.mixin;

import dev.bladetetra.combat.SpeedEfficiencyRuntime;
import mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState;
import mods.flammpfeil.slashblade.capability.slashblade.SlashBladeState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;

/** Overrides the inherited default once, so charge checks, Just windows and ready VFX agree. */
@Mixin(value = SlashBladeState.class, remap = false)
public abstract class SpeedEfficiencyBladeStateMixin implements ISlashBladeState {
    @Override public int getFullChargeTicks(LivingEntity user) {
        return SpeedEfficiencyRuntime.normalSaTicks(this, user, ISlashBladeState.super.getFullChargeTicks(user));
    }
}
