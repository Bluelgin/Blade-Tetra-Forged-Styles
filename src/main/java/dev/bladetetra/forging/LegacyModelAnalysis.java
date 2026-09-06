package dev.bladetetra.forging;

import java.io.*;
import java.util.*;

/** Initial horizontal OBJ selection. The player can still refine it in advanced mode. */
public final class LegacyModelAnalysis {
    public record Result(LegacyCalibrationProfile profile, boolean saya, boolean usable) {}
    public static Result analyze(Reader reader) throws IOException {
        return analyze(reader, LegacyModelAdapter.STANDARD);
    }
    public static Result analyze(Reader reader, LegacyModelAdapter adapter) throws IOException {
        List<float[]> vertices = new ArrayList<>();
        Set<Integer> blade = new HashSet<>();
        Set<Integer> handle = new HashSet<>();
        boolean saya = false;
        String group = "";
        try (BufferedReader input = new BufferedReader(reader)) {
            String line;
            while ((line = input.readLine()) != null) {
                String[] fields = line.trim().split("\\s+");
                if (fields.length < 2) continue;
                if (fields[0].equals("v") && fields.length >= 4) {
                    vertices.add(new float[]{Float.parseFloat(fields[1]),
                            Float.parseFloat(fields[2]), Float.parseFloat(fields[3])});
                } else if (fields[0].equals("g") || fields[0].equals("o")) group = fields[1];
                else if (fields[0].equals("f")) {
                    if (adapter.sayaGroups().contains(group)) saya = true;
                    Set<Integer> target = adapter.bladeGroups().contains(group) ? blade
                            : adapter.hiltGroups().contains(group) ? handle : null;
                    if (target != null) for (int i = 1; i < fields.length; i++) {
                        int index = Integer.parseInt(fields[i].split("/")[0]);
                        target.add(index < 0 ? vertices.size() + index : index - 1);
                    }
                }
            }
        }
        if (blade.isEmpty()) return new Result(LegacyCalibrationProfile.DEFAULT, saya, false);
        float min = Float.POSITIVE_INFINITY, max = Float.NEGATIVE_INFINITY;
        for (int i : blade) {
            float[] v = vertices.get(i);
            float coordinate = adapter.coordinate(v[0], v[1], v[2]);
            min = Math.min(min, coordinate); max = Math.max(max, coordinate);
        }
        float length = max - min;
        if (!Float.isFinite(length) || length < .01F) return new Result(LegacyCalibrationProfile.DEFAULT, saya, false);
        Set<Integer> candidates = handle.isEmpty() ? blade : handle;
        float best = -1F, center = .772F;
        for (int i : candidates) {
            float[] v = vertices.get(i);
            float t = (adapter.coordinate(v[0], v[1], v[2]) - min) / length;
            if (t < .55F || t > .94F) continue;
            float radius = Math.abs(v[1]) + Math.abs(v[2]);
            if (radius > best) { best = radius; center = t; }
        }
        float half = .018F;
        float gripStart = Math.min(.96F, center + half);
        var profile = new LegacyCalibrationProfile(center, half,
                (gripStart + 1F) * .5F, (1F - gripStart) * .5F,
                LegacyCalibrationProfile.IDENTITY, LegacyCalibrationProfile.IDENTITY,
                LegacyCalibrationProfile.IDENTITY, false, 0L).normalized();
        return new Result(adapter.apply(profile), saya, best >= 0);
    }
    private LegacyModelAnalysis() {}
}
