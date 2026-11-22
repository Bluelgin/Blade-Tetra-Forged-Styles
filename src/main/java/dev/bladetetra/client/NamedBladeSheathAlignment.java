package dev.bladetetra.client;

import mods.flammpfeil.slashblade.client.renderer.model.obj.Face;
import mods.flammpfeil.slashblade.client.renderer.model.obj.Vertex;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/** Samples the mounted shells in the native closed model space. Only the sheath is changed. */
final class NamedBladeSheathAlignment {
    static final int MAX_FACES = 8_192;
    private static final int SECTIONS = 17;
    private static final float END_INSET = .035F;

    private record Ring(float lowY, float highY, float lowZ, float highZ) {
        float y() { return (lowY + highY) * .5F; }
        float z() { return (lowZ + highZ) * .5F; }
        float width() { return highY - lowY; }
        float depth() { return highZ - lowZ; }
        boolean covers(Ring other, float tolerance) {
            return lowY <= other.lowY + tolerance && highY >= other.highY - tolerance
                    && lowZ <= other.lowZ + tolerance && highZ >= other.highZ - tolerance;
        }
    }

    // Sections exclude tip poles, where the measured width can approach zero.
    static final class Profile {
        private final float tip, mouth;
        private final Ring[] rings;

        private Profile(float tip, float mouth, Ring[] rings) {
            this.tip = tip;
            this.mouth = mouth;
            this.rings = rings;
        }

        float length() { return mouth - tip; }

        private Ring at(float x) {
            float sample = sample((x - tip) / length());
            int left = (int) sample;
            Ring a = rings[left], b = rings[Math.min(left + 1, SECTIONS - 1)];
            float t = sample - left;
            return new Ring(lerp(a.lowY, b.lowY, t), lerp(a.highY, b.highY, t),
                    lerp(a.lowZ, b.lowZ, t), lerp(a.highZ, b.highZ, t));
        }
    }

    static final class Fit {
        private final Profile sheath;
        private final float tip, mouth;
        private final Section[] sections;

        private Fit(Profile sheath, float tip, float mouth, Section[] sections) {
            this.sheath = sheath;
            this.tip = tip;
            this.mouth = mouth;
            this.sections = sections;
        }

        boolean unchanged() { return sections == null; }

        Vector3f transform(Vector3f point) {
            if (unchanged()) return point;
            float fraction = (point.x - sheath.tip) / sheath.length();
            float sample = sample(fraction);
            int left = (int) sample, right = Math.min(left + 1, SECTIONS - 1);
            var a = sections[left];
            var b = sections[right];
            float t = sample - left;
            float y = lerp(a.centerY, b.centerY, t), z = lerp(a.centerZ, b.centerZ, t);
            point.x = lerp(tip, mouth, fraction);
            point.y = (point.y - y) * lerp(a.width, b.width, t) + y + lerp(a.shiftY, b.shiftY, t);
            point.z = (point.z - z) * lerp(a.depth, b.depth, t) + z + lerp(a.shiftZ, b.shiftZ, t);
            return point;
        }

        void transformNormal(Vector3f point, Vector3f normal) {
            if (unchanged()) return;
            float section = sample((point.x - sheath.tip) / sheath.length());
            int left = (int) section;
            var a = sections[left];
            var b = sections[Math.min(left + 1, SECTIONS - 1)];
            float t = section - left;
            float nx = (mouth - tip) / sheath.length();
            float ny = normal.y / lerp(a.width, b.width, t);
            float nz = normal.z / lerp(a.depth, b.depth, t);
            // Numerical longitudinal derivatives of the same bounded warp.
            var first = transform(new Vector3f(point).add(-.01F, 0, 0));
            var last = transform(new Vector3f(point).add(.01F, 0, 0));
            normal.set((normal.x - (last.y - first.y) * 50 * ny - (last.z - first.z) * 50 * nz) / nx,
                    ny, nz).normalize();
        }
    }

