package dev.bladetetra.client;

import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import dev.bladetetra.forging.LegacyImprintKind;
import mods.flammpfeil.slashblade.client.renderer.model.BladeModelManager;
import mods.flammpfeil.slashblade.client.renderer.model.obj.Face;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** Client-only source artwork selection and crisp, metal-lit ingot composition. */
final class NamedIngotArtwork {
    private record Region(double u0, double v0, double u1, double v1) {
        boolean valid() {
            return Double.isFinite(u0 + v0 + u1 + v1) && u0 >= 0 && v0 >= 0
                    && u1 <= 1 && v1 <= 1 && u1 > u0 && v1 > v0;
        }
    }
    private static final Region WHOLE = new Region(0, 0, 1, 1);

    static NativeImage source(ResourceManager resources, LegacyImprintKind kind) throws IOException {
        ResourceLocation texture = kind.texture();
        Region region = iconRegion(resources, kind);
        // Resource packs can explicitly identify a standalone backplate or atlas
        // region. Never guess that every provider has a similarly named PNG.
        var override = resources.getResource(new ResourceLocation("blade_tetra",
                "named_ingot_visuals/" + kind.name().getNamespace() + "/"
                        + kind.name().getPath() + ".json"));
        if (override.isPresent()) {
            try (var reader = new InputStreamReader(override.get().open(), StandardCharsets.UTF_8)) {
                var json = JsonParser.parseReader(reader).getAsJsonObject();
                ResourceLocation candidateTexture = texture;
                Region candidateRegion = region;
                if (json.has("texture")) {
                    candidateTexture = ResourceLocation.tryParse(json.get("texture").getAsString());
                    candidateRegion = WHOLE;
                }
                if (json.has("region")) {
                    var a = json.getAsJsonArray("region");
                    if (a.size() != 4) throw new IllegalArgumentException("region needs four UV values");
                    candidateRegion = new Region(a.get(0).getAsDouble(), a.get(1).getAsDouble(),
                            a.get(2).getAsDouble(), a.get(3).getAsDouble());
                }
                if (candidateTexture != null && candidateRegion.valid()
                        && resources.getResource(candidateTexture).isPresent()) {
                    texture = candidateTexture;
                    region = candidateRegion;
                }
            } catch (RuntimeException | IOException ignored) {
                // Optional presentation metadata cannot make an ingot unusable.
            }
        }
        try (var input = resources.getResourceOrThrow(texture).open();
                var image = NativeImage.read(input)) {
            if (Boolean.getBoolean("blade_tetra.namedIngotArtReview"))
                com.mojang.logging.LogUtils.getLogger().info("NAMED_INGOT_ART_SOURCE: {} {} {}", kind.id(), texture, region);
            int x0 = (int) (region.u0 * image.getWidth());
            int y0 = (int) (region.v0 * image.getHeight());
            int x1 = Math.min(image.getWidth(), (int) Math.ceil(region.u1 * image.getWidth()));
            int y1 = Math.min(image.getHeight(), (int) Math.ceil(region.v1 * image.getHeight()));
            var crop = new NativeImage(Math.max(1, x1 - x0), Math.max(1, y1 - y0), true);
            for (int y = 0; y < crop.getHeight(); y++) for (int x = 0; x < crop.getWidth(); x++)
                crop.setPixelRGBA(x, y, image.getPixelRGBA(x0 + x, y0 + y));
            return crop;
        }
    }

    private static Region iconRegion(ResourceManager resources, LegacyImprintKind kind) {
        // A broad textured face in item_blade is commonly the icon's backing
        // artwork. This is a bounded heuristic, not a guaranteed provider API.
        // Parsed Wavefront UVs already have V flipped into image coordinates.
        try {
            if (resources.getResource(kind.model()).isEmpty()) return WHOLE;
            var model = BladeModelManager.getInstance().getModel(kind.model());
            if (model.groupObjects.size() > 4096) return WHOLE;
            Region best = WHOLE;
            double bestScore = 0;
            Face bestFace = null;
            java.util.List<Face> candidates = new java.util.ArrayList<>();
            int inspected = 0;
            for (var group : model.groupObjects) {
                if (!group.name.equals("item_blade") && !group.name.equals("backplate")) continue;
                for (var face : group.faces) {
                    if (++inspected > 200_000) return best;
                    if (face.textureCoordinates == null || face.textureCoordinates.length < 3) continue;
                    double u0 = 1, v0 = 1, u1 = 0, v1 = 0;
                    for (var uv : face.textureCoordinates) {
                        if (uv == null) continue;
                        u0 = Math.min(u0, uv.u); v0 = Math.min(v0, uv.v);
                        u1 = Math.max(u1, uv.u); v1 = Math.max(v1, uv.v);
                    }
                    var candidate = new Region(u0, v0, u1, v1);
                    double w = u1 - u0, h = v1 - v0;
                    if (!candidate.valid() || w * h < .015 || w * h > .8) continue;
                    double score = projectedArea(face) * Math.min(w, h) / Math.max(w, h);
                    candidates.add(face);
                    if (group.name.equals("backplate")) score *= 4;
                    if (score > bestScore) { best = candidate; bestScore = score; bestFace = face; }
                }
            }
            // OBJ exporters split the same backing quad into two triangles.
            // Join its coplanar edge-sharing neighbor rather than cropping half
            // the emblem, or choosing a large but physically tiny atlas patch.
            if (bestFace != null) for (var face : candidates) {
                if (!samePlaneAndEdge(bestFace, face)) continue;
                double u0 = best.u0, v0 = best.v0, u1 = best.u1, v1 = best.v1;
                for (var uv : face.textureCoordinates) {
                    if (uv == null) continue;
                    u0 = Math.min(u0, uv.u); v0 = Math.min(v0, uv.v);
                    u1 = Math.max(u1, uv.u); v1 = Math.max(v1, uv.v);
                }
                var joined = new Region(u0, v0, u1, v1);
                if (joined.valid() && (u1-u0)*(v1-v0) <= .8) best = joined;
            }
            return best;
        } catch (RuntimeException | LinkageError ignored) {
            return WHOLE;
        }
    }

