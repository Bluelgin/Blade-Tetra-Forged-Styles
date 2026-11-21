package dev.bladetetra.client;

import mods.flammpfeil.slashblade.client.renderer.model.obj.Face;
import mods.flammpfeil.slashblade.client.renderer.model.obj.TextureCoordinate;
import mods.flammpfeil.slashblade.client.renderer.model.obj.Vertex;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToDoubleFunction;

/** Clips convex OBJ triangles and quads while retaining their vertex attributes. */
final class LegacyFaceClipper {
    static List<Face> clip(List<Face> faces, ToDoubleFunction<Vertex> coordinate,
            float min, float max) {
        List<Face> result = new ArrayList<>();
        for (Face source : faces) {
            if (source == null || source.vertices == null || source.vertices.length < 3) continue;
            var polygon = polygon(source);
            if (Float.isFinite(min)) polygon = clipPlane(polygon, min, true, coordinate);
            if (Float.isFinite(max)) polygon = clipPlane(polygon, max, false, coordinate);
            for (int i = 1; i + 1 < polygon.size(); i++) {
                var a = polygon.get(0);
                var b = polygon.get(i);
                var c = polygon.get(i + 1);
                if (RuntimeWavefrontViewFactory.validTriangle(a.position, b.position, c.position)) {
                    result.add(face(a, b, c));
                }
            }
        }
        return result;
    }

    private static List<ClipVertex> polygon(Face face) {
        if (face.vertices == null) return List.of();
        List<ClipVertex> result = new ArrayList<>(face.vertices.length);
        for (int index = 0; index < face.vertices.length; index++) {
            Vertex normal = face.vertexNormals != null && index < face.vertexNormals.length
                    ? face.vertexNormals[index] : face.faceNormal;
            TextureCoordinate uv = face.textureCoordinates != null
                    && index < face.textureCoordinates.length
                    ? face.textureCoordinates[index] : new TextureCoordinate(0.0F, 0.0F);
            result.add(new ClipVertex(face.vertices[index], normal,
                    uv == null ? new TextureCoordinate(0, 0) : uv));
        }
        return result;
    }

    private static List<ClipVertex> clipPlane(List<ClipVertex> input,
            float boundary, boolean keepGreater, ToDoubleFunction<Vertex> coordinate) {
        if (input.isEmpty()) return input;
        List<ClipVertex> output = new ArrayList<>();
        ClipVertex previous = input.get(input.size() - 1);
        float previousCoordinate = (float) coordinate.applyAsDouble(previous.position);
        boolean previousInside = keepGreater ? previousCoordinate >= boundary : previousCoordinate <= boundary;
        for (ClipVertex current : input) {
            float currentCoordinate = (float) coordinate.applyAsDouble(current.position);
            boolean currentInside = keepGreater ? currentCoordinate >= boundary : currentCoordinate <= boundary;
            if (currentInside != previousInside) {
                float amount = (boundary - previousCoordinate) / (currentCoordinate - previousCoordinate);
                output.add(interpolate(previous, current, amount));
            }
            if (currentInside) output.add(current);
            previous = current;
            previousCoordinate = currentCoordinate;
            previousInside = currentInside;
        }
        return output;
    }

    private static ClipVertex interpolate(ClipVertex from, ClipVertex to, float amount) {
        Vertex position = new Vertex(
                lerp(from.position.x, to.position.x, amount),
                lerp(from.position.y, to.position.y, amount),
                lerp(from.position.z, to.position.z, amount));
        Vertex normal = from.normal == null || to.normal == null ? null : new Vertex(
                lerp(from.normal.x, to.normal.x, amount),
                lerp(from.normal.y, to.normal.y, amount),
                lerp(from.normal.z, to.normal.z, amount));
        TextureCoordinate uv = new TextureCoordinate(
                lerp(from.uv.u, to.uv.u, amount),
                lerp(from.uv.v, to.uv.v, amount),
                lerp(from.uv.w, to.uv.w, amount));
        return new ClipVertex(position, normal, uv);
    }

    private static float lerp(float from, float to, float amount) {
        return from + (to - from) * amount;
    }

    private static Face face(ClipVertex first, ClipVertex second, ClipVertex third) {
        Face result = new Face();
        ClipVertex[] values = { first, second, third };
        result.vertices = new Vertex[3];
        result.vertexNormals = new Vertex[3];
        result.textureCoordinates = new TextureCoordinate[3];
        for (int index = 0; index < values.length; index++) {
            result.vertices[index] = values[index].position;
            result.vertexNormals[index] = values[index].normal;
            result.textureCoordinates[index] = values[index].uv;
        }
        result.faceNormal = result.calculateFaceNormal();
        for (int index = 0; index < result.vertexNormals.length; index++) {
            if (result.vertexNormals[index] == null) result.vertexNormals[index] = result.faceNormal;
        }
        return result;
    }


    private record ClipVertex(Vertex position, Vertex normal, TextureCoordinate uv) {}

    private LegacyFaceClipper() {}
}