    private record Section(float centerY, float centerZ, float shiftY, float shiftZ, float width, float depth) {}
    private record Edge(Vector3f a, Vector3f b) {}

    static Profile profile(List<Face> faces, Matrix4f transform, boolean shell) {
        if (faces.isEmpty() || faces.size() > MAX_FACES) return null;
        List<Face> body = shell ? shell(faces, transform) : faces;
        List<Edge> edges = new ArrayList<>(body.size() * 3);
        float tip = Float.POSITIVE_INFINITY, mouth = Float.NEGATIVE_INFINITY;
        for (var face : body) {
            var last = position(face.vertices[face.vertices.length - 1], transform);
            for (var vertex : face.vertices) {
                var p = position(vertex, transform);
                if (!p.isFinite()) return null;
                tip = Math.min(tip, p.x);
                mouth = Math.max(mouth, p.x);
                edges.add(new Edge(last, p));
                last = p;
            }
        }
        if (!(mouth - tip > .001F)) return null;
        Ring[] rings = new Ring[SECTIONS];
        for (int i = 0; i < rings.length; i++) {
            float x = lerp(tip, mouth, station(i));
            float lowY = Float.POSITIVE_INFINITY, lowZ = lowY;
            float highY = Float.NEGATIVE_INFINITY, highZ = highY;
            for (var edge : edges) {
                var a = edge.a;
                var b = edge.b;
                if (a.x == b.x || x < Math.min(a.x, b.x) || x > Math.max(a.x, b.x)) continue;
                float t = (x - a.x) / (b.x - a.x);
                float y = lerp(a.y, b.y, t), z = lerp(a.z, b.z, t);
                lowY = Math.min(lowY, y);
                highY = Math.max(highY, y);
                lowZ = Math.min(lowZ, z);
                highZ = Math.max(highZ, z);
            }
            if (!Float.isFinite(lowY)) return null;
            rings[i] = new Ring(lowY, highY, lowZ, highZ);
        }
        return new Profile(tip, mouth, rings);
    }

    // Find the longitudinal shell when accessories are disconnected. Keep all
    // faces for drawing, including the disconnected components.
    private static List<Face> shell(List<Face> faces, Matrix4f transform) {
        record Point(float x, float y, float z) {}
        int[] roots = new int[faces.size()];
        var owners = new HashMap<Point, Integer>();
        for (int i = 0; i < faces.size(); i++) {
            roots[i] = i;
            for (var v : faces.get(i).vertices) {
                Integer other = owners.putIfAbsent(new Point(v.x + 0F, v.y + 0F, v.z + 0F), i);
                if (other != null) roots[root(roots, i)] = root(roots, other);
            }
        }
        float[] min = new float[faces.size()], max = new float[faces.size()];
        java.util.Arrays.fill(min, Float.POSITIVE_INFINITY);
        java.util.Arrays.fill(max, Float.NEGATIVE_INFINITY);
        float totalMin = Float.POSITIVE_INFINITY, totalMax = Float.NEGATIVE_INFINITY;
        int longest = 0;
        for (int i = 0; i < faces.size(); i++) {
            int group = root(roots, i);
            for (var v : faces.get(i).vertices) {
                float x = transform.m00() * v.x + transform.m10() * v.y + transform.m20() * v.z + transform.m30();
                min[group] = Math.min(min[group], x);
                max[group] = Math.max(max[group], x);
            }
            if (max[group] - min[group] > max[longest] - min[longest]) longest = group;
            totalMin = Math.min(totalMin, min[group]);
            totalMax = Math.max(totalMax, max[group]);
        }
        if (max[longest] - min[longest] < (totalMax - totalMin) * .8F) return faces;
        List<Face> body = new ArrayList<>();
        for (int i = 0; i < faces.size(); i++) if (root(roots, i) == longest) body.add(faces.get(i));
        return body;
    }

