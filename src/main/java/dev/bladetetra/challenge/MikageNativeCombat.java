package dev.bladetetra.challenge;

import dev.bladetetra.challenge.mikage.CastScope;
import mods.flammpfeil.slashblade.entity.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.phys.*;
import net.minecraft.world.level.ClipContext;
import java.util.*;
import java.util.function.*;

/** Native callbacks select, collide and hurt. This adapter authorizes targets before native effects. */
public final class MikageNativeCombat {
    record Rule(UUID target, boolean melee, boolean fresh, BiPredicate<ServerPlayer, Entity> contact,
                BiConsumer<ServerPlayer, Entity> hit) { }
    private record Binding(MikageEntity boss, CastScope scope, Rule rule, Set<UUID> contacts) { }
    private static final Map<Entity, Binding> ATTACKS = new WeakHashMap<>();
    private static final Map<CastScope, Set<Entity>> SCOPES = new IdentityHashMap<>();
    private static final ThreadLocal<Binding> SPAWNING = new ThreadLocal<>();
    private MikageNativeCombat() { }
    static void run(MikageEntity boss, CastScope scope, Rule rule, Runnable nativeAction) {
        Binding previous = SPAWNING.get();
        SPAWNING.set(new Binding(boss, scope, rule, new HashSet<>()));
        try { nativeAction.run(); }
        finally { if (previous == null) SPAWNING.remove(); else SPAWNING.set(previous); }
    }
    static boolean capture(Entity entity) {
        if (owns(entity)) return true;
        Binding binding = SPAWNING.get();
        if (binding == null) binding = ATTACKS.get(MikageBladeAttackTrace.projectile());
        if (binding == null || binding.scope.closed() || shooter(entity) != binding.boss) return false;
        track(entity, binding.boss, binding.scope, binding.rule);
        if (entity instanceof EntitySlashEffect slash) slash.setColor(0xFF1838);
        if (entity instanceof EntityJudgementCut cut) cut.setColor(0xFF1838);
        if (entity instanceof EntityDrive drive) drive.setColor(0xFF1838);
        return true;
    }
    static void track(Entity entity, MikageEntity boss, CastScope scope, Rule rule) {
        if (scope.closed()) { entity.discard(); return; }
        Set<Entity> owned = SCOPES.get(scope);
        if (owned == null) {
            owned = Collections.newSetFromMap(new IdentityHashMap<>()); SCOPES.put(scope, owned);
            scope.own(() -> discard(scope));
        }
        owned.removeIf(e -> { if (!e.isRemoved()) return false; ATTACKS.remove(e); return true; });
        owned.add(entity); ATTACKS.put(entity, new Binding(boss, scope, rule, new HashSet<>()));
        entity.getPersistentData().putBoolean("blade_tetra_mikage_attack", true);
        entity.getPersistentData().putLong("blade_tetra_mikage_cast", scope.id());
    }
    private static Binding binding(Entity source) {
        Binding b = ATTACKS.get(source), current = SPAWNING.get();
        return b != null ? b : current != null && source == current.boss ? current : null;
    }
    static boolean owns(Entity entity) {
        if (entity == null || entity.level().isClientSide()) return false;
        Binding b = binding(entity); return b != null && !b.scope.closed();
    }
    public static boolean mayCollide(Entity source, Entity target) {
        if (source.level().isClientSide()) return !authored(source);
        Binding b = ATTACKS.get(source);
        if (!(shooter(source) instanceof MikageEntity)) return true;
        return b != null && !b.scope.closed() && !source.getPersistentData().getBoolean("blade_tetra_reflected")
                && target instanceof ServerPlayer player && b.boss.encounter().eligible(player)
                && (b.rule.target == null || b.rule.target.equals(player.getUUID()))
                && !b.contacts.contains(player.getUUID());
    }
    public static boolean projectileContact(Entity source, Entity target) {
        if (!(shooter(source) instanceof MikageEntity)) return true;
        return mayCollide(source, target) && swordContact(source, (ServerPlayer) target);
    }
    public static boolean nativeImpact(Entity source, Entity target) {
        return owns(source) && (source instanceof MikageGateSwordEntity sword
                ? sword.nativeImpact(target) : mayCollide(source, target));
    }
    public static boolean mayReflect(Entity source, Entity actor) {
        if (source.level().isClientSide()) return true;
        if (!(shooter(source) instanceof MikageEntity boss)) return true;
        return owns(source) && actor instanceof ServerPlayer player && boss.encounter().eligible(player);
    }
    public static void reflected(Entity source) {
        if (owns(source)) source.getPersistentData().putBoolean("blade_tetra_reflected", true);
    }
    public static boolean authored(Entity source) { return shooter(source) instanceof MikageEntity; }
    static void clearAttacks(CastScope scope) {
        var owned = SCOPES.get(scope);
        if (owned != null) { for (Entity entity : owned) { ATTACKS.remove(entity); entity.discard(); } owned.clear(); }
    }
    static void discard(CastScope scope) {
        var owned = SCOPES.remove(scope);
        if (owned != null) for (Entity entity : owned) { ATTACKS.remove(entity); entity.discard(); }
    }
    public static Entity shooter(Entity source) {
        return source instanceof EntityAbstractSummonedSword sword ? sword.getShooter()
                : source instanceof EntityJudgementCut cut ? cut.getOwner()
                : source instanceof EntitySlashEffect slash ? slash.getShooter() : source;
    }
    public static boolean driving(LivingEntity actor) { Binding b = SPAWNING.get(); return b != null && b.boss == actor; }
    /** Null delegates to other bosses/players; authorized entities are returned to native AttackManager. */
    public static List<Entity> targets(Entity source, AABB bounds, double reach) {
        if (source.level().isClientSide()) return null;
        Entity actor = shooter(source), traced = MikageBladeAttackTrace.projectile();
        if (source == actor && traced != null && shooter(traced) == actor) source = traced;
        if (!(actor instanceof MikageEntity boss)) return null;
        Binding b = ATTACKS.get(source);
        if (b == null && source == boss) b = SPAWNING.get();
        if (b == null || b.boss != boss || b.scope.closed() || source.isRemoved()) return new ArrayList<>();
        List<Entity> result = new ArrayList<>();
        for (ServerPlayer player : List.copyOf(((net.minecraft.server.level.ServerLevel) boss.level()).players())) {
            if (!bounds.intersects(player.getBoundingBox()) || reach > 0 && actor.distanceToSqr(player) > reach * reach
                    || boss.level().clip(new ClipContext(source.getEyePosition(), player.getEyePosition(),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, source)).getType() != HitResult.Type.MISS) continue;
            if (contact(b, source, player)) result.add(player);
            if (b.scope.closed() || source.isRemoved()) { result.clear(); break; }
        }
        return result;
    }
    private static boolean contact(Binding b, Entity source, ServerPlayer player) {
        if (!b.boss.encounter().eligible(player) || b.rule.target != null && !b.rule.target.equals(player.getUUID())
                || b.contacts.contains(player.getUUID())) return false;
        if (!b.rule.fresh && b.boss.duel().protects(player)) return false;
        if (!b.rule.contact.test(player, source) || b.scope.closed()) return false;
        if (player.invulnerableTime > 0) return false;
        b.contacts.add(player.getUUID()); return true;
    }
    static boolean swordContact(Entity source, ServerPlayer player) {
        Binding b = ATTACKS.get(source); return b != null && !b.scope.closed() && contact(b, source, player);
    }
    static boolean fresh(ServerPlayer player) {
        Binding b = ATTACKS.get(MikageBladeAttackTrace.projectile()); if (b == null) b = SPAWNING.get();
        return b != null && b.rule.fresh && player.getUUID().equals(b.rule.target);
    }
    static void accepted(Entity source, ServerPlayer player) {
        Binding b = binding(source);
        if (b != null && !b.scope.closed() && b.contacts.contains(player.getUUID())) b.rule.hit.accept(player, source);
    }
    public static boolean managed(Consumer<Entity> attack, Entity target) {
        Entity source = MikageBladeAttackTrace.projectile();
        Binding b = ATTACKS.get(source); if (b == null) b = SPAWNING.get();
        if (b == null) return false;
        // Keep vanilla i-frames: native forceHit/resetHit must not erase another contact's protection.
        if (!b.scope.closed() && target instanceof ServerPlayer player && player.invulnerableTime <= 0) attack.accept(target);
        return true;
    }
    static void clear() { for (var scope : List.copyOf(SCOPES.keySet())) discard(scope); ATTACKS.clear(); SPAWNING.remove(); }
}
