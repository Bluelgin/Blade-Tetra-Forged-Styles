package dev.bladetetra.challenge;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.WeakHashMap;

/** Adapted from BlackFoxAttackTrace: native areaAttack rewrites slash/cut sources to their shooter. */
public final class MikageBladeAttackTrace {
    private static final Map<Entity, ItemStack> BIRTH_BLADES = new WeakHashMap<>();
    private static final ThreadLocal<Deque<Entity>> ACTIVE = ThreadLocal.withInitial(ArrayDeque::new);

    static void capture(Entity projectile, ItemStack blade) { BIRTH_BLADES.put(projectile, blade); }
    static ItemStack birthBlade(Entity projectile) { return BIRTH_BLADES.get(projectile); }
    public static void enter(Entity projectile) { ACTIVE.get().push(projectile); }
    public static void exit() {
        Deque<Entity> stack = ACTIVE.get();
        if (!stack.isEmpty()) stack.pop();
        if (stack.isEmpty()) ACTIVE.remove();
    }
    static Entity projectile() { return ACTIVE.get().peek(); }
    static void clear() { BIRTH_BLADES.clear(); ACTIVE.remove(); }
    private MikageBladeAttackTrace() {}
}
