package dev.bladetetra.client;

/** Render-only appearance: never adds awakening NBT or combat effects to the boss's weapon. */
final class MikageBladeVisuals {
    static final float MODEL_SCALE = .0045F * 1.25F;
    private static final ThreadLocal<Boolean> ACTIVE = ThreadLocal.withInitial(() -> false);

    private MikageBladeVisuals() { }

    static boolean isRendering() {
        return ACTIVE.get();
    }

    static void render(Runnable draw) {
        boolean previous = ACTIVE.get();
        ACTIVE.set(true);
        try {
            draw.run();
        } finally {
            ACTIVE.set(previous);
        }
    }
}
