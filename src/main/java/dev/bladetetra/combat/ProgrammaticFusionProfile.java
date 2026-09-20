package dev.bladetetra.combat;

/**
 * Small semantic description used by the generic legacy-fusion executor.
 * Profiles describe intent only; they never call a third-party Slash Art.
 */
public record ProgrammaticFusionProfile(Entry entry, Response response,
        double intensity) {
    static final ProgrammaticFusionProfile SAFE = new ProgrammaticFusionProfile(
            Entry.DIRECT, Response.FOCUSED_DRIVE, 0.65D);

    public ProgrammaticFusionProfile {
        entry = entry == null ? Entry.DIRECT : entry;
        response = response == null ? Response.FOCUSED_DRIVE : response;
        intensity = Math.max(0.25D, Math.min(1.0D, intensity));
    }

    public enum Entry {
        DIRECT,
        DASH,
        ARC
    }

    public enum Response {
        FOCUSED_DRIVE(1),
        CROSS_DRIVE(2),
        FAN_DRIVE(3);

        private final int projectileCount;

        Response(int projectileCount) {
            this.projectileCount = projectileCount;
        }

        int projectileCount() {
            return projectileCount;
        }
    }
}
