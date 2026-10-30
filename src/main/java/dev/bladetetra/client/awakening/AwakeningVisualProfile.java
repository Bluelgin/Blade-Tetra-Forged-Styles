package dev.bladetetra.client.awakening;

/** One composable geometry provider per soul. Lifetimes and networking live elsewhere. */
public interface AwakeningVisualProfile {
    double forward();
    double height();
    float angle();
    void draw(RitualEffectBatch frame);
    AwakeningVisualProfile[] PROFILES = {null, new AkatsukiAwakeningVisual(),
            new KyoukaAwakeningVisual(), new SenbonzakuraAwakeningVisual(), new RaikiriAwakeningVisual()};
    static AwakeningVisualProfile get(int soul) { return PROFILES[soul]; }
}
