package dev.bladetetra.easteregg;

import net.minecraft.resources.ResourceLocation;

/** Pure Sakura flow rules. This class needs no Forge registry initialization. */
public final class SakuraFlowRules {
    static int halfSecondCost(int grade, int pointsPerSecond) {
        int percentage = switch (grade) {
            case 7 -> 67;
            case 6 -> 80;
            case 5 -> 88;
            default -> 100;
        };
        return Math.max(1, (pointsPerSecond * percentage + 199) / 200);
    }

    public static int stage(ResourceLocation combo) {
        if (combo == null) return 0;
        if ("blade_tetra".equals(combo.getNamespace())
                && "awakened_sakura_b6".equals(combo.getPath())) return 6;
        String path = combo.getPath();
        return "slashblade".equals(combo.getNamespace()) && path.length() == 8
                && path.startsWith("combo_b") && path.charAt(7) >= '1' && path.charAt(7) <= '6'
                ? path.charAt(7) - '0' : 0;
    }

    static boolean isNativeBAttack(ResourceLocation combo) { return stage(combo) != 0; }

    /** Native B6 authors hits at ticks 0..6. Never replace it before tick 7. */
    public static boolean completedB6(long elapsed) { return elapsed >= 7; }

    private SakuraFlowRules() {}
}
