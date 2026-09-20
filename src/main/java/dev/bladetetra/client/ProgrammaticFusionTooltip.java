package dev.bladetetra.client;

import dev.bladetetra.BladeTetra;
import dev.bladetetra.combat.ProgrammaticFusionPlan;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Small easter egg for mixed add-on fusions still using Blade Tetra presentation. */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ProgrammaticFusionTooltip {
    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        if (!ProgrammaticFusionPlan.hasUnadaptedThirdPartyArt(event.getItemStack())) {
            return;
        }
        event.getToolTip().add(Component.literal("恭喜你发现了未适配的融合SA")
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }

    private ProgrammaticFusionTooltip() {
    }
}
