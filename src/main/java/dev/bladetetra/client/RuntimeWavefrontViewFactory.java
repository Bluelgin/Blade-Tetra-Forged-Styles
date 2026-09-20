package dev.bladetetra.client;

import mods.flammpfeil.slashblade.client.renderer.model.obj.Face;
import mods.flammpfeil.slashblade.client.renderer.model.obj.TextureCoordinate;
import mods.flammpfeil.slashblade.client.renderer.model.obj.Vertex;
import mods.flammpfeil.slashblade.client.renderer.model.obj.WavefrontObject;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
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
        StringBuilder out = new StringBuilder(Math.max(256, faces.size() * 192));
        out.append("# Blade Tetra immutable runtime part\n");
        out.append("g ").append(safeGroupName(groupName)).append('\n');

        int nextIndex = 1;
        for (Face face : faces) {
            if (face == null || face.vertices == null || face.vertices.length < 3) {
                continue;
            }
            // SlashBlade's OBJ loader requires one drawing mode per group. Always
            // triangulate here so source quads and clipped triangles can coexist.
            for (int corner = 1; corner + 1 < face.vertices.length; corner++) {
                int[] triangle = {0, corner, corner + 1};
                for (int sourceIndex : triangle) {
                    Vertex position = face.vertices[sourceIndex];
                    TextureCoordinate uv = texture(face, sourceIndex);
                    Vertex normal = normal(face, sourceIndex);
                    out.append("v ").append(number(position.x)).append(' ')
                            .append(number(position.y)).append(' ')
                            .append(number(position.z)).append('\n');
                    // WavefrontObject flips V while parsing. Flip it back in the
                    // generated OBJ so the parsed runtime view preserves the live UV.
                    out.append("vt ").append(number(uv.u)).append(' ')
                            .append(number(1.0F - uv.v)).append('\n');
                    out.append("vn ").append(number(normal.x)).append(' ')
                            .append(number(normal.y)).append(' ')
                            .append(number(normal.z)).append('\n');
                }
                out.append("f ")
                        .append(nextIndex).append('/').append(nextIndex).append('/').append(nextIndex).append(' ')
                        .append(nextIndex + 1).append('/').append(nextIndex + 1).append('/').append(nextIndex + 1).append(' ')
                        .append(nextIndex + 2).append('/').append(nextIndex + 2).append('/').append(nextIndex + 2)
                        .append('\n');
                nextIndex += 3;
            }
        }
        if (nextIndex == 1) {
            throw new IllegalArgumentException("runtime Wavefront view has no renderable faces");
        }
        return out.toString();
    }

    private static TextureCoordinate texture(Face face, int index) {
        if (face.textureCoordinates != null
                && index < face.textureCoordinates.length
                && face.textureCoordinates[index] != null) {
            return face.textureCoordinates[index];
        }
        return new TextureCoordinate(0.0F, 0.0F);
    }

    private static Vertex normal(Face face, int index) {
        if (face.vertexNormals != null
                && index < face.vertexNormals.length
                && face.vertexNormals[index] != null) {
            return face.vertexNormals[index];
        }
        if (face.faceNormal != null) {
            return face.faceNormal;
        }
        return face.calculateFaceNormal();
    }

    private static String safeGroupName(String value) {
        if (value == null || value.isBlank()) {
            return "runtime";
        }
        StringBuilder safe = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            safe.append(Character.isLetterOrDigit(character)
                    || character == '_' || character == '.' ? character : '_');
        }
        return safe.toString();
    }

    private static String number(float value) {
        if (!Float.isFinite(value)) {
            throw new IllegalArgumentException("non-finite runtime Wavefront coordinate");
        }
        return BigDecimal.valueOf((double) value).stripTrailingZeros().toPlainString();
    }
}
