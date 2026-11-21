package dev.bladetetra.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.bladetetra.forging.NamedLegacyParts;
import dev.bladetetra.forging.LegacyImprintKind;
import dev.bladetetra.item.SaltyFishParts.Length;
import mods.flammpfeil.slashblade.client.renderer.model.BladeModelManager;
import mods.flammpfeil.slashblade.client.renderer.model.obj.Face;
import mods.flammpfeil.slashblade.client.renderer.model.obj.Vertex;
import mods.flammpfeil.slashblade.client.renderer.model.obj.WavefrontObject;
import mods.flammpfeil.slashblade.client.renderer.util.BladeRenderState;
import mods.flammpfeil.slashblade.event.client.RenderOverrideEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;

import java.util.ArrayList;
import java.util.List;

/**
 * Runtime view over SlashBlade's own OBJ resources. Faces are selected from
 * the source model and rebuilt as immutable Wavefront views whose final geometry
 * already exists during construction, so constructor-time render optimizers can
 * safely snapshot/bake them.
 */
public final class LegacyModelPartRenderer {
    private record FishKey(Object source, String group, boolean hideBlade, boolean hideGuard, Float coveredTo) {}
    private static final LegacyMeshCache<FishKey, WavefrontObject> VIEWS =
            new LegacyMeshCache<>(160, 100_000, LegacyRenderSources::triangleCount);

    enum Part { BLADE, TSUBA, TSUKA, SAYA, HILT }
    public static boolean supports(String target) {
        return "blade".equals(target) || "blade_damaged".equals(target)
                || "blade_fragment".equals(target)
                || "sheath".equals(target) || "item_blade".equals(target)
                || "item_bladens".equals(target) || "item_damaged".equals(target);
    }

    /** Fish uses the same native blade and sheath passes, with the stock guard seat as its datum. */
    static void renderFish(RenderOverrideEvent event, Length blade, Length saya,
            LegacyImprintKind salvaged, ResourceLocation ordinary, ResourceLocation emissive) {
        String target = event.getOriginalTarget();
        var named = NamedLegacyParts.fromStack(event.getStack());
        var namedBlade = NamedLegacyParts.bladeFromStack(event.getStack());
        LegacyImprintKind guard = named.tsuba();
        if (guard == null) guard = salvaged;

        event.setCanceled(true);
        if ("sheath".equals(target)) {
            if (saya == null) drawNamedOrStockSaya(event, named.saya(), ordinary, emissive);
            else drawFish(event, saya, "saya", target, null);
            return;
        }

        boolean icon = InventoryBladeModel.isIcon(target);
        boolean showSaya = icon && !"item_bladens".equals(target) && !"item_damaged".equals(target);
        String group = "item_damaged".equals(target) ? "blade_damaged" : icon ? "blade" : target;
        PoseStack poses = event.getPoseStack();
        poses.pushPose();
        try {
            if (icon) {
                // Draw the same geometry/UVs used in hand. Fit in the native 142-unit icon quad.
                var originalBlade = bounds(faces(event.getOriginalModel(), group));
                var originalSaya = bounds(faces(event.getOriginalModel(), "sheath"));
                float tip = blade != null ? ("blade_damaged".equals(group) ? -122 : blade.bladeTip)
                        : originalBlade.valid() ? originalBlade.min : -282;
                if (showSaya) tip = Math.min(tip,
                        saya != null ? saya.sayaTip : originalSaya.valid() ? originalSaya.min : -294);
                float scale = 142 / (42 - tip);
                poses.translate(0, 0, 16);
                poses.mulPose(Axis.ZP.rotationDegrees(135));
                poses.scale(scale, scale, scale);
                poses.translate(-(42 + tip) * .5F, -4, 0);
            }

            Float coveredTo = null;
            if (showSaya) {
                if (saya != null) {
                    // The narrow tail fin is decorative. Leave enough room for a long blade to show.
                    coveredTo = (float) saya.sayaTip;
                } else {
                    var shell = bounds(faces(event.getOriginalModel(), "sheath"));
                    if (shell.valid()) coveredTo = shell.min;
                }
            }
            WavefrontObject guardView = fishGuard(event, guard, group);
            var inheritedBlade = coveredTo == null && blade == null
                    ? mounted(event, namedBlade, LegacySourceMesh.Component.BLADE, group) : null;
            boolean hideBlade = blade != null || inheritedBlade != null;
            boolean hideGuard = guardView != null;
            drawStockFishAssembly(event, group, hideBlade, hideGuard, coveredTo, ordinary, emissive);

            if (blade != null) {
                if (SenbonzakuraDispersedRender.bladeFraction(event.getStack(), target) > .001F) {
                    drawFish(event, blade, "blade", group, coveredTo);
                }
            } else if (inheritedBlade != null
                    && SenbonzakuraDispersedRender.bladeFraction(event.getStack(), target) > .001F) {
                NamedBladeRenderPlan.drawMounted(event, group, LegacySourceMesh.Component.BLADE,
                        namedBlade, inheritedBlade);
            }
            if (hideGuard) drawFishPass(event, guardView, group, guard.texture(), null);
            if (showSaya) {
                if (saya == null) drawNamedOrStockSaya(event, named.saya(), ordinary, emissive);
                else drawFish(event, saya, "saya", "sheath", null);
            }
        } finally {
            poses.popPose();
        }
    }

