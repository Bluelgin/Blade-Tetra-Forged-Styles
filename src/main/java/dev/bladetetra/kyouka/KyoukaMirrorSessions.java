package dev.bladetetra.kyouka;

import dev.bladetetra.combat.*;
import dev.bladetetra.config.GameplayConfig;
import dev.bladetetra.easteregg.*;
import dev.bladetetra.item.ModularSlashBladeItem;
import dev.bladetetra.network.*;
import mods.flammpfeil.slashblade.capability.concentrationrank.IConcentrationRank;
import mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState;
import mods.flammpfeil.slashblade.entity.*;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.animal.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import java.util.*;

/** Event-created, bounded state. No movement history, world entity query or recurring mirror session. */
@Mod.EventBusSubscriber(modid = "blade_tetra")
public final class KyoukaMirrorSessions {
    static final String WEAPON = "blade_tetra_awakened_v2_id";
    private static final String COOLDOWN = "blade_tetra_mirror_recall_until";
    static final Map<UUID, State> STATES = new HashMap<>();
    private static final Map<EntitySlashEffect, Action> SHOTS = new IdentityHashMap<>();
    record Action(ItemStack blade, UUID weapon, ResourceLocation combo, long started) {}
    record LastMirrorSlash(Action action, UUID target, int targetId, long tick, Vec3 anchor,
            float yaw, float baseline, BladeStyle style, CombatBalanceRules.AttackKind kind) {}
    record Pending(LastMirrorSlash slash, long due, boolean water, UUID visual) {}
    static final class State {
        final ServerPlayer owner;
        final ResourceLocation dimension;
        Action action, accepted;
        LastMirrorSlash last;
        final List<Pending> pending = new ArrayList<>();
        ItemStack armorBlade;
        UUID armorWeapon;
        long promotion = -1, nextAttack, replayUntil;
        State(ServerPlayer owner) { this.owner = owner; dimension = owner.level().dimension().location(); }
    }
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
    static boolean inRange(ServerPlayer owner, LivingEntity target) {
        double range = GameplayConfig.KYOUKA_RANGE.get();
        return owner.distanceToSqr(target) <= range * range && owner.serverLevel().hasChunkAt(target.blockPosition())
                && owner.serverLevel().getEntity(target.getId()) == target;
    }
    private static long clock(ServerPlayer p) { return p.getServer().overworld().getGameTime(); }
    private static UUID weapon(ItemStack blade) {
        var tag = blade.getOrCreateTag();
        if (!tag.hasUUID(WEAPON)) tag.putUUID(WEAPON, UUID.randomUUID());
        return tag.getUUID(WEAPON);
    }
    private static State state(ServerPlayer p) {
        State s = STATES.get(p.getUUID());
        if (s == null && STATES.size() < GameplayConfig.KYOUKA_MAX_SESSIONS.get()
                && STATES.values().stream().filter(v -> v.owner.level()==p.level()).count() < GameplayConfig.KYOUKA_WORLD_SESSIONS.get()) {
            s = new State(p); STATES.put(p.getUUID(), s);
        }
        return s;
    }
    static void committed(ServerPlayer p, ISlashBladeState nativeState) {
        State s = state(p);
        if (s == null) return;
        var combo = nativeState.getComboSeq();
        if (combo.getPath().equals("none") || combo.getPath().equals("standby")) return;
        s.action = new Action(p.getMainHandItem(), weapon(p.getMainHandItem()), combo, nativeState.getLastActionTime());
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void spawned(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || event.loadedFromDisk() || event.getEntity().getClass() != EntitySlashEffect.class
                || !(event.getEntity() instanceof EntitySlashEffect slash)
                || !(slash.getShooter() instanceof ServerPlayer p)) return;
        State s = STATES.get(p.getUUID());
        if (s != null && s.action != null && validAction(s, s.action) && SHOTS.size() < 1024)
            SHOTS.put(slash, s.action);
    }
    static boolean validAction(State s, Action a) {
        var p = s.owner;
        return !p.isRemoved() && p.isAlive() && !p.isSpectator()
                && p.level().dimension().location().equals(s.dimension)
                && a != null && p.getMainHandItem() == a.blade && eligible(a.blade)
                && a.blade.hasTag() && a.weapon.equals(a.blade.getTag().getUUID(WEAPON));
    }
    public static boolean armored(ServerPlayer p) {
        State s = STATES.get(p.getUUID());
        return p.hasEffect(WaterMirrorEffect.ARMOR.get()) && s != null && s.armorBlade != null
                && p.getMainHandItem() == s.armorBlade && eligible(s.armorBlade) && p.isAlive() && !p.isSpectator()
                && !p.isRemoved() && p.getAirSupply() > 0 && p.level().dimension().location().equals(s.dimension)
                && s.armorWeapon.equals(weapon(s.armorBlade));
    }
    static void activate(ServerPlayer p, Set<EntitySpiralSwords> ignored) {
        if (!p.isAlive() || p.isRemoved() || p.isSpectator() || p.getAirSupply() <= 0 || !eligible(p.getMainHandItem())) return;
        State s = state(p);
        if (s == null || armored(p)) return;
        cancelPending(s); s.last = null; s.promotion = -1; s.replayUntil = 0;
        s.armorBlade = p.getMainHandItem(); s.armorWeapon = weapon(s.armorBlade);
        p.addEffect(new MobEffectInstance(WaterMirrorEffect.ARMOR.get(), -1, 0, false, false, true));
    }
    public static void rankChanged(LivingEntity user, int before, int after) {
        if (!(user instanceof ServerPlayer p) || before >= IConcentrationRank.ConcentrationRanks.SSS.level
                || after < IConcentrationRank.ConcentrationRanks.SSS.level || !eligible(p.getMainHandItem())) return;
        State s = state(p);
        if (s != null && !armored(p)) s.promotion = p.level().getGameTime();
    }
    static void hit(ServerPlayer p, LivingEntity target, float baseline, DamageSource source) {
        State s = STATES.get(p.getUUID());
        if (s == null || !legal(p, target) || !inRange(p, target)) return;
        Action a = source.getDirectEntity() instanceof EntitySlashEffect slash ? SHOTS.get(slash) : s.action;
        long now = p.level().getGameTime();
        if (!validAction(s, a) || s.accepted == a || now < s.nextAttack || now < s.replayUntil) return;
        var nativeState = p.getMainHandItem().getCapability(ItemSlashBlade.BLADESTATE).orElse(null);
        if (!(source.getDirectEntity() instanceof EntitySlashEffect)
                && (now - a.started > 60 || nativeState == null || !nativeState.getComboSeq().equals(a.combo)
                || nativeState.getLastActionTime() != a.started)) return;
        s.accepted = a; s.nextAttack = now + 2;
        float phase = CombatBalanceRuntime.secondaryPhaseScale(p, source.getDirectEntity());
        if (!Float.isFinite(phase) || phase <= 0) return;
        var last = new LastMirrorSlash(a, target.getUUID(), target.getId(), now, target.position(),
                p.getYRot(), Math.min(1024, baseline * phase), StyleResolver.resolve(a.blade), CombatBalanceRuntime.kind(p, source.getDirectEntity()));
        if (armored(p)) {
            if (s.pending.size() < 8) {
                s.pending.add(new Pending(last, now + 14, true, UUID.randomUUID()));
                WaterMirrorCombat.visual(p, target, KyoukaVfxPacket.WATER_GATHER, 14);
            }
        } else s.last = last;
    }
    private static net.minecraft.nbt.CompoundTag persisted(ServerPlayer p) {
        var root = p.getPersistentData();
        if (!root.contains(Player.PERSISTED_NBT_TAG)) root.put(Player.PERSISTED_NBT_TAG, new net.minecraft.nbt.CompoundTag());
        return root.getCompound(Player.PERSISTED_NBT_TAG);
    }
    static void endArmor(State s) {
        s.owner.removeEffect(WaterMirrorEffect.ARMOR.get());
        s.armorBlade = null; s.armorWeapon = null;
        s.pending.removeIf(Pending::water); s.last = null; s.promotion = -1;
    }
    private static KyoukaMirrorPacket packet(State s, Pending h, boolean stop) {
        var l = h.slash;
        return new KyoukaMirrorPacket(h.visual, s.owner.getUUID(), l.action.weapon, l.target, l.targetId,
                s.dimension, l.action.combo, h.due, 1, l.yaw, l.anchor, stop, stop ? ItemStack.EMPTY : l.action.blade.copy());
    }
    private static void mirrorPacket(State s, Pending h, boolean stop) {
        var l = h.slash;
        ModNetwork.CHANNEL.send(PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(
                l.anchor.x, l.anchor.y, l.anchor.z, 96, net.minecraft.resources.ResourceKey.create(
                        net.minecraft.core.registries.Registries.DIMENSION, s.dimension))), packet(s,h,stop));
    }
    private static void cancelPending(State s) {
        for (var h : s.pending) if (!h.water) mirrorPacket(s, h, true);
        s.pending.clear();
    }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) step();
    }
    static void step() {
        KyoukaSpiralObserver.flush();
        SHOTS.keySet().removeIf(e -> !e.isAlive() || e.isRemoved());
        for (State s : List.copyOf(STATES.values())) {
            var p = s.owner; long now = p.level().getGameTime();
            if (p.isRemoved() || !p.isAlive() || !eligible(p.getMainHandItem())
                    || !p.level().dimension().location().equals(s.dimension)) {
                endArmor(s); cancelPending(s); STATES.remove(p.getUUID(), s); continue;
            }
            if (s.armorBlade != null && !armored(p)) endArmor(s);
            if (s.last != null && (now - s.last.tick > 60 || !validAction(s, s.last.action))) s.last = null;
            // Deferred to tick END: native Rank may rise inside hurt, before the hit callback returns.
            if (s.promotion >= 0) {
                if (!armored(p) && s.last != null && now - s.promotion <= 1
                        && clock(p) >= persisted(p).getLong(COOLDOWN)) {
                    var h = new Pending(s.last, now + 10, false, UUID.randomUUID());
                    persisted(p).putLong(COOLDOWN, clock(p) + 600);
                    s.pending.add(h); WaterMirrorCombat.visual(p, p, KyoukaVfxPacket.WATER_DEFLECT, 10); s.replayUntil = now + 30; mirrorPacket(s, h, false);
                }
                s.promotion = -1;
            }
            Iterator<Pending> it = s.pending.iterator();
            while (it.hasNext()) {
                Pending h = it.next();
                if (!validAction(s, h.slash.action) || h.water && !armored(p)) {
                    if (!h.water) mirrorPacket(s, h, true); it.remove(); continue;
                }
                if (now < h.due) continue;
                if (now == h.due) KyoukaMirrorDamageService.apply(s, h);
                if (h.water || now >= h.due + 20) {
                    if (!h.water) mirrorPacket(s, h, true); it.remove();
                }
            }
        }
        WaterMirrorCombat.prune();
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        State s = STATES.remove(event.getEntity().getUUID());
        if (s != null) { endArmor(s); cancelPending(s); }
    }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        State s = STATES.remove(event.getEntity().getUUID());
        if (s != null) { endArmor(s); cancelPending(s); }
    }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent e) {
        // Infinite effects saved during an unclean shutdown have no valid server weapon session.
        e.getEntity().removeEffect(WaterMirrorEffect.ARMOR.get());
    }
    @SubscribeEvent public static void tracking(PlayerEvent.StartTracking e) {
        if (!(e.getEntity() instanceof ServerPlayer viewer)) return;
        for (State s : STATES.values()) {
            if (s.owner.level()!=viewer.level()) continue;
            for (Pending h : s.pending) if (!h.water && (e.getTarget()==s.owner || e.getTarget().getUUID().equals(h.slash.target)))
                ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> viewer),packet(s,h,false));
        }
    }
    @SubscribeEvent public static void unload(net.minecraftforge.event.level.LevelEvent.Unload e) {
        if (e.getLevel().isClientSide()) return;
        for (State s : List.copyOf(STATES.values())) if (s.owner.level()==e.getLevel()) {
            endArmor(s); cancelPending(s); STATES.remove(s.owner.getUUID(),s);
        }
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) {
        for (State s : List.copyOf(STATES.values())) { endArmor(s); cancelPending(s); }
        STATES.clear(); SHOTS.clear(); KyoukaSpiralObserver.clear(); WaterMirrorCombat.clear();
    }
    private KyoukaMirrorSessions() {}
}
