package dev.btsealed.mixin;

import dev.btsealed.runtime.NativeFusionEngine;
import dev.btsealed.runtime.PortableFusion;
import mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState;
import mods.flammpfeil.slashblade.capability.slashblade.SlashBladeState;
import mods.flammpfeil.slashblade.slasharts.SlashArts;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

/** Routes sealed releases through the existing SlashBlade motion and charge lifecycle. */
@Mixin(value = SlashBladeState.class, remap = false)
public abstract class PortableFusionBladeStateMixin implements ISlashBladeState,
        NativeFusionEngine.MotionRevision {
    @Unique private long btSealed$motionRevision;

    @Override public long bladeTetra$motionRevision() {
        return btSealed$motionRevision;
    }

    @Inject(method = "setComboSeq", at = @At("RETURN"))
    private void btSealed$recordMotion(ResourceLocation next, CallbackInfo callback) {
        btSealed$motionRevision++;
    }

    @Override public ResourceLocation doChargeAction(LivingEntity user, int elapsed) {
        return PortableFusion.ENGINE.charge(this, user, elapsed,
                () -> ISlashBladeState.super.doChargeAction(user, elapsed));
    }

    @Override public void updateComboSeq(LivingEntity user, ResourceLocation next) {
        PortableFusion.ENGINE.motion(this, user, next,
                () -> ISlashBladeState.super.updateComboSeq(user, next));
    }

    @Override public SlashArts getSlashArts() {
        SlashArts source = PortableFusion.ENGINE.contextualArt(this);
        return source != null ? source : ISlashBladeState.super.getSlashArts();
    }

    @Override public Map.Entry<Integer, ResourceLocation> resolvCurrentComboStateTicks(LivingEntity user) {
        return PortableFusion.ENGINE.resolveTimeout(this, user,
                () -> ISlashBladeState.super.resolvCurrentComboStateTicks(user));
    }

}
