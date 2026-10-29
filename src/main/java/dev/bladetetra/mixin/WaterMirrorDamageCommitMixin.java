package dev.bladetetra.mixin;

import dev.bladetetra.kyouka.WaterMirrorCombat;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Forge has no public post-health-write event. Finalize only after the real player damage method returns. */
@Mixin(Player.class)
public abstract class WaterMirrorDamageCommitMixin {
    @Inject(method = {"actuallyHurt(Lnet/minecraft/world/damagesource/DamageSource;F)V",
            "m_6475_(Lnet/minecraft/world/damagesource/DamageSource;F)V"}, at = @At("RETURN"), remap = false)
    private void bladeTetra$commit(DamageSource source, float amount, CallbackInfo ci) {
        if ((Object)this instanceof ServerPlayer p) WaterMirrorCombat.finish(p);
    }
}
