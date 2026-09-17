package dev.bladetetra.client.vfx;

import com.mojang.blaze3d.vertex.BufferBuilder;
import dev.bladetetra.BladeTetra;
import dev.bladetetra.config.ClientVisualConfig;
import dev.bladetetra.visual.TechniqueVfxData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Bounded client-only effects, keyed by Ritual visual id rather than a shared global ability state. */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID, value = Dist.CLIENT)
public final class MikageDivineVfxEvents {
    private static final Map<Key, Scene> SCENES = new LinkedHashMap<>();
    private static final MultiBufferSource.BufferSource BUFFERS = MultiBufferSource.immediate(new BufferBuilder(128 * 1024));
    private static ClientLevel activeLevel;
    private static int serial;
    private record Key(int ritual, String kind, int target, int serial) {}

    private static boolean enabled() {
        return ClientVisualConfig.ENABLE_BLADE_COMBAT_VFX.get()
                && ClientVisualConfig.BLADE_COMBAT_VFX_INTENSITY.get() > 0;
    }
    private static void accept(TechniqueVfxData data) {
        var mc = Minecraft.getInstance();
        if (mc.level != activeLevel) { clear(); activeLevel = mc.level; }
        if (mc.level == null || mc.player == null) return;
        String kind = data.effectId().getPath().substring("divine/".length());
        // Cleanup must work even if a user disabled VFX or moved beyond the draw distance.
        if (kind.equals("end")) { SCENES.keySet().removeIf(k -> k.ritual == data.seed()); return; }
        if (kind.equals("array_end") || kind.equals("wall_end")) {
            String ended = kind.substring(0, kind.indexOf('_'));
            SCENES.keySet().removeIf(k -> k.ritual == data.seed() && k.kind.equals(ended));
        }
        if (!enabled()) return;
        Vec3 at = new Vec3(data.endX(), data.endY(), data.endZ());
        if (!Double.isFinite(at.x) || !Double.isFinite(at.y) || !Double.isFinite(at.z)) return;
        double range = ClientVisualConfig.BLADE_COMBAT_VFX_DISTANCE.get();
        if (mc.player.distanceToSqr(at) > range * range) return;
        boolean transientEffect = kind.equals("dash") || kind.equals("anchor_hit") || kind.endsWith("_end");
        Key key = new Key(data.seed(), kind, data.targetEntityId(), transientEffect ? ++serial : 0);
        if (!SCENES.containsKey(key) && SCENES.size() >= 64) SCENES.remove(SCENES.keySet().iterator().next());
        SCENES.put(key, new Scene(kind, data, mc.level));
    }

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        if (mc.level != activeLevel || mc.player == null || !enabled()) { clear(); activeLevel = mc.level; return; }
        if (mc.isPaused()) return;
        double range = ClientVisualConfig.BLADE_COMBAT_VFX_DISTANCE.get();
        SCENES.values().removeIf(s -> ++s.age >= s.duration || !s.update(mc.level)
                || mc.player.distanceToSqr(s.at) > range * range);
    }

    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || !enabled()
                || Minecraft.getInstance().level != activeLevel || SCENES.isEmpty()) return;
        var poses = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();
        poses.pushPose(); poses.translate(-camera.x, -camera.y, -camera.z);
        try {
            for (Scene scene : SCENES.values()) MikageDivineVfxRenderer.draw(poses, BUFFERS, scene,
                    event.getPartialTick(), ClientVisualConfig.BLADE_COMBAT_VFX_QUALITY.get(), camera);
        } finally { BUFFERS.endBatch(); poses.popPose(); }
    }
    static void clear() { SCENES.clear(); }

    static final class Scene {
        final String kind;
        final int duration, targetId;
        final boolean follows;
        int age;
        UUID target;
        Vec3 at, previous;
        float height = 1.8F;
        Scene(String kind, TechniqueVfxData data, ClientLevel level) {
            this.kind = kind; duration = Math.max(1, Math.min(240, data.duration()));
            at = previous = new Vec3(data.endX(), data.endY(), data.endZ()); targetId = data.targetEntityId();
            follows = kind.equals("guard") || kind.equals("mark") || kind.equals("binding");
            if (follows) bind(level.getEntity(targetId));
        }
        private void bind(Entity entity) { if (entity != null) { target = entity.getUUID(); height = entity.getBbHeight(); } }
        boolean update(ClientLevel level) {
            previous = at;
            if (!follows) return true; // Hazard markers stay where the server snapshotted them.
            Entity entity = level.getEntity(targetId);
            if (target == null) { bind(entity); if (target == null) return age < 5; }
            if (entity == null || !entity.isAlive() || !entity.getUUID().equals(target)
                    || entity.position().distanceToSqr(at) > 24 * 24) return false;
            at = entity.position(); return true;
        }
    }

    @Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
            event.enqueueWork(() -> {
                for (String kind : new String[]{"array", "wall", "mark", "guard", "binding", "entrance",
                        "dash", "hazard", "anchor_hit", "array_end", "wall_end", "end"})
                    TechniqueVfxRegistry.registerDuringSetup(new ResourceLocation(BladeTetra.MOD_ID, "divine/" + kind), MikageDivineVfxEvents::accept);
            });
        }
        @SubscribeEvent public static void reload(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener((ResourceManagerReloadListener) manager -> clear());
        }
    }
    private MikageDivineVfxEvents() {}
}
