package dev.bladetetra.client;

import dev.bladetetra.forging.LegacyCalibrationProfile;
import dev.bladetetra.client.LegacySourceMesh.Component;
import dev.bladetetra.client.LegacySourceMesh.Layers;
import dev.bladetetra.client.LegacyModelPartRenderer.Part;
import dev.bladetetra.forging.LegacyModelAdapter.Axis;
import mods.flammpfeil.slashblade.client.renderer.model.obj.Face;
import mods.flammpfeil.slashblade.client.renderer.model.obj.WavefrontObject;
import org.joml.Vector3f;

import java.util.List;

/** Resolves source sockets into the authored Tetra world or item pose. */
final class LegacyBladeMounts {
    private static final LegacyMeshFrame WORLD = LegacyMeshFrame.cardinal(Axis.X, false, Axis.Y);

    static LegacyMeshFrame.Mount fit(LegacySourceMesh source, Component part, Layers reference,
            Layers healthyBlade, Layers displayed, WavefrontObject target, String state) {
        List<Face> faces = reference.reference();
        var bladeSocket = source.anchor(Component.BLADE, healthyBlade.reference(), null);
        var anchor = source.anchor(part, faces, bladeSocket == null ? null : bladeSocket.origin());
        if (anchor == null) return null;

        boolean icon = state.startsWith("item_");
        var ordinary = LegacyModelPartRenderer.modularFaces(target, icon ? state : worldGroup(part), stockPart(part));
        if (ordinary.isEmpty()) return null;
        var to = icon ? iconFrame(target, state, part, ordinary) : worldFrame(target, part, ordinary);
        if (to == null) return null;

        boolean authoredIcon = icon && source.hasExplicitIcon(part, state);
        if (authoredIcon) {
            anchor = iconFrame(displayed.reference(), part, to);
            if (anchor == null) return null;
            faces = displayed.reference();
        } else if (icon && part == Component.BLADE && state.equals("item_damaged")) {
            faces = displayed.reference();
        }
        float stockLength = to.span(ordinary).length();
        float fromLength = anchor.span(faces).length();
        float units = authoredIcon ? 1 : source.rules.unitScale();
        float scale = source.rules.preserveShape(part.key()) ? units : stockLength / fromLength;
        if (icon && source.rules.preserveShape(part.key()) && !authoredIcon) {
            var world = LegacyModelPartRenderer.modularFaces(target, worldGroup(part), stockPart(part));
            var worldLength = WORLD.span(world);
            if (!worldLength.valid()) return null;
            scale *= stockLength / worldLength.length();
        }
        float proportional = scale / units;
        if (!Float.isFinite(proportional) || proportional < .25F || proportional > 4) return null;

        var adjustment = switch (part) {
            case BLADE -> source.profile.bladeTransform();
            case HILT -> source.profile.tsubaTransform();
            case SAYA -> source.profile.sayaTransform();
        };
        var mount = new LegacyMeshFrame.Mount(anchor, to, source.rules.adjustment(part.key(), adjustment),
                scale, icon ? 20 : 40);
        return icon ? fitIconEnvelope(mount, displayed, target, state) : mount;
    }

    /** Keep unusual grips and decorations inside the footprint of the authored icon. */
    private static LegacyMeshFrame.Mount fitIconEnvelope(LegacyMeshFrame.Mount mount, Layers mesh,
            WavefrontObject target, String state) {
        float minX = Float.POSITIVE_INFINITY, minY = minX;
        float maxX = Float.NEGATIVE_INFINITY, maxY = maxX;
        for (var part : Component.values()) {
            if (part == Component.SAYA && !state.equals("item_blade")) continue;
            var faces = LegacyModelPartRenderer.modularFaces(target, state, stockPart(part));
            for (Face face : faces) for (var v : face.vertices) {
                minX = Math.min(minX, v.x);
                maxX = Math.max(maxX, v.x);
                minY = Math.min(minY, v.y);
                maxY = Math.max(maxY, v.y);
            }
        }
        if (!(minX < maxX && minY < maxY)) return null;

        // Allow a small margin for curves or authored decorations while keeping
        // the socket fixed and scaling the part uniformly in all directions.
        float marginX = (maxX - minX) * .04F, marginY = (maxY - minY) * .04F;
        minX -= marginX;
        maxX += marginX;
        minY -= marginY;
        maxY += marginY;
        var transform = mount.matrix();
        Vector3f pivot = transform.transformPosition(new Vector3f(mount.source().origin()));
        if (pivot.x < minX || pivot.x > maxX || pivot.y < minY || pivot.y > maxY) return null;
        Vector3f point = new Vector3f();
        float fit = 1;
        for (var layer : List.of(mesh.base(), mesh.luminous())) {
            for (Face face : layer) for (var v : face.vertices) {
                transform.transformPosition(point.set(v.x, v.y, v.z));
                fit = Math.min(fit, axisFit(pivot.x, point.x, minX, maxX));
                fit = Math.min(fit, axisFit(pivot.y, point.y, minY, maxY));
            }
        }
        if (!Float.isFinite(fit) || fit < .20F) return null;
        return fit == 1 ? mount : new LegacyMeshFrame.Mount(mount.source(), mount.target(),
                mount.adjustment(), mount.scale() * fit, mount.offsetUnits());
    }

