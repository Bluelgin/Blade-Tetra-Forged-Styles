package dev.bladetetra.kyouka;

import dev.bladetetra.config.GameplayConfig;
import dev.bladetetra.easteregg.*;
import dev.bladetetra.item.ModularSlashBladeItem;
import dev.bladetetra.network.*;
import mods.flammpfeil.slashblade.capability.concentrationrank.CapabilityConcentrationRank;
import mods.flammpfeil.slashblade.capability.concentrationrank.IConcentrationRank;
import mods.flammpfeil.slashblade.entity.EntitySpiralSwords;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.animal.*;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import java.util.*;

@Mod.EventBusSubscriber(modid = "blade_tetra")
public final class KyoukaMirrorSessions {
    static final Map<UUID, KyoukaMirrorSession> SESSIONS = new HashMap<>();
    private static final String WEAPON = "blade_tetra_awakened_v2_id";
    public static boolean eligible(ItemStack blade) {
        return GameplayConfig.ENABLE_AWAKENED_V2.get() && blade.getItem() instanceof ModularSlashBladeItem
                && SoulLegacyState.active(blade) == SoulLegacyState.Legacy.KYOUKA
                && SoulLegacyState.isActive(blade, SoulLegacyState.Legacy.KYOUKA)
                && blade.getCapability(ItemSlashBlade.BLADESTATE).map(s -> !s.isBroken()).orElse(false);
    }
    public static boolean legal(ServerPlayer owner, LivingEntity target) {
        return target != owner && target.isAlive() && target.level() == owner.level()
                && target.isAttackable() && !(target instanceof Player) && !(target instanceof TamableAnimal)
                && !(target instanceof Animal) && !(target instanceof WaterAnimal)
                && !owner.isAlliedTo(target) && !target.isAlliedTo(owner)
                && (target instanceof Enemy || target instanceof Mob mob && mob.getTarget() == owner);
    }
    static void activate(ServerPlayer owner, Set<EntitySpiralSwords> ring) {
        if (!eligible(owner.getMainHandItem()) || SESSIONS.containsKey(owner.getUUID())
                || SESSIONS.size() >= GameplayConfig.KYOUKA_MAX_SESSIONS.get()
                || SESSIONS.values().stream().filter(s -> s.owner.level() == owner.level()).count()
                        >= GameplayConfig.KYOUKA_WORLD_SESSIONS.get()) return;
        var state = owner.getMainHandItem().getCapability(ItemSlashBlade.BLADESTATE).orElse(null);
        Entity locked = state == null ? null : state.getTargetEntity(owner.level());
        if (!(locked instanceof LivingEntity target) || !legal(owner, target)
                || !inRange(owner, target) || !owner.hasLineOfSight(target)) return;
        var rank = owner.getCapability(CapabilityConcentrationRank.RANK_POINT).orElse(null);
        // Other capability implementations may override default addRankPoint; fail closed.
        if (rank == null || rank.getClass() != mods.flammpfeil.slashblade.capability.concentrationrank.ConcentrationRank.class) return;
        int cost = GameplayConfig.KYOUKA_START_COST.get();
        long now = owner.level().getGameTime();
        if (!KyoukaRules.canStart(rank.getRankPoint(now), rank.getUnitCapacity(), rank.getRank(now).level, cost)) return;
        var tag = owner.getMainHandItem().getOrCreateTag();
        if (!tag.hasUUID(WEAPON)) tag.putUUID(WEAPON, UUID.randomUUID());
        var session = new KyoukaMirrorSession(owner, target, tag.getUUID(WEAPON), ring);
        SESSIONS.put(owner.getUUID(), session);
        AwakenedRankConstraintService.lock(rank, owner.getUUID());
        rank.addRankPoint(owner, -cost);
        if (!valid(session)) { stop(session); return; }
        viewers(session);
    }
    static boolean inRange(ServerPlayer owner, LivingEntity target) {
        double range = GameplayConfig.KYOUKA_RANGE.get();
        return owner.distanceToSqr(target) <= range * range
                && owner.serverLevel().hasChunkAt(target.blockPosition())
                && owner.serverLevel().getEntity(target.getId()) == target;
    }
    static boolean valid(KyoukaMirrorSession s) {
        if (s.owner.isRemoved() || !s.owner.isAlive() || s.owner.isSpectator()
                || s.owner.getMainHandItem() != s.blade || !eligible(s.blade)
                || !s.blade.getOrCreateTag().hasUUID(WEAPON)
                || !s.weapon.equals(s.blade.getOrCreateTag().getUUID(WEAPON))
                || !legal(s.owner, s.target) || !inRange(s.owner, s.target)) return false;
        s.ring.removeIf(e -> !e.isAlive() || e.itFired() || e.level() != s.owner.level()
                || e.getOwner() != s.owner || e.getVehicle() != s.owner
                || s.owner.serverLevel().getEntity(e.getId()) != e);
        if (s.ring.isEmpty()) return false;
        return s.owner.getCapability(CapabilityConcentrationRank.RANK_POINT).map(r ->
                KyoukaRules.sustain(r.getRankPoint(s.owner.level().getGameTime()), r.getUnitCapacity(),
                        r.getRank(s.owner.level().getGameTime()).level)).orElse(false);
    }
    static boolean spend(KyoukaMirrorSession s, int amount) {
        IConcentrationRank rank = s.owner.getCapability(CapabilityConcentrationRank.RANK_POINT).orElse(null);
        if (!valid(s) || rank == null || rank.getRankPoint(s.owner.level().getGameTime()) < amount) return false;
        rank.addRankPoint(s.owner, -amount);
        return true;
    }
    static void send(ServerPlayer viewer, KyoukaMirrorSession s, boolean full, boolean stop) {
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> viewer),
                new KyoukaMirrorPacket(s.id, s.owner.getUUID(), s.weapon,
                        s.target.getUUID(), s.target.getId(), s.owner.level().dimension().location(),
                        s.combo, s.started, s.action, s.yaw, stop, full ? s.blade.copy() : ItemStack.EMPTY));
    }
    static void changed(KyoukaMirrorSession s) {
        for (UUID id : s.viewers) {
            var p = s.owner.getServer().getPlayerList().getPlayer(id);
            if (p != null) send(p, s, false, false);
        }
    }
    private static void viewers(KyoukaMirrorSession s) {
        Set<UUID> current = new HashSet<>();
        for (ServerPlayer p : s.owner.serverLevel().players()) {
            if (p.distanceToSqr(s.target) > 96*96) continue;
            current.add(p.getUUID());
            if (!s.viewers.contains(p.getUUID())) send(p, s, true, false);
        }
        for (UUID id : s.viewers) if (!current.contains(id)) {
            var p = s.owner.getServer().getPlayerList().getPlayer(id);
            if (p != null) send(p, s, false, true);
        }
        s.viewers.clear(); s.viewers.addAll(current);
    }
    static void stop(KyoukaMirrorSession s) {
        SESSIONS.remove(s.owner.getUUID(), s);
        for (UUID id : s.viewers) {
            var p = s.owner.getServer().getPlayerList().getPlayer(id);
            if (p != null) send(p, s, false, true);
        }
        AwakenedRankConstraintService.unlock(s.owner.getUUID());
        s.shots.clear(); s.budgets.clear(); s.viewers.clear();
    }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        step();
    }
    static void step() {
        // End old batches before accepting a replacement native cast.
        for (var s : List.copyOf(SESSIONS.values())) {
            long now = s.owner.level().getGameTime();
            if (!valid(s)) { stop(s); continue; }
            if (now >= s.nextDrain) {
                s.nextDrain = now + 20;
                if (!spend(s, GameplayConfig.KYOUKA_DRAIN.get()) || !valid(s)) { stop(s); continue; }
            }
            s.shots.values().removeIf(shot -> !shot.entity().isAlive());
            if (now % 10 == 0) viewers(s);
            if (now % 80 == 0) changed(s);
        }
        KyoukaSpiralObserver.flush();
        KyoukaMirrorDamageService.flush();
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        var s = SESSIONS.get(event.getEntity().getUUID()); if (s != null) stop(s);
    }
    @SubscribeEvent public static void unload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) return;
        for (var s : List.copyOf(SESSIONS.values())) if (s.owner.level() == event.getLevel()) stop(s);
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) {
        for (var s : List.copyOf(SESSIONS.values())) stop(s);
        KyoukaSpiralObserver.clear(); KyoukaMirrorDamageService.clear();
        AwakenedRankConstraintService.clear();
    }
    private KyoukaMirrorSessions() {}
}
