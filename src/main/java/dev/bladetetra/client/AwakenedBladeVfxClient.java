package dev.bladetetra.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.bladetetra.BladeTetra;
import dev.bladetetra.config.ClientVisualConfig;
import dev.bladetetra.easteregg.AwakeningPresentationTimeline;
import dev.bladetetra.easteregg.SoulLegacyState;
import dev.bladetetra.combat.BladeStyle;
import dev.bladetetra.combat.StyleResolver;
import net.minecraft.world.entity.player.Player;
import dev.bladetetra.network.AwakenedBladeVfxPacket;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static dev.bladetetra.client.vfx.render.VfxPrimitives.texturedQuad;
import static dev.bladetetra.client.vfx.render.AwakenedAtlasBatch.*;

/**
 * Client-only, atlas-backed presentation. Combat and target selection stay on the server.
 * Uses actual UV-inset atlas tiles from Astra's awakened-blades-v2 art handoff.
 */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class AwakenedBladeVfxClient {
    private static final ResourceLocation[] ATLASES = {
            ResourceLocation.fromNamespaceAndPath(BladeTetra.MOD_ID,
                    "textures/effect/awakened/akatsuki/atlas.png"),
            ResourceLocation.fromNamespaceAndPath(BladeTetra.MOD_ID,
                    "textures/effect/awakened/kyouka/atlas.png"),
            ResourceLocation.fromNamespaceAndPath(BladeTetra.MOD_ID,
                    "textures/effect/awakened/senbonzakura/atlas.png"),
            ResourceLocation.fromNamespaceAndPath(BladeTetra.MOD_ID,
                    "textures/effect/awakened/raikiri/atlas.png")
    };
    private static final ResourceLocation[] ANIMATIONS = {
            ResourceLocation.fromNamespaceAndPath(BladeTetra.MOD_ID,
                    "textures/effect/awakened/akatsuki/scar_lifecycle.png"),
            ResourceLocation.fromNamespaceAndPath(BladeTetra.MOD_ID,
                    "textures/effect/awakened/kyouka/mirror_lifecycle.png"),
            ResourceLocation.fromNamespaceAndPath(BladeTetra.MOD_ID,
                    "textures/effect/awakened/senbonzakura/blade_disperse.png"),
            ResourceLocation.fromNamespaceAndPath(BladeTetra.MOD_ID,
                    "textures/effect/awakened/raikiri/step_flicker.png")
    };
    private static final List<Effect> EFFECTS = new ArrayList<>();
    private static ClientLevel currentLevel;

    public static void spawn(AwakenedBladeVfxPacket packet) {
        if (!ClientVisualConfig.ENABLE_BLADE_COMBAT_VFX.get()
                || packet.soul() < 1 || packet.soul() > 4
                || packet.type() < 0 || packet.type() > 4) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;
        if (currentLevel != minecraft.level) {
            EFFECTS.clear();
            currentLevel = minecraft.level;
        }
        double max = ClientVisualConfig.BLADE_COMBAT_VFX_DISTANCE.get();
        if (minecraft.player != null
                && minecraft.player.distanceToSqr(packet.x(), packet.y(), packet.z())
                > max * max) return;
        int cap = Math.max(12, ClientVisualConfig.BLADE_COMBAT_VFX_MAX_EFFECTS.get());
        // Release replaces persistent Akatsuki scar marks around this target.
        if (packet.soul() == 1 && packet.type() == AwakenedBladeVfxPacket.RELEASE) {
            EFFECTS.removeIf(fx -> fx.data.soul() == 1
                    && fx.data.type() == AwakenedBladeVfxPacket.MARK
                    && new Vec3(fx.data.x(), fx.data.y(), fx.data.z())
                            .distanceToSqr(packet.x(), packet.y(), packet.z()) < 16.0D);
        }
        EFFECTS.add(new Effect(packet));
        while (EFFECTS.size() > cap) EFFECTS.remove(0);
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || Minecraft.getInstance().isPaused()) return;
        ClientLevel level = Minecraft.getInstance().level;
        if (level != currentLevel) {
            EFFECTS.clear();
            currentLevel = level;
        }
        Iterator<Effect> iter = EFFECTS.iterator();
        while (iter.hasNext()) {
            Effect fx = iter.next();
            if (++fx.age >= fx.duration) iter.remove();
        }
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES
                || !ClientVisualConfig.ENABLE_BLADE_COMBAT_VFX.get()) return;
        Camera camera = event.getCamera();
        PoseStack poses = event.getPoseStack();
        poses.pushPose();
        Vec3 eye = camera.getPosition();
        poses.translate(-eye.x, -eye.y, -eye.z);
        Matrix4f matrix = poses.last().pose();
        float intensity = ClientVisualConfig.BLADE_COMBAT_VFX_INTENSITY.get().floatValue();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        for (int soul = 1; soul <= 4; soul++) {
            RenderSystem.setShaderTexture(0, ATLASES[soul - 1]);
            BufferBuilder buffer = Tesselator.getInstance().getBuilder();
            buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            for (Effect fx : EFFECTS) {
                if (fx.data.soul() == soul) draw(buffer, matrix, eye, fx,
                        event.getPartialTick(), intensity);
            }
            dev.bladetetra.client.awakening.AwakeningVisualController.drawBatch(
                    buffer, matrix, eye, event.getPartialTick(), intensity, soul);
            if (soul == 3) drawSakuraCrescent(buffer, matrix, eye,
                    event.getPartialTick(), intensity);
            Tesselator.getInstance().end();

            RenderSystem.setShaderTexture(0, ANIMATIONS[soul - 1]);
            buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            for (Effect fx : EFFECTS) {
                if (fx.data.soul() == soul) drawAnimation(buffer, matrix, eye, fx,
                        event.getPartialTick(), intensity);
            }
            Tesselator.getInstance().end();
        }
        RenderSystem.setShaderTexture(0,dev.bladetetra.client.awakening.AwakeningVisualController.BACKDROP);
        BufferBuilder ritualBackdrop = Tesselator.getInstance().getBuilder();
        ritualBackdrop.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_TEX_COLOR);
        dev.bladetetra.client.awakening.AwakeningVisualController.drawBackdrop(
                ritualBackdrop,matrix,eye,event.getPartialTick(),intensity);
        Tesselator.getInstance().end();
        RenderSystem.setShaderTexture(0,new ResourceLocation("blade_tetra","textures/effect/awakened/ritual/white_energy.png"));
        ritualBackdrop.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_TEX_COLOR);
        dev.bladetetra.client.awakening.AwakeningVisualController.drawMirrors(
                ritualBackdrop,matrix,eye,event.getPartialTick(),intensity);
        Tesselator.getInstance().end();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        drawTrueNames(event, poses, camera);
        poses.popPose();
    }

    /** World-space true-name signature, depth tested, independently localized. */
    private static void drawTrueNames(RenderLevelStageEvent event, PoseStack poses,
            Camera camera) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Font font = mc.font;
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        for (Effect fx : EFFECTS) {
            int type = fx.data.type();
            // Awakening names now have a separate owner-only animated HUD.
            // Keep world-space names only for regular ultimate releases to
            // avoid two overlapping title systems during the ceremony.
            if (type != AwakenedBladeVfxPacket.RELEASE) continue;
            float at = fx.age + event.getPartialTick();
            if (at < 22.0F || at >= fx.duration - 5.0F) continue;
            float opacity = Mth.clamp((at - 22.0F) / 7.0F, 0, 1)
                    * Mth.clamp((fx.duration - 5.0F - at) / 8.0F, 0, 1);
            String id = switch (fx.data.soul()) {
                case 1 -> "akatsuki";
                case 2 -> "kyouka";
                case 3 -> "senbonzakura";
                default -> "raikiri";
            };
            Component title = Component.translatable(
                    "vfx.blade_tetra.true_name." + id);
            int alpha = Math.max(4, (int) (opacity * 220.0F));
            int color = (alpha << 24) | switch (fx.data.soul()) {
                case 1 -> 0xF9A4B6;
                case 2 -> 0xADDFFB;
                case 3 -> 0xFFB5E6;
                default -> 0xB8E8FF;
            };
            poses.pushPose();
            // onRender already subtracts the camera position.
            poses.translate(fx.data.x(), fx.data.y() + 2.0D + at * 0.006D,
                    fx.data.z());
            poses.mulPose(camera.rotation());
            poses.scale(-0.025F, -0.025F, 0.025F);
            font.drawInBatch(title, -font.width(title) / 2.0F,
                    0.0F, color, false, poses.last().pose(),
                    buffers, Font.DisplayMode.NORMAL, 0, 0x00F000F0);
            poses.popPose();
        }
        buffers.endBatch();
    }

    private static void draw(BufferBuilder buffer, Matrix4f matrix, Vec3 eye,
            Effect fx, float partial, float intensity) {
        AwakenedBladeVfxPacket data = fx.data;
        float age = fx.age + partial;
        float t = age / fx.duration;
        float alpha = Mth.clamp(age / 4.0F, 0, 1)
                * Mth.clamp((fx.duration - age) / 8.0F, 0, 1)
                * Mth.clamp(intensity, 0, 1);
        if (alpha <= 0) return;
        Vec3 pos = new Vec3(data.x(), data.y(), data.z());
        int soul = data.soul();
        int type = data.type();
        if (type == AwakenedBladeVfxPacket.AWAKEN) {
            drawAwakening(buffer, matrix, eye, pos, soul, age, alpha);
            return;
        }
        if (soul == 1 && type == AwakenedBladeVfxPacket.RELEASE) {
            drawCausalExecution(buffer, matrix, eye, pos, age, alpha,
                    data.variant());
            return;
        }
        if (soul == 3 && type == AwakenedBladeVfxPacket.MARK
                && data.variant() >= 10) {
            drawSakuraVolley(buffer, matrix, eye, data, age, alpha);
            return;
        }
        if (soul == 3 && type == AwakenedBladeVfxPacket.STANCE) {
            // Disperse/reassemble the *real* model via the upstream render hook,
            // while these bounded Astra atlas shards visually bridge the swap.
            // 1 = release fragments from the physical blade; 0 = condense.
            boolean disperse = data.variant() == 1;
            double progress = Mth.clamp(age / 12.0F, 0.0F, 1.0F);
            double eased = progress * progress * (3.0D - 2.0D * progress);
            double radius = 0.12D + (disperse ? eased : 1.0D - eased) * 1.10D;
            for (int i = 0; i < 9; i++) {
                double angle = i * Math.PI * 2.0D / 9.0D + age * 0.055D;
                Vec3 shard = pos.add(Math.cos(angle) * radius,
                        (i % 3 - 1) * 0.22D + 0.10D + Math.sin(angle * 2) * 0.14D,
                        Math.sin(angle) * radius);
                billboardTile(buffer, matrix, eye, shard,
                        0.30D + 0.11D * (disperse ? eased : 1.0D - eased),
                        i % 3, soul, alpha * 0.86F, (float) angle);
            }
            return;
        }
        if (soul == 3 && type == AwakenedBladeVfxPacket.MARK
                && data.variant() == 9) {
            // Detached visible blade body for third-person and self view.
            // Nine quads from the existing Astra atlas, no spawned entities.
            for (int i = 0; i < 9; i++) {
                double angle = Math.PI * 2.0D * i / 9 + age * 0.045D;
                Vec3 piece = pos.add(Math.cos(angle) * 0.90D,
                        0.15D + Math.sin(angle * 2) * 0.24D,
                        Math.sin(angle) * 0.90D);
                billboardTile(buffer, matrix, eye, piece, 0.39D,
                        i % 3, soul, alpha * 0.75F, (float) angle);
            }
            return;
        }
        // 128px tiles, 8x4 on Akatsuki and 4x4 on the other three atlases.
        int tile = switch (soul) {
            case 1 -> switch (type) {
                case AwakenedBladeVfxPacket.AWAKEN -> 19; // crescent
                case AwakenedBladeVfxPacket.MARK -> (Math.floorMod(data.variant(), 6) * 3 + 1);
                default -> 21; // execution_cross
            };
            case 2 -> switch (type) {
                case AwakenedBladeVfxPacket.RELEASE -> 3; // replay_cut
                case AwakenedBladeVfxPacket.MARK -> 0; // water_mirror
                default -> 1; // cracked mirror
            };
            case 3 -> switch (type) {
                case AwakenedBladeVfxPacket.AWAKEN -> 5; // falling_petal
                case AwakenedBladeVfxPacket.RELEASE -> 6; // array_core
                case AwakenedBladeVfxPacket.STANCE -> 1; // blade_medium
                default -> 0; // blade_small
            };
            default -> switch (type) {
                case AwakenedBladeVfxPacket.AWAKEN -> 6; // landing_pulse
                case AwakenedBladeVfxPacket.RELEASE -> 8; // piercing_light
                case AwakenedBladeVfxPacket.STEP -> 3; // step_ribbon
                default -> 4; // direction_arc
            };
        };
        if (soul == 4 && (type == AwakenedBladeVfxPacket.STEP
                || type == AwakenedBladeVfxPacket.RELEASE)) {
            Vec3 end = new Vec3(data.endX(), data.endY(), data.endZ());
            segment(buffer, matrix, eye, pos, end,
                    (type == AwakenedBladeVfxPacket.STEP
                            ? 0.20D + Math.min(3, data.variant()) * 0.045D
                            : 0.42D) * (1 - t * 0.6D),
                    tile, soul, alpha);
            if (type == AwakenedBladeVfxPacket.RELEASE) {
                billboardTile(buffer, matrix, eye, end, 1.35D, 6,
                        soul, alpha * 0.8F, 0);
            }
            return;
        }
        double baseSize = type == AwakenedBladeVfxPacket.AWAKEN ? 2.5D
                : type == AwakenedBladeVfxPacket.RELEASE ? 2.5D : 0.95D;
        if (soul == 1 && type == AwakenedBladeVfxPacket.RELEASE) baseSize = 4.3D;
        if (soul == 3 && type == AwakenedBladeVfxPacket.RELEASE) {
            // Three distinct formations using the *same* Astra blades.
            // Server selects the formation; no decorative entity has an AI tick.
            int count = ClientVisualConfig.BLADE_COMBAT_VFX_MAX_EFFECTS.get() < 32 ? 16 : 32;
            Vec3 end = new Vec3(data.endX(), data.endY(), data.endZ());
            Vec3 heading = end.subtract(pos).multiply(1, 0, 1);
            if (heading.lengthSqr() < 0.01D) heading = new Vec3(0, 0, 1);
            heading = heading.normalize();
            Vec3 sideways = new Vec3(-heading.z, 0, heading.x);
            int mode = Math.floorMod(data.variant(), 3);
            double grow = Mth.clamp((age - 6) / 16.0F, 0, 1);
            double fire = Mth.clamp((age - 28) / 14.0F, 0, 1);
            for (int i = 0; i < count; i++) {
                double a = i * Math.PI * 2.0D / count;
                Vec3 blade;
                if (mode == 1) {
                    // Forward fan converges along the genuine player-to-target axis.
                    double spread = ((i / (double)(count - 1)) - 0.5D) * 5.2D;
                    blade = pos.add(heading.scale(0.8D + 1.0D * grow + 3.0D * fire))
                            .add(sideways.scale(spread * (1.0D - 0.8D * fire)))
                            .add(0, 1.2D + (i % 3) * 0.15D, 0);
                } else if (mode == 2) {
                    // Aerial sword rain falls toward the selected enemy location.
                    blade = end.add(Math.cos(a) * (1.0D + grow * 1.4D),
                            3.2D + (i % 5) * 0.3D - fire * 3.2D,
                            Math.sin(a) * (1.0D + grow * 1.4D));
                } else {
                    // A short-lived defensive ring centred on the player.
                    double radius = (1.1D + grow * 0.9D) * (1 - fire * 0.5D);
                    blade = pos.add(Math.cos(a + age * 0.02D) * radius,
                            1.0D + (i % 4) * 0.2D,
                            Math.sin(a + age * 0.02D) * radius);
                }
                billboardTile(buffer, matrix, eye, blade, 0.38D,
                        i % 8 == 0 ? 2 : 0, soul, alpha * 0.8F, (float) a);
            }
        }
        if (soul == 1 && type == AwakenedBladeVfxPacket.RELEASE) {
            // Each directional scar was earned by circling the target.
            for (int sector = 0; sector < 6; sector++) {
                if ((data.variant() & (1 << sector)) == 0) continue;
                float phase = Mth.clamp((age - sector * 2.0F) / 5.0F, 0, 1);
                billboardTile(buffer, matrix, eye, pos.add(0,
                                Math.sin(sector * Math.PI / 3) * 0.25D, 0),
                        2.7D * (1 - 0.50D * Mth.clamp((age - 24) / 10.0F, 0, 1)),
                        sector * 3 + 1, soul, alpha * phase, 0);
            }
        }
        if (soul == 2 && type == AwakenedBladeVfxPacket.MARK) {
            Vec3 anchor = new Vec3(data.endX(), data.endY(), data.endZ());
            boolean replay = anchor.distanceToSqr(pos) > 0.25D;
            if (replay) {
                // Old attacker position -> old target position, never a live
                // homing trail. Short trails use one alpha-blended atlas quad.
                segment(buffer, matrix, eye, pos, anchor, 0.13D, 3,
                        soul, alpha * 0.65F);
            }
            billboardTile(buffer, matrix, eye, replay ? anchor : pos, 1.45D, 3,
                    soul, alpha * 0.8F,
                    (float) Math.toRadians(Math.floorMod(data.variant(), 360)));
        }
        billboardTile(buffer, matrix, eye, pos, baseSize, tile, soul,
                alpha, (float) (type == AwakenedBladeVfxPacket.RELEASE
                        ? Math.sin(t * Math.PI) * 0.2D : 0));
        if (type == AwakenedBladeVfxPacket.AWAKEN
                || type == AwakenedBladeVfxPacket.RELEASE) {
            int seal = switch (soul) {
                case 1 -> 23; // akatsuki true_name_seal
                case 2 -> 12; // kyouka true_name_seal
                case 3 -> 7; // senbonzakura true_name_seal
                default -> 9; // raikiri true_name_seal
            };
            float sealAlpha = alpha * Mth.clamp((age - 12.0F) / 7.0F, 0, 1)
                    * Mth.clamp((fx.duration - age) / 12.0F, 0, 1);
            billboardTile(buffer, matrix, eye, pos.add(0, 0.55D, 0),
                    1.35D, seal, soul, sealAlpha, 0);
        }
        if (soul == 2 && type == AwakenedBladeVfxPacket.RELEASE) {
            int mirrors = ClientVisualConfig.BLADE_COMBAT_VFX_MAX_EFFECTS.get() < 32 ? 3 : 8;
            for (int i = 0; i < mirrors; i++) {
                double a = i * Math.PI * 2.0D / mirrors;
                float fade = alpha * Mth.clamp((age - i * 2.0F) / 5.0F, 0, 1);
                Vec3 mirror = pos.add(Math.cos(a) * 2.0D, 0.2D,
                        Math.sin(a) * 2.0D);
                billboardTile(buffer, matrix, eye, mirror, 1.1D,
                        age > 40 ? 1 : 0, soul, fade * 0.55F, (float) a);
                if (age >= 24 + i && age < 40) {
                    billboardTile(buffer, matrix, eye,
                            pos.add(Math.cos(a) * 0.8D, 0.25D,
                                    Math.sin(a) * 0.8D),
                            1.75D, 3, soul, fade * 0.8F, (float) a);
                }
            }
        }
    }

    /**
     * Follow the actual player's world position instead of replaying nine
     * once-per-second static billboards. One bounded atlas batch draws every
     * dispersed player's half-moon formation with no network heartbeat.
     */
    private static void drawSakuraCrescent(BufferBuilder buffer, Matrix4f matrix,
            Vec3 eye, float partial, float intensity) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        double max = ClientVisualConfig.BLADE_COMBAT_VFX_DISTANCE.get();
        int count = Math.max(12, Math.min(42,
                ClientVisualConfig.BLADE_COMBAT_VFX_MAX_EFFECTS.get()));
        double age = mc.level.getGameTime() + partial;
        int viewers = 0;
        for (Player player : mc.level.players()) {
            if (++viewers > 32) break;
            if (dev.bladetetra.client.awakening.AwakeningVisualController.presenting(player)) continue;
            var blade = player.getMainHandItem();
            if (blade.getTag() == null
                    || !blade.getTag().getBoolean("blade_tetra_awakened_v2_sakura_dispersed")
                    || StyleResolver.resolve(blade) != BladeStyle.RENGEKI
                    || SoulLegacyState.active(blade) != SoulLegacyState.Legacy.SENBONZAKURA
                    || !SoulLegacyState.isActive(blade,
                            SoulLegacyState.Legacy.SENBONZAKURA)) continue;
            Vec3 center = new Vec3(
                    Mth.lerp(partial, player.xOld, player.getX()),
                    Mth.lerp(partial, player.yOld, player.getY()) + 1.25D,
                    Mth.lerp(partial, player.zOld, player.getZ()));
            if (center.distanceToSqr(eye) > max * max) continue;
            double yaw = Math.toRadians(Mth.lerp(partial, player.yRotO, player.getYRot()));
            Vec3 forward = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
            Vec3 right = new Vec3(forward.z, 0, -forward.x);
            Vec3 back = forward.scale(-0.95D);
            for (int i = 0; i < count; i++) {
                double angle = -Math.PI / 2.0D + Math.PI * (i + 0.5D) / count;
                double wing = Math.sin(angle);
                double arch = Math.cos(angle);
                double drift = Math.sin(age * 0.07D + i * 1.618D) * 0.095D;
                Vec3 shard = center.add(back.scale(1.0D + 0.12D * (i % 3)))
                        .add(right.scale(wing * (1.65D + drift)))
                        .add(0, arch * 0.6D + (i % 4 - 1.5D) * 0.09D, 0);
                billboardTile(buffer, matrix, eye, shard,
                        0.25D + (i % 5) * 0.035D, i % 3, 3,
                        intensity * (0.65F + (i % 4) * 0.07F),
                        (float) (angle * 0.45D + age * 0.013D));
            }
        }
    }

    /**
     * Every accepted native hit may show a bounded Zenith-like ribbon of
     * returning fragments. Positions are deterministic from the hit packet.
     * These quads never spawn projectiles, collision or extra damage.
     */
    private static void drawSakuraVolley(BufferBuilder buffer, Matrix4f matrix,
            Vec3 eye, AwakenedBladeVfxPacket data, float age, float alpha) {
        Vec3 origin = new Vec3(data.x(), data.y(), data.z());
        Vec3 target = new Vec3(data.endX(), data.endY(), data.endZ());
        Vec3 displacement = target.subtract(origin);
        if (displacement.lengthSqr() < 0.04D
                || displacement.lengthSqr() > 400.0D) return;
        Vec3 forward = displacement.normalize();
        Vec3 side = new Vec3(-forward.z, 0, forward.x);
        if (side.lengthSqr() < 0.01D) side = new Vec3(1, 0, 0);
        else side = side.normalize();
        int grade = Mth.clamp(data.variant() - 10, 4, 7);
        int count = 9 + (grade - 4) * 3;
        for (int i = 0; i < count; i++) {
            double angle = i * 2.399963229728653D;
            Vec3 start = origin.subtract(forward.scale(0.75D))
                    .add(side.scale(Math.sin(angle) * 1.0D))
                    .add(0, Math.cos(angle) * 0.48D, 0);
            Vec3 end = target.add(side.scale(Math.cos(angle) * 0.62D))
                    .add(0, Math.sin(angle) * 0.42D, 0);
            double u = Mth.clamp((age - i * 0.32D) / (9.0D + i % 4), 0, 1);
            if (u <= 0 || u >= 1) continue;
            double curve = Math.sin(u * Math.PI) * (0.35D + i % 3 * 0.14D);
            Vec3 blade = start.lerp(end, u).add(side.scale(curve));
            double earlier = Math.max(0, u - 0.065D);
            Vec3 tail = start.lerp(end, earlier)
                    .add(side.scale(Math.sin(earlier * Math.PI)
                            * (0.35D + i % 3 * 0.14D)));
            // Thin, short trails: do not draw light beams across the arena.
            segment(buffer, matrix, eye, tail, blade,
                    0.022D, 1, 3, alpha * 0.43F);
            billboardTile(buffer, matrix, eye, blade, 0.24D + (i % 4) * 0.045D,
                    i % 3, 3, alpha * 0.92F, (float) angle);
        }
    }

    /** Each phase matches the real causal impact at tick 33. */
    private static void drawCausalExecution(BufferBuilder buffer, Matrix4f matrix,
            Vec3 eye, Vec3 pos, float age, float alpha, int directions) {
        for (int sector = 0; sector < 6; sector++) {
            if ((directions & (1 << sector)) == 0) continue;
            float appear = Mth.clamp((age - sector * 2.0F) / 4.0F, 0, 1);
            float contract = Mth.clamp((age - 24.0F) / 8.0F, 0, 1);
            int frame = age > 40 ? sector * 3 + 2 : sector * 3 + 1;
            billboardTile(buffer, matrix, eye,
                    pos.add(0, Math.sin(sector * Math.PI / 3.0D) * 0.18D, 0),
                    2.9D * (1 - contract * 0.55D), frame, 1,
                    alpha * appear * (age > 42 ? 0.5F : 0.9F), 0);
        }
        if (age >= 10 && age < 43) {
            float a = alpha * Mth.clamp((age - 10) / 8.0F, 0, 1)
                    * Mth.clamp((43 - age) / 7.0F, 0, 1);
            billboardTile(buffer, matrix, eye, pos, 0.9D, 18, 1, a, 0);
        }
        if (age >= 14 && age < 43) {
            float a = alpha * Mth.clamp((age - 14) / 6.0F, 0, 1)
                    * Mth.clamp((43 - age) / 7.0F, 0, 1);
            billboardTile(buffer, matrix, eye, pos, 4.1D, 20, 1, a, 0);
        }
        if (age >= 32 && age < 42) {
            float a = alpha * Mth.clamp((age - 32) / 2.0F, 0, 1)
                    * Mth.clamp((42 - age) / 6.0F, 0, 1);
            billboardTile(buffer, matrix, eye, pos, 4.6D,
                    21, 1, a, 0);
        }
    }

    /**
     * Astra's compact 44-tick ceremonies: no new ritual conditions, only
     * different successive art layers after the server confirms an unlock.
     */
    private static void drawAwakening(BufferBuilder buffer, Matrix4f matrix,
            Vec3 eye, Vec3 pos, int soul, float age, float alpha) {
        float t = Mth.clamp(age / AwakeningPresentationTimeline.WORLD_END, 0, 1);
        switch (soul) {
            case 1 -> {
                // The blood crescent anchors *world-space* causal scars;
                // unlike camera-facing glyphs, these strokes occupy actual
                // 3D start/end positions and contract into one cut at 32t.
                if (age >= 10.0F && age < 43.0F) {
                    float appear = Mth.clamp((age - 10.0F) / 9.0F, 0, 1);
                    float breakApart = Mth.clamp((age - 32.0F) / 11.0F, 0, 1);
                    for (int i = 0; i < 3; i++) {
                        double angle = (i - 1) * Math.PI / 3.5D + 0.25D;
                        double radius = 1.30D * (1.0D - breakApart * 0.68D);
                        Vec3 along = new Vec3(Math.cos(angle), 0, Math.sin(angle))
                                .scale(radius);
                        Vec3 lift = new Vec3(0, 0.20D * (i - 1), 0);
                        segment(buffer, matrix, eye,
                                pos.subtract(along).add(lift),
                                pos.add(along).add(lift),
                                0.065D + breakApart * 0.070D,
                                16, soul, alpha * appear * (1 - breakApart * 0.55F));
                    }
                    if (age >= 29 && age < 39) {
                        float flash = Mth.clamp((age - 29) / 3.0F, 0, 1)
                                * Mth.clamp((39 - age) / 5.0F, 0, 1);
                        segment(buffer, matrix, eye,
                                pos.add(-1.9, 1.1, -0.8),
                                pos.add(1.9, -1.1, 0.8),
                                0.13D, 21, soul, alpha * flash);
                    }
                }
                // 0-6 crescent, 6-23 sword trace, 23-32 name seal, 32-44 fade.
                float moon = alpha * Mth.clamp(age / 6.0F, 0, 1);
                billboardTile(buffer, matrix, eye, pos.add(0, 0.65D, 0),
                        2.5D, 19, soul, moon, 0);
                if (age >= 6 && age <= 40) {
                    float trace = alpha * Mth.clamp((age - 6) / 5.0F, 0, 1)
                            * Mth.clamp((40 - age) / 8.0F, 0, 1);
                    billboardTile(buffer, matrix, eye, pos.add(0.25D, 0, 0),
                            1.3D, 22, soul, trace, -0.27F);
                }
            }
            case 2 -> {
                // Water pool at feet, reflected ghost, mirror burst, then seal.
                Vec3 pool = pos.add(0, -0.95D, 0);
                billboardTile(buffer, matrix, eye, pool, 2.1D,
                        age < 19 ? 0 : 1, soul, alpha * 0.82F, 0);
                if (age >= 8 && age < 26) {
                    billboardTile(buffer, matrix, eye, pos, 1.3D, 2,
                            soul, alpha * 0.6F, 0);
                }
                if (age >= 18 && age < 35) {
                    float fade = alpha * Mth.clamp((35 - age) / 12.0F, 0, 1);
                    for (int i = 0; i < 4; i++) {
                        double a = Math.PI * 0.5D * i;
                        Vec3 shard = pool.add(Math.cos(a) * (0.2D + 0.07D * (age - 18)),
                                0.35D + Math.max(0, age - 18) * 0.045D,
                                Math.sin(a) * (0.2D + 0.07D * (age - 18)));
                        billboardTile(buffer, matrix, eye, shard, 0.28D,
                                5 + i, soul, fade, (float) a);
                    }
                }
            }
            case 3 -> {
                // 0-16 orbital petal ring, 16-27 condensed sword, 32+ scatter.
                int petals = 12;
                double gather = Mth.clamp((age - 16) / 11.0F, 0, 1);
                double scatter = Mth.clamp((age - 32) / 12.0F, 0, 1);
                for (int i = 0; i < petals; i++) {
                    double a = i * Math.PI * 2.0D / petals + age * 0.08D;
                    double radius = (1.2D - gather * 1.1D) + scatter * 1.6D;
                    Vec3 v = pos.add(Math.cos(a) * radius,
                            Math.sin(a * 2) * (0.35D + 0.08D * scatter),
                            Math.sin(a) * radius);
                    billboardTile(buffer, matrix, eye, v,
                            0.36D, 5, soul, alpha * 0.8F, (float) a);
                }
                if (age >= 16 && age <= 32) {
                    billboardTile(buffer, matrix, eye, pos, 1.1D, 2,
                            soul, alpha * Mth.clamp((age - 16) / 7.0F, 0, 1), 0);
                }
            }
            default -> {
                // Lightning crawls onto the sheath, peaks at 12-17, then fades.
                for (int i = 0; i < 3 && age < 27; i++) {
                    double a = age * 0.2D + i * Math.PI * 2.0D / 3.0D;
                    Vec3 spark = pos.add(Math.cos(a) * 0.52D,
                            -0.25D + i * 0.27D, Math.sin(a) * 0.52D);
                    billboardTile(buffer, matrix, eye, spark, 0.65D, 4,
                            soul, alpha * (age <= 17 ? 0.8F : 0.35F), (float) a);
                }
                if (age >= 12 && age <= 23) {
                    billboardTile(buffer, matrix, eye, pos, 2.3D, 8,
                            soul, alpha * Mth.clamp((age - 12) / 3.0F, 0, 1)
                                    * Mth.clamp((23 - age) / 6.0F, 0, 1), 0);
                }
            }
        }
        int seal = switch (soul) {
            case 1 -> 23; case 2 -> 12; case 3 -> 7; default -> 9;
        };
        float name = alpha * Mth.clamp((age - (soul == 2 ? 25 : 23)) / 4.0F, 0, 1)
                * Mth.clamp((43.0F - age) / 11.0F, 0, 1);
        billboardTile(buffer, matrix, eye, pos.add(0, 0.7D, 0),
                1.3D, seal, soul, name, 0);
    }

    private static void drawAnimation(BufferBuilder buffer, Matrix4f matrix,
            Vec3 eye, Effect fx, float partial, float intensity) {
        AwakenedBladeVfxPacket data = fx.data;
        int soul = data.soul(), type = data.type();
        boolean relevant = switch (soul) {
            case 1 -> type == AwakenedBladeVfxPacket.MARK;
            case 2 -> type == AwakenedBladeVfxPacket.MARK
                    || type == AwakenedBladeVfxPacket.AWAKEN;
            case 3 -> type == AwakenedBladeVfxPacket.STANCE;
            default -> type == AwakenedBladeVfxPacket.STEP
                    || type == AwakenedBladeVfxPacket.AWAKEN;
        };
        if (!relevant) return;
        float age = fx.age + partial;
        float alpha = Mth.clamp(age / 3.0F, 0, 1)
                * Mth.clamp((fx.duration - age) / 6.0F, 0, 1)
                * Mth.clamp(intensity, 0, 1);
        int frame = switch (soul) {
            case 1 -> age < 4 ? (int) age : age < fx.duration - 6
                    ? 6 : 10 + (int) Math.min(5, age - (fx.duration - 6));
            case 2 -> Math.min(15, (int) age / 2);
            case 3 -> data.variant() == 0 ? 15 - Math.min(15, (int) age)
                    : Math.min(15, (int) age);
            default -> Math.min(7, (int) age);
        };
        Vec3 center = new Vec3(data.x(), data.y(), data.z());
        if (soul == 4 && type == AwakenedBladeVfxPacket.STEP) {
            center = new Vec3(data.endX(), data.endY(), data.endZ());
        }
        double size = soul == 2 ? 2.0D : soul == 3 ? 1.3D : 1.9D;
        if (soul == 1) size = 2.8D;
        // All animation sheets are 4x4 with half-pixel inset (Astra contract).
        Vec3 look = eye.subtract(center);
        if (look.lengthSqr() < 0.001D) look = new Vec3(0, 0, 1);
        look = look.normalize();
        Vec3 right = new Vec3(0, 1, 0).cross(look);
        if (right.lengthSqr() < 0.001D) right = new Vec3(1, 0, 0);
        right = right.normalize().scale(size * 0.5D);
        Vec3 up = look.cross(right).normalize().scale(size * 0.5D);
        float u0 = ((frame % 4) * 128 + 0.5F) / 512.0F;
        float v0 = ((frame / 4) * 128 + 0.5F) / 512.0F;
        float u1 = ((frame % 4) * 128 + 127.5F) / 512.0F;
        float v1 = ((frame / 4) * 128 + 127.5F) / 512.0F;
        texturedQuad(buffer, matrix, center.subtract(right).add(up),
                center.subtract(right).subtract(up), center.add(right).subtract(up),
                center.add(right).add(up), u0, v0, u1, v1, alpha, 0xFFFFFF);
    }

    private static final class Effect {
        final AwakenedBladeVfxPacket data;
        final int duration;
        int age;

        Effect(AwakenedBladeVfxPacket data) {
            this.data = data;
            this.duration = switch (data.type()) {
                case AwakenedBladeVfxPacket.AWAKEN -> AwakeningPresentationTimeline.WORLD_END;
                case AwakenedBladeVfxPacket.RELEASE -> data.soul() == 3 ? 64
                        : data.soul() == 4 ? 40 : 56;
                case AwakenedBladeVfxPacket.STEP -> 22;
                case AwakenedBladeVfxPacket.MARK -> data.soul() == 1 ? 160 : 18;
                default -> 18;
            };
        }
    }

    private AwakenedBladeVfxClient() {
    }
}
