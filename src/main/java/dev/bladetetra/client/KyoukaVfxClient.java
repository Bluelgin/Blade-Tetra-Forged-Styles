package dev.bladetetra.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.bladetetra.BladeTetra;
import dev.bladetetra.config.ClientVisualConfig;
import dev.bladetetra.network.KyoukaVfxPacket;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
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

/** Ground mirror and finishing shards; the actual cuts remain SlashBlade entities. */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class KyoukaVfxClient {
    private static final ResourceLocation POOL_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            BladeTetra.MOD_ID, "textures/effect/kyouka/mirror_pool.png");
    private static final ResourceLocation SHARD_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            BladeTetra.MOD_ID, "textures/effect/kyouka/mirror_shards.png");
    private static final List<Effect> EFFECTS = new ArrayList<>();
    private static ClientLevel activeLevel;

    public static void spawn(KyoukaVfxPacket packet) {
        if (!ClientVisualConfig.ENABLE_BLADE_COMBAT_VFX.get()) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != activeLevel) {
            EFFECTS.clear();
            activeLevel = minecraft.level;
        }
        if (minecraft.player != null) {
            double maximum = ClientVisualConfig.BLADE_COMBAT_VFX_DISTANCE.get();
            if (minecraft.player.distanceToSqr(packet.x(), packet.y(), packet.z())
                    > maximum * maximum) return;
        }
        if (packet.type() == KyoukaVfxPacket.REFLECTION_POOL
                || packet.type() == KyoukaVfxPacket.BREAK_CHARGE) {
            EFFECTS.removeIf(effect -> effect.type != KyoukaVfxPacket.BREAK_SHATTER
                    && effect.sourceEntityId == packet.sourceEntityId()
                    && effect.targetEntityId == packet.targetEntityId());
        }
        EFFECTS.add(new Effect(packet));
        int cap = Math.max(12, ClientVisualConfig.BLADE_COMBAT_VFX_MAX_EFFECTS.get());
        while (EFFECTS.size() > cap) EFFECTS.remove(0);
    }

    @SubscribeEvent
    public static void clientTick(TickEvent.ClientTickEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (event.phase != TickEvent.Phase.END || minecraft.isPaused()) return;
        if (minecraft.level != activeLevel) {
            EFFECTS.clear();
            activeLevel = minecraft.level;
        }
        Iterator<Effect> iterator = EFFECTS.iterator();
        while (iterator.hasNext()) {
            Effect effect = iterator.next();
            effect.tick(activeLevel);
            if (effect.age >= effect.duration) iterator.remove();
        }
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES
                || EFFECTS.isEmpty()
                || !ClientVisualConfig.ENABLE_BLADE_COMBAT_VFX.get()) return;
        Camera camera = event.getCamera();
        PoseStack poses = event.getPoseStack();
        poses.pushPose();
        poses.translate(-camera.getPosition().x, -camera.getPosition().y,
                -camera.getPosition().z);
        Matrix4f matrix = poses.last().pose();
        float global = ClientVisualConfig.BLADE_COMBAT_VFX_INTENSITY.get().floatValue();

        RenderSystem.enableBlend();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderTexture(0, POOL_TEXTURE);
        renderPools(matrix, event.getPartialTick(), global);

        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE);
        RenderSystem.setShaderTexture(0, SHARD_TEXTURE);
        renderShards(matrix, camera.getPosition(), event.getPartialTick(), global);
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        poses.popPose();
    }

    private static void renderPools(Matrix4f matrix, float partialTick, float global) {
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        for (Effect effect : EFFECTS) {
            if (effect.type == KyoukaVfxPacket.BREAK_SHATTER) continue;
            float age = effect.age + partialTick;
            float fadeIn = Mth.clamp(age / 5.0F, 0.0F, 1.0F);
            float fadeOut = Mth.clamp((effect.duration - age) / 9.0F, 0.0F, 1.0F);
            float pulse = effect.type == KyoukaVfxPacket.BREAK_CHARGE
                    ? 0.88F + 0.12F * Mth.sin(age * 0.75F) : 1.0F;
            float alpha = fadeIn * fadeOut * pulse * global
                    * (effect.type == KyoukaVfxPacket.BREAK_CHARGE ? 0.82F : 0.58F);
            double radius = effect.scale * (effect.type == KyoukaVfxPacket.BREAK_CHARGE
                    ? 1.55D + age / Math.max(1.0D, effect.duration) * 0.25D : 1.30D);
            horizontalQuad(buffer, matrix, effect.position.add(0.0D, 0.035D, 0.0D),
                    radius, alpha);
        }
        Tesselator.getInstance().end();
    }

    private static void renderShards(Matrix4f matrix, Vec3 camera,
            float partialTick, float global) {
        for (Effect effect : EFFECTS) {
            if (effect.type != KyoukaVfxPacket.BREAK_SHATTER) continue;
            float age = effect.age + partialTick;
            float t = Mth.clamp(age / effect.duration, 0.0F, 1.0F);
            float alpha = (1.0F - t) * (1.0F - t) * global * 0.92F;
            for (int index = 0; index < 12; index++) {
                double angle = deterministic(effect.seed, index, 0) * Math.PI * 2.0D;
                double speed = 0.55D + deterministic(effect.seed, index, 1) * 1.15D;
                double radius = speed * (0.18D + t * 1.85D) * effect.scale;
                double rise = (0.25D + deterministic(effect.seed, index, 2) * 1.35D)
                        * Math.sin(t * Math.PI) * effect.scale;
                Vec3 center = effect.position.add(Math.cos(angle) * radius,
                        0.35D + rise, Math.sin(angle) * radius);
                double size = (0.18D + deterministic(effect.seed, index, 3) * 0.20D)
                        * effect.scale * (1.0D - t * 0.28D);
                billboardCell(matrix, camera, center, size,
                        (float) (angle + t * 5.0D + index), alpha,
                        index % 4, index / 4);
            }
        }
    }

    private static void horizontalQuad(BufferBuilder buffer, Matrix4f matrix,
            Vec3 center, double radius, float alpha) {
        vertex(buffer, matrix, center.add(-radius, 0, -radius), 0, 0, alpha);
        vertex(buffer, matrix, center.add(-radius, 0, radius), 0, 1, alpha);
        vertex(buffer, matrix, center.add(radius, 0, radius), 1, 1, alpha);
        vertex(buffer, matrix, center.add(radius, 0, -radius), 1, 0, alpha);
    }

    private static void billboardCell(Matrix4f matrix, Vec3 camera, Vec3 center,
            double halfSize, float rotation, float alpha, int column, int row) {
        Vec3 facing = camera.subtract(center);
        if (facing.lengthSqr() < 0.001D) facing = new Vec3(0, 0, 1);
        facing = facing.normalize();
        Vec3 right = new Vec3(0, 1, 0).cross(facing);
        if (right.lengthSqr() < 0.001D) right = new Vec3(1, 0, 0);
        right = right.normalize();
        Vec3 up = facing.cross(right).normalize();
        Vec3 rotatedRight = right.scale(Math.cos(rotation))
                .add(up.scale(Math.sin(rotation))).scale(halfSize);
        Vec3 rotatedUp = up.scale(Math.cos(rotation))
                .subtract(right.scale(Math.sin(rotation))).scale(halfSize);
        float u0 = column / 4.0F;
        float u1 = (column + 1) / 4.0F;
        float v0 = row / 3.0F;
        float v1 = (row + 1) / 3.0F;
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        vertex(buffer, matrix, center.subtract(rotatedRight).add(rotatedUp), u0, v0, alpha);
        vertex(buffer, matrix, center.subtract(rotatedRight).subtract(rotatedUp), u0, v1, alpha);
        vertex(buffer, matrix, center.add(rotatedRight).subtract(rotatedUp), u1, v1, alpha);
        vertex(buffer, matrix, center.add(rotatedRight).add(rotatedUp), u1, v0, alpha);
        Tesselator.getInstance().end();
    }

    private static void vertex(BufferBuilder buffer, Matrix4f matrix, Vec3 position,
            float u, float v, float alpha) {
        buffer.vertex(matrix, (float) position.x, (float) position.y, (float) position.z)
                .uv(u, v).color(255, 255, 255,
                        Mth.clamp(Math.round(alpha * 255.0F), 0, 255)).endVertex();
    }

    private static double deterministic(int seed, int index, int channel) {
        int value = seed ^ index * 0x45D9F3B ^ channel * 0x119DE1F3;
        value ^= value >>> 16;
        value *= 0x45D9F3B;
        value ^= value >>> 16;
        return (value & 0x7FFFFFFF) / (double) Integer.MAX_VALUE;
    }

    private static final class Effect {
        final int type;
        final int sourceEntityId;
        final int targetEntityId;
        final float scale;
        final int duration;
        final int seed;
        Vec3 position;
        int age;

        Effect(KyoukaVfxPacket packet) {
            type = packet.type();
            sourceEntityId = packet.sourceEntityId();
            targetEntityId = packet.targetEntityId();
            scale = packet.scale();
            duration = Math.max(1, packet.duration());
            seed = packet.seed();
            position = new Vec3(packet.x(), packet.y(), packet.z());
        }

        void tick(ClientLevel level) {
            age++;
            Entity target = level == null || targetEntityId < 0
                    ? null : level.getEntity(targetEntityId);
            if (target != null) position = target.position();
        }
    }

    private KyoukaVfxClient() {
    }
}
