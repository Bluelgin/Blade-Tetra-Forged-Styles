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

import static dev.bladetetra.client.vfx.render.VfxPrimitives.horizontalTexturedQuad;
import static dev.bladetetra.client.vfx.render.VfxPrimitives.rotatedAtlasBillboard;

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
    private static final java.util.Map<java.util.UUID,Long> SEEN = new java.util.HashMap<>();

    public static void spawn(KyoukaVfxPacket packet) {
        if (!ClientVisualConfig.ENABLE_BLADE_COMBAT_VFX.get()) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != activeLevel) {
            EFFECTS.clear(); SEEN.clear(); dev.bladetetra.client.kyouka.WaterMirrorLayer.clear();
            activeLevel = minecraft.level;
        }
        if (minecraft.level == null || !packet.dimension().equals(minecraft.level.dimension().location())
                || packet.duration() < 1 || packet.duration() > 240 && packet.type() >= KyoukaVfxPacket.WATER_GATHER || !Float.isFinite(packet.scale())
                || SEEN.containsKey(packet.eventId())) return;
        long elapsed = minecraft.level.getGameTime() - packet.started();
        if (elapsed >= packet.duration() || elapsed < -40) return;
        if (SEEN.size() >= 512) SEEN.remove(SEEN.keySet().iterator().next());
        SEEN.put(packet.eventId(), minecraft.level.getGameTime()+240);
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
        if (packet.type() == KyoukaVfxPacket.WATER_DEFLECT
                && minecraft.level.getEntity(packet.sourceEntityId()) instanceof net.minecraft.client.player.AbstractClientPlayer p)
            dev.bladetetra.client.kyouka.WaterMirrorLayer.impulse(p,packet.started());
        Effect effect = new Effect(packet); effect.age = (int)Math.max(0,elapsed);
        EFFECTS.add(effect);
        int cap = Math.max(12, ClientVisualConfig.BLADE_COMBAT_VFX_MAX_EFFECTS.get());
        while (EFFECTS.size() > cap) EFFECTS.remove(0);
    }

    @SubscribeEvent
    public static void arm(net.minecraftforge.client.event.RenderArmEvent e) {
        var p = e.getPlayer();
        if (!p.hasEffect(dev.bladetetra.kyouka.WaterMirrorEffect.ARMOR.get())
                || !ClientVisualConfig.ENABLE_BLADE_COMBAT_VFX.get()) return;
        var renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(p);
        if (!(renderer instanceof net.minecraft.client.renderer.entity.player.PlayerRenderer playerRenderer)) return;
        var model = playerRenderer.getModel();
        var arm = e.getArm() == net.minecraft.world.entity.HumanoidArm.RIGHT ? model.rightArm : model.leftArm;
        float x = arm.xRot, y = arm.yRot, z = arm.zRot;
        try {
            arm.xRot = 0; arm.yRot = 0; arm.zRot = 0;
            float ratio = Math.max(0, Math.min(1, p.getAirSupply()/(float)Math.max(1,p.getMaxAirSupply())));
            arm.render(e.getPoseStack(), e.getMultiBufferSource().getBuffer(net.minecraft.client.renderer.RenderType.entityTranslucent(
                    dev.bladetetra.client.kyouka.WaterMirrorLayer.WATER)), e.getPackedLight(),
                    net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, .65F, .88F, 1, .06F+.18F*ratio);
        } finally { arm.xRot=x; arm.yRot=y; arm.zRot=z; }
    }

    @SubscribeEvent
    public static void clientTick(TickEvent.ClientTickEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (event.phase != TickEvent.Phase.END || minecraft.isPaused()) return;
        if (minecraft.level != activeLevel) {
            EFFECTS.clear(); SEEN.clear(); dev.bladetetra.client.kyouka.WaterMirrorLayer.clear();
            activeLevel = minecraft.level;
        }
        if (activeLevel != null) SEEN.values().removeIf(until -> until < activeLevel.getGameTime());
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
        renderWater(matrix, camera.getPosition(), event.getPartialTick(), global);

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
            if (effect.type == KyoukaVfxPacket.BREAK_SHATTER || effect.type >= KyoukaVfxPacket.WATER_GATHER) continue;
            float age = effect.age + partialTick;
            float fadeIn = Mth.clamp(age / 5.0F, 0.0F, 1.0F);
            float fadeOut = Mth.clamp((effect.duration - age) / 9.0F, 0.0F, 1.0F);
            float pulse = effect.type == KyoukaVfxPacket.BREAK_CHARGE
                    ? 0.88F + 0.12F * Mth.sin(age * 0.75F) : 1.0F;
            float alpha = fadeIn * fadeOut * pulse * global
                    * (effect.type == KyoukaVfxPacket.BREAK_CHARGE ? 0.82F : 0.58F);
            double radius = effect.scale * (effect.type == KyoukaVfxPacket.BREAK_CHARGE
                    ? 1.55D + age / Math.max(1.0D, effect.duration) * 0.25D : 1.30D);
            horizontalTexturedQuad(buffer, matrix,
                    effect.position.add(0.0D, 0.035D, 0.0D), radius, alpha, 0xFFFFFF);
        }
        Tesselator.getInstance().end();
    }

    private static void renderWater(Matrix4f matrix, Vec3 camera, float partial, float global) {
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        for (Effect e : EFFECTS) {
            if (e.type < KyoukaVfxPacket.WATER_GATHER) continue;
            float t = Mth.clamp((e.age + partial) / e.duration, 0, 1);
            Vec3 center = e.position.add(0, .9, 0);
            boolean gather = e.type == KyoukaVfxPacket.WATER_GATHER;
            // Droplets converge and compress; impact emits an expanding blue-white water ring.
            for (int i=0; i<10; i++) {
                double angle = i * Math.PI * 2 / 10 + t * 2;
                double radius = gather ? 1.1 * (1-t) : .15 + t * 1.8;
                Vec3 droplet = center.add(Math.cos(angle)*radius, Math.sin(angle*2)*radius*.35, Math.sin(angle)*radius);
                dev.bladetetra.client.vfx.render.VfxPrimitives.billboard(buffer, matrix, camera,
                        droplet, gather ? .04 + .08*t : .12*(1-t), (1-t*.7F)*global*.8F, 0xB2E9FF);
            }
            if (!gather) horizontalTexturedQuad(buffer, matrix, e.position.add(0,.08,0), .3+t*1.7, (1-t)*global, 0xCAEFFF);
            else dev.bladetetra.client.vfx.render.VfxPrimitives.billboard(buffer, matrix, camera,
                    center, .08+.18*t, t*global*.8F, 0xE0F8FF);
        }
        Tesselator.getInstance().end();
    }

    private static void renderShards(Matrix4f matrix, Vec3 camera,
            float partialTick, float global) {
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
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
                rotatedAtlasBillboard(buffer, matrix, camera, center, size,
                        (float) (angle + t * 5.0D + index), alpha, 0xFFFFFF,
                        4, 3, index % 4, index / 4);
            }
        }
        Tesselator.getInstance().end();
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
            if (target != null && type < KyoukaVfxPacket.WATER_GATHER) position = target.position();
        }
    }

    private KyoukaVfxClient() {
    }
}
