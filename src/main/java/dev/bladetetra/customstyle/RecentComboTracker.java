package dev.bladetetra.customstyle;

import dev.bladetetra.BladeTetra;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Observes the real committed blade state, never raw mouse input.
 * Only one most-recent distinct attack is retained per connected player.
 */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class RecentComboTracker {
    private static final Map<UUID, LastAction> LAST = new ConcurrentHashMap<>();
    private static final Map<UUID, ObservedState> OBSERVED = new ConcurrentHashMap<>();
    private static final long MAX_AGE_TICKS = 20L * 20L;

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof ItemSlashBlade)) return;

        stack.getCapability(ItemSlashBlade.BLADESTATE).ifPresent(state -> {
            ResourceLocation action = state.getComboSeq();
            long started = state.getLastActionTime();
            long now = player.level().getGameTime();
            if (action == null || started < 0L || started > now
                    || now - started > MAX_AGE_TICKS) return;

            ObservedState sample = new ObservedState(action, started);
            ObservedState previous = OBSERVED.put(player.getUUID(), sample);
            if (sample.equals(previous) || !ComboCompatibilityResolver.playable(action)) return;

            long sequence = LAST.containsKey(player.getUUID())
                    ? LAST.get(player.getUUID()).sequence() + 1L : 1L;
            LAST.put(player.getUUID(), new LastAction(action, sequence, now));
        });
    }

    public static LastAction mostRecent(ServerPlayer player) {
        LastAction last = LAST.get(player.getUUID());
        return last != null && player.level().getGameTime() - last.observedAt() <= MAX_AGE_TICKS
                ? last : null;
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        LAST.remove(id);
        OBSERVED.remove(id);
        StyleRecordingSession.discard(id);
    }

    public record LastAction(ResourceLocation id, long sequence, long observedAt) {}
    private record ObservedState(ResourceLocation id, long lastActionTime) {}
    private RecentComboTracker() {}
}
