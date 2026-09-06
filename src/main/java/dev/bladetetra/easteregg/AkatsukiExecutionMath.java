package dev.bladetetra.easteregg;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Pure balance calculations for Akatsuki's execution, kept testable without Minecraft. */
public final class AkatsukiExecutionMath {
    public static double representativeDamage(List<Float> recentDamage) {
        if (recentDamage.isEmpty()) return 0.0D;
        List<Float> sorted = new ArrayList<>(recentDamage);
        Collections.sort(sorted);
        int from = sorted.size() >= 5 ? 1 : 0;
        int to = sorted.size() >= 5 ? sorted.size() - 1 : sorted.size();
        double total = 0.0D;
        for (int index = from; index < to; index++) total += sorted.get(index);
        return total / Math.max(1, to - from);
    }

    public static float threshold(float maximumHealth, double representativeDamage,
            double hitMultiplier, double minimumFraction, double maximumFraction) {
        double minimum = maximumHealth * minimumFraction;
        double maximum = maximumHealth * maximumFraction;
        return (float) Math.max(minimum,
                Math.min(maximum, representativeDamage * hitMultiplier));
    }

    public static float pulseDamage(double representativeDamage,
            float executionStartHealth, double damageFraction, int minimumPulses) {
        return (float) Math.max(0.05D, Math.min(
                representativeDamage * damageFraction,
                executionStartHealth / Math.max(1, minimumPulses)));
    }

    private AkatsukiExecutionMath() {
    }
}
