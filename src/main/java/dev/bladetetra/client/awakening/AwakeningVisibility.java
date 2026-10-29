package dev.bladetetra.client.awakening;

/** Pure policy: the native mounted sheath, icons and unrelated weapons survive. */
public final class AwakeningVisibility {
    public static boolean isBladeTarget(String target) {
        return "blade".equals(target) || "blade_damaged".equals(target)
                || "blade_luminous".equals(target) || "blade_damaged_luminous".equals(target);
    }

    public static boolean isSheathTarget(String target) {
        return "sheath".equals(target) || "sheath_luminous".equals(target);
    }

    public static boolean hide(boolean validSession, boolean sameHeldObject,
            boolean prepared, boolean floatingPass, String target) {
        return validSession && sameHeldObject && prepared && !floatingPass
                && isBladeTarget(target);
    }

    private AwakeningVisibility() {}
}
