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
        if (type == AwakenedBladeVfxPacket.AWAKEN) {
            drawAwakening(buffer, matrix, eye, pos, soul, age, alpha);
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
            for (int i = 0; i < 5; i++) {
                double a = i * Math.PI * 2.0D / 5.0D;
                Vec3 mirror = pos.add(Math.cos(a) * 2.0D, 0.2D,
                        Math.sin(a) * 2.0D);
                billboardTile(buffer, matrix, eye, mirror, 1.1D,
                        t > 0.68F ? 1 : 0, soul, alpha * 0.55F, (float) a);
            }
        }
    }

    /**
     * Astra's compact 44-tick ceremonies: no new ritual conditions, only
     * different successive art layers after the server confirms an unlock.
     */
    private static void drawAwakening(BufferBuilder buffer, Matrix4f matrix,
            Vec3 eye, Vec3 pos, int soul, float age, float alpha) {
        float t = Mth.clamp(age / 44.0F, 0, 1);
        switch (soul) {
            case 1 -> {
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
                case AwakenedBladeVfxPacket.MARK -> data.soul() == 1 ? 160 : 18;
                default -> 18;
            };
        }
    }

    private AwakenedBladeVfxClient() {
    }
}
