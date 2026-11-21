package dev.bladetetra.client;

import mods.flammpfeil.slashblade.client.renderer.model.obj.Face;
import mods.flammpfeil.slashblade.client.renderer.model.obj.TextureCoordinate;
import mods.flammpfeil.slashblade.client.renderer.model.obj.Vertex;
import mods.flammpfeil.slashblade.client.renderer.model.obj.WavefrontObject;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Builds immutable SlashBlade runtime model views with their final geometry
 * already present during {@link WavefrontObject} construction.
 *
 * <p>Some client render optimizers snapshot/bake Wavefront geometry from the
 * constructor. Building an empty model and replacing {@code groupObjects}
 * afterwards therefore produces a permanently empty optimized view. This
 * factory serializes Blade Tetra's already-selected/clipped faces to a tiny
 * in-memory OBJ first, so constructor-time optimizers see exactly the same
 * final geometry that vanilla SlashBlade sees.</p>
 */
final class RuntimeWavefrontViewFactory {
    static final String RUNTIME_FILENAME = "blade_tetra_runtime_part";

    private RuntimeWavefrontViewFactory() {}

    static WavefrontObject create(String groupName, List<Face> faces) {
        Objects.requireNonNull(faces, "faces");
        if (faces.isEmpty()) {
            throw new IllegalArgumentException("runtime Wavefront view requires faces");
        }
        String obj = serialize(groupName, faces);
        return new WavefrontObject(RUNTIME_FILENAME,
                new ByteArrayInputStream(obj.getBytes(StandardCharsets.UTF_8)));
    }

    static String serialize(String groupName, List<Face> faces) {
        Objects.requireNonNull(faces, "faces");
        return serialize(Map.of(groupName == null ? "runtime" : groupName, faces));
    }

    static WavefrontObject create(Map<String, List<Face>> groups) {
        return new WavefrontObject(RUNTIME_FILENAME,
                new ByteArrayInputStream(serialize(groups).getBytes(StandardCharsets.UTF_8)));
    }

    private static String serialize(Map<String, List<Face>> groups) {
        var output = new ObjOutput();
        for (var group : groups.entrySet()) output.group(group.getKey(), group.getValue());
        if (output.triangles == 0) throw new IllegalArgumentException("runtime model has no renderable faces");
        return output.text.toString();
    }

    private record Triple(float x, float y, float z) {
        Triple(Vertex vertex) { this(vertex.x, vertex.y, vertex.z); }
    }

    private record Uv(float u, float v) {}

    private static final class ObjOutput {
        private final StringBuilder text = new StringBuilder("# Blade Tetra immutable runtime part\n");
        private final Map<Triple, Integer> vertices = new HashMap<>();
        private final Map<Triple, Integer> normals = new HashMap<>();
        private final Map<Uv, Integer> textures = new HashMap<>();
        private int triangles;

        void group(String name, List<Face> faces) {
            boolean started = false;
            for (Face face : faces) {
                if (!validFace(face)) continue;
                for (int corner = 1; corner + 1 < face.vertices.length; corner++) {
                    Vertex flat = triangleNormal(face.vertices[0], face.vertices[corner], face.vertices[corner + 1]);
                    if (flat == null) continue;
                    if (!started) {
                        text.append("g ").append(safeGroupName(name)).append('\n');
                        started = true;
                    }
                    if (++triangles > 100_000) throw new IllegalArgumentException("runtime triangle budget");
                    String first = corner(face, 0, flat);
                    String second = corner(face, corner, flat);
                    String third = corner(face, corner + 1, flat);
                    text.append("f ").append(first).append(' ').append(second).append(' ').append(third).append('\n');
                }
            }
        }

        private String corner(Face face, int index, Vertex flat) {
            int position = index(vertices, "v ", new Triple(face.vertices[index]));
            var uv = texture(face, index);
            int texture = textures.computeIfAbsent(new Uv(uv.u, uv.v), key -> {
                // The native parser flips V. Undo its flip in serialized OBJ space.
                text.append("vt ").append(number(key.u)).append(' ').append(number(1 - key.v)).append('\n');
                return textures.size() + 1;
            });
            int normal = index(normals, "vn ", new Triple(normal(face, index, flat)));
            return position + "/" + texture + "/" + normal;
        }

        private int index(Map<Triple, Integer> indices, String prefix, Triple value) {
            return indices.computeIfAbsent(value, key -> {
                text.append(prefix).append(number(key.x)).append(' ').append(number(key.y)).append(' ')
                        .append(number(key.z)).append('\n');
                return indices.size() + 1;
            });
        }
    }

    static boolean validFace(Face face) {
        if (face == null || face.vertices == null || face.vertices.length < 3 || face.vertices.length > 4) return false;
        for (Vertex v : face.vertices) if (!finite(v)) return false;
        for (int i = 1; i + 1 < face.vertices.length; i++) {
            if (validTriangle(face.vertices[0], face.vertices[i], face.vertices[i + 1])) return true;
        }
        return false;
    }

    static boolean validTriangle(Vertex a, Vertex b, Vertex c) {
        return finite(a) && finite(b) && finite(c) && triangleNormal(a, b, c) != null;
    }

    private static Vertex triangleNormal(Vertex a, Vertex b, Vertex c) {
        float abx = b.x - a.x, aby = b.y - a.y, abz = b.z - a.z;
        float acx = c.x - a.x, acy = c.y - a.y, acz = c.z - a.z;
        return unit(aby * acz - abz * acy, abz * acx - abx * acz, abx * acy - aby * acx);
    }

    private static Vertex unit(float x, float y, float z) {
        double length = Math.sqrt((double) x * x + (double) y * y + (double) z * z);
        return length > 0 && Double.isFinite(length)
                ? new Vertex((float) (x / length), (float) (y / length), (float) (z / length)) : null;
    }

    private static boolean finite(Vertex v) {
        return v != null && Float.isFinite(v.x) && Float.isFinite(v.y) && Float.isFinite(v.z);
    }

    private static TextureCoordinate texture(Face face, int index) {
        if (face.textureCoordinates != null && index < face.textureCoordinates.length) {
            TextureCoordinate uv = face.textureCoordinates[index];
            if (uv != null && Float.isFinite(uv.u) && Float.isFinite(uv.v)) return uv;
        }
        return new TextureCoordinate(0.0F, 0.0F);
    }

    private static Vertex normal(Face face, int index, Vertex fallback) {
        if (face.vertexNormals != null && index < face.vertexNormals.length) {
            Vertex normal = face.vertexNormals[index];
            if (finite(normal)) {
                Vertex unit = unit(normal.x, normal.y, normal.z);
                if (unit != null) return unit;
            }
        }
        return fallback;
    }

    private static String safeGroupName(String value) {
        if (value == null || value.isBlank()) {
            return "runtime";
        }
        StringBuilder safe = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            safe.append((character >= 'a' && character <= 'z'
                    || character >= 'A' && character <= 'Z' || character >= '0' && character <= '9')
                    || character == '_' || character == '.' ? character : '_');
        }
        return safe.toString();
    }

    private static String number(float value) {
        if (!Float.isFinite(value)) {
            throw new IllegalArgumentException("non-finite runtime Wavefront coordinate");
        }
        String decimal = Float.toString(value);
        return decimal.indexOf('E') < 0 ? decimal : new BigDecimal(decimal).toPlainString();
    }
}
