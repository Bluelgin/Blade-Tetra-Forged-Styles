package dev.bladetetra.item;

import dev.bladetetra.visual.SayaPresetSkin;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * A consumable cosmetic pattern used to apply authored saya artwork.
 */
public final class SayaPatternItem extends Item {
    private final SayaPresetSkin preset;

    public SayaPatternItem(SayaPresetSkin preset) {
        super(new Item.Properties().stacksTo(16));
        if (preset == null || !preset.present()) {
            throw new IllegalArgumentException(
                    "Saya pattern items require a visible preset");
        }
        this.preset = preset;
    }

    public SayaPresetSkin preset() {
        return preset;
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            @Nullable Level level,
            List<Component> tooltip,
            TooltipFlag flag) {
        tooltip.add(Component.translatable(
                        "tooltip.blade_tetra.saya_pattern",
                        preset.displayName())
                .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable(
                        "tooltip.blade_tetra.saya_pattern.use")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
