package dev.bladetetra.client;

import dev.bladetetra.BladeTetra;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Positive-only player/tester hint for named blades that can actually be researched. */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LegacyResearchTooltipClient {
    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        LegacyResearchabilityClient.Result result =
                LegacyResearchabilityClient.inspect(event.getItemStack());
        if (!result.researchable()) return;

        event.getToolTip().add(Component.translatable(
                "tooltip.blade_tetra.imprint.researchable")
                .withStyle(ChatFormatting.AQUA));
        if (event.getFlags().isAdvanced()) {
            event.getToolTip().add(Component.translatable(
                    "tooltip.blade_tetra.imprint.catalog_id", result.kind().id())
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    private LegacyResearchTooltipClient() {}
}
