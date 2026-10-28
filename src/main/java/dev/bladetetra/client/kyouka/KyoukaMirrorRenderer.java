package dev.bladetetra.client.kyouka;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.bladetetra.client.awakening.FloatingBladeRenderer;
import dev.bladetetra.config.ClientVisualConfig;
import dev.bladetetra.network.KyoukaMirrorPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/** Target-centered world renderer; observers do not need the caster entity in client tracking range. */
@Mod.EventBusSubscriber(modid = "blade_tetra", value = Dist.CLIENT)
public final class KyoukaMirrorRenderer {
    private static final Map<UUID, Visual> SESSIONS = new HashMap<>();
    private static final NativeBladeMotionSampler SAMPLER = new NativeBladeMotionSampler();
    private static ClientLevel world;
    public static void accept(KyoukaMirrorPacket p) {
        var level = Minecraft.getInstance().level;
        if (world != level) { SESSIONS.clear(); world = level; }
        if (level == null || !p.dimension().equals(level.dimension().location())) return;
        if (p.stop()) { SESSIONS.remove(p.session()); return; }
        var previous = SESSIONS.get(p.session());
        if (previous != null && p.action() < previous.packet.action()) return;
        if (previous == null) {
            if (p.appearance().isEmpty() || SESSIONS.size() >= 256) return;
            previous = new Visual(p.appearance().copy()); SESSIONS.put(p.session(), previous);
        }
        previous.packet = p; previous.received = level.getGameTime();
    }
    public static void reload() {
        SAMPLER.reset();
        for (var s : SESSIONS.values()) { s.model = null; s.unsupported = false; }
    }
    @SubscribeEvent public static void unload(LevelEvent.Unload e) {
        if (e.getLevel() == world) { SESSIONS.clear(); world = null; SAMPLER.reset(); }
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        var mc = Minecraft.getInstance(); var level = mc.level;
        if (level == null || world != level) return;
        SESSIONS.values().removeIf(v -> level.getGameTime() - v.received > 240);
        if (!ClientVisualConfig.ENABLE_BLADE_COMBAT_VFX.get()) return;
        Vec3 eye = e.getCamera().getPosition();
        double distance = ClientVisualConfig.BLADE_COMBAT_VFX_DISTANCE.get();
        PoseStack pose = e.getPoseStack(); var buffers = mc.renderBuffers().bufferSource();
        int rendered = 0;
        for (Visual v : SESSIONS.values()) {
            var data = v.packet;
            var entity = level.getEntity(data.targetId());
            if (!(entity instanceof LivingEntity target) || !target.isAlive() || !data.target().equals(target.getUUID())
                    || eye.distanceToSqr(target.position()) > distance*distance
                    || !e.getFrustum().isVisible(target.getBoundingBox().inflate(4))) continue;
            if (v.model == null && !v.unsupported) {
                v.model = FloatingBladeRenderer.prepare(v.appearance); v.unsupported = v.model == null;
            }
            if (v.model == null) continue;
            if (++rendered > Math.max(1, ClientVisualConfig.BLADE_COMBAT_VFX_MAX_EFFECTS.get()/3)) break;
            double clock = level.getGameTime() + e.getPartialTick();
            // Exactly one native sample for this action/frame, shared unchanged by all three blades.
            var motion = data.action() > 0 ? SAMPLER.sample(data.combo(), clock - data.started()) : null;
            Vec3 center = target.getPosition(e.getPartialTick());
            for (int i=0; i<3; i++) {
                double angle = Math.toRadians(data.yaw()) + i * Math.PI * 2 / 3;
                double radius = Math.max(1.2, target.getBbWidth()*.5 + .8);
                pose.pushPose();
                try {
                    pose.translate(center.x-eye.x+Math.cos(angle)*radius,
                            center.y-eye.y+.1+Math.sin(clock*.06)*.06,
                            center.z-eye.z+Math.sin(angle)*radius);
                    pose.mulPose(Axis.YP.rotation((float)(-angle-Math.PI*.5)));
                    if (motion != null) {
                        pose.mulPoseMatrix(motion);
                        pose.last().normal().set(pose.last().pose()).invert().transpose();
                    } else {
                        pose.translate(0, 1.6, 0); pose.mulPose(Axis.ZP.rotationDegrees(-90));
                        pose.scale(.0078125F, .0078125F, .0078125F); pose.translate(-20, 0, 0);
                    }
                    FloatingBladeRenderer.renderMirror(v.model, v.appearance, pose, buffers);
                } finally { pose.popPose(); }
            }
        }
    }
    private static final class Visual {
        final ItemStack appearance;
        KyoukaMirrorPacket packet;
        FloatingBladeRenderer.Prepared model;
        long received; boolean unsupported;
        Visual(ItemStack appearance) { this.appearance = appearance; }
    }
    private KyoukaMirrorRenderer() {}
}