    private static int root(int[] roots, int index) {
        while (index != roots[index]) {
            roots[index] = roots[roots[index]];
            index = roots[index];
        }
        return index;
    }

    static boolean covers(Profile blade, Profile sheath, float tolerance) {
        if (blade == null || sheath == null || sheath.tip > blade.tip + tolerance
                || sheath.mouth < blade.mouth - tolerance) return false;
        for (int i = 0; i < SECTIONS; i++) {
            float x = lerp(blade.tip, blade.mouth, station(i));
            if (!sheath.at(x).covers(blade.rings[i], tolerance)) return false;
        }
        return true;
    }

    static Fit fit(Profile blade, Profile sheath) {
        if (blade == null || sheath == null) return null;
        float length = blade.length();
        if (covers(blade, sheath, length * .005F)) return new Fit(sheath, sheath.tip, sheath.mouth, null);
        float tip = Math.min(sheath.tip, blade.tip - length * .018F);
        float mouth = blade.mouth;
        float longitudinal = (mouth - tip) / sheath.length();
        if (longitudinal < .75F || longitudinal > 1.35F || Math.abs(mouth - sheath.mouth) > length * .12F) return null;
        var sections = new Section[SECTIONS];
        for (int i = 0; i < SECTIONS; i++) {
            Ring current = sheath.rings[i];
            Ring cuttingEdge = blade.at(lerp(tip, mouth, station(i)));
            if (!(current.width() > .001F && current.depth() > .001F)) return null;
            float dy = cuttingEdge.y() - current.y(), dz = cuttingEdge.z() - current.z();
            float width = Math.max(1, (cuttingEdge.width() + length * .018F) / current.width());
            float depth = Math.max(1, (cuttingEdge.depth() + length * .008F) / current.depth());
            if (Math.hypot(dy, dz) > length * .22F || width > 1.65F || depth > 1.65F) return null;
            if (i > 0 && Math.hypot(dy - sections[i - 1].shiftY, dz - sections[i - 1].shiftZ) > length * .065F) {
                return null;
            }
            sections[i] = new Section(current.y(), current.z(), dy, dz, width, depth);
        }
        return new Fit(sheath, tip, mouth, sections);
    }

    static List<Face> bake(List<Face> faces, Matrix4f mount, Fit fit) {
        List<Face> result = new ArrayList<>(faces.size());
        var normals = mount.normal(new Matrix3f());
        for (Face face : faces) {
            var copy = new Face();
            copy.vertices = new Vertex[face.vertices.length];
            copy.textureCoordinates = face.textureCoordinates;
            if (face.vertexNormals != null) copy.vertexNormals = new Vertex[copy.vertices.length];
            for (int i = 0; i < copy.vertices.length; i++) {
                var point = position(face.vertices[i], mount);
                if (copy.vertexNormals != null && i < face.vertexNormals.length && face.vertexNormals[i] != null) {
                    var input = face.vertexNormals[i];
                    var normal = normals.transform(new Vector3f(input.x, input.y, input.z));
                    fit.transformNormal(point, normal);
                    copy.vertexNormals[i] = new Vertex(normal.x, normal.y, normal.z);
                }
                fit.transform(point);
                copy.vertices[i] = new Vertex(point.x, point.y, point.z);
            }
            result.add(copy);
        }
        return result;
    }

    private static Vector3f position(Vertex vertex, Matrix4f transform) {
        return transform.transformPosition(new Vector3f(vertex.x, vertex.y, vertex.z));
    }

    private static float station(int index) {
        return lerp(END_INSET, 1 - END_INSET, (float) index / (SECTIONS - 1));
    }

    private static float sample(float fraction) {
        return Math.max(0, Math.min(SECTIONS - 1, (fraction - END_INSET) / (1 - 2 * END_INSET) * (SECTIONS - 1)));
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private NamedBladeSheathAlignment() {}
}
