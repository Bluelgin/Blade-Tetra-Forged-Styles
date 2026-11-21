package dev.bladetetra.mixin;

import dev.bladetetra.challenge.MikageCorridorNativeCombo;
import dev.bladetetra.combat.ProgrammaticFusionHandler;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.Map;
import java.util.function.Consumer;

/** Registered actions share an offset. The boss and fusion use their own combo clocks. */
@Mixin(targets = "mods.flammpfeil.slashblade.registry.combo.ComboState$TimeLineTickAction", remap = false)
public abstract class MikageNativeTimelineMixin {
    @Shadow private Map<Integer, Consumer<LivingEntity>> timeLine;
    @Inject(method = "accept(Lnet/minecraft/world/entity/LivingEntity;)V", at = @At("HEAD"), cancellable = true)
    private void bladeTetra$ownClock(LivingEntity entity, CallbackInfo ci) {
        int frame = ProgrammaticFusionHandler.timelineFrame(entity);
        if (dev.bladetetra.challenge.MikageNativeCombat.driving(entity)) {
            frame = (int) MikageCorridorNativeCombo.elapsed(entity);
        }
        if (frame < 0) return;
        var action = timeLine.get(frame);
        if (action != null) action.accept(entity);
        ci.cancel();
    }
}