    private static float axisFit(float pivot, float point, float min, float max) {
        if (point < min) return (min - pivot) / (point - pivot);
        if (point > max) return (max - pivot) / (point - pivot);
        return 1;
    }

    private static String worldGroup(Component part) {
        return part == Component.SAYA ? "sheath" : "blade";
    }

    private static Part stockPart(Component part) {
        return switch (part) {
            case BLADE -> Part.BLADE;
            case HILT -> Part.HILT;
            case SAYA -> Part.SAYA;
        };
    }

    private static LegacyMeshFrame worldFrame(WavefrontObject target, Component part, List<Face> faces) {
        var blade = LegacyModelPartRenderer.modularFaces(target, "blade", Part.BLADE);
        var span = WORLD.span(blade);
        if (!span.valid()) return null;
        Vector3f root = WORLD.section(blade, span.max(), span.length() * .035F);
        if (part != Component.SAYA) return root == null ? null : WORLD.at(root);
        span = WORLD.span(faces);
        if (!span.valid()) return null;
        Vector3f mouth = WORLD.section(faces, span.max(), span.length() * .04F);
        return mouth == null ? null : WORLD.at(mouth);
    }

    private static LegacyMeshFrame iconFrame(WavefrontObject target, String state, Component part, List<Face> faces) {
        // The stock hilt and blade belong to the same icon sword. Use their
        // relative positions to orient the measured blade axis.
        var blade = LegacyModelPartRenderer.modularFaces(target, state, Part.BLADE);
        var hilt = LegacyModelPartRenderer.modularFaces(target, state, Part.HILT);
        if (part == Component.SAYA) return socket(faces, principalAxis(faces, new Vector3f(1, 1, 0)), true);
        if (blade.isEmpty() || hilt.isEmpty()) return null;
        Vector3f towardGrip = centroid(hilt).sub(centroid(blade));
        Vector3f along = principalAxis(blade, towardGrip);
        return socket(blade, along, true);
    }

    private static LegacyMeshFrame iconFrame(List<Face> faces, Component part, LegacyMeshFrame target) {
        return socket(faces, principalAxis(faces, target.direction()), part != Component.HILT);
    }

    private static LegacyMeshFrame socket(List<Face> faces, Vector3f along, boolean rear) {
        if (faces.isEmpty()) return null;
        var frame = LegacyMeshFrame.of(new Vector3f(), along, new Vector3f(-along.y, along.x, 0));
        var span = frame.span(faces);
        if (!span.valid()) return null;
        var origin = frame.section(faces, rear ? span.max() : span.min(), span.length() * .05F);
        return origin == null ? null : frame.at(origin);
    }

    private static Vector3f centroid(List<Face> faces) {
        Vector3f center = new Vector3f();
        int n = 0;
        for (Face face : faces) for (var v : face.vertices) {
            center.add(v.x, v.y, v.z);
            n++;
        }
        return n == 0 ? center : center.div(n);
    }

    private static Vector3f principalAxis(List<Face> faces, Vector3f toward) {
        Vector3f center = centroid(faces);
        double xx = 0, yy = 0, xy = 0;
        for (Face face : faces) for (var v : face.vertices) {
            double x = v.x - center.x, y = v.y - center.y;
            xx += x * x;
            yy += y * y;
            xy += x * y;
        }
        double angle = .5 * Math.atan2(2 * xy, xx - yy);
        Vector3f along = new Vector3f((float) Math.cos(angle), (float) Math.sin(angle), 0);
        return along.dot(toward) < 0 ? along.negate() : along;
    }

    private LegacyBladeMounts() {}
}
