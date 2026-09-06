package dev.bladetetra.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.bladetetra.BladeTetra;
import dev.bladetetra.config.ClientVisualConfig;
import dev.bladetetra.network.MikageHudPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.CustomizeGuiOverlayEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

/** Torii-shaped, three-stage HUD used only for Mikage's own boss event. */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MikageBossHud {
    private static final ResourceLocation FRAME = new ResourceLocation(BladeTetra.MOD_ID,
            "textures/gui/mikage_boss_frame.png");
    private static final ResourceLocation HEALTH = new ResourceLocation(BladeTetra.MOD_ID,
            "textures/gui/mikage_health_fill.png");
    private static final ResourceLocation DAMAGE = new ResourceLocation(BladeTetra.MOD_ID,
            "textures/gui/mikage_damage_fill.png");
    private static final ResourceLocation ECHO = new ResourceLocation(BladeTetra.MOD_ID,
            "textures/gui/mikage_echo_fill.png");
    private static final ResourceLocation TECHNIQUE = new ResourceLocation(BladeTetra.MOD_ID,
            "textures/gui/mikage_technique_frame.png");
    private static final int FRAME_WIDTH = 430;
    private static final int FRAME_HEIGHT = 123;
    private static final int BAR_X = 64;
    private static final int BAR_Y = 71;
    private static final int BAR_WIDTH = 303;
    private static final int BAR_HEIGHT = 18;
    private static UUID bossEventId;
    private static int phase = 1;
    private static boolean reminiscence;
    private static int technique;
    private static int techniqueRemaining;
    private static int techniqueTotal;
    private static int staleTicks;
    private static float displayedHealth = 1.0F;
    private static float delayedHealth = 1.0F;
    private static float lastActualHealth = 1.0F;
    private static int damageDelay;
    private static int shakeTicks;

    public static void update(MikageHudPacket packet) {
        MikageBloodMoonDomainClient.update(packet);
        if (!packet.active()) {
            clear();
            return;
        }
        bossEventId = packet.bossEventId();
        if (packet.phase() != phase) {
            shakeTicks = 14;
        }
        phase = packet.phase();
        reminiscence = packet.reminiscence();
        technique = packet.technique();
        techniqueRemaining = packet.remainingTicks();
        techniqueTotal = packet.totalTicks();
        staleTicks = 30;
        MikageMusicClient.ensurePlaying(packet.challengeId());
    }

    private static void clear() {
        MikageBloodMoonDomainClient.clear();
        bossEventId = null;
        technique = 0;
        staleTicks = 0;
        displayedHealth = 1.0F;
        delayedHealth = 1.0F;
        lastActualHealth = 1.0F;
        MikageMusicClient.fadeOut();
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || bossEventId == null) {
            return;
        }
        if (--staleTicks <= 0 || Minecraft.getInstance().level == null) {
            clear();
            return;
        }
        if (damageDelay > 0) damageDelay--;
        if (shakeTicks > 0) shakeTicks--;
    }

    @SubscribeEvent
    public static void render(CustomizeGuiOverlayEvent.BossEventProgress event) {
        if (!ClientVisualConfig.ENABLE_MIKAGE_BOSS_BAR.get()
                || bossEventId == null || !bossEventId.equals(event.getBossEvent().getId())) {
            return;
        }
        event.setCanceled(true);
        event.setIncrement(Math.round((technique > 0 ? 160.0F : 128.0F)
                * effectiveScale()));
        renderCustom(event.getGuiGraphics(), event.getBossEvent(), event.getPartialTick());
    }

    private static void renderCustom(GuiGraphics graphics, LerpingBossEvent event,
            float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        Font font = minecraft.font;
        float actual = Mth.clamp(event.getProgress(), 0.0F, 1.0F);
        if (actual < lastActualHealth - 0.001F) {
            damageDelay = 12;
            if (lastActualHealth - actual > 0.035F) {
                shakeTicks = 7;
            }
        }
        lastActualHealth = actual;
        displayedHealth += (actual - displayedHealth) * 0.42F;
        if (damageDelay <= 0) {
            delayedHealth += (actual - delayedHealth) * 0.075F;
        } else {
            delayedHealth = Math.max(delayedHealth, actual);
        }

        float scale = effectiveScale();
        float opacity = ClientVisualConfig.MIKAGE_BOSS_BAR_OPACITY.get().floatValue();
        boolean reduced = ClientVisualConfig.REDUCE_MIKAGE_HUD_MOTION.get();
        int width = FRAME_WIDTH;
        int x = (int) ((minecraft.getWindow().getGuiScaledWidth() / scale - width) * 0.5F);
        int y = 4;
        if (!reduced && shakeTicks > 0) {
            x += Math.round(Mth.sin((shakeTicks + partialTick) * 3.4F) * Math.min(3, shakeTicks));
        }

        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.scale(scale, scale, 1.0F);
        RenderSystem.enableBlend();

        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, opacity);
        int delayedWidth = Math.round(BAR_WIDTH * delayedHealth);
        int healthWidth = Math.round(BAR_WIDTH * displayedHealth);
        blitClipped(graphics, DAMAGE, x + BAR_X, y + BAR_Y, delayedWidth);
        blitClipped(graphics, reminiscence ? ECHO : HEALTH,
                x + BAR_X, y + BAR_Y, healthWidth);
        graphics.blit(FRAME, x, y, 0, 0, FRAME_WIDTH, FRAME_HEIGHT,
                FRAME_WIDTH, FRAME_HEIGHT);

        // Fixed 2/3 and 1/3 phase notches.
        for (float marker : new float[]{1.0F / 3.0F, 2.0F / 3.0F}) {
            int mx = x + BAR_X + Math.round(BAR_WIDTH * marker);
            fill(graphics, mx - 1, y + BAR_Y - 1, mx + 1,
                    y + BAR_Y + BAR_HEIGHT + 1, 0xD8E4AE68);
        }
        Component name = Component.translatable(reminiscence
                ? "entity.blade_tetra.mikage.echo"
                : phase == 1 ? "entity.blade_tetra.mikage"
                : "entity.blade_tetra.mikage.phase" + phase);
        int nameX = x + BAR_X;
        graphics.drawString(font, name, nameX, y + 94, 0xFFF7E3D1, true);

        Component stage = Component.translatable("hud.blade_tetra.mikage.phase" + phase);
        graphics.drawString(font, stage, x + width - 63 - font.width(stage), y + 94,
                0xFFC99A91, true);

        if (ClientVisualConfig.SHOW_MIKAGE_TECHNIQUE_BAR.get()
                && technique > 0 && techniqueTotal > 0) {
            renderTechnique(graphics, font, x, y + 113, width, opacity);
        }

        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();
        pose.popPose();
    }

    private static void renderTechnique(GuiGraphics graphics, Font font,
            int x, int y, int width, float opacity) {
        float progress = 1.0F - Mth.clamp(techniqueRemaining / (float) techniqueTotal,
                0.0F, 1.0F);
        int a = Mth.clamp(Math.round(opacity * 230.0F), 0, 255);
        int tx = x + 45;
        graphics.blit(TECHNIQUE, tx, y, 0, 0, 340, 40, 340, 40);
        int color = technique == 6 ? 0xD89B2B
                : technique == 7 ? 0x8735A8
                : technique == 8 ? 0xE03848
                : technique == 3 ? 0xA52C78
                : technique == 2 ? 0xB88A27 : 0xC72D36;
        fill(graphics, tx + 35, y + 16,
                tx + 35 + Math.round(270 * progress), y + 20, a << 24 | color);
        Component label = Component.translatable("hud.blade_tetra.mikage.technique" + technique);
        graphics.drawString(font, label, x + width / 2 - font.width(label) / 2,
                y + 22, 0xFFE9D6CC, true);
        Component hint = Component.translatable("hud.blade_tetra.mikage.technique" + technique
                + ".hint");
        graphics.drawString(font, hint, x + width / 2 - font.width(hint) / 2,
                y + 32, 0xFFCAAFA7, true);
    }

    private static void blitClipped(GuiGraphics graphics, ResourceLocation texture,
            int x, int y, int width) {
        if (width > 0) {
            graphics.blit(texture, x, y, 0, 0, width, BAR_HEIGHT,
                    BAR_WIDTH, BAR_HEIGHT);
        }
    }

    private static void fill(GuiGraphics graphics, int x1, int y1, int x2, int y2, int color) {
        if ((color >>> 24) != 0 && x2 > x1 && y2 > y1) {
            graphics.fill(x1, y1, x2, y2, color);
        }
    }

    private static float effectiveScale() {
        Minecraft minecraft = Minecraft.getInstance();
        float configured = ClientVisualConfig.MIKAGE_BOSS_BAR_SCALE.get().floatValue();
        float compact = configured * 0.65F;
        float screenCap = minecraft.getWindow().getGuiScaledWidth() * 0.55F / FRAME_WIDTH;
        return Math.max(0.38F, Math.min(compact, screenCap));
    }

    private MikageBossHud() {
    }
}
