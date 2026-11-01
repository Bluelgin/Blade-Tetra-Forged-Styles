package dev.bladetetra.mixin;

import dev.bladetetra.kyouka.KyoukaAfterimages;
import mods.flammpfeil.slashblade.ability.SlayerStyleArts;
import mods.flammpfeil.slashblade.util.InputCommand;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import java.util.EnumSet;

/** Observe actual movement boundaries, including delayed Air Trick, without changing native input or motion. */
@Mixin(value = SlayerStyleArts.class, remap = false)
public abstract class KyoukaNativeTrickMixin {
    @Unique private final ThreadLocal<KyoukaAfterimages.Departure> bladeTetra$departure = new ThreadLocal<>();
    @Unique private static final ThreadLocal<KyoukaAfterimages.Departure> bladeTetra$teleportDeparture = new ThreadLocal<>();
    @Inject(method = "handleSprintMove", at = @At("HEAD"))
    private void beforeDodge(ServerPlayer p, EnumSet<InputCommand> commands, CallbackInfoReturnable<Boolean> ci) {
        bladeTetra$departure.set(KyoukaAfterimages.capture(p));
    }
    @Inject(method = {"handleBackSprintSneak", "executeTrickUp"}, at = @At("HEAD"))
    private void beforeTrick(ServerPlayer p, CallbackInfoReturnable<Boolean> ci) {
        bladeTetra$departure.set(KyoukaAfterimages.capture(p));
    }
    @Inject(method = "handleSprintMove", at = @At("RETURN"))
    private void afterDodge(ServerPlayer p, EnumSet<InputCommand> commands, CallbackInfoReturnable<Boolean> ci) {
        var departure = bladeTetra$departure.get(); bladeTetra$departure.remove();
        KyoukaAfterimages.completed(departure);
    }
    @Inject(method = {"handleBackSprintSneak", "executeTrickUp"}, at = @At("RETURN"))
    private void afterTrick(ServerPlayer p, CallbackInfoReturnable<Boolean> ci) {
        var departure = bladeTetra$departure.get(); bladeTetra$departure.remove();
        KyoukaAfterimages.completed(departure);
    }
    @Inject(method = "executeTeleport", at = @At("HEAD"))
    private static void beforeTeleport(Entity entity, LivingEntity target, CallbackInfo ci) {
        bladeTetra$teleportDeparture.set(KyoukaAfterimages.capture(entity));
    }
    @Inject(method = "executeTeleport", at = @At("RETURN"))
    private static void afterTeleport(Entity entity, LivingEntity target, CallbackInfo ci) {
        var departure = bladeTetra$teleportDeparture.get(); bladeTetra$teleportDeparture.remove();
        KyoukaAfterimages.completed(departure);
    }
}
