package dev.bladetetra.forging;

/** Source definition attack only; ancillary benefits grow slower than attack. */
public record NamedMaterialStats(double attack, int durability, int capacity,
                                 int integrityCost, int integrityGain,
                                 double secondary, double tertiary) {
    public static NamedMaterialStats of(double sourceAttack) {
        double attack = Double.isFinite(sourceAttack) ? Math.max(0, sourceAttack) : 0;
        double ratio = Math.max(1, attack) / 5;
        double log = Math.log(ratio) / Math.log(2);
        return new NamedMaterialStats(attack,
                bounded(250 * Math.pow(ratio, .85), 120, 5000),
                bounded(84 + 24 * log, 60, 160),
                bounded(2 + log / 1.5, 2, 5), bounded(5 + log, 3, 8),
                Math.max(3, Math.min(4.6, 3.8 - .2 * log)),
                Math.max(1, Math.min(4.5, 3 + .3 * log)));
    }

    public static int requiredHammerLevel(double sourceAttack) {
        return Double.isFinite(sourceAttack) && sourceAttack >= 12 ? 4 : 3;
    }

    public double contribution(String part) {
        return attack * ("blade".equals(part) ? .5 : .25);
    }

    private static int bounded(double value, int min, int max) {
        return (int) Math.round(Math.max(min, Math.min(max, value)));
    }
}
