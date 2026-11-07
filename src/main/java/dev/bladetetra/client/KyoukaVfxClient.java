package dev.bladetetra.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import dev.bladetetra.BladeTetra;
import dev.bladetetra.client.kyouka.WaterMirrorVisuals;
import dev.bladetetra.config.ClientVisualConfig;
import dev.bladetetra.kyouka.WaterMirrorEffect;
import dev.bladetetra.network.KyoukaVfxPacket;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
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
            EFFECTS.clear(); SEEN.clear();
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
                    && effect.type < KyoukaVfxPacket.ARMOR_DEFLECT
                    && effect.sourceEntityId == packet.sourceEntityId()
                    && effect.targetEntityId == packet.targetEntityId());
        }
        if (packet.type() == KyoukaVfxPacket.AFTERIMAGE_FADE) {
            int kept = 0;
            for (int i = EFFECTS.size() - 1; i >= 0; i--) {
                Effect previous = EFFECTS.get(i);
                if (previous.type == packet.type() && previous.sourceEntityId == packet.sourceEntityId()
                        && ++kept >= 2) EFFECTS.remove(i);
            }
        }
        Effect effect = new Effect(packet);
        effect.age = (int) Math.max(0, elapsed);
        if (packet.type() == KyoukaVfxPacket.ARMOR_DEFLECT
                && packet.targetEntityId() >= 0) {
            Entity attacker = minecraft.level.getEntity(packet.targetEntityId());
            if (attacker != null) effect.direction = attacker.position().subtract(effect.position).normalize();
        }
        EFFECTS.add(effect);
        int cap = Math.max(12, ClientVisualConfig.BLADE_COMBAT_VFX_MAX_EFFECTS.get());
        while (EFFECTS.size() > cap) EFFECTS.remove(0);
    }

    @SubscribeEvent
    public static void clientTick(TickEvent.ClientTickEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (event.phase != TickEvent.Phase.END || minecraft.isPaused()) return;
        if (minecraft.level != activeLevel) {
            EFFECTS.clear(); SEEN.clear();
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
                || !ClientVisualConfig.ENABLE_BLADE_COMBAT_VFX.get()) return;
        renderFilm(event);
        if (EFFECTS.isEmpty()) return;
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

    private static void renderFilm(RenderLevelStageEvent event) {
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        var camera = event.getCamera();
        var eye = camera.getPosition();
        var pose = event.getPoseStack();
        var buffers = mc.renderBuffers().bufferSource();
        double distance = ClientVisualConfig.BLADE_COMBAT_VFX_DISTANCE.get();
        float intensity = ClientVisualConfig.BLADE_COMBAT_VFX_INTENSITY.get().floatValue();
        float partial = event.getPartialTick();
        boolean drew = false;
        for (var player : mc.level.players()) {
            if (!player.hasEffect(WaterMirrorEffect.ARMOR.get()) || !player.isAlive() || player.isInvisible()
                    || (player == camera.getEntity() && !camera.isDetached())
                    || eye.distanceToSqr(player.position()) > distance * distance
                    || !event.getFrustum().isVisible(player.getBoundingBox().inflate(.8))) continue;
            var pos = player.getPosition(partial).subtract(eye);
            Effect hit = lastDeflection(player);
            float ripple = hit == null ? 0 : Mth.clamp((hit.age + partial + 1) / hit.duration, 0, 1);
            float angle = hit == null ? 0 : (float) Math.atan2(hit.direction.z, hit.direction.x) - .7F;
            pose.pushPose();
            try {
                pose.translate(pos.x, pos.y, pos.z);
                WaterMirrorVisuals.armor(pose, buffers, player.getBbWidth() * .8F + .22F, player.getBbHeight(),
                        (mc.level.getGameTime() % 4096) + partial,
                        intensity * (.10F + .30F * airRatio(player)), ripple, angle);
                drew = true;
            } finally {
                pose.popPose();
            }
        }
        for (Effect effect : EFFECTS) {
            if (effect.type != KyoukaVfxPacket.AFTERIMAGE_FADE
                    || eye.distanceToSqr(effect.position) > distance * distance) continue;
            float age = effect.age + partial;
            float progress = Mth.clamp(age / effect.duration, 0, 1);
            var pos = effect.position.subtract(eye);
            pose.pushPose();
            try {
                pose.translate(pos.x, pos.y, pos.z);
                pose.mulPose(Axis.YP.rotationDegrees(-Float.intBitsToFloat(effect.seed)));
                pose.scale(1F + progress * .2F, 1F - progress * .43F, 1F + progress * .2F);
                WaterMirrorVisuals.echo(pose, buffers, age, .28F * (1 - progress) * intensity, false);
                drew = true;
            } finally {
                pose.popPose();
            }
        }
        if (drew) buffers.endBatch(WaterMirrorVisuals.FILM);
    }

    @SubscribeEvent
    public static void renderArmorHud(RenderGuiEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (!ClientVisualConfig.ENABLE_BLADE_COMBAT_VFX.get() || mc.options.hideGui
                || !mc.options.getCameraType().isFirstPerson() || mc.level != activeLevel
                || mc.player == null || mc.getCameraEntity() != mc.player) return;
        Effect hit = lastDeflection(mc.player);
        boolean armored = mc.player.hasEffect(WaterMirrorEffect.ARMOR.get());
        if (!armored && hit == null) return;
        float t = hit == null ? 1 : Mth.clamp((hit.age + event.getPartialTick()) / hit.duration, 0, 1);
        float pulse = hit == null ? 0 : .46F * Mth.sin(t * Mth.PI);
        float base = armored ? .055F + .085F * airRatio(mc.player) : 0;
        float intensity = ClientVisualConfig.BLADE_COMBAT_VFX_INTENSITY.get().floatValue();
        var camera = mc.gameRenderer.getMainCamera();
        float right = hit == null ? 0 : (float) -hit.direction.dot(new Vec3(camera.getLeftVector()));
        float up = hit == null ? 0 : (float) hit.direction.dot(new Vec3(camera.getUpVector()));
        var gui = event.getGuiGraphics();
        float w = gui.guiWidth();
        float h = gui.guiHeight();
        float inset = Math.min(w, h) * (.09F + .035F * (1 - t));
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, WaterMirrorVisuals.WATER);
        var buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        Matrix4f matrix = gui.pose().last().pose();
        edge(buffer, matrix, 0, 0, w, 0, 0, inset, intensity * (base + pulse * (.35F + .65F * Math.max(0, up))));
        edge(buffer, matrix, w, 0, w, h, -inset, 0, intensity * (base + pulse * (.35F + .65F * Math.max(0, right))));
        edge(buffer, matrix, w, h, 0, h, 0, -inset, intensity * (base + pulse * (.35F + .65F * Math.max(0, -up))));
        edge(buffer, matrix, 0, h, 0, 0, inset, 0, intensity * (base + pulse * (.35F + .65F * Math.max(0, -right))));
        Tesselator.getInstance().end();
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    private static void edge(BufferBuilder buffer, Matrix4f matrix, float x0, float y0, float x1, float y1,
            float inwardX, float inwardY, float alpha) {
        alpha = Mth.clamp(alpha, 0, 1);
        buffer.vertex(matrix, x0, y0, 0).uv(0, 0).color(.55F, .85F, 1F, alpha).endVertex();
        buffer.vertex(matrix, x1, y1, 0).uv(1, 0).color(.55F, .85F, 1F, alpha).endVertex();
        buffer.vertex(matrix, x1 + inwardX, y1 + inwardY, 0).uv(1, 1).color(.55F, .85F, 1F, 0F).endVertex();
        buffer.vertex(matrix, x0 + inwardX, y0 + inwardY, 0).uv(0, 1).color(.55F, .85F, 1F, 0F).endVertex();
    }

    private static float airRatio(Player player) {
        return Mth.clamp(player.getAirSupply() / (float) Math.max(1, player.getMaxAirSupply()), 0, 1);
    }

    private static Effect lastDeflection(Player player) {
        for (int i = EFFECTS.size() - 1; i >= 0; i--) {
            Effect effect = EFFECTS.get(i);
            if (effect.type == KyoukaVfxPacket.ARMOR_DEFLECT && effect.sourceEntityId == player.getId()) return effect;
        }
        return null;
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
            if (e.type < KyoukaVfxPacket.WATER_GATHER || e.type == KyoukaVfxPacket.AFTERIMAGE_FADE) continue;
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
        Vec3 direction = Vec3.ZERO;
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
