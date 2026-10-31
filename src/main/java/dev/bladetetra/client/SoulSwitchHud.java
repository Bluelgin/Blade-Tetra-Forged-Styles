package dev.bladetetra.client;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.bladetetra.BladeTetra;
import dev.bladetetra.config.ClientVisualConfig;
import dev.bladetetra.easteregg.AwakenedBladeCombat;
import dev.bladetetra.easteregg.SoulLegacyState;
import dev.bladetetra.easteregg.SoulSkillState;
import dev.bladetetra.network.SoulSwitchReply;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.Locale;
import java.util.UUID;

/** Two compact soul seals. No energy bar, frame polling, shaders or resource simulation. */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID, value = Dist.CLIENT)
public final class SoulSwitchHud {
    private static final ResourceLocation[] ICONS = java.util.Arrays.stream(SoulLegacyState.Legacy.values())
            .map(s -> new ResourceLocation(BladeTetra.MOD_ID, "textures/gui/soul_switch/"
                    + s.name().toLowerCase(Locale.ROOT) + ".png")).toArray(ResourceLocation[]::new);
    private static final ResourceLocation RING = new ResourceLocation(BladeTetra.MOD_ID, "textures/gui/soul_switch/pulse.png");
    private static ClientLevel world;
    private static UUID bladeId;
    private static int slot = -1, selected;
    private static long pulseAt = -100, messageUntil;
    private static Component message;

