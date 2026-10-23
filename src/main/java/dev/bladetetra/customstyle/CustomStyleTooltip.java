package dev.bladetetra.customstyle;

import dev.bladetetra.BladeTetra;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import se.mickelus.tetra.blocks.scroll.ScrollItem;

/**
 * The native Tetra scroll stays visually native. Data-driven content shows the
 * authored sequence before Astra's future texture/GUI work lands.
 */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CustomStyleTooltip {
    @SubscribeEvent
    public static void tooltip(ItemTooltipEvent event) {
        if (event.getItemStack().getItem() != ScrollItem.instance) return;
        CustomStyleSpec spec = CustomStyleScrolls.read(event.getItemStack());
        if (spec == null) return;
        event.getToolTip().add(Component.translatable(
                "tooltip.blade_tetra.custom_style.name", spec.name())
                .withStyle(ChatFormatting.GOLD));
        event.getToolTip().add(Component.translatable(
                "tooltip.blade_tetra.custom_style.count", spec.actions().size())
                .withStyle(ChatFormatting.GRAY));
        for (int i = 0; i < spec.actions().size(); i++) {
            event.getToolTip().add(Component.literal(
                    (i + 1) + ". " + spec.actions().get(i))
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    private CustomStyleTooltip() {}
}
