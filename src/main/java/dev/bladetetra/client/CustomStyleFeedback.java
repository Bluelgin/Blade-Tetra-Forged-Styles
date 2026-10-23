package dev.bladetetra.client;

import dev.bladetetra.BladeTetra;
import dev.bladetetra.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Adapts existing server system messages. Never infers a capture from swinging. */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID, value = Dist.CLIENT)
public final class CustomStyleFeedback {
    private static ClientLevel world;
    private static Component message;
    private static String state, sheet;
    private static int remaining, elapsed, frameTicks, count = -1, maximum;

    @SubscribeEvent public static void message(ClientChatReceivedEvent.System event) {
        // Match structured translation keys, not localized text or other players' chat.
        if (!(event.getMessage().getContents() instanceof TranslatableContents text)) return;
        String key = text.getKey();
        switch (key) {
            case "message.blade_tetra.custom_style.added" -> show(event.getMessage(), "state_added", "ink_write", 1);
            case "message.blade_tetra.custom_style.full" -> show(event.getMessage(), "state_full", null, 1);
            case "message.blade_tetra.custom_style.no_recent_action",
                 "message.blade_tetra.custom_style.already_captured",
                 "message.blade_tetra.custom_style.empty",
                 "message.blade_tetra.style_exchange.empty",
                 "message.blade_tetra.style_exchange.invalid_name" -> show(event.getMessage(), "state_unavailable", null, 1);
            case "message.blade_tetra.custom_style.finished",
                 "message.blade_tetra.style_exchange.finished" -> compiled();
            case "message.blade_tetra.style_exchange.undone",
                 "message.blade_tetra.style_exchange.cleared" -> show(event.getMessage(), "state_waiting", null, 1);
            default -> { return; }
        }
        Object[] args = text.getArgs();
        // Only capture result messages carry a current draft count and capacity.
        if (key.startsWith("message.blade_tetra.custom_style.") && args.length == 2
                && args[0] instanceof Number n && args[1] instanceof Number max
                && n.intValue() >= 0 && max.intValue() > 0 && n.intValue() <= max.intValue()) {
            count = n.intValue();
            maximum = max.intValue();
        }
    }

    static void compiled() {
        show(Component.translatable("ui.blade_tetra.custom_style.compiled"), "state_compiled", "seal_set", 2);
    }

    private static void show(Component text, String icon, String animation, int ticks) {
        world = Minecraft.getInstance().level;
        message = text;
        state = icon;
        sheet = animation;
        frameTicks = ticks;
        elapsed = 0;
        remaining = 60;
        count = -1;
    }

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        if (world != mc.level || mc.player == null || !mc.player.isAlive()) {
            clear();
            world = mc.level;
        } else if (!mc.isPaused() && remaining > 0) {
            elapsed++;
            if (--remaining == 0) clear();
        }
    }

    @SubscribeEvent public static void render(RenderGuiEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || mc.screen != null) return;
        boolean held = mc.player.getMainHandItem().is(ModItems.STYLE_RECORDER.get())
                || mc.player.getOffhandItem().is(ModItems.STYLE_RECORDER.get());
        if (!held && remaining <= 0) return;
        var g = event.getGuiGraphics();
        // A small fixed corner panel, away from crosshair, health and hotbar.
        g.blit(CustomStyleArt.texture("recording_hud"), 8, 8, 0, 0, 160, 36, 160, 36);
        CustomStyleArt.icon(g, remaining > 0 ? state : "state_waiting", 17, 14);
        Component text = remaining > 0 ? message
                : Component.translatable("screen.blade_tetra.custom_style.recorder_hint");
        g.drawString(mc.font, CustomStyleArt.fit(text, 126), 34, 13, 0x211D2A, false);
        Component detail = remaining > 0 && count >= 0
                ? Component.translatable("ui.blade_tetra.custom_style.count", count, maximum)
                : Component.translatable("screen.blade_tetra.custom_style.recorder_use", mc.options.keyUse.getTranslatedKeyMessage());
        g.drawString(mc.font, CustomStyleArt.fit(detail, 112), 34, 32, 0x735747, false);
        // Bounded, tick-driven, one pass. Zero screen-effects scale gives a static result.
        if (sheet != null && elapsed < frameTicks * 6 && mc.options.screenEffectScale().get() > 0) {
            g.blit(CustomStyleArt.texture(sheet), 144, 26, 0, (elapsed / frameTicks) * 16,
                    16, 16, 16, 96);
        }
    }

    private static void clear() {
        remaining = 0;
        message = null;
        sheet = null;
        state = null;
        count = -1;
    }

    private CustomStyleFeedback() {}
}
