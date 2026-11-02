package dev.bladetetra.challenge;

/** Release gate from 1.6.0 onward. Keep registries and saved progress while combat is unfinished. */
public final class MikageChallengeAccess {
    private static final boolean COMBAT_RELEASE_READY = false;
    private static final boolean VISITOR_RELEASE_READY = false;
    public static boolean visitorOpen() { return VISITOR_RELEASE_READY; }
    public static boolean combatOpen() { return COMBAT_RELEASE_READY; }
    public static boolean allowsGate(boolean visitor) { return visitor ? visitorOpen() : combatOpen(); }
    public static boolean shouldSuspend(boolean visitor, boolean victorious) {
        return !combatOpen() && !visitor && !victorious;
    }
    private MikageChallengeAccess() { }
}
