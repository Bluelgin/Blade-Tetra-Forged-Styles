package dev.bladetetra.mixin;

import dev.bladetetra.combat.ProgrammaticFusionHandler;
import mods.flammpfeil.slashblade.registry.combo.ComboState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.function.Consumer;

/** Keep native callbacks and give each fusion source its own timeline and one execution per frame. */
@Mixin(value = ComboState.class, remap = false)
public abstract class FusionComboTickMixin {
    @Redirect(method = "tickAction", at = @At(value = "INVOKE",
            target = "Ljava/util/function/Consumer;accept(Ljava/lang/Object;)V"))
    private void bladeTetra$fusionTick(Consumer<LivingEntity> action, Object user) {
        LivingEntity entity = (LivingEntity) user;
        ProgrammaticFusionHandler.tickAction((ComboState) (Object) this,
                entity, () -> action.accept(entity));
    }
}
