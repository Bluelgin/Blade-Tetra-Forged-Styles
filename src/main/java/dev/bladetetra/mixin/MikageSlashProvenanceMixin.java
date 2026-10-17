package dev.bladetetra.mixin;

import dev.bladetetra.challenge.MikageBladeAttackTrace;
import dev.bladetetra.challenge.MikageCorridorNativeCombo;
import mods.flammpfeil.slashblade.entity.EntityJudgementCut;
import mods.flammpfeil.slashblade.entity.EntitySlashEffect;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Preserve native hit provenance; a judgement cut must never masquerade as a direct sword swing. */
@Mixin(value = {EntitySlashEffect.class, EntityJudgementCut.class}, remap = false)
public abstract class MikageSlashProvenanceMixin {
    @Inject(method = {"tick", "m_8119_"}, at = @At("HEAD"), remap = false)
    private void bladeTetra$beginHit(CallbackInfo ci) {
        MikageCorridorNativeCombo.follow((Entity) (Object) this);
        MikageBladeAttackTrace.enter((Entity) (Object) this);
    }
    @Inject(method = {"tick", "m_8119_"}, at = @At("RETURN"), remap = false)
    private void bladeTetra$endHit(CallbackInfo ci) { MikageBladeAttackTrace.exit(); }
}
