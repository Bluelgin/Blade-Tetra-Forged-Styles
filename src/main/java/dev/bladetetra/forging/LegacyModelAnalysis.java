package dev.bladetetra.forging;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Bounded OBJ analysis used by development/tests and explicit adapter workflows.
 * Runtime addon discovery no longer parses provider geometry on the server.
 */
public final class LegacyModelAnalysis {
    static final int MAX_VERTICES = 200_000;
    static final int MAX_FACES = 200_000;
    static final int MAX_FACE_VERTICES = 128;
    static final int MAX_GROUPS = 4_096;
    static final int MAX_LINE_LENGTH = 65_536;
    static final long MAX_CHARACTERS = 32L * 1024L * 1024L;

    public record Result(LegacyCalibrationProfile profile, boolean saya, boolean usable) {}

    public static Result analyze(Reader reader) throws IOException {
        return analyze(reader, LegacyModelAdapter.STANDARD);
    }

    public static Result analyze(Reader reader, LegacyModelAdapter adapter) throws IOException {
        if (reader == null) {
            return unusable(false);
        }
        adapter = adapter == null ? LegacyModelAdapter.STANDARD : adapter;
        List<float[]> vertices = new ArrayList<>();
        Set<Integer> blade = new HashSet<>();
        Set<Integer> handle = new HashSet<>();
        boolean saya = false;
        String group = "";
        int faces = 0;
        int groups = 0;
        long characters = 0;

        try (BufferedReader input = new BufferedReader(reader)) {
            String line;
            while ((line = input.readLine()) != null) {
                characters += line.length() + 1L;
                if (characters > MAX_CHARACTERS || line.length() > MAX_LINE_LENGTH) {
                    return unusable(saya);
                }
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
                String[] fields = trimmed.split("\\s+");
                if (fields.length < 2) continue;

                if (fields[0].equals("v") && fields.length >= 4) {
                    if (vertices.size() >= MAX_VERTICES) return unusable(saya);
                    Float x = finite(fields[1]);
                    Float y = finite(fields[2]);
                    Float z = finite(fields[3]);
                    if (x == null || y == null || z == null) return unusable(saya);
                    vertices.add(new float[]{x, y, z});
                } else if (fields[0].equals("g") || fields[0].equals("o")) {
                    if (++groups > MAX_GROUPS) return unusable(saya);
                    group = fields[1];
                } else if (fields[0].equals("f")) {
                    if (++faces > MAX_FACES || fields.length - 1 > MAX_FACE_VERTICES) {
                        return unusable(saya);
                    }
                    if (adapter.sayaGroups().contains(group)) saya = true;
                    Set<Integer> target = adapter.bladeGroups().contains(group) ? blade
                            : adapter.hiltGroups().contains(group) ? handle : null;
                    if (target == null) continue;
                    for (int i = 1; i < fields.length; i++) {
                        Integer raw = faceIndex(fields[i]);
                        if (raw == null || raw == 0) return unusable(saya);
                        int index = raw < 0 ? vertices.size() + raw : raw - 1;
                        if (index < 0 || index >= vertices.size()) return unusable(saya);
                        target.add(index);
                    }
                }
            }
        }

        if (blade.isEmpty()) return unusable(saya);
        float min = Float.POSITIVE_INFINITY;
        float max = Float.NEGATIVE_INFINITY;
        for (int index : blade) {
            float[] vertex = vertices.get(index);
            float coordinate = adapter.coordinate(vertex[0], vertex[1], vertex[2]);
            if (!Float.isFinite(coordinate)) return unusable(saya);
            min = Math.min(min, coordinate);
            max = Math.max(max, coordinate);
        }
        float length = max - min;
        if (!Float.isFinite(length) || length < .01F) return unusable(saya);

        Set<Integer> candidates = handle.isEmpty() ? blade : handle;
        float best = -1F;
        float center = .772F;
        for (int index : candidates) {
            float[] vertex = vertices.get(index);
            float t = (adapter.coordinate(vertex[0], vertex[1], vertex[2]) - min) / length;
            if (!Float.isFinite(t) || t < .55F || t > .94F) continue;
            float radius = Math.abs(vertex[1]) + Math.abs(vertex[2]);
            if (Float.isFinite(radius) && radius > best) {
                best = radius;
                center = t;
            }
        }

        float half = .018F;
        float gripStart = Math.min(.96F, center + half);
        LegacyCalibrationProfile profile = new LegacyCalibrationProfile(
                center, half, (gripStart + 1F) * .5F, (1F - gripStart) * .5F,
                LegacyCalibrationProfile.IDENTITY, LegacyCalibrationProfile.IDENTITY,
                LegacyCalibrationProfile.IDENTITY, false, 0L).normalized();
        return new Result(adapter.apply(profile), saya, best >= 0);
    }

    private static Result unusable(boolean saya) {
        return new Result(LegacyCalibrationProfile.DEFAULT, saya, false);
    }

    private static Float finite(String value) {
        try {
            float parsed = Float.parseFloat(value);
            return Float.isFinite(parsed) ? parsed : null;
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static Integer faceIndex(String token) {
        if (token == null || token.isBlank()) return null;
        int slash = token.indexOf('/');
        String value = slash >= 0 ? token.substring(0, slash) : token;
        if (value.isBlank()) return null;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private LegacyModelAnalysis() {}
}
