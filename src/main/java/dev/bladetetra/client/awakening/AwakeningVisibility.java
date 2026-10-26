package dev.bladetetra.client.awakening;

/** Pure policy: icons/GUI, other weapons and unprepared models always survive. */
public final class AwakeningVisibility {
    public static boolean hide(boolean validSession, boolean sameHeldObject,
            boolean prepared, boolean floatingPass, String target) {
        return validSession && sameHeldObject && prepared && !floatingPass
                && ("blade".equals(target) || "blade_damaged".equals(target)
                || "blade_luminous".equals(target) || "blade_damaged_luminous".equals(target)
                || "sheath".equals(target) || "sheath_luminous".equals(target));
    }
    private AwakeningVisibility() {}
}
