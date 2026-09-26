package dev.bladetetra.client;

import dev.bladetetra.BladeTetra;
import dev.bladetetra.combat.ForgedSlashArtPlan;
import dev.bladetetra.combat.ProgrammaticFusionPlan;
import dev.bladetetra.registry.ModSlashBladeAbilities;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Client presentation for runtime-generated Slash Arts. */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ProgrammaticFusionTooltip {
    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        ForgedSlashArtPlan forged = ForgedSlashArtPlan.from(event.getItemStack());
        if (forged != null) {
            replaceForgedSlashArtName(event, forged);
        }

        ProgrammaticFusionPlan plan = ProgrammaticFusionPlan.from(event.getItemStack());
        if (plan != null) {
            replaceProgrammaticSlashArtName(event, plan);
        }

        if (ProgrammaticFusionPlan.hasUnadaptedThirdPartyArt(event.getItemStack())) {
            event.getToolTip().add(Component.literal("恭喜你发现了未适配的融合SA")
                    .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        }
    }

    private static void replaceForgedSlashArtName(
            ItemTooltipEvent event, ForgedSlashArtPlan plan) {
        event.getItemStack().getCapability(ItemSlashBlade.BLADESTATE).ifPresent(state -> {
            if (!ModSlashBladeAbilities.FORGED_SLASH_ART.getId()
                    .equals(state.getSlashArtsKey())) {
                return;
            }
            Component generatedName = Component.translatable(
                            plan.primary().translationKey())
                    .append(Component.literal(" · "))
                    .append(Component.translatable(plan.secondary().translationKey()));
            replaceSlashArtLine(event, generatedName);
        });
    }

    private static void replaceProgrammaticSlashArtName(
            ItemTooltipEvent event, ProgrammaticFusionPlan plan) {
        event.getItemStack().getCapability(ItemSlashBlade.BLADESTATE).ifPresent(state -> {
            if (!ModSlashBladeAbilities.PROGRAMMATIC_FUSION.getId()
                    .equals(state.getSlashArtsKey())) {
                return;
            }
            replaceSlashArtLine(event, ProgrammaticFusionNameGrammar.name(plan));
        });
    }

    private static void replaceSlashArtLine(ItemTooltipEvent event, Component name) {
        Component replacement = Component.translatable(
                        "slashblade.tooltip.slash_art", name)
                .withStyle(ChatFormatting.GRAY);
        for (int index = 0; index < event.getToolTip().size(); index++) {
            Component line = event.getToolTip().get(index);
            if (line.getContents() instanceof TranslatableContents contents
                    && "slashblade.tooltip.slash_art".equals(contents.getKey())) {
                event.getToolTip().set(index, replacement);
                return;
            }
        }

        // Another tooltip transformer may have removed SlashBlade's normal line.
        // Keep the generated SA visible rather than exposing the structural id.
        event.getToolTip().add(replacement);
    }

    private ProgrammaticFusionTooltip() {
    }
}