    private static double projectedArea(Face face) {
        if (face.vertices == null || face.vertices.length < 3) return 0;
        var a = face.vertices[0];
        double area = 0;
        for (var v : face.vertices) if (Math.abs(v.z - a.z) > .001) return 0;
        for (int i = 1; i + 1 < face.vertices.length; i++) {
            var b = face.vertices[i]; var c = face.vertices[i + 1];
            area += Math.abs((b.x-a.x)*(c.y-a.y)-(b.y-a.y)*(c.x-a.x)) / 2;
        }
        return area;
    }

    private static boolean samePlaneAndEdge(Face a, Face b) {
        if (projectedArea(b) <= 0 || Math.abs(a.vertices[0].z - b.vertices[0].z) > .001) return false;
        int shared = 0;
        for (var v : a.vertices) {
            for (var w : b.vertices) {
                if (Math.abs(v.x-w.x)+Math.abs(v.y-w.y)+Math.abs(v.z-w.z) < .001) {
                    shared++; break;
                }
            }
        }
        return shared >= 2;
    }

    static void compose(NativeImage iron, NativeImage source, NativeImage output) {
        int size = output.getWidth();
        int minX = size, minY = size, maxX = 0, maxY = 0;
        for (int y = 0; y < size; y++) for (int x = 0; x < size; x++) {
            if (opaque(iron, x, y, size)) {
                minX = Math.min(minX, x); minY = Math.min(minY, y);
                maxX = Math.max(maxX, x); maxY = Math.max(maxY, y);
            }
        }
        // Transparent gaps in a provider's artwork use its own opaque average,
        // never unrelated parts of the atlas or an arbitrary replacement color.
        long rr = 0, gg = 0, bb = 0, count = 0;
        for (int y = 0; y < source.getHeight(); y++) for (int x = 0; x < source.getWidth(); x++) {
            int p = source.getPixelRGBA(x, y);
            if ((p >>> 24) < 40) continue;
            rr += p & 255; gg += (p >>> 8) & 255; bb += (p >>> 16) & 255; count++;
        }
        int fallback = count == 0 ? 0xff999999
                : 0xff000000 | ((int) (bb / count) << 16) | ((int) (gg / count) << 8) | (int) (rr / count);
        for (int y = 0; y < size; y++) for (int x = 0; x < size; x++) {
            if (!opaque(iron, x, y, size)) { output.setPixelRGBA(x, y, 0); continue; }
            int metal = iron.getPixelRGBA(x * iron.getWidth() / size, y * iron.getHeight() / size);
            boolean edge = !opaque(iron, x - 1, y, size) || !opaque(iron, x + 1, y, size)
                    || !opaque(iron, x, y - 1, size) || !opaque(iron, x, y + 1, size);
            if (edge) { output.setPixelRGBA(x, y, metal); continue; }
            double u = (x - minX) / (double) Math.max(1, maxX - minX);
            double v = (y - minY) / (double) Math.max(1, maxY - minY);
            int sx = Math.min(source.getWidth() - 1, (int) (u * source.getWidth()));
            int sy = Math.min(source.getHeight() - 1, (int) (v * source.getHeight()));
            int pixel = source.getPixelRGBA(sx, sy);
            if ((pixel >>> 24) < 40) pixel = fallback;
            int luminance = ((metal & 255) * 30 + ((metal >>> 8) & 255) * 59
                    + ((metal >>> 16) & 255) * 11) / 100;
            // Original iron lighting defines the top and dark side. Source
            // artwork keeps its orientation and large motifs: no quadrant hash,
            // rotation or blur. Quantization keeps the result pixel-art crisp.
            double light = .48 + luminance / 255.0 * .65;
            int shine = luminance > 210 ? 18 : 0;
            int r = shade(pixel & 255, light, shine);
            int g = shade((pixel >>> 8) & 255, light, shine);
            int b = shade((pixel >>> 16) & 255, light, shine);
            output.setPixelRGBA(x, y, 0xff000000 | (b << 16) | (g << 8) | r);
        }
    }

    private static int shade(int value, double light, int shine) {
        return Math.min(248, Math.max(24, (int) (value * light + shine)) / 8 * 8);
    }

    private static boolean opaque(NativeImage image, int x, int y, int size) {
        return x >= 0 && y >= 0 && x < size && y < size
                && (image.getPixelRGBA(x * image.getWidth() / size,
                        y * image.getHeight() / size) >>> 24) >= 40;
    }

    private NamedIngotArtwork() {}
}
