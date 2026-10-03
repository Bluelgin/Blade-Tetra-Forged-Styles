package dev.bladetetra.mixin;

import dev.bladetetra.combat.CombatBalanceRuntime;
import mods.flammpfeil.slashblade.slasharts.SlashArts;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Covers native Super SA selection as well as ordinary and forged SA selectors. */
@Mixin(value = SlashArts.class, remap = false)
public abstract class CombatBalanceSlashArtsMixin {
    @Inject(method = "doArts", at = @At("RETURN"))
    private void bladeTetra$selected(SlashArts.ArtsType type, LivingEntity user, CallbackInfoReturnable<ResourceLocation> callback) {
        CombatBalanceRuntime.selectedSlashArt(user, callback.getReturnValue());
    }
}
