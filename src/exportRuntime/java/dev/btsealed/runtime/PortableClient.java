package dev.btsealed.runtime;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = PortableBladeRuntime.ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class PortableClient {
    @SubscribeEvent
    public static void registerReloadListener(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(PortableProfiles.clientListener());
    }

    @Mod.EventBusSubscriber(modid = PortableBladeRuntime.ID, value = Dist.CLIENT)
    public static final class ForgeEvents {
        @SubscribeEvent
        public static void tooltip(net.minecraftforge.event.entity.player.ItemTooltipEvent event) {
            var stack = event.getItemStack();
            if (!stack.is(mods.flammpfeil.slashblade.registry.SlashBladeItems.SLASHBLADE.get())) return;
            stack.getCapability(mods.flammpfeil.slashblade.item.ItemSlashBlade.BLADESTATE).ifPresent(state -> {
                String key = state.getTranslationKey();
                if (!key.startsWith("item.bt_export_") || !key.endsWith(".sealed_blade")) return;
                String lore = key + ".lore";
                if (net.minecraft.client.resources.language.I18n.exists(lore)) {
                    for (var line : Minecraft.getInstance().font.split(
                            net.minecraft.network.chat.Component.translatable(lore), 220)) {
                        StringBuilder displayed = new StringBuilder();
                        line.accept((index, style, codepoint) -> {
                            displayed.appendCodePoint(codepoint);
                            return true;
                        });
                        event.getToolTip().add(net.minecraft.network.chat.Component.literal(displayed.toString())
                                .withStyle(net.minecraft.ChatFormatting.GRAY));
                    }
                }
            });
        }

        @SubscribeEvent
        public static void attack(InputEvent.InteractionKeyMappingTriggered event) {
            var player = Minecraft.getInstance().player;
            if (event.isAttack() && player != null && PortableDangakuSpin.ownsCombo(player)) {
                event.setCanceled(true);
                event.setSwingHand(false);
            }
        }
    }

    private PortableClient() {}
}
