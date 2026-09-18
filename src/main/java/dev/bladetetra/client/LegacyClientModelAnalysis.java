package dev.bladetetra.client;

import dev.bladetetra.BladeTetra;
import dev.bladetetra.forging.LegacyCalibrationProfile;
import dev.bladetetra.forging.LegacyImprintKind;
import dev.bladetetra.forging.LegacyImprintProfileResolver;
import dev.bladetetra.forging.LegacyModelAdapter;
import mods.flammpfeil.slashblade.client.renderer.model.BladeModelManager;
import mods.flammpfeil.slashblade.client.renderer.model.obj.Face;
import mods.flammpfeil.slashblade.client.renderer.model.obj.GroupObject;
import mods.flammpfeil.slashblade.client.renderer.model.obj.Vertex;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Client-only lazy geometry validation over SlashBlade's already parsed model.
 * The server never opens provider OBJ files for automatic discovery.
 */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.MOD)
public final class LegacyClientModelAnalysis {
    private static final int MAX_CACHE = 256;
    private static final int MAX_GROUPS = 4_096;
    private static final int MAX_RELEVANT_FACES = 200_000;
    private static final int MAX_VERTEX_REFERENCES = 600_000;

    private static final Map<String, LegacyImprintProfileResolver.Resolution> CACHE =
            new LinkedHashMap<>(64, .75F, true) {
                @Override
                protected boolean removeEldestEntry(
                        Map.Entry<String, LegacyImprintProfileResolver.Resolution> eldest) {
                    return size() > MAX_CACHE;
                }
            };

