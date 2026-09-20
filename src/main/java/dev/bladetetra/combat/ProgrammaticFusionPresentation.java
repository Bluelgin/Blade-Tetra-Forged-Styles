package dev.bladetetra.combat;

/**
 * Runtime presentation support for one source Slash Art. Semantics and
 * presentation are deliberately separate: a profile can still provide a safe
 * Blade Tetra fallback even when the original add-on executor is unavailable.
 */
public record ProgrammaticFusionPresentation(boolean delegateRelease,
        boolean delegateResponse) {
    public static final ProgrammaticFusionPresentation NONE =
            new ProgrammaticFusionPresentation(false, false);
    public static final ProgrammaticFusionPresentation FULL =
            new ProgrammaticFusionPresentation(true, true);
    public static final ProgrammaticFusionPresentation RELEASE_ONLY =
            new ProgrammaticFusionPresentation(true, false);
    public static final ProgrammaticFusionPresentation RESPONSE_ONLY =
            new ProgrammaticFusionPresentation(false, true);
}
