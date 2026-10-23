package dev.bladetetra.aura;

import dev.bladetetra.combat.BladeStyle;

/** Visual-only differences. All styles consume one damage budget per beat. */
public record SwordAuraStyleProfile(float speed, int lifetimeTicks,
        float mainSize, float additionalRoll, double additionalHeight) {

    public static SwordAuraStyleProfile forStyle(BladeStyle style) {
        return switch (style) {
            case STANDARD, CUSTOM -> new SwordAuraStyleProfile(2.0F, 9, 1.00F, 0, 0);
            case IAIDO -> new SwordAuraStyleProfile(2.7F, 8, 0.80F, 0, 0);
            case RENGEKI -> new SwordAuraStyleProfile(2.25F, 8, 0.87F, 34, 0.10D);
            case DANGAKU -> new SwordAuraStyleProfile(1.70F, 11, 1.40F, 7, 0.27D);
        };
    }

    public boolean hasDecoration() {
        return additionalRoll != 0.0F;
    }
}
