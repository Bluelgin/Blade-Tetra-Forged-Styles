package dev.bladetetra.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.bladetetra.forging.LegacyCalibrationProfile.PartTransform;
import dev.bladetetra.forging.LegacyModelAdapter.Axis;
import mods.flammpfeil.slashblade.client.renderer.model.obj.Face;
import mods.flammpfeil.slashblade.client.renderer.model.obj.Vertex;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;

/** The longitudinal coordinate increases from tip to grip. All frames are right-handed. */
record LegacyMeshFrame(Vector3f origin, Quaternionf rotation) {
    static LegacyMeshFrame cardinal(Axis axis, boolean reversed, Axis width) {
        return of(new Vector3f(), unit(axis).mul(reversed ? -1 : 1), unit(width));
    }

    static LegacyMeshFrame of(Vector3f origin, Vector3f along, Vector3f width) {
        Vector3f x = new Vector3f(along).normalize();
        Vector3f z = x.cross(width, new Vector3f()).normalize();
        Vector3f y = z.cross(x, new Vector3f());
        Quaternionf rotation = new Quaternionf().setFromNormalized(
                new Matrix3f().setColumn(0, x).setColumn(1, y).setColumn(2, z));
        return new LegacyMeshFrame(new Vector3f(origin), rotation);
    }

    static Vector3f unit(Axis axis) {
        return switch (axis) {
            case X -> new Vector3f(1, 0, 0);
            case Y -> new Vector3f(0, 1, 0);
            case Z -> new Vector3f(0, 0, 1);
        };
    }

    Vector3f direction() {
        return rotation.transform(new Vector3f(1, 0, 0));
    }

    Vector3f width() {
        return rotation.transform(new Vector3f(0, 1, 0));
    }

    float along(Vertex v) {
        return longitudinal().coordinate(v);
    }

    Projection longitudinal() {
        var direction = direction();
        return new Projection(direction, direction.dot(origin));
    }

    record Projection(Vector3f direction, float offset) {
        float coordinate(Vertex v) {
            return direction.x * v.x + direction.y * v.y + direction.z * v.z - offset;
        }
    }

    LegacyMeshFrame at(Vector3f position) {
        return new LegacyMeshFrame(new Vector3f(position), rotation);
    }

    Span span(List<Face> faces) {
        Projection longitudinal = longitudinal();
        float min = Float.POSITIVE_INFINITY, max = Float.NEGATIVE_INFINITY;
        for (Face face : faces) for (Vertex v : face.vertices) {
            float x = longitudinal.coordinate(v);
            min = Math.min(min, x);
            max = Math.max(max, x);
        }
        return new Span(min, max);
    }

    /** Center of a local cross-section near the socket, independent of distant ornaments. */
    Vector3f section(List<Face> faces, float station, float tolerance) {
        var inverse = new Quaternionf(rotation).conjugate();
        Vector3f point = new Vector3f();
        float minY = Float.POSITIVE_INFINITY, minZ = minY;
        float maxY = Float.NEGATIVE_INFINITY, maxZ = maxY;
        for (Face face : faces) for (Vertex v : face.vertices) {
            inverse.transform(point.set(v.x, v.y, v.z).sub(origin));
            if (Math.abs(point.x - station) > tolerance) continue;
            minY = Math.min(minY, point.y);
            maxY = Math.max(maxY, point.y);
            minZ = Math.min(minZ, point.z);
            maxZ = Math.max(maxZ, point.z);
        }
        if (!Float.isFinite(minY)) return null;
        return rotation.transform(point.set(station, (minY + maxY) * .5F, (minZ + maxZ) * .5F)).add(origin);
    }

    record Span(float min, float max) {
        float length() { return max - min; }
        boolean valid() { return Float.isFinite(length()) && length() > .001F; }
        float at(float t) { return min + length() * t; }
    }

    /** JOML post-multiplies. The source anchor maps to the adjusted target anchor. */
    record Mount(LegacyMeshFrame source, LegacyMeshFrame target, PartTransform adjustment, float scale,
                 float offsetUnits) {
        Matrix4f matrix() {
            return new Matrix4f().translation(target.origin).rotate(target.rotation)
                    .translate(adjustment.offsetX() * offsetUnits, adjustment.offsetY() * offsetUnits, 0)
                    .rotateZ((float) Math.toRadians(adjustment.rotation()))
                    .scale(scale * adjustment.scale())
                    .scale(adjustment.length(), adjustment.width(), adjustment.width())
                    .rotate(new Quaternionf(source.rotation).conjugate())
                    .translate(new Vector3f(source.origin).negate());
        }

        void apply(PoseStack poses) {
            poses.translate(target.origin.x, target.origin.y, target.origin.z);
            poses.mulPose(target.rotation);
            poses.translate(adjustment.offsetX() * offsetUnits, adjustment.offsetY() * offsetUnits, 0);
            poses.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(adjustment.rotation()));
            float uniform = scale * adjustment.scale();
            poses.scale(uniform * adjustment.length(), uniform * adjustment.width(), uniform * adjustment.width());
            poses.mulPose(new Quaternionf(source.rotation).conjugate());
            poses.translate(-source.origin.x, -source.origin.y, -source.origin.z);
        }
    }
}
