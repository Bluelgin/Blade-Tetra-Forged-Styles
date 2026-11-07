package dev.bladetetra.client;

/**
 * Pure, deterministic visual decision. The server always creates the same
 * targetable decoy; client capabilities only change how it is drawn.
 */
public final class KyoukaDecoyVisualPolicy {
    public enum Mode { PLAYER_ECHO, WATER_SILHOUETTE }

    public static Mode select(boolean ysmInstalled, boolean ownerTracked, boolean modelFailed) {
        return !ysmInstalled && ownerTracked && !modelFailed
                ? Mode.PLAYER_ECHO : Mode.WATER_SILHOUETTE;
    }

    private KyoukaDecoyVisualPolicy() {}
}
