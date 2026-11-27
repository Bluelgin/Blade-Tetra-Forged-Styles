package dev.bladetetra.combat;

import net.minecraft.world.entity.Entity;

import java.util.ArrayDeque;
import java.util.Deque;

/** SlashBlade's area attacks can expose the shooter in place of the native slash entity. */
public final class NativeSlashProvenance {
    private static final ThreadLocal<Deque<Entity>> ACTIVE = new ThreadLocal<>();

    private NativeSlashProvenance() {}

    public static void enter(Entity projectile) {
        var stack = ACTIVE.get();
        if (stack == null) {
            stack = new ArrayDeque<>();
            ACTIVE.set(stack);
        }
        stack.push(projectile);
    }

    public static void exit() {
        var stack = ACTIVE.get();
        if (stack == null) return;
        stack.pop();
        if (stack.isEmpty()) ACTIVE.remove();
    }

    public static Entity projectile() {
        var stack = ACTIVE.get();
        return stack == null ? null : stack.peek();
    }
}
