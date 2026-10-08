package dev.bladetetra.client.vfx;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.bladetetra.BladeTetra;
import dev.bladetetra.config.ClientVisualConfig;
import dev.bladetetra.visual.TechniqueVfxData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/** Resource-id routed, bounded transient gates. Hidden/cue phases deliberately have no visual marker. */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID, value = Dist.CLIENT)
public final class MikageThousandGatesVfxClient {
    private static final List<Scene> SCENES = new ArrayList<>();
    private static ClientLevel activeLevel;
    static {
        for (String kind : List.of("enter", "exit", "break", "slash", "end"))
            TechniqueVfxRegistry.registerDuringSetup(new ResourceLocation(BladeTetra.MOD_ID,
                    "mikage/gates/" + kind), MikageThousandGatesVfxClient::accept);
    }
    private static boolean enabled() {
        return ClientVisualConfig.ENABLE_BLADE_COMBAT_VFX.get()
                && ClientVisualConfig.BLADE_COMBAT_VFX_INTENSITY.get() > 0;
    }
    private static void accept(TechniqueVfxData data) {
        var mc = Minecraft.getInstance();
        if (mc.level != activeLevel) { SCENES.clear(); activeLevel = mc.level; }
        String kind = data.effectId().getPath().substring("mikage/gates/".length());
        if (kind.equals("end")) {
            SCENES.removeIf(s -> s.data.sourceEntityId() == data.sourceEntityId() && s.data.seed() == data.seed());
            return;
        }
        if (!enabled() || mc.level == null || mc.player == null
                || !Double.isFinite(data.endX()) || !Double.isFinite(data.endY()) || !Double.isFinite(data.endZ())) return;
        SCENES.removeIf(s -> s.data.sourceEntityId() == data.sourceEntityId() && s.data.seed() == data.seed()
                && (s.kind.equals(kind) || isPortal(s.kind) && isPortal(kind)));
        if (SCENES.size() >= 32) SCENES.remove(0);
        SCENES.add(new Scene(data, kind));
    }
    private static boolean isPortal(String kind) { return kind.equals("enter") || kind.equals("exit"); }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        if (mc.level != activeLevel || mc.player == null || !enabled()) {
            SCENES.clear(); activeLevel = mc.level; return;
        }
        if (!mc.isPaused()) SCENES.removeIf(s -> ++s.age >= s.data.duration());
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || !enabled()
                || Minecraft.getInstance().level != activeLevel || SCENES.isEmpty()) return;
        var poses = event.getPoseStack();
        var camera = event.getCamera().getPosition();
        poses.pushPose(); poses.translate(-camera.x, -camera.y, -camera.z);
        RenderSystem.enableBlend(); RenderSystem.disableCull(); RenderSystem.depthMask(false);
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        try {
            var buffer = Tesselator.getInstance().getBuilder();
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            for (Scene scene : SCENES) if (inRange(scene, camera))
                MikageThousandGatesVfxRenderer.gate(buffer, poses.last().pose(), scene,
                        event.getPartialTick(), camera);
            Tesselator.getInstance().end();
            RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
            for (Scene scene : SCENES) if (inRange(scene, camera)) {
                if (!scene.kind.equals("break") && !scene.kind.equals("slash")) continue;
                RenderSystem.setShaderTexture(0, scene.kind.equals("break")
                        ? MikageThousandGatesVfxRenderer.SHARDS : MikageThousandGatesVfxRenderer.SLASH);
                buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
                MikageThousandGatesVfxRenderer.texture(buffer, poses.last().pose(), scene,
                        event.getPartialTick(), camera);
                Tesselator.getInstance().end();
            }
        } finally {
            RenderSystem.depthMask(true); RenderSystem.defaultBlendFunc();
            RenderSystem.enableCull(); RenderSystem.disableBlend(); poses.popPose();
        }
    }
    private static boolean inRange(Scene scene, net.minecraft.world.phys.Vec3 camera) {
        double range = ClientVisualConfig.BLADE_COMBAT_VFX_DISTANCE.get();
        var d = scene.data;
        return camera.distanceToSqr(d.endX(), d.endY(), d.endZ()) <= range * range;
    }
    static final class Scene {
        final TechniqueVfxData data;
        final String kind;
        int age;
        Scene(TechniqueVfxData data, String kind) { this.data = data; this.kind = kind; }
    }
    @Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Reload {
        @SubscribeEvent public static void reload(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener((ResourceManagerReloadListener) manager -> SCENES.clear());
        }
    }
    private MikageThousandGatesVfxClient() {}
}
