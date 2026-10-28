package dev.bladetetra.mixin;

import dev.bladetetra.kyouka.NativeBladeActionObserver;
import mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** After final event routing/cancellation and timestamp commit, before native clickAction. */
@Mixin(value = ISlashBladeState.class, remap = false)
public interface NativeBladeCommitMixin {
    @Inject(method = "updateComboSeq", at = @At(value = "INVOKE", target =
            "Lmods/flammpfeil/slashblade/capability/slashblade/ISlashBladeState;setLastActionTime(J)V", shift = At.Shift.AFTER))
    default void bladeTetra$committed(LivingEntity user, ResourceLocation candidate, CallbackInfo ci) {
        NativeBladeActionObserver.committed(user, (ISlashBladeState) this);
    }
}
