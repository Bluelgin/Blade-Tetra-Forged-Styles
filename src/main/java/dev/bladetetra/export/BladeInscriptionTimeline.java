package dev.bladetetra.export;

/** Wall-clock choreography. Sampling is pure; sound cues are consumed once per playback. */
final class BladeInscriptionTimeline {
    static final long DURATION = 8_000;
    static final long IMPACT = 4_500;
    private static final long[] CUES = {1_200, 3_000, IMPACT, 5_100};
    private int played;

    record Frame(long millis, int phase, float settle, float charge, float writing,
                 float release, float name, float impact) {}

    static Frame sample(long millis) {
        long t = Math.max(0, Math.min(DURATION, millis));
        return new Frame(t, t < 1_200 ? 0 : t < 3_000 ? 1 : t < IMPACT ? 2 : t < 6_000 ? 3 : 4,
                smooth(t, 0, 1_200), smooth(t, 1_200, 3_000), linear(t, 3_000, IMPACT),
                smooth(t, IMPACT, 6_000), smooth(t, 4_900, 6_000),
                t < IMPACT ? 0 : 1 - linear(t, IMPACT, 4_950));
    }

    /** Coalesce a stalled frame to its latest cue, rather than playing a burst of stale audio. */
    int consumeCue(long millis) {
        int latest = -1;
        for (int i = 0; i < CUES.length; i++) {
            if (millis >= CUES[i] && (played & (1 << i)) == 0) {
                played |= 1 << i;
                latest = i;
            }
        }
        return latest;
    }

    void reset() { played = 0; }

    static float smooth(long t, long start, long end) {
        float p = linear(t, start, end);
        return p * p * (3 - 2 * p);
    }

    private static float linear(long t, long start, long end) {
        return Math.max(0, Math.min(1, (t - start) / (float) (end - start)));
    }
}
