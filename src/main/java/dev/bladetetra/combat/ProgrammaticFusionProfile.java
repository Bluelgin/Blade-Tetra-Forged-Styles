package dev.bladetetra.combat;

/**
 * Small semantic description used by the generic legacy-fusion executor.
 * Profiles describe intent only. Third-party source execution is authorized by a
 * separate presentation policy: exact audits are preferred, known dictionary
 * entries may be observed dynamically, and every failure can still fall back to
 * this bounded semantic grammar.
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

    /**
     * Saya/release grammar. Native SlashBlade entries map back to their original
     * ComboState while DIRECT remains the conservative compatibility fallback.
     */
    public enum Entry {
        DIRECT,
        JUDGEMENT_CUT,
        SAKURA_END,
        VOID_SLASH,
        CIRCLE_SLASH,
        DRIVE_VERTICAL,
        DRIVE_HORIZONTAL,
        WAVE_EDGE,
        PIERCING
    }

    /**
     * Tsuba/response grammar. Every response has a fixed bounded primitive count;
     * ProgrammaticFusionPlan divides one shared damage budget across that count.
     */
    public enum Response {
        FOCUSED_DRIVE(1),
        JUDGEMENT_ECHO(3),
        SAKURA_CROSS(2),
        VOID_TRIDENT(3),
        CIRCLE_RING(4),
        VERTICAL_DRIVE(1),
        HORIZONTAL_DRIVE(1),
        WAVE_EDGE(4),
        PIERCING_FOCUS(1);

        private final int projectileCount;

        Response(int projectileCount) {
            this.projectileCount = projectileCount;
        }

        int projectileCount() {
            return projectileCount;
        }
    }
}
