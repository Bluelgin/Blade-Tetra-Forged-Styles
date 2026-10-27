package dev.bladetetra.client.reflection;

/** Render-thread-only recursion guard; Forge stage callbacks are suppressed inside this pass. */
public final class ReflectionPass {
    private static boolean active;
    public static boolean active() { return active; }
    static void enter() {
        if (active) throw new IllegalStateException("Recursive world reflection");
        active=true;
    }
    static void leave() { active=false; }
    private ReflectionPass() {}
}
