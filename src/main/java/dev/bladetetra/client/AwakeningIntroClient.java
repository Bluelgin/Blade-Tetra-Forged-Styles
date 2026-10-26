package dev.bladetetra.client;

import dev.bladetetra.BladeTetra;
import dev.bladetetra.config.ClientVisualConfig;
import dev.bladetetra.easteregg.AwakeningPresentationTimeline;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * Local true-name presentation, separate from vanilla titles and the
 * world-space particle/billboard renderer. The server decides *when* an
 * awakening succeeds; only this screen layer decides *how* it looks.
 */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE,
        value = Dist.CLIENT)
public final class AwakeningIntroClient {
    private static Intro active;
    private static ClientLevel world;

    public static void begin(int soul, int age) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !AwakeningPresentationTimeline.validSoul(soul)) return;
        world = mc.level;
        String title = Component.translatable(
                AwakeningPresentationTimeline.nameKey(soul)).getString();
        active = new Intro(soul, title);
        active.age = age;
    }

    public static void cancel() { active = null; }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (world != mc.level) {
            world = mc.level;
            active = null;
        }
        if (mc.isPaused() || active == null) return;
        Intro intro = active;
        ++intro.age;
        if (intro.age >= AwakeningPresentationTimeline.OVERLAY_END) active = null;
    }

    /** Called exclusively by the registered independent Forge GUI overlay. */
    static void render(GuiGraphics gui, int width, int height, float partial) {
        Intro intro = active;
        if (intro == null || world != Minecraft.getInstance().level
                || Minecraft.getInstance().options.hideGui
                || !ClientVisualConfig.ENABLE_BLADE_COMBAT_VFX.get()) return;
        Font font = Minecraft.getInstance().font;
        float time = intro.age + partial;
        float fade = AwakeningPresentationTimeline.fade(intro.age);
        if (fade <= 0.0F) return;
        int centerY = Math.round(height * 0.38F);
        int tint = AwakeningPresentationTimeline.tint(intro.soul);
        // A restrained horizontal veil, not an opaque full-screen dialog.
        int veilAlpha = Math.round(60.0F * fade);
        gui.fill(0, centerY - 34, width, centerY + 46, (veilAlpha << 24) | 0x080B16);
        gui.fill(width / 4, centerY + 23, width * 3 / 4, centerY + 24,
                withAlpha(tint, 0.65F * fade));
        String label = "TRUE NAME REVELATION";
        gui.drawString(font, label, (width - font.width(label)) / 2,
                centerY - 24, withAlpha(0xB6BDCC, 0.60F * fade), false);

        int total = 0;
        for (String glyph : intro.glyphs) total += font.width(glyph) + 2;
        int x = width / 2 - total / 2;
        for (int i = 0; i < intro.glyphs.size(); ++i) {
            String glyph = intro.glyphs.get(i);
            int glyphWidth = font.width(glyph);
            float enter = AwakeningPresentationTimeline.smooth(
                    (time - AwakeningPresentationTimeline.NAME_START - i * 1.35F) / 8.0F);
            if (enter > 0.0F) {
                float offsetY;
                float offsetX = 0;
                float flicker = 1.0F;
                switch (intro.soul) {
                    case 1 -> { // red crescent: opposing sections converge like a cut
                        offsetX = (i < intro.glyphs.size() / 2 ? -1 : 1)
                                * (1.0F - enter) * 24.0F;
                        offsetY = (i % 2 == 0 ? -1 : 1) * (1.0F - enter) * 9.0F;
                    }
                    case 2 -> { // mirror: rise out of the ghost reflection
                        offsetY = (1.0F - enter) * 22.0F;
                        gui.drawString(font, glyph, x + Math.round(offsetX),
                                centerY + 13 + Math.round(offsetY * 0.3F),
                                withAlpha(0x4EA4CD, 0.20F * fade * enter), false);
                    }
                    case 3 -> { // blossom: each character condenses along a petal arc
                        offsetY = (1.0F - enter) * 21.0F
                                * (float) Math.sin(i * 1.8D);
                        offsetX = (1.0F - enter) * 11.0F
                                * (float) Math.cos(i * 1.8D);
                    }
                    default -> { // thunder: flash into place with asynchronous flicker
                        offsetY = (1.0F - enter) * -8.0F;
                        flicker = time < 88.0F && ((int) time + i * 3) % 5 == 0
                                ? 0.30F : 1.0F;
                    }
                }
                gui.drawString(font, glyph, x + Math.round(offsetX),
                        centerY + Math.round(offsetY),
                        withAlpha(tint, fade * enter * flicker), true);
            }
            x += glyphWidth + 2;
        }
        if (intro.soul == 1 && time >= 82.0F && time < 96.0F) {
            // Signature cut across the completed name at the awakening beat.
            float cut = AwakeningPresentationTimeline.smooth((time - 82.0F) / 4.0F);
            int left = width / 2 - Math.round((total + 22) * cut / 2.0F);
            int right = width / 2 + Math.round((total + 22) * cut / 2.0F);
            gui.fill(left, centerY + 4, right, centerY + 5,
                    withAlpha(0xFFE6EA, (1.0F - Math.abs(time - 86.0F) / 12.0F)
                            * fade * 0.86F));
        }
    }

    private static int withAlpha(int rgb, float opacity) {
        int a = Math.max(0, Math.min(255, Math.round(opacity * 255)));
        return a < 4 ? 0 : (a << 24) | (rgb & 0xFFFFFF);
    }

    private static final class Intro {
        final int soul;
        final List<String> glyphs = new ArrayList<>();
        int age;

        Intro(int soul, String title) {
            this.soul = soul;
            title.codePoints().forEach(codePoint ->
                    glyphs.add(new String(Character.toChars(codePoint))));
        }
    }

    private AwakeningIntroClient() {}
}
