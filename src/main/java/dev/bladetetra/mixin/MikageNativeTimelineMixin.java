package dev.bladetetra.mixin;

import dev.bladetetra.challenge.MikageCorridorNativeCombo;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.Map;
import java.util.function.Consumer;

/** Registered actions have a shared offset. Boss clocks must not mutate a player's offset. */
@Mixin(targets = "mods.flammpfeil.slashblade.registry.combo.ComboState$TimeLineTickAction", remap = false)
public abstract class MikageNativeTimelineMixin {
    @Shadow private Map<Integer, Consumer<LivingEntity>> timeLine;
    @Inject(method = "accept(Lnet/minecraft/world/entity/LivingEntity;)V", at = @At("HEAD"), cancellable = true)
    private void bladeTetra$ownClock(LivingEntity entity, CallbackInfo ci) {
        if (!MikageCorridorNativeCombo.driving(entity)) return;
        var action = timeLine.get((int) MikageCorridorNativeCombo.elapsed(entity));
        if (action != null) action.accept(entity);
        ci.cancel();
    }
}
