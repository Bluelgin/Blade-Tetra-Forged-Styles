package dev.bladetetra.client.vfx;

import com.mojang.blaze3d.vertex.BufferBuilder;
import dev.bladetetra.BladeTetra;
import dev.bladetetra.config.ClientVisualConfig;
import dev.bladetetra.visual.DeadThoughtVisualEvents;
import dev.bladetetra.visual.DeadThoughtVisualMath;
import dev.bladetetra.visual.TechniqueVfxData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
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

/** Client-local scene records, not world entities. Owns lifetime/deduplication, not combat. */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID, value = Dist.CLIENT)
public final class DeadThoughtVfxClient {
    private static final Map<Key, Scene> SCENES = new LinkedHashMap<>();
    private static final Map<Key, Collapse> COLLAPSES = new LinkedHashMap<>();
    private static final Map<Integer, Scar> SCARS = new LinkedHashMap<>();
    private static final MultiBufferSource.BufferSource BUFFERS =
            MultiBufferSource.immediate(new BufferBuilder(256 * 1024));
    private static ClientLevel activeLevel;

    private static boolean enabled() {
        return ClientVisualConfig.ENABLE_BLADE_COMBAT_VFX.get()
                && ClientVisualConfig.BLADE_COMBAT_VFX_INTENSITY.get() > 0;
    }

