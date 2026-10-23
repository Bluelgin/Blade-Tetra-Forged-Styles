package dev.bladetetra.customstyle;

import dev.bladetetra.item.ModularSlashBladeItem;
import net.minecraft.world.item.ItemStack;

/** Only custom blades use these physical blade-length presets. */
public enum CustomBladeLength {
    SHORT(1.0D, "wakizashi"),
    STANDARD(2.5D, "orthodox"),
    LONG(4.0D, "nodachi");

    private final double reachBonus;
    private final String modelName;

    CustomBladeLength(double reachBonus, String modelName) {
        this.reachBonus = reachBonus;
        this.modelName = modelName;
    }

    public double reachBonus() { return reachBonus; }
    public String modelName() { return modelName; }

    public static boolean isCustomModule(String module) {
        return ModularSlashBladeItem.CUSTOM_SHORT_BLADE_MODULE.equals(module)
                || ModularSlashBladeItem.CUSTOM_STANDARD_BLADE_MODULE.equals(module)
                || ModularSlashBladeItem.CUSTOM_LONG_BLADE_MODULE.equals(module);
    }

    public static CustomBladeLength from(ItemStack stack) {
        if (stack.getTag() == null) return STANDARD;
        return switch (stack.getTag().getString(ModularSlashBladeItem.BLADE_SLOT)) {
            case ModularSlashBladeItem.CUSTOM_SHORT_BLADE_MODULE -> SHORT;
            case ModularSlashBladeItem.CUSTOM_LONG_BLADE_MODULE -> LONG;
            default -> STANDARD;
        };
    }
}