    @SubscribeEvent
    public static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> LegacyImprintProfileResolver.installClientResolver(
                LegacyClientModelAnalysis::resolve));
    }

    @SubscribeEvent
    public static void registerReloadListener(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) resourceManager -> clear());
    }

    public static synchronized LegacyImprintProfileResolver.Resolution resolve(
            LegacyImprintKind kind) {
        // A physical client and its integrated server share one JVM, so the side-neutral
        // resolver function is visible to both threads. Geometry is a client presentation
        // concern: never touch Minecraft resources or SlashBlade's client model manager
        // from an integrated-server thread. Dedicated servers never install this resolver.
        Minecraft minecraft = Minecraft.getInstance();
        if (!minecraft.isSameThread()) {
            return new LegacyImprintProfileResolver.Resolution(
                    kind.rawDefaultProfile(), true, "non-client thread metadata");
        }

        String key = kind.id() + "|" + kind.model() + "|" + kind.texture();
        LegacyImprintProfileResolver.Resolution cached = CACHE.get(key);
        if (cached != null) return cached;
        LegacyImprintProfileResolver.Resolution result = analyze(kind);
        CACHE.put(key, result);
        return result;
    }

    private static LegacyImprintProfileResolver.Resolution analyze(LegacyImprintKind kind) {
        LegacyCalibrationProfile fallback = kind.rawDefaultProfile();
        try {
            // BladeModelManager deliberately has a fallback model. Verify the provider's
            // resources first so a missing addon asset cannot be mistaken for a valid
            // imprint merely because SlashBlade returned its default model.
            var resources = Minecraft.getInstance().getResourceManager();
            if (resources.getResource(kind.model()).isEmpty()) {
                return fail(fallback, "missing model resource");
            }
            if (resources.getResource(kind.texture()).isEmpty()) {
                return fail(fallback, "missing texture resource");
            }

            var model = BladeModelManager.getInstance().getModel(kind.model());
            if (model == null || model.groupObjects == null
                    || model.groupObjects.size() > MAX_GROUPS) {
                return fail(fallback, "model/group budget");
            }
            LegacyModelAdapter adapter = LegacyModelAdapter.resolve(kind.model());
            Range blade = new Range();
            Range hilt = new Range();
            boolean saya = false;
            int relevantFaces = 0;
            int vertexReferences = 0;

            for (GroupObject group : model.groupObjects) {
                if (group == null || group.faces == null) continue;
                boolean isBlade = adapter.bladeGroups().contains(group.name);
                boolean isHilt = adapter.hiltGroups().contains(group.name);
                boolean isSaya = adapter.sayaGroups().contains(group.name);
                if (!isBlade && !isHilt && !isSaya) continue;
                relevantFaces += group.faces.size();
                if (relevantFaces > MAX_RELEVANT_FACES) {
                    return fail(fallback, "face budget");
                }
                for (Face face : group.faces) {
                    if (face == null || face.vertices == null) continue;
                    vertexReferences += face.vertices.length;
                    if (vertexReferences > MAX_VERTEX_REFERENCES) {
                        return fail(fallback, "vertex-reference budget");
                    }
                    if (isSaya && face.vertices.length >= 3) saya = true;
                    for (Vertex vertex : face.vertices) {
                        if (!finite(vertex)) return fail(fallback, "non-finite vertex");
                        if (isBlade) blade.include(vertex, adapter);
                        if (isHilt) hilt.include(vertex, adapter);
                    }
                }
            }

            if (!blade.valid() || !saya) {
                return fail(fallback, !saya ? "no independent saya group" : "no blade group");
            }
            float best = -1.0F;
            float center = .772F;
            for (GroupObject group : model.groupObjects) {
                if (group == null || group.faces == null) continue;
                boolean candidateGroup = hilt.valid()
                        ? adapter.hiltGroups().contains(group.name)
                        : adapter.bladeGroups().contains(group.name);
                if (!candidateGroup) continue;
                for (Face face : group.faces) {
                    if (face == null || face.vertices == null) continue;
                    for (Vertex vertex : face.vertices) {
                        float coordinate = adapter.coordinate(vertex.x, vertex.y, vertex.z);
                        float t = (coordinate - blade.min) / (blade.max - blade.min);
                        if (!Float.isFinite(t) || t < .55F || t > .94F) continue;
                        float radius = radial(vertex, adapter.axis());
                        if (Float.isFinite(radius) && radius > best) {
                            best = radius;
                            center = t;
                        }
                    }
                }
            }
            if (best < 0.0F) return fail(fallback, "no safe complete-hilt boundary");

            float half = .018F;
            float gripStart = Math.min(.96F, center + half);
            LegacyCalibrationProfile inferred = new LegacyCalibrationProfile(
                    center, half, (gripStart + 1F) * .5F, (1F - gripStart) * .5F,
                    LegacyCalibrationProfile.IDENTITY, LegacyCalibrationProfile.IDENTITY,
                    LegacyCalibrationProfile.IDENTITY, false, 0L).normalized();
            LegacyCalibrationProfile profile = kind.id().startsWith("fox_")
                    ? fallback : adapter.apply(inferred);
            return new LegacyImprintProfileResolver.Resolution(profile, true, "client model");
        } catch (RuntimeException exception) {
            return fail(fallback, exception.getClass().getSimpleName());
        }
    }

    private static boolean finite(Vertex vertex) {
        return vertex != null && Float.isFinite(vertex.x)
                && Float.isFinite(vertex.y) && Float.isFinite(vertex.z);
    }

    private static float radial(Vertex vertex, LegacyModelAdapter.Axis axis) {
        return switch (axis) {
            case X -> Math.abs(vertex.y) + Math.abs(vertex.z);
            case Y -> Math.abs(vertex.x) + Math.abs(vertex.z);
            case Z -> Math.abs(vertex.x) + Math.abs(vertex.y);
        };
    }

    private static LegacyImprintProfileResolver.Resolution fail(
            LegacyCalibrationProfile fallback, String reason) {
        return new LegacyImprintProfileResolver.Resolution(fallback, false, reason);
    }

    public static synchronized void clear() {
        CACHE.clear();
    }

    private static final class Range {
        private float min = Float.POSITIVE_INFINITY;
        private float max = Float.NEGATIVE_INFINITY;

        void include(Vertex vertex, LegacyModelAdapter adapter) {
            float value = adapter.coordinate(vertex.x, vertex.y, vertex.z);
            if (!Float.isFinite(value)) return;
            min = Math.min(min, value);
            max = Math.max(max, value);
        }

        boolean valid() {
            return Float.isFinite(min) && Float.isFinite(max) && max - min >= .01F;
        }
    }

    private LegacyClientModelAnalysis() {}
}