    static void accept(TechniqueVfxData data) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != activeLevel) { clear(); activeLevel = mc.level; }
        if (mc.level == null || mc.player == null || !enabled()) return;
        Vec3 anchor = new Vec3(data.endX(), data.endY(), data.endZ());
        if (!finite(anchor) || !Float.isFinite(data.yaw()) || !Float.isFinite(data.intensity())) return;
        double distance = ClientVisualConfig.BLADE_COMBAT_VFX_DISTANCE.get();
        if (mc.player.distanceToSqr(anchor) > distance * distance) return;
        Key key = new Key(data.sourceEntityId(), data.seed());
        ResourceLocation stage = data.effectId();

        if (stage.equals(DeadThoughtVisualEvents.END)) {
            SCENES.remove(key);
            return;
        }
        if (stage.equals(DeadThoughtVisualEvents.SOUL_COLLAPSE)) {
            trim(COLLAPSES, DeadThoughtVisualMath.MAX_COLLAPSES);
            COLLAPSES.put(key, new Collapse(data, mc.level));
            if (data.targetEntityId() >= 0) SCARS.remove(data.targetEntityId());
            return;
        }
        if (stage.equals(DeadThoughtVisualEvents.START)) {
            if (SCENES.containsKey(key)) return;
            SCENES.keySet().removeIf(k -> k.source == key.source);
            trim(SCENES, DeadThoughtVisualMath.MAX_SCENES);
            SCENES.put(key, new Scene(data, mc.level));
            return;
        }
        if (stage.equals(DeadThoughtVisualEvents.EROSION)
                || stage.equals(DeadThoughtVisualEvents.STATE)
                || stage.equals(DeadThoughtVisualEvents.SOUL_BROKEN)) {
            Entity target = mc.level.getEntity(data.targetEntityId());
            if (!(target instanceof LivingEntity living) || !living.isAlive()) return;
            boolean broken = stage.equals(DeadThoughtVisualEvents.SOUL_BROKEN);
            int severity = broken ? 3 : DeadThoughtVisualMath.severity(data.intensity());
            Scar old = SCARS.get(target.getId());
            if (old == null || !old.target.equals(target.getUUID())) {
                trim(SCARS, DeadThoughtVisualMath.MAX_SCARS);
                old = new Scar(target, severity);
                SCARS.put(target.getId(), old);
            }
            old.severity = Math.max(old.severity, severity);
            old.broken |= broken;
            old.age = stage.equals(DeadThoughtVisualEvents.STATE) || broken ? 60 : 0;
            Scene scene = SCENES.get(key);
            if (scene != null) {
                if (scene.targetId < 0) scene.bindTarget(target);
                if (scene.targetId == target.getId()) scene.eroded = true;
            }
            return;
        }
        Scene scene = SCENES.get(key);
        if (scene == null) return; // Stale stages must not recreate an expired scene.
        if (stage.equals(DeadThoughtVisualEvents.LEFT) && scene.left < 0) scene.left = scene.age;
        if (stage.equals(DeadThoughtVisualEvents.RIGHT) && scene.right < 0) scene.right = scene.age;
        if (stage.equals(DeadThoughtVisualEvents.FINAL) && scene.finisher < 0)
            scene.finisher = Math.min(scene.age, 20);
    }

    private static <K, V> void trim(Map<K, V> map, int limit) {
        while (map.size() >= limit) map.remove(map.keySet().iterator().next());
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != activeLevel || mc.player == null || !enabled()) {
            clear(); activeLevel = mc.level; return;
        }
        if (mc.isPaused()) return;
        double range = ClientVisualConfig.BLADE_COMBAT_VFX_DISTANCE.get();
        SCENES.values().removeIf(s -> {
            s.age++;
            if (s.age >= DeadThoughtVisualMath.LIFETIME || !s.update(mc.level)) return true;
            return mc.player.distanceToSqr(s.anchor) > range * range;
        });
        COLLAPSES.values().removeIf(c -> {
            c.age++;
            return c.age >= DeadThoughtVisualMath.COLLAPSE_LIFETIME
                    || mc.player.distanceToSqr(c.anchor) > range * range;
        });
        SCARS.entrySet().removeIf(entry -> {
            Scar scar = entry.getValue(); scar.age++;
            Entity e = mc.level.getEntity(entry.getKey());
            return e == null || !e.isAlive() || !scar.target.equals(e.getUUID())
                    || e.position().distanceToSqr(scar.lastPosition) > 16 * 16
                    || mc.player.distanceToSqr(e) > range * range
                    || (!scar.broken && scar.severity < 3 && scar.age > 45)
                    || !scar.follow(e);
        });
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || !enabled()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.level != activeLevel) return;
        int quality = ClientVisualConfig.BLADE_COMBAT_VFX_QUALITY.get();
        Vec3 camera = event.getCamera().getPosition();
        var poses = event.getPoseStack();
        poses.pushPose();
        poses.translate(-camera.x, -camera.y, -camera.z);
        try {
            for (Scene scene : SCENES.values())
                DeadThoughtSceneRenderer.draw(poses, BUFFERS, scene, event.getPartialTick(), quality);
            for (Collapse collapse : COLLAPSES.values())
                DeadThoughtSceneRenderer.drawCollapse(poses, BUFFERS, collapse,
                        event.getPartialTick(), quality);
            for (var entry : SCARS.entrySet()) {
                Entity target = mc.level.getEntity(entry.getKey());
                if (target instanceof LivingEntity living && living.isAlive())
                    DeadThoughtScarRenderer.draw(poses, BUFFERS, living, entry.getValue(),
                            event.getPartialTick(), quality, camera);
            }
        } finally {
            BUFFERS.endBatch();
            poses.popPose();
        }
    }

    static float atmosphere(float partial) {
        Minecraft mc = Minecraft.getInstance();
        if (!enabled() || mc.player == null || mc.level != activeLevel) return 0;
        float strength = 0;
        for (Scene scene : SCENES.values()) {
            if (scene.sourceId != mc.player.getId()) continue;
            float t = scene.age + partial;
            strength = Math.max(strength, Math.min(DeadThoughtVisualMath.ramp(t, 0, 2),
                    1 - DeadThoughtVisualMath.ramp(t, 8, 6)));
        }
        for (Collapse collapse : COLLAPSES.values()) {
            if (collapse.sourceId != mc.player.getId()) continue;
            float t = collapse.age + partial;
            strength = Math.max(strength, .65F * (1 - DeadThoughtVisualMath.ramp(t, 7, 8)));
        }
        return strength;
    }

    public static void clear() { SCENES.clear(); COLLAPSES.clear(); SCARS.clear(); }
    private static boolean finite(Vec3 v) {
        return Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z);
    }

    private record Key(int source, int serial) {}

    static final class Scene {
        final int sourceId;
        final float yaw;
        UUID source;
        int targetId;
        UUID target;
        Vec3 anchor, previous;
        float width = .6F, height = 1.8F;
        int age, left = -1, right = -1, finisher = -1;
        boolean eroded;
        Scene(TechniqueVfxData data, ClientLevel level) {
            sourceId = data.sourceEntityId(); targetId = data.targetEntityId(); yaw = data.yaw();
            anchor = previous = new Vec3(data.endX(), data.endY(), data.endZ());
            Entity owner = level.getEntity(sourceId);
            if (owner != null) source = owner.getUUID();
            Entity e = level.getEntity(targetId);
            if (e != null) bindTarget(e);
        }
        void bindTarget(Entity e) {
            targetId = e.getId(); target = e.getUUID();
            anchor = previous = e.position(); width = e.getBbWidth(); height = e.getBbHeight();
        }
        boolean update(ClientLevel level) {
            Entity owner = level.getEntity(sourceId);
            if (owner == null) return age <= 5 && source == null;
            if (!owner.isAlive() || (source != null && !source.equals(owner.getUUID()))) return false;
            source = owner.getUUID();
            previous = anchor;
            if (targetId < 0) return true;
            Entity e = level.getEntity(targetId);
            if (e == null) return age <= 5 && target == null;
            if (!e.isAlive() || (target != null && !target.equals(e.getUUID()))
                    || e.position().distanceToSqr(anchor) > 16 * 16) return false;
            target = e.getUUID(); anchor = e.position(); width = e.getBbWidth(); height = e.getBbHeight();
            return true;
        }
    }

    /** Snapshot-only collapse scene. It never follows or queries the target after creation. */
    static final class Collapse {
        final int sourceId;
        final Vec3 anchor;
        final float yaw;
        final float scale;
        final float width;
        final float height;
        int age;

        Collapse(TechniqueVfxData data, ClientLevel level) {
            sourceId = data.sourceEntityId();
            yaw = data.yaw();
            anchor = new Vec3(data.endX(), data.endY(), data.endZ());
            Entity entity = level.getEntity(data.targetEntityId());
            if (entity instanceof LivingEntity living) {
                width = Math.max(.3F, living.getBbWidth());
                height = Math.max(.65F, living.getBbHeight());
                scale = DeadThoughtVisualMath.scale(width, height);
            } else {
                scale = Math.max(.8F, Math.min(2.2F, data.intensity()));
                width = Math.max(.6F, scale * .7F);
                height = Math.max(1.8F, scale * 2.0F);
            }
        }
    }

    static final class Scar {
        final UUID target;
        Vec3 lastPosition;
        int severity, age;
        boolean broken;
        Scar(Entity entity, int severity) {
            target = entity.getUUID(); lastPosition = entity.position(); this.severity = severity;
        }
        boolean follow(Entity entity) { lastPosition = entity.position(); return true; }
    }

    @Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        @SubscribeEvent
        public static void setup(FMLClientSetupEvent event) {
            event.enqueueWork(() -> {
                for (ResourceLocation id : DeadThoughtVisualEvents.ALL)
                    TechniqueVfxRegistry.registerDuringSetup(id, DeadThoughtVfxClient::accept);
            });
        }
        @SubscribeEvent
        public static void reload(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener((ResourceManagerReloadListener) manager -> {
                clear(); DeadThoughtAtmosphere.reload();
            });
        }
    }
}