    public static void accept(SoulSwitchReply p) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || !mc.level.dimension().location().equals(p.dimension())
                || mc.player.getInventory().selected != p.slot()) return;
        ItemStack blade = mc.player.getMainHandItem();
        var tag = blade.getTag();
        if (!SoulSkillState.fusion(blade) || tag == null || !tag.hasUUID(SoulSkillState.BLADE_ID)
                || !p.blade().equals(tag.getUUID(SoulSkillState.BLADE_ID))
                || SoulLegacyState.inscribed(blade).stream().noneMatch(s -> s.ordinal() == p.soul())) return;
        remember(mc, blade);
        long now = mc.level.getGameTime();
        if (p.result() == SoulSwitchReply.SUCCESS) {
            tag.putInt(SoulSkillState.SELECTED, p.soul());
            selected = p.soul(); pulseAt = now;
            var soul = SoulLegacyState.Legacy.values()[selected];
            message = SoulSkillState.enabled(blade, soul)
                    ? Component.translatable("hud.blade_tetra.soul.switched", SoulLegacyState.displayName(soul))
                    : unavailable(blade, soul);
        } else {
            message = Component.translatable("hud.blade_tetra.soul." + switch (p.result()) {
                case SoulSwitchReply.COOLDOWN -> "cooldown";
                case SoulSwitchReply.BUSY -> "busy";
                default -> "dormant";
            });
        }
        messageUntil = now + 35;
    }

    private static void remember(Minecraft mc, ItemStack blade) {
        world = mc.level;
        bladeId = blade.hasTag() && blade.getTag().hasUUID(SoulSkillState.BLADE_ID)
                ? blade.getTag().getUUID(SoulSkillState.BLADE_ID) : null;
        slot = mc.player.getInventory().selected;
        selected = SoulSkillState.selected(blade).ordinal();
        pulseAt = -100; message = null; messageUntil = 0;
    }

    private static Component unavailable(ItemStack blade, SoulLegacyState.Legacy soul) {
        if (SoulLegacyState.isBroken(blade)) return Component.translatable("hud.blade_tetra.soul.broken");
        if (!SoulLegacyState.isUnlocked(blade, soul)) return Component.translatable("hud.blade_tetra.soul.dormant");
        return Component.translatable("hud.blade_tetra.soul.requires",
                Component.translatable(SoulSkillState.requiredStyle(soul).getTranslationKey()));
    }

    @SubscribeEvent public static void render(RenderGuiEvent.Post e) {
        var mc = Minecraft.getInstance();
        if (mc.level != world && world != null) { world = null; bladeId = null; message = null; }
        if (mc.player == null || mc.level == null || mc.options.hideGui || mc.screen != null
                || !mc.player.isAlive() || mc.player.isSpectator()
                || !AwakenedBladeCombat.replacesLegacy() || !ClientVisualConfig.SHOW_SOUL_SWITCH_HUD.get()) return;
        var blade = mc.player.getMainHandItem();
        var souls = SoulLegacyState.inscribed(blade);
        if (souls.isEmpty()) return;
        UUID id = blade.hasTag() && blade.getTag().hasUUID(SoulSkillState.BLADE_ID)
                ? blade.getTag().getUUID(SoulSkillState.BLADE_ID) : null;
        int soul = SoulSkillState.selected(blade).ordinal();
        if (world != mc.level || !java.util.Objects.equals(bladeId, id) || slot != mc.player.getInventory().selected
                || selected != soul) remember(mc, blade);
        if (soul == 0) return;
        var g = e.getGuiGraphics();
        int size = ClientVisualConfig.SOUL_SWITCH_ICON_SIZE.get();
        int center = g.guiWidth() / 2 + ClientVisualConfig.SOUL_SWITCH_HUD_X.get();
        int y = g.guiHeight() - 84 - ClientVisualConfig.SOUL_SWITCH_HUD_Y.get();
        int gap = size + 5, left = center - (souls.size() == 2 ? gap : 0) / 2;
        double now = mc.level.getGameTime() + e.getPartialTick(), elapsed = now - pulseAt;
        boolean motion = !ClientVisualConfig.REDUCE_SOUL_SWITCH_MOTION.get() && mc.options.screenEffectScale().get() > 0;
        int index = 0;
        for (var s : souls) {
            int x = left + index++ * gap;
            boolean current = s.ordinal() == soul, usable = SoulSkillState.enabled(blade, s);
            float alpha = current ? (usable ? 1F : .5F) : .35F;
            int drawSize = current ? size : Math.max(14, size - 4);
            if (motion && current && elapsed >= 0 && elapsed < 9) {
                drawSize += Math.round((float)Math.sin(Math.PI * elapsed / 9) * 3);
                int ringSize = size + 4 + (int)(elapsed * 1.1);
                icon(g, RING, x, y + size / 2, ringSize, (float)(1 - elapsed / 9) * .8F);
            }
            icon(g, ICONS[s.ordinal()], x, y + size / 2, drawSize, alpha);
            if (current) {
                // A shape cue remains readable without relying only on brightness or hue.
                g.fill(x - 2, y + size + 1, x + 2, y + size + 2, usable ? 0xFFF2DEB3 : 0xFF87818B);
                if (!usable) {
                    g.fill(x + size / 2 - 5, y + size - 7, x + size / 2, y + size - 2, 0xFF9A8A87);
                    g.fill(x + size / 2 - 4, y + size - 10, x + size / 2 - 1, y + size - 5, 0xFF9A8A87);
                }
            }
        }
        if (souls.size() == 2 && ClientVisualConfig.SHOW_SOUL_SWITCH_KEY.get())
            g.drawCenteredString(mc.font, SoulSwitchInput.SWITCH.getTranslatedKeyMessage(), center, y + size + 5, 0xFFAAA2AF);
        if (message != null && now < messageUntil)
            g.drawCenteredString(mc.font, message, center, y - 13, 0xFFF0E6DF);
    }

    private static void icon(GuiGraphics g, ResourceLocation texture, int cx, int cy, int size, float alpha) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1, 1, 1, alpha);
        try { g.blit(texture, cx - size / 2, cy - size / 2, size, size, 0, 0, 64, 64, 64, 64); }
        finally { RenderSystem.setShaderColor(1, 1, 1, 1); RenderSystem.disableBlend(); }
    }
    private SoulSwitchHud() {}
}
