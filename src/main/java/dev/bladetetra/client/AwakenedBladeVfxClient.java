package dev.bladetetra.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.bladetetra.BladeTetra;
import dev.bladetetra.config.ClientVisualConfig;
import dev.bladetetra.network.AwakenedBladeVfxPacket;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
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
                || EFFECTS.isEmpty()
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
            Tesselator.getInstance().end();

            RenderSystem.setShaderTexture(0, ANIMATIONS[soul - 1]);
            buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            for (Effect fx : EFFECTS) {
                if (fx.data.soul() == soul) drawAnimation(buffer, matrix, eye, fx,
                        event.getPartialTick(), intensity);
            }
            Tesselator.getInstance().end();
        }
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        poses.popPose();
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
                    (type == AwakenedBladeVfxPacket.STEP ? 0.25D : 0.42D)
                            * (1 - t * 0.6D),
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
            // One event expands into decorative quads, never into gameplay entities.
            int count = ClientVisualConfig.BLADE_COMBAT_VFX_MAX_EFFECTS.get() < 32 ? 16 : 32;
            for (int i = 0; i < count; i++) {
                double angle = i * Math.PI * 2.0D / count;
                double formation = Mth.clamp((age - 6) / 16.0F, 0, 1);
                double dive = Mth.clamp((age - 28) / 14.0F, 0, 1);
                double radius = (1.1D + formation * 1.6D) * (1 - dive * 0.8D);
                Vec3 blade = pos.add(Math.cos(angle) * radius,
                        0.4D + formation * (i % 5) * 0.34D - dive * 1.4D,
                        Math.sin(angle) * radius);
                billboardTile(buffer, matrix, eye, blade, 0.38D,
                        0, soul, alpha * 0.8F, (float) angle);
            }
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
            for (int i = 0; i < 5; i++) {
                double a = i * Math.PI * 2.0D / 5.0D;
                Vec3 mirror = pos.add(Math.cos(a) * 2.0D, 0.2D,
                        Math.sin(a) * 2.0D);
                billboardTile(buffer, matrix, eye, mirror, 1.1D,
                        t > 0.68F ? 1 : 0, soul, alpha * 0.55F, (float) a);
            }
        }
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

    private static void segment(BufferBuilder buffer, Matrix4f matrix, Vec3 eye,
            Vec3 from, Vec3 to, double width, int tile, int soul, float alpha) {
        Vec3 axis = to.subtract(from);
        if (axis.lengthSqr() < 0.001D || axis.lengthSqr() > 64.0D) return;
        Vec3 side = axis.cross(eye.subtract(from.add(to).scale(0.5D)));
        if (side.lengthSqr() < 0.001D) side = axis.cross(new Vec3(0, 1, 0));
        if (side.lengthSqr() < 0.001D) return;
        side = side.normalize().scale(width);
        uvQuad(buffer, matrix, from.add(side), from.subtract(side),
                to.subtract(side), to.add(side), tile, soul, alpha);
    }

    private static void billboardTile(BufferBuilder buffer, Matrix4f matrix,
            Vec3 eye, Vec3 center, double size, int tile, int soul,
            float alpha, float rotation) {
        Vec3 look = eye.subtract(center);
        if (look.lengthSqr() < 0.001D) look = new Vec3(0, 0, 1);
        look = look.normalize();
        Vec3 right = new Vec3(0, 1, 0).cross(look);
        if (right.lengthSqr() < 0.001D) right = new Vec3(1, 0, 0);
        right = right.normalize();
        Vec3 up = look.cross(right).normalize();
        Vec3 dx = right.scale(Math.cos(rotation)).add(up.scale(Math.sin(rotation)))
                .scale(size * 0.5D);
        Vec3 dy = up.scale(Math.cos(rotation)).subtract(right.scale(Math.sin(rotation)))
                .scale(size * 0.5D);
        uvQuad(buffer, matrix, center.subtract(dx).add(dy),
                center.subtract(dx).subtract(dy), center.add(dx).subtract(dy),
                center.add(dx).add(dy), tile, soul, alpha);
    }

    private static void uvQuad(BufferBuilder buffer, Matrix4f matrix,
            Vec3 a, Vec3 b, Vec3 c, Vec3 d, int tile, int soul, float alpha) {
        int columns = soul == 1 ? 8 : 4;
        int height = soul == 1 ? 512 : 512;
        int width = soul == 1 ? 1024 : 512;
        int x = (tile % columns) * 128;
        int y = (tile / columns) * 128;
        float u0 = (x + 0.5F) / width;
        float v0 = (y + 0.5F) / height;
        float u1 = (x + 127.5F) / width;
        float v1 = (y + 127.5F) / height;
        texturedQuad(buffer, matrix, a, b, c, d,
                u0, v0, u1, v1, alpha, 0xFFFFFF);
    }

    private static final class Effect {
        final AwakenedBladeVfxPacket data;
        final int duration;
        int age;

        Effect(AwakenedBladeVfxPacket data) {
            this.data = data;
            this.duration = switch (data.type()) {
                case AwakenedBladeVfxPacket.AWAKEN -> 44;
                case AwakenedBladeVfxPacket.RELEASE -> data.soul() == 3 ? 64
                        : data.soul() == 4 ? 40 : 56;
                case AwakenedBladeVfxPacket.STEP -> 22;
                default -> 18;
            };
        }
    }

    private AwakenedBladeVfxClient() {
    }
}
