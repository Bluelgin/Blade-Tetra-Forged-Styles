package dev.btsealed.runtime;

import mods.flammpfeil.slashblade.entity.EntitySlashEffect;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import mods.flammpfeil.slashblade.util.KnockBacks;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.fml.LogicalSide;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Standalone counterpart to the reinforced-habaki native melee/knockback pipeline.
 *
 * <p>SlashBlade cancels ordinary slash momentum on a callback after melee. Do
 * not just multiply LivingKnockBackEvent strength: it runs before the native
 * momentum formula, misses that callback and changes special toss/SA behavior.
 * Instead preserve ordinary slash momentum, then scale only the horizontal
 * impulse produced by a matching AttackManager melee scope after vanilla
 * resistance and damping. No source-mod class is referenced.</p>
 */
public final class PortableHabakiKnockback {
    private static final String REINFORCED_SLASH = "bt_export_reinforced_slash";
    private static final String NATIVE_KNOCKBACK_FACTOR = "knockback_factor";
    private static final double HORIZONTAL_SCALE = 1.5D;

    private record SlashContext(EntitySlashEffect slash) {}
    private static final ThreadLocal<Deque<SlashContext>> SLASHES =
            ThreadLocal.withInitial(ArrayDeque::new);
    private static final ThreadLocal<Deque<Hit>> HITS =
            ThreadLocal.withInitial(ArrayDeque::new);

    private static final class Hit {
        private final Entity target;
        private final boolean reinforced;
        private final boolean slash;
        private Vec3 before;
        private boolean applied;

        private Hit(Entity target, boolean reinforced, boolean slash) {
            this.target = target;
            this.reinforced = reinforced;
            this.slash = slash;
        }
    }

    static void slashSpawn(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || event.loadedFromDisk()
                || !(event.getEntity() instanceof EntitySlashEffect slash)
                || slash.getIndirect() || slash.getKnockBack() != KnockBacks.cancel
                || !(slash.getShooter() instanceof LivingEntity owner)) return;
        if (isReinforcedOrdinary(owner)) {
            slash.getPersistentData().putBoolean(REINFORCED_SLASH, true);
        }
    }

    /** Surrounds a native slash tick, including the area-attack melee callbacks. */
    public static void beginSlashTick(EntitySlashEffect slash) {
        SLASHES.get().push(new SlashContext(slash));
    }

    public static void endSlashTick() {
        var stack = SLASHES.get();
        if (!stack.isEmpty()) stack.pop();
        if (stack.isEmpty()) SLASHES.remove();
    }

    public static boolean keepMomentum(EntitySlashEffect slash) {
        return slash.getKnockBack() == KnockBacks.cancel
                && slash.getPersistentData().getBoolean(REINFORCED_SLASH);
    }

    /** Scoped around AttackManager.doMeleeAttack; nested attacks are restored safely. */
    public static void beginMelee(LivingEntity user, Entity target) {
        EntitySlashEffect slash = SLASHES.get().isEmpty() ? null : SLASHES.get().peek().slash();
        boolean reinforced = target != null && !target.getPersistentData().contains(NATIVE_KNOCKBACK_FACTOR)
                && (slash != null ? keepMomentum(slash) : isReinforcedOrdinary(user));
        HITS.get().push(new Hit(target, reinforced, slash != null));
    }

    public static void endMelee() {
        var stack = HITS.get();
        if (!stack.isEmpty()) stack.pop();
        if (stack.isEmpty()) HITS.remove();
    }

    /** Capture the target's velocity immediately before native knockback damping. */
    public static void beforeKnockback(LivingEntity target) {
        var stack = HITS.get();
        if (stack.isEmpty()) return;
        Hit hit = stack.peek();
        if (hit.reinforced && hit.target == target && hit.before == null) {
            hit.before = target.getDeltaMovement();
        }
    }

    /** Applies horizontal scaling only after the actual vanilla knockback result. */
    public static void afterKnockback(LivingEntity target) {
        var stack = HITS.get();
        if (stack.isEmpty()) return;
        Hit hit = stack.peek();
        if (!hit.reinforced || hit.target != target || hit.before == null || hit.applied) return;
        hit.applied = true;
        Vec3 current = target.getDeltaMovement();
        Vec3 before = hit.before;
        // On a resisted, cancelled or otherwise skipped native knockback no impulse
        // was applied. Preserve vanilla semantics rather than inventing motion.
        if (current.equals(before)) return;
        double x = horizontal(before.x, current.x);
        double z = horizontal(before.z, current.z);
        target.setDeltaMovement(x, hit.slash ? before.y : current.y, z);
    }

    public static double horizontal(double incoming, double result) {
        double retained = incoming * .5D;
        return retained + (result - retained) * HORIZONTAL_SCALE;
    }

    private static boolean isReinforcedOrdinary(LivingEntity owner) {
        if (owner.level().isClientSide()) return false;
        var blade = owner.getMainHandItem();
        var profile = PortableProfiles.forBlade(blade, LogicalSide.SERVER);
        if (profile == null || !profile.effects().reinforcedHabaki()) return false;
        var state = blade.getCapability(ItemSlashBlade.BLADESTATE).orElse(null);
        if (state == null || state.isBroken()
                || PortableCombos.DANGAKU.getId().equals(state.getComboRoot())) return false;
        return ordinaryCombo(state.getComboSeq());
    }

    /**
     * Explicit ordinary-action allowlist. Native Slash Arts and unsupported
     * unusual combos never receive a reinforced impulse by accident.
     */
    static boolean ordinaryCombo(ResourceLocation combo) {
        if (combo == null) return false;
        if (PortableCombos.hasMove(combo)
                || combo.equals(ComboStateRegistry.AERIAL_RAVE_A1.getId())
                || combo.equals(ComboStateRegistry.AERIAL_CLEAVE.getId())) return true;
        if (!PortableBladeRuntime.ID.equals(combo.getNamespace())) return false;
        String name = combo.getPath();
        if (name.startsWith("sequence_")) {
            // All registered custom moves use this prefix; no other SA does.
            return name.matches("sequence_[0-6]_[a-z0-9_]+");
        }
        return name.equals("iaido_draw") || name.equals("iaido_follow")
                || name.equals("iaido_return") || name.equals("iaido_finish")
                || name.equals("iaido_sheathe");
    }

    private PortableHabakiKnockback() {}
}
