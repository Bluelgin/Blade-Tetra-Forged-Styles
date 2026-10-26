package dev.bladetetra.easteregg;

import net.minecraft.resources.ResourceLocation;

/** Pure Sakura flow rules. This class needs no Forge registry initialization. */
final class SakuraFlowRules {
    static int halfSecondCost(int grade, int pointsPerSecond) {
        int percentage = switch (grade) {
            case 7 -> 67;
            case 6 -> 80;
            case 5 -> 88;
            default -> 100;
        };
        return Math.max(1, (pointsPerSecond * percentage + 199) / 200);
    }

    static boolean isNativeBAttack(ResourceLocation combo) {
        if (combo == null || !"slashblade".equals(combo.getNamespace())) return false;
        String path = combo.getPath();
        return path.length() == 8 && path.startsWith("combo_b")
                && path.charAt(7) >= '1' && path.charAt(7) <= '7';
    }

    private SakuraFlowRules() {}
}