    private static void drawStockFishAssembly(RenderOverrideEvent event, String group, boolean hideBlade,
            boolean hideGuard, Float coveredTo, ResourceLocation ordinary, ResourceLocation emissive) {
        WavefrontObject source = event.getModel();
        if (!hideBlade && !hideGuard && coveredTo == null) {
            drawFishPass(event, source, group, ordinary, emissive);
            return;
        }
        var key = new FishKey(source, group, hideBlade, hideGuard, coveredTo);
        WavefrontObject filtered = fishView(key, group, () -> {
            List<Face> selected = new ArrayList<>();
            for (Face face : faces(source, group)) {
                if (hideGuard && InventoryGuardGeometry.isGuard(face)) continue;
                if (!DispersedBladeGeometry.steel(face)) selected.add(face);
                else if (!hideBlade) {
                    if (coveredTo == null) selected.add(face);
                    else selected.addAll(clipBefore(face, coveredTo));
                }
            }
            return selected;
        });
        drawFishPass(event, filtered, group, ordinary, emissive);
    }

    private static NamedBladeRenderPlan.Mounted mounted(RenderOverrideEvent event,
            LegacyImprintKind kind, LegacySourceMesh.Component part, String state) {
        return kind == null ? null : NamedBladeRenderPlan.mounted(LegacyRenderSources.resolve(kind),
                part, event.getOriginalModel(), state);
    }

    private static void drawNamedOrStockSaya(RenderOverrideEvent event, LegacyImprintKind kind,
            ResourceLocation ordinary, ResourceLocation emissive) {
        var part = LegacySourceMesh.Component.SAYA;
        var mounted = mounted(event, kind, part, "sheath");
        if (mounted == null) drawFishPass(event, event.getModel(), "sheath", ordinary, emissive);
        else NamedBladeRenderPlan.drawMounted(event, "sheath", part, kind, mounted);
    }

    private static WavefrontObject fishGuard(RenderOverrideEvent event, LegacyImprintKind guard, String group) {
        if (guard == null || "blade_fragment".equals(group)) return null;
        var source = LegacyRenderSources.resolve(guard);
        if (source.mesh() == null) return null;
        return fishView(new FishKey(source.identity(), group, false, false, null), group, () -> {
            var mesh = source.mesh();
            var hilt = LegacyRenderSources.geometry(source, LegacySourceMesh.Component.HILT, "blade").reference();
            var span = mesh.frame.span(hilt);
            if (!span.valid()) return List.of();
            var selected = mesh.clip(hilt, span.min(), span.at(.28F));
            var rotation = new org.joml.Quaternionf(mesh.frame.rotation()).conjugate();
            List<Face> local = new ArrayList<>(selected.size());
            for (Face face : selected) {
                Face fitted = new Face();
                fitted.vertices = java.util.Arrays.stream(face.vertices).map(v -> {
                    var p = rotation.transform(new org.joml.Vector3f(v.x, v.y, v.z));
                    return new Vertex(p.x, p.y, p.z);
                }).toArray(Vertex[]::new);
                fitted.textureCoordinates = face.textureCoordinates;
                local.add(fitted);
            }
            var extent = extent(local);
            if (!extent.valid()) return List.of();
            // Fish lips are fixed at x=-34. Preserve the silhouette around that seat.
            float x = (extent.maxX + extent.minX) * .5F;
            float thickness = 4 / (extent.maxX - extent.minX);
            float radial = Math.min(1.2F, Math.max(.65F, extent.thickness() / 25))
                    * 28 / extent.thickness();
            for (Face face : local) for (Vertex v : face.vertices) {
                v.x = -36 + (v.x - x) * thickness;
                v.y = 4 + (v.y - extent.centerY()) * radial;
                v.z = (v.z - extent.centerZ()) * radial;
            }
            return local;
        });
    }

    private static void drawFish(RenderOverrideEvent event, Length length, String part, String group,
            Float coveredTo) {
        String path = "model/modular/salty_fish/";
        String name = length.part(part);
        WavefrontObject model = BladeModelManager.getInstance().getModel(
                new ResourceLocation("blade_tetra", path + name + ".obj"));
        if (coveredTo != null) {
            if (length.bladeTip >= coveredTo) return;
            WavefrontObject source = model;
            var key = new FishKey(source, group, true, false, coveredTo);
            model = fishView(key, group, () -> faces(source, group).stream()
                    .flatMap(face -> clipBefore(face, coveredTo).stream()).toList());
        }
        String texture = name;
        if ("blade".equals(part)) {
            texture = "fishbone";
        } else if (EnchantmentHelper.getItemEnchantmentLevel(Enchantments.FIRE_ASPECT, event.getStack()) > 0) {
            texture = name + "_cooked";
        }
        drawFishPass(event, model, group, new ResourceLocation("blade_tetra", path + texture + ".png"), null);
    }

