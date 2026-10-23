package dev.bladetetra.client;

import com.google.gson.*;
import org.joml.Vector3f;
import java.io.Reader;
import java.util.*;

/** Neutral pose data: Bedrock rotation constants and sampled tracks share one interpolation path. */
final class MikageComboBTracks {
    private final Map<Integer, Map<String, NavigableMap<Float, Vector3f>>> clips = new HashMap<>();
    static MikageComboBTracks read(Reader reader) {
        var result = new MikageComboBTracks();
        var animations = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("animations");
        for (int stage = 1; stage <= 7; stage++) {
            Map<String, NavigableMap<Float, Vector3f>> bones = new HashMap<>();
            for (var bone : animations.getAsJsonObject("combo_b" + stage).getAsJsonObject("bones").entrySet()) {
                NavigableMap<Float, Vector3f> track = new TreeMap<>();
                var rotation = bone.getValue().getAsJsonObject().get("rotation");
                if (rotation.isJsonArray()) track.put(0F, vector(rotation.getAsJsonArray()));
                else for (var sample : rotation.getAsJsonObject().entrySet()) {
                    float time = Float.parseFloat(sample.getKey());
                    if (!Float.isFinite(time) || time < 0 || time > 5) throw new IllegalArgumentException("Pose time");
                    track.put(time, vector(sample.getValue().getAsJsonArray()));
                }
                if (!track.isEmpty()) bones.put(bone.getKey(), track);
            }
            if (!bones.containsKey("RightArm") || !bones.containsKey("RightForeArm"))
                throw new IllegalArgumentException("Missing native sword arm");
            result.clips.put(stage, bones);
        }
        return result;
    }
    Map<String, Vector3f> sample(int stage, float seconds) {
        Map<String, Vector3f> result = new HashMap<>();
        clips.getOrDefault(stage, Map.of()).forEach((name, track) -> {
            var left = track.floorEntry(seconds); if (left == null) left = track.firstEntry();
            var right = track.ceilingEntry(seconds); if (right == null) right = track.lastEntry();
            float span = right.getKey() - left.getKey();
            float t = span <= 0 ? 0 : Math.max(0, Math.min(1, (seconds - left.getKey()) / span));
            result.put(name, new Vector3f(left.getValue()).lerp(right.getValue(), t));
        });
        return result;
    }
    private static Vector3f vector(JsonArray a) {
        if (a.size() != 3) throw new IllegalArgumentException("Pose vector");
        var v = new Vector3f(a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat());
        if (!Float.isFinite(v.x) || !Float.isFinite(v.y) || !Float.isFinite(v.z)) throw new IllegalArgumentException("Pose rotation");
        return v;
    }
}
