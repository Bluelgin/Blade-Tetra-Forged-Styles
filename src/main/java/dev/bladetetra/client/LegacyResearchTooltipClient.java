package dev.bladetetra.client;

import dev.bladetetra.BladeTetra;
import dev.bladetetra.forging.NamedBladeIngotEligibility;
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
        // Extraction and tooltip consult one shared side-neutral named-blade
        // eligibility gate. Client-only OBJ support only affects visual promises.
        NamedBladeIngotEligibility.Result extraction =
                NamedBladeIngotEligibility.inspect(event.getItemStack());
        if (extraction.kind() == null) return;

        LegacyResearchabilityClient.Result visual =
                LegacyResearchabilityClient.inspect(extraction.kind());
        if (visual.researchable()) {
            // Preserve the existing positive hint for independently renderable
            // models: old gameplay and other mods can continue relying on it.
            event.getToolTip().add(Component.translatable(
                    "tooltip.blade_tetra.imprint.researchable")
                    .withStyle(ChatFormatting.AQUA));
        }
        if (extraction.extractable()) {
            event.getToolTip().add(Component.translatable(
                    "tooltip.blade_tetra.imprint.ingot_ready")
                    .withStyle(ChatFormatting.GOLD));
        } else if (extraction.status()
                == NamedBladeIngotEligibility.Status.INSUFFICIENT_DURABILITY) {
            event.getToolTip().add(Component.translatable(
                    "tooltip.blade_tetra.imprint.ingot_repair")
                    .withStyle(ChatFormatting.GRAY));
        }

        if (!visual.researchable()) {
            // Do not imply the client's OBJ passed validation. The server may
            // still extract semantic ingot identity; installed parts fall back
            // to the ordinary model if the addon geometry is unsupported.
            event.getToolTip().add(Component.translatable(
                    "tooltip.blade_tetra.imprint.visual_fallback")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        if (event.getFlags().isAdvanced()) {
            event.getToolTip().add(Component.translatable(
                    "tooltip.blade_tetra.imprint.catalog_id", extraction.kind().id())
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    private LegacyResearchTooltipClient() {}
}
