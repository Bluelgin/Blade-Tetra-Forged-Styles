package dev.bladetetra.client;

import dev.bladetetra.BladeTetra;
import dev.bladetetra.forging.LegacyImprintKind;
import dev.bladetetra.forging.LegacyImprintProfileResolver;
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
    private static final Map<LegacyImprintKind, LegacyImprintProfileResolver.Resolution> CACHE =
            new LinkedHashMap<>(64, .75F, true) {
                @Override
                protected boolean removeEldestEntry(
                        Map.Entry<LegacyImprintKind, LegacyImprintProfileResolver.Resolution> eldest) {
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

    public static LegacyImprintProfileResolver.Resolution resolve(LegacyImprintKind kind) {
        // A physical client and its integrated server share one JVM, so the side-neutral
        // resolver function is visible to both threads. Geometry is a client presentation
        // concern: never touch Minecraft resources or SlashBlade's client model manager
        // from an integrated-server thread. Dedicated servers never install this resolver.
        Minecraft minecraft = Minecraft.getInstance();
        if (!minecraft.isSameThread()) {
            return new LegacyImprintProfileResolver.Resolution(
                    kind.defaultProfile(), true, "non-client thread metadata");
        }

        // Keep the integrated-server fast path outside this lock. A large client model may
        // take noticeable time to inspect, but it must never stall the logical server while
        // waiting for the presentation cache.
        synchronized (CACHE) {
            LegacyImprintProfileResolver.Resolution cached = CACHE.get(kind);
            if (cached != null) return cached;
            LegacyImprintProfileResolver.Resolution result = analyze(kind);
            CACHE.put(kind, result);
            return result;
        }
    }

    private static LegacyImprintProfileResolver.Resolution analyze(LegacyImprintKind kind) {
        var source = LegacyRenderSources.resolve(kind);
        if (source.mesh() == null) {
            return new LegacyImprintProfileResolver.Resolution(kind.defaultProfile(), false, source.reason());
        }
        var statuses = java.util.Arrays.stream(LegacySourceMesh.Component.values())
                .map(source::support).toList();
        boolean usable = statuses.contains(LegacyRenderSources.Support.FULL)
                || statuses.contains(LegacyRenderSources.Support.PARTIAL);
        return new LegacyImprintProfileResolver.Resolution(source.mesh().profile, usable,
                "blade=" + statuses.get(0) + ", hilt=" + statuses.get(1) + ", saya=" + statuses.get(2));
    }

    public static void clear() {
        synchronized (CACHE) {
            CACHE.clear();
            LegacyRenderSources.clear();
            NamedBladeRenderPlan.clear();
        }
    }

    private LegacyClientModelAnalysis() {}
}
