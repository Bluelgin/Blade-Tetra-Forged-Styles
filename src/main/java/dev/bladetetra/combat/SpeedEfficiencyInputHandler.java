package dev.bladetetra.combat;

import dev.bladetetra.BladeTetra;
import mods.flammpfeil.slashblade.capability.inputstate.IInputState;
import mods.flammpfeil.slashblade.capability.mobeffect.CapabilityMobEffect;
import mods.flammpfeil.slashblade.event.handler.InputCommandEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;

/** Observe native ground-dodge recovery before/after input, without changing dodge execution. */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID)
public final class SpeedEfficiencyInputHandler {
    // The native sword scheduler lambda receives IInputState but not its owner.
    // Weak keys AND weak owners avoid keeping disconnected players/capabilities alive.
    private static final Map<IInputState, WeakReference<ServerPlayer>> OWNERS = new WeakHashMap<>();
    private static final Map<InputCommandEvent, Long> PREVIOUS_RECOVERY = new WeakHashMap<>();
    private SpeedEfficiencyInputHandler() { }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void beforeInput(InputCommandEvent event) {
        OWNERS.put(event.getState(), new WeakReference<>(event.getEntity()));
        event.getEntity().getCapability(CapabilityMobEffect.MOB_EFFECT).ifPresent(state ->
                PREVIOUS_RECOVERY.put(event, state.getAvoidCooldown().orElse(Long.MIN_VALUE)));
    }

    public static ServerPlayer owner(IInputState input) {
        WeakReference<ServerPlayer> reference = OWNERS.get(input);
        return reference == null ? null : reference.get();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void afterInput(InputCommandEvent event) {
        Long previous = PREVIOUS_RECOVERY.remove(event);
        ServerPlayer player = event.getEntity();
        if (previous == null || !SpeedEfficiencyRuntime.eligible(player)) return;
        long now = player.level().getGameTime();
        player.getCapability(CapabilityMobEffect.MOB_EFFECT).ifPresent(state -> {
            long deadline = state.getAvoidCooldown().orElse(Long.MIN_VALUE);
            // Native doAvoid opens a 20-tick cycle on its first successful dodge.
            // Never reduce it again for the second/third dodge or for a failed input.
            if (state.getAvoidCount() == 1 && deadline != previous
                    && deadline == now + SpeedEfficiencyRules.NATIVE_AVOID_RECOVERY) {
                state.setAvoidCooldown(java.util.Optional.of(now + SpeedEfficiencyRules.ticks(
                        SpeedEfficiencyRules.NATIVE_AVOID_RECOVERY, SpeedEfficiencyRuntime.reduction(player))));
            }
        });
    }

    @SubscribeEvent public static void stopped(ServerStoppedEvent event) {
        OWNERS.clear(); PREVIOUS_RECOVERY.clear();
    }
}
