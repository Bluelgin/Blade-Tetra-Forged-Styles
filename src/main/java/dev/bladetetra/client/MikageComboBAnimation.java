package dev.bladetetra.client;

import com.google.gson.*;
import dev.bladetetra.BladeTetra;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;
import java.util.*;

/** Native B VMD rotation samples retargeted to the shared arm rig; see bundled animation MIT. */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class MikageComboBAnimation {
    private static final ResourceLocation FILE = new ResourceLocation(BladeTetra.MOD_ID, "animations/mikage_combo_b.json");
    private static final Map<Integer, Map<String, NavigableMap<Float, Vector3f>>> CLIPS = new HashMap<>();
    private static boolean loaded;
    @SubscribeEvent public static void reload(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) manager -> { loaded = false; CLIPS.clear(); });
    }
    public static Map<String, Vector3f> sample(int stage, float seconds) {
        if (!loaded) load();
        Map<String, Vector3f> result = new HashMap<>();
        CLIPS.getOrDefault(stage, Map.of()).forEach((name, track) -> {
            var left = track.floorEntry(seconds); if (left == null) left = track.firstEntry();
            var right = track.ceilingEntry(seconds); if (right == null) right = track.lastEntry();
            float span = right.getKey() - left.getKey();
            float t = span <= 0 ? 0 : Math.max(0, Math.min(1, (seconds - left.getKey()) / span));
            result.put(name, new Vector3f(left.getValue()).lerp(right.getValue(), t));
        });
        return result;
    }
    private static void load() {
        loaded = true;
        try (var reader = Minecraft.getInstance().getResourceManager().getResource(FILE).orElseThrow().openAsReader()) {
            var clips = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("animations");
            for (int stage = 1; stage <= 7; stage++) {
                Map<String, NavigableMap<Float, Vector3f>> bones = new HashMap<>();
                for (var bone : clips.getAsJsonObject("combo_b" + stage).getAsJsonObject("bones").entrySet()) {
                    NavigableMap<Float, Vector3f> track = new TreeMap<>();
                    for (var sample : bone.getValue().getAsJsonObject().getAsJsonObject("rotation").entrySet()) {
                        var v = sample.getValue().getAsJsonArray();
                        track.put(Float.parseFloat(sample.getKey()), new Vector3f(v.get(0).getAsFloat(), v.get(1).getAsFloat(), v.get(2).getAsFloat()));
                    }
                    if (!track.isEmpty()) bones.put(bone.getKey(), track);
                }
                CLIPS.put(stage, bones);
            }
        } catch (Exception ex) {
            CLIPS.clear(); org.slf4j.LoggerFactory.getLogger(MikageComboBAnimation.class).warn("Could not load native Combo B poses", ex);
        }
    }
    private MikageComboBAnimation() {}
}
