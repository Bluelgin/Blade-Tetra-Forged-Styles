package dev.bladetetra.forging;

import se.mickelus.tetra.module.data.MaterialData;
import se.mickelus.tetra.module.data.MaterialVariantData;
import se.mickelus.tetra.module.data.VariantData;

public final class HabakiMaterialStats {
    public static void apply(MaterialVariantData template, MaterialData material, VariantData result) {
        int durabilityBudget = switch (template.key) {
            case "basic_habaki/" -> 100;
            case "precision_habaki/" -> 65;
            case "reinforced_habaki/" -> 280;
            default -> 0;
        };
        if (durabilityBudget == 0) return;

        result.durability += bonus(material.durability, durabilityBudget, 1500);
        if ("precision_habaki/".equals(template.key)) {
            result.magicCapacity += bonus(material.magicCapacity, 30, 100);
        }
    }

    private static int bonus(double materialValue, int limit, int halfBonusAt) {
        double value = Math.max(0, materialValue);
        return (int) Math.round(limit * value / (value + halfBonusAt));
    }

    private HabakiMaterialStats() {}
}
