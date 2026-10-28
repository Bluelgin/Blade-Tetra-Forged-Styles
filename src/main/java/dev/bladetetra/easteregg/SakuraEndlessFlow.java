package dev.bladetetra.easteregg;

import dev.bladetetra.BladeTetra;
import mods.flammpfeil.slashblade.capability.concentrationrank.CapabilityConcentrationRank;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/** Dispersed Rank anti-credit guard only. Ordinary combos never start or pay for an SA. */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID)
public final class SakuraEndlessFlow {
    private record Ceiling(ItemStack blade, int slot, Object dimension, long points) {}
    private static final Map<UUID, Ceiling> CEILINGS = new HashMap<>();
    public static boolean eligible(ItemStack blade) {
        return SakuraStanceController.supported(blade) && SakuraStanceController.dispersed(blade);
    }
    public static boolean isBurning(ServerPlayer player, ItemStack blade) {
        return SakuraInfiniteDanceSA.active(player, blade);
    }
    public static int initialRank(ServerPlayer player, ItemStack blade) {
        return SakuraInfiniteDanceSA.initialRank(player, blade);
    }
    @SubscribeEvent public static void onPlayerTick(TickEvent.PlayerTickEvent e) {
        if (!(e.player instanceof ServerPlayer player)) return;
        ItemStack blade = player.getMainHandItem();
        if (!player.isAlive() || !eligible(blade)) { CEILINGS.remove(player.getUUID()); return; }
        player.getCapability(CapabilityConcentrationRank.RANK_POINT).ifPresent(rank -> {
            long now = player.level().getGameTime(), points = Math.max(0, rank.getRankPoint(now));
            Ceiling old = CEILINGS.get(player.getUUID());
            if (old != null && old.blade == blade && old.slot == player.getInventory().selected
                    && old.dimension.equals(player.level().dimension()) && points > old.points) {
                rank.addRankPoint(player, old.points - points);
                points = Math.max(0, rank.getRankPoint(now));
            }
            CEILINGS.put(player.getUUID(), new Ceiling(blade, player.getInventory().selected,
                    player.level().dimension(), points));
        });
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e) { CEILINGS.remove(e.getEntity().getUUID()); }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e) { CEILINGS.remove(e.getEntity().getUUID()); }
    @SubscribeEvent public static void death(LivingDeathEvent e) { CEILINGS.remove(e.getEntity().getUUID()); }
    @SubscribeEvent public static void clone(PlayerEvent.Clone e) { CEILINGS.remove(e.getEntity().getUUID()); }
    @SubscribeEvent public static void stop(ServerStoppedEvent e) { CEILINGS.clear(); }
    private SakuraEndlessFlow() {}
}