    private static List<Face> clipBefore(Face face, float x) {
        return LegacyFaceClipper.clip(List.of(face), vertex -> vertex.x, Float.NEGATIVE_INFINITY, x);
    }

    private static WavefrontObject fishView(FishKey key, String group,
            java.util.function.Supplier<List<Face>> builder) {
        return VIEWS.get(key, ignored -> LegacyRenderSources.view(group, builder.get()));
    }

    private static void drawFishPass(RenderOverrideEvent event, WavefrontObject model, String group,
            ResourceLocation texture, ResourceLocation emissive) {
        if (model == null) return;
        BladeRenderState.resetCol();
        BladeRenderState.renderOverrided(event.getStack(), model, group, texture,
                event.getPoseStack(), event.getBuffer(), event.getPackedLightIn(),
                event.getGetRenderType(), event.isEnableEffect());
        if (emissive != null) {
            BladeRenderState.renderOverridedLuminous(event.getStack(), model, group, emissive,
                    event.getPoseStack(), event.getBuffer(), event.getPackedLightIn());
        }
    }

    private static Extent extent(List<Face> faces) {
        return extentOfVertices(faces.stream().filter(face -> face.vertices != null)
                .flatMap(face -> java.util.Arrays.stream(face.vertices)).toList());
    }

    private static Extent extentOfVertices(List<Vertex> vertices) {
        float ax = Float.POSITIVE_INFINITY, ay = ax, az = ax;
        float bx = Float.NEGATIVE_INFINITY, by = bx, bz = bx;
        for (Vertex v : vertices) {
            ax = Math.min(ax, v.x); bx = Math.max(bx, v.x);
            ay = Math.min(ay, v.y); by = Math.max(by, v.y);
            az = Math.min(az, v.z); bz = Math.max(bz, v.z);
        }
        return new Extent(ax, bx, ay, by, az, bz);
    }
    private record Extent(float minX, float maxX, float minY, float maxY, float minZ, float maxZ) {
        boolean valid() { return Float.isFinite(minX) && maxX > minX && thickness() > .0001F; }
        float centerY() { return (minY + maxY) * .5F; }
        float centerZ() { return (minZ + maxZ) * .5F; }
        float thickness() { return Math.max(maxY - minY, maxZ - minZ); }
    }

    /** The authored atlas islands distinguish the blade, collar, guard and grip on every style. */
    static List<Face> modularFaces(WavefrontObject model, String group, Part part) {
        var mesh = faces(model, group);
        if (mesh.isEmpty() && "item_bladens".equals(group)) mesh = faces(model, "item_blade");
        boolean icon = group.startsWith("item_");
        return switch (part) {
            case BLADE -> filter(mesh, face -> InventoryGuardGeometry.inside(face, 0, 0, 64, 32));
            case TSUBA -> filter(mesh, face -> InventoryGuardGeometry.isGuard(face)
                    || InventoryGuardGeometry.inside(face, 80, 58, 96, 82));
            case TSUKA -> filter(mesh, face -> InventoryGuardGeometry.inside(face, 1, 59, 47, 81));
            case HILT -> filter(mesh, face -> InventoryGuardGeometry.isGuard(face)
                    || InventoryGuardGeometry.inside(face, 1, 59, 47, 81)
                    || InventoryGuardGeometry.inside(face, 80, 58, 96, 82));
            case SAYA -> icon ? filter(mesh, face -> InventoryGuardGeometry.inside(face, 0, 32, 64, 56)) : mesh;
        };
    }

    static List<Face> retainFromX(List<Face> faces, float cutoff) {
        return LegacyFaceClipper.clip(faces, vertex -> vertex.x, cutoff, Float.POSITIVE_INFINITY);
    }

    static boolean hasFaces(WavefrontObject source, String name) {
        for (var group : source.groupObjects) {
            if (group != null && name.equals(group.name) && group.faces != null && !group.faces.isEmpty()) return true;
        }
        return false;
    }

    private static List<Face> faces(WavefrontObject source, String name) {
        List<Face> result = new ArrayList<>();
        for (var group : source.groupObjects) {
            if (group != null && group.faces != null && name.equals(group.name)) result.addAll(group.faces);
        }
        return result;
    }

    private static List<Face> filter(List<Face> input,
            java.util.function.Predicate<Face> predicate) {
        List<Face> output = new ArrayList<>();
        for (Face face : input) {
            if (predicate.test(face)) {
                output.add(face);
            }
        }
        return output;
    }

    private static Bounds bounds(List<Face> faces) {
        float min = Float.POSITIVE_INFINITY;
        float max = Float.NEGATIVE_INFINITY;
        for (Face face : faces) {
            if (face.vertices == null) continue;
            for (Vertex vertex : face.vertices) {
                min = Math.min(min, vertex.x);
                max = Math.max(max, vertex.x);
            }
        }
        return new Bounds(min, max);
    }

    private record Bounds(float min, float max) {
        boolean valid() { return Float.isFinite(min) && Float.isFinite(max) && max > min; }
    }

    public static synchronized void clear() {
        VIEWS.clear();
    }

    private LegacyModelPartRenderer() {
    }
}
