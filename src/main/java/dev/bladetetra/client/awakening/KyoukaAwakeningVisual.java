package dev.bladetetra.client.awakening;

/** Mirror geometry/capture is rendered in its own material pass; keep the common flight lifecycle. */
final class KyoukaAwakeningVisual implements AwakeningVisualProfile {
    public double forward(){return 2.2;} public double height(){return 2.15;}
    public float angle(){return -12;}
    public void draw(RitualEffectBatch f) { /* No atlas planes, pseudo floor reflection or magic rings. */ }
}
