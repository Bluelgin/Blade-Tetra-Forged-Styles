package dev.bladetetra.kyouka;

import dev.bladetetra.network.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import java.util.*;

/** Public Forge breathing and final-health-damage hooks. No private oxygen store. */
@Mod.EventBusSubscriber(modid = "blade_tetra")
public final class WaterMirrorCombat {
    private record Ledger(WaterMirrorDefenseBudget budget, net.minecraft.server.MinecraftServer server) {}
    private record Payment(ServerPlayer player, LivingDamageEvent event, float health, float saved, long tick) {}
    private static final Map<UUID, Ledger> DEFENSE = new HashMap<>();
    private static final Map<UUID, Payment> PAYMENTS = new HashMap<>();
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void breathe(LivingBreatheEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !KyoukaMirrorSessions.armored(p)) return;
        // Forge has already called decreaseAirSupply: keep its Respiration-dependent amount.
        if (p.getAirSupply() <= Math.max(1, e.getConsumeAirAmount())) {
            var s = KyoukaMirrorSessions.STATES.get(p.getUUID());
            if (s != null) KyoukaMirrorSessions.endArmor(s);
            return; // Original conditions remain intact, including real underwater drowning.
        }
        if (p.hasEffect(MobEffects.WATER_BREATHING) || p.hasEffect(MobEffects.CONDUIT_POWER)
                || p.getAbilities().invulnerable) return;
        // Other mods may alter this event after us, including oxygen and infinite-breath equipment.
        e.setCanBreathe(false);
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void defend(LivingDamageEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !KyoukaMirrorSessions.armored(p)
                || e.getAmount() <= 0 || e.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)
                || e.getSource().is(DamageTypeTags.BYPASSES_EFFECTS) || PAYMENTS.containsKey(p.getUUID())) return;
        long now = p.getServer().overworld().getGameTime();
        var ledger = DEFENSE.computeIfAbsent(p.getUUID(), id -> new Ledger(new WaterMirrorDefenseBudget(), p.getServer()));
        float saved = ledger.budget.available(now, e.getAmount(), p.getAirSupply(), p.getMaxAirSupply());
        if (saved <= 0) return;
        PAYMENTS.put(p.getUUID(), new Payment(p, e, p.getHealth(), saved, now));
        e.setAmount(e.getAmount() - saved);
    }
    public static void finish(ServerPlayer p) {
        Payment payment = PAYMENTS.remove(p.getUUID());
        if (payment == null) return;
        if (!payment.event.isCanceled() && payment.event.getAmount() > 0 && p.getHealth() < payment.health) {
            DEFENSE.get(p.getUUID()).budget.commit(payment.tick, payment.saved);
            p.setAirSupply(Math.max(0, p.getAirSupply() - 20));
            visual(p, p, KyoukaVfxPacket.WATER_DEFLECT, 12);
            if (p.getAirSupply() <= 0) {
                var s = KyoukaMirrorSessions.STATES.get(p.getUUID());
                if (s != null) KyoukaMirrorSessions.endArmor(s);
            }
        }
    }
    static void prune() {
        // A throwing damage callback must not leave an uncommitted reservation alive.
        PAYMENTS.clear();
        DEFENSE.values().removeIf(l -> l.budget.expired(l.server.overworld().getGameTime()));
    }
    static void visual(ServerPlayer p, LivingEntity target, int type, int duration) {
        var pos = target.position();
        ModNetwork.CHANNEL.send(PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(
                pos.x, pos.y, pos.z, 96, p.level().dimension())), new KyoukaVfxPacket(type, pos.x, pos.y, pos.z,
                p.getId(), target.getId(), 1, duration, p.getRandom().nextInt()));
    }
    static void clear() { DEFENSE.clear(); PAYMENTS.clear(); }
    private WaterMirrorCombat() {}
}
