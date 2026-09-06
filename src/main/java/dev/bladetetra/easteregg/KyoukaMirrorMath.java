package dev.bladetetra.easteregg;

/** Pure balance formulas for Kyouka Suigetsu's reflected cuts. */
public final class KyoukaMirrorMath {
    public static float reflectedDamage(double recordedDamage, double ratio, double cap) {
        return (float) Math.max(0.0D,
                Math.min(Math.max(0.0D, cap), Math.max(0.0D, recordedDamage)
                        * Math.max(0.0D, ratio)));
    }

    public static float breakDamage(double slashArtDamage, double recordedDamage,
            double slashArtRatio, double recordedRatio, double cap) {
        double combined = Math.max(0.0D, slashArtDamage) * Math.max(0.0D, slashArtRatio)
                + Math.max(0.0D, recordedDamage) * Math.max(0.0D, recordedRatio);
        return (float) Math.max(0.0D, Math.min(Math.max(0.0D, cap), combined));
    }

    private KyoukaMirrorMath() {
    }
}
