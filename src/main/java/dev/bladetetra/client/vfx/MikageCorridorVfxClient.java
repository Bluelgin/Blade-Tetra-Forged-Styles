package dev.bladetetra.client.vfx;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import dev.bladetetra.BladeTetra;
import dev.bladetetra.challenge.MikageEntity;
import dev.bladetetra.client.MikagePhantomSwordRenderer;
import dev.bladetetra.config.ClientVisualConfig;
import dev.bladetetra.visual.TechniqueVfxData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;
import static dev.bladetetra.client.vfx.render.VfxPrimitives.*;

/** Six persistent cast gates, bounded sword trails and a harmless detached sword after parry. */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID, value = Dist.CLIENT)
public final class MikageCorridorVfxClient {
    private static final List<Scene> SCENES = new ArrayList<>();
    private static ClientLevel world;
    static {
        for (String kind : List.of("gate", "cue", "arrival", "down", "end"))
            TechniqueVfxRegistry.registerDuringSetup(new ResourceLocation(BladeTetra.MOD_ID,
                    "mikage/corridor/" + kind), MikageCorridorVfxClient::accept);
    }
    private static boolean enabled() { return ClientVisualConfig.ENABLE_BLADE_COMBAT_VFX.get()
            && ClientVisualConfig.BLADE_COMBAT_VFX_INTENSITY.get() > 0; }
    private static void accept(TechniqueVfxData d) {
        var mc = Minecraft.getInstance();
        if (mc.level != world) { SCENES.clear(); world = mc.level; }
        if (world == null || !Double.isFinite(d.endX()) || !Double.isFinite(d.endY()) || !Double.isFinite(d.endZ())) return;
        String kind = d.effectId().getPath().substring("mikage/corridor/".length());
        if (kind.equals("arrival")) {
            if (world.getEntity(d.sourceEntityId()) instanceof MikageEntity boss) {
                boss.lerpTo(d.endX(), d.endY(), d.endZ(), d.yaw(), 0, 0, true);
                boss.moveTo(d.endX(), d.endY(), d.endZ(), d.yaw(), 0);
                boss.setYHeadRot(d.yaw()); boss.yHeadRotO = d.yaw();
                boss.setYBodyRot(d.yaw()); boss.yBodyRotO = d.yaw();
            }
            return;
        }
        if (kind.equals("end")) {
            for (Scene s : SCENES) if (sameCast(s, d) && !s.kind.equals("down")) s.life = Math.min(s.life, s.age + 12);
            return;
        }
        if (!enabled()) return;
        SCENES.removeIf(s -> sameCast(s, d) && s.data.targetEntityId() == d.targetEntityId());
        if (SCENES.size() >= 64) SCENES.remove(0);
        SCENES.add(new Scene(d, kind));
    }
    private static boolean sameCast(Scene s, TechniqueVfxData d) {
        return s.data.sourceEntityId() == d.sourceEntityId() && s.data.seed() == d.seed();
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        if (mc.level != world || mc.player == null || !enabled()) { SCENES.clear(); world = mc.level; return; }
        if (!mc.isPaused()) SCENES.removeIf(s -> ++s.age >= s.life);
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || !enabled() || world == null
                || world != Minecraft.getInstance().level || SCENES.isEmpty()) return;
        var poses = event.getPoseStack(); var camera = event.getCamera().getPosition();
        double range = ClientVisualConfig.BLADE_COMBAT_VFX_DISTANCE.get();
        poses.pushPose(); poses.translate(-camera.x, -camera.y, -camera.z);
        RenderSystem.enableBlend(); RenderSystem.disableCull(); RenderSystem.depthMask(false);
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        try {
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            var b = Tesselator.getInstance().getBuilder(); b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            for (Scene s : SCENES) if (camera.distanceToSqr(s.at()) <= range * range)
                draw(b, poses.last().pose(), s, event.getPartialTick(), camera);
            Tesselator.getInstance().end();
            var buffers = Minecraft.getInstance().renderBuffers().bufferSource();
            for (Scene s : SCENES) if (s.kind.equals("down") && camera.distanceToSqr(s.at()) <= range * range) {
                float t = Math.min(1, (s.age + event.getPartialTick()) / 12F);
                if (t >= 1) continue;
                Vec3 at = s.at().lerp(new Vec3(s.data.endX(), s.data.endY(), s.data.endZ()), t);
                poses.pushPose(); poses.translate(at.x, at.y -.16, at.z);
                MikagePhantomSwordRenderer.renderRideSword(poses, buffers, 15728880, s.data.yaw(), 0xFF1838, 1.05F * (1 - t * .35F));
                poses.popPose();
            }
            buffers.endBatch();
        } finally {
            RenderSystem.depthMask(true); RenderSystem.defaultBlendFunc(); RenderSystem.enableCull();
            RenderSystem.disableBlend(); poses.popPose();
        }
    }
    private static void draw(BufferBuilder b, org.joml.Matrix4f m, Scene s, float partial, Vec3 camera) {
        float fade = Math.min(1, (s.life - s.age - partial) / 12F)
                * ClientVisualConfig.BLADE_COMBAT_VFX_INTENSITY.get().floatValue();
        Vec3 at = s.at();
        if (s.kind.equals("down")) {
            float t = Math.max(0, (s.age + partial - 10) / 6F);
            if (t <= 0) return;
            Vec3 end = new Vec3(s.data.endX(), s.data.endY(), s.data.endZ());
            for (int i = 0; i < 12; i++) {
                double a = i * 2.39996;
                Vec3 shard = end.add(Math.cos(a)*t*2, Math.sin(a)*t - t*t, Math.sin(a)*t*2);
                bandFacing(b,m,shard,shard.add(Math.cos(a)*.2,.08,Math.sin(a)*.2),camera,.035,color(1,.65F,.25F,fade));
            }
            return;
        }
        double a = Math.toRadians(s.data.yaw()); Vec3 right = new Vec3(Math.cos(a),0,Math.sin(a));
        int red = color(1,.08F,.22F,fade*.7F), white = color(1,.85F,.7F,fade*.75F);
        for (int sign : new int[]{-1,1}) {
            Vec3 base = at.add(right.scale(sign*1.05));
            bandFacing(b,m,base,base.add(0,2.6,0),camera,.085,red);
        }
        for (double y : new double[]{2.12,2.65}) {
            Vec3 mid = at.add(0,y,0);
            bandFacing(b,m,mid.subtract(right.scale(1.5)),mid.add(right.scale(1.5)),camera,.09,red);
            bandFacing(b,m,mid.subtract(right.scale(1.5)),mid.add(right.scale(1.5)),camera,.022,white);
        }
        float pulse = s.kind.equals("cue") ? .22F + .12F*(float)Math.sin((s.age+partial)*.7) : .055F;
        quad(b,m,at.subtract(right),at.add(right),at.add(right).add(0,2.5,0),at.subtract(right).add(0,2.5,0),color(.45F,.65F,1,fade*pulse));
        if (world.getEntity(s.data.sourceEntityId()) instanceof MikageEntity boss && boss.isRidingPhantomSword()) {
            Vec3 point = boss.getPosition(partial).add(0,-.12,0);
            Vec3 forward = boss.getLookAngle().multiply(1,0,1).normalize();
            bandFacing(b,m,point,point.subtract(forward.scale(2.2)),camera,.035,color(1,.25F,.35F,fade*.35F));
        }
    }
    private static final class Scene {
        final TechniqueVfxData data; final String kind; int age, life;
        Scene(TechniqueVfxData data, String kind) { this.data=data; this.kind=kind; life=data.duration(); }
        Vec3 at() { return new Vec3(data.startX(), data.startY(), data.startZ()); }
    }
    @Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Reload {
        @SubscribeEvent public static void reload(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener((ResourceManagerReloadListener) manager -> SCENES.clear());
        }
    }
    private MikageCorridorVfxClient() {}
}
