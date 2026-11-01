package dev.bladetetra.mixin;

import dev.bladetetra.easteregg.AkatsukiBloodTendrils;
import mods.flammpfeil.slashblade.ability.SlayerStyleArts;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Native V teleports can be shorter than the channel's displacement fallback. */
@Mixin(value=SlayerStyleArts.class,remap=false)
public abstract class AkatsukiThreadTeleportMixin {
    @Inject(method="executeTeleport",at=@At("HEAD"))
    private static void interruptBloodThreads(Entity entity,LivingEntity target,CallbackInfo ci) {
        if(entity instanceof ServerPlayer p&&AkatsukiBloodTendrils.active(p))AkatsukiBloodTendrils.cancel(p);
    }
}
