package dev.bladetetra.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import dev.bladetetra.forging.NamedLegacyParts;
import dev.bladetetra.forging.LegacyImprintKind;
import dev.bladetetra.forging.LegacyCalibration;
import dev.bladetetra.forging.LegacyCalibrationProfile;
import dev.bladetetra.forging.LegacyModelAdapter;
import mods.flammpfeil.slashblade.client.renderer.model.BladeModelManager;
import mods.flammpfeil.slashblade.client.renderer.model.obj.Face;
import mods.flammpfeil.slashblade.client.renderer.model.obj.GroupObject;
import mods.flammpfeil.slashblade.client.renderer.model.obj.TextureCoordinate;
import mods.flammpfeil.slashblade.client.renderer.model.obj.Vertex;
import mods.flammpfeil.slashblade.client.renderer.model.obj.WavefrontObject;
import mods.flammpfeil.slashblade.client.renderer.util.BladeRenderState;
import mods.flammpfeil.slashblade.event.client.RenderOverrideEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Runtime view over SlashBlade's own OBJ resources. Faces are selected from
 * the source model and rebuilt as immutable Wavefront views whose final geometry
 * already exists during construction, so constructor-time render optimizers can
 * safely snapshot/bake them.
 */
public final class LegacyModelPartRenderer {
    private static final ResourceLocation FOX_MODEL = ResourceLocation.fromNamespaceAndPath(
            "slashblade", "model/named/sange/sange.obj");
    private static final ResourceLocation BLACK_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "slashblade", "model/named/sange/black.png");
    private static final ResourceLocation WHITE_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "slashblade", "model/named/sange/white.png");
    private static final ResourceLocation IRON_MODEL = ResourceLocation.fromNamespaceAndPath(
            "blade_tetra", "model/modular/alpha9/katana_basic_simple_stable.obj");
    private static final ResourceLocation IRON_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "blade_tetra", "model/modular/standard_256.png");
    private static final int MAX_CACHE = 160;
    private static final Map<String, WavefrontObject> VIEWS = new LinkedHashMap<>(64, .75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, WavefrontObject> eldest) {
            return size() > MAX_CACHE;
        }
    };

    private enum Part { BLADE, TSUBA, TSUKA, SAYA, HILT }
    private record MountKey(WavefrontObject source, WavefrontObject target, Part part,
            float tc, float tr, float hc, float hr) {}
    private static final Map<MountKey, Mount> MOUNTS = new LinkedHashMap<>(64, .75F, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<MountKey, Mount> entry) {
            return size() > MAX_CACHE;
        }
    };
    private static final Map<WavefrontObject, Extent> BLADE_EXTENTS = new LinkedHashMap<>(64, .75F, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<WavefrontObject, Extent> entry) {
            return size() > MAX_CACHE;
        }
    };
    private static final Map<WavefrontObject, LegacyModelAdapter> MODEL_ADAPTERS = new WeakHashMap<>();

    public static boolean supports(String target) {
        return "blade".equals(target) || "blade_damaged".equals(target)
                || "blade_fragment".equals(target)
                || "sheath".equals(target) || "item_blade".equals(target)
                || "item_bladens".equals(target) || "item_damaged".equals(target);
    }

    public static void render(RenderOverrideEvent event, NamedLegacyParts parts,
            ResourceLocation ordinaryTexture, ResourceLocation ordinaryEmissive) {
        String target = event.getOriginalTarget();
        if (!supports(target)) {
            return;
        }
        // Gameplay identity is semantic. Rendering applies the optional client geometry
        // capability at this boundary so an unsupported provider falls back visually
        // without changing affinity, durability or orthodox ability ownership.
        parts = NamedLegacyParts.visualFromStack(event.getStack());
        event.setCanceled(true);
        if ("sheath".equals(target)) {
            renderPart(event, parts.saya(), Part.SAYA, target,
                    ordinaryTexture, ordinaryEmissive);
            return;
        }
        if (target.startsWith("blade")) {
            renderPart(event, null, Part.BLADE, target,
                    ordinaryTexture, ordinaryEmissive);
            if ("blade_fragment".equals(target)) {
                return;
            }
            renderPart(event, parts.tsuba(), Part.TSUBA, target,
                    ordinaryTexture, ordinaryEmissive);
            renderPart(event, parts.tsuka(), Part.TSUKA, target,
                    ordinaryTexture, ordinaryEmissive);
            return;
        }
        renderPart(event, null, Part.BLADE, target,
                ordinaryTexture, ordinaryEmissive);
        renderPart(event, parts.tsuba(), Part.TSUBA, target,
                ordinaryTexture, ordinaryEmissive);
        renderPart(event, parts.tsuka(), Part.TSUKA, target,
                ordinaryTexture, ordinaryEmissive);
        if (!"item_bladens".equals(target)) {
            renderPart(event, parts.saya(), Part.SAYA, target,
                    ordinaryTexture, ordinaryEmissive);
        }
    }

    private static void renderPart(RenderOverrideEvent event,
            LegacyImprintKind kind, Part part, String target,
            ResourceLocation ordinaryTexture, ResourceLocation ordinaryEmissive) {
        NamedLegacyParts installedParts = NamedLegacyParts.visualFromStack(event.getStack());
        if (part == Part.TSUKA && installedParts.tsuba() != null) return;
        if (part == Part.TSUBA && kind != null) part = Part.HILT;
        boolean inherited = kind != null;
        WavefrontObject source = inherited
                ? BladeModelManager.getInstance().getModel(kind.model())
                : event.getOriginalModel();
        if (inherited) register(source, kind.model());
        LegacyCalibrationProfile profile = inherited
                ? kind.visualProfile()
                : LegacyCalibrationProfile.DEFAULT;
        WavefrontObject view = view(source, target, part, profile);
        if (view == null) {
            return;
        }
        ResourceLocation texture = inherited ? kind.texture() : ordinaryTexture;
        PoseStack poses = event.getPoseStack();
        poses.pushPose();
        if (inherited && !target.startsWith("item_")) {
            if (part == Part.SAYA) {
                // Restore the previous origin-preserving uniform sheath fit. Never recenter Y/Z.
                if (!kind.id().startsWith("fox_")) fitLegacySaya(poses, source, event.getOriginalModel());
            } else if (part == Part.HILT) {
                applyMount(poses, source, event.getOriginalModel(), profile, part, source, profile);
            }
        }
        try {
            BladeRenderState.resetCol();
            BladeRenderState.renderOverrided(event.getStack(), view, target, texture,
                    poses, event.getBuffer(), event.getPackedLightIn(),
                    event.getGetRenderType(), event.isEnableEffect());
            if (!inherited && ordinaryEmissive != null) {
                BladeRenderState.renderOverridedLuminous(event.getStack(), view, target,
                        ordinaryEmissive, poses, event.getBuffer(), event.getPackedLightIn());
            }
        } finally {
            poses.popPose();
            BladeRenderState.resetCol();
        }
    }

    private static LegacyCalibrationProfile.PartTransform transform(
            LegacyCalibrationProfile profile, Part part) {
        return switch (part) {
            case TSUBA -> profile.tsubaTransform();
            case TSUKA -> profile.tsukaTransform();
            case SAYA -> profile.sayaTransform();
            default -> LegacyCalibrationProfile.IDENTITY;
        };
    }

    private static void fitLegacySaya(PoseStack poses, WavefrontObject source, WavefrontObject target) {
        Bounds from = bounds(faces(source, "sheath")), to = bounds(faces(target, "sheath"));
        if (!from.valid() || !to.valid()) return;
        float scale = (to.max - to.min) / (from.max - from.min);
        poses.translate(to.max - from.max * scale, 0, 0);
        poses.scale(scale, scale, scale);
    }

    private static void applyTransform(PoseStack poses,
            LegacyCalibrationProfile.PartTransform transform, boolean flipped) {
        float direction = flipped ? -1.0F : 1.0F;
        poses.translate(transform.offsetX() * 40.0F,
                transform.offsetY() * 40.0F, 0.0F);
        poses.mulPose(Axis.ZP.rotationDegrees(transform.rotation()));
        poses.scale(transform.scale() * direction, transform.scale(), transform.scale());
    }

    /** Mount actual cut geometry, never a fraction of the source's complete sword length. */
    private static void applyMount(PoseStack poses, WavefrontObject source, WavefrontObject target,
            LegacyCalibrationProfile profile, Part part, WavefrontObject guard,
            LegacyCalibrationProfile guardProfile) {
        Mount mount = mount(source, target, profile, part);
        if (mount == null) return;
        var tweak = transform(profile, part).normalized();
        float x = mount.targetX(), y = mount.targetY(), z = mount.targetZ();
        if (part == Part.TSUKA) {
            Mount guardMount = mount(guard, target, guardProfile, Part.TSUBA);
            if (guardMount != null) {
                var rear = guardMount.point(guardMount.sourceMaxX(), guardMount.sourceY(),
                        guardMount.sourceZ(), guardProfile.tsubaTransform());
                x = rear.x; y = rear.y; z = rear.z;
            }
        }
        poses.translate(x + tweak.offsetX() * 40, y + tweak.offsetY() * 40, z);
        poses.mulPose(Axis.ZP.rotationDegrees(tweak.rotation()));
        poses.scale(mount.lengthScale() * tweak.scale() * tweak.length(),
                mount.widthScale() * tweak.scale() * tweak.width(),
                mount.widthScale() * tweak.scale() * tweak.width());
        poses.translate(-mount.sourceX(), -mount.sourceY(), -mount.sourceZ());
    }

    private static void applyBladeTransform(PoseStack poses, WavefrontObject model, LegacyCalibrationProfile profile) {
        Extent blade = bladeExtent(model);
        if (!blade.valid()) return;
        var tweak = profile.normalized().bladeTransform();
        poses.translate(blade.maxX, blade.centerY(), blade.centerZ());
        poses.scale(tweak.length(), tweak.width(), tweak.width());
        poses.translate(-blade.maxX, -blade.centerY(), -blade.centerZ());
    }

    private static synchronized Mount mount(WavefrontObject source, WavefrontObject target,
            LegacyCalibrationProfile profile, Part part) {
        MountKey key = new MountKey(source, target, part, profile.tsubaCenter(), profile.tsubaRadius(),
                profile.tsukaCenter(), profile.tsukaRadius());
        if (MOUNTS.containsKey(key)) return MOUNTS.get(key);
        String group = part == Part.SAYA ? "sheath" : "blade";
        Extent from = extent(select(source, group, part, profile));
        Extent to = extent(select(target, group, part, LegacyCalibrationProfile.DEFAULT));
        if (!from.valid() || !to.valid()) return null;
        float sourceX = part == Part.SAYA ? from.maxX : from.minX;
        float targetX = part == Part.SAYA ? to.maxX : to.minX;
        Mount result = new Mount(sourceX, from.centerY(), from.centerZ(), from.maxX,
                targetX, to.centerY(), to.centerZ(),
                (to.maxX - to.minX) / (from.maxX - from.minX),
                part == Part.HILT ? (to.maxX - to.minX) / (from.maxX - from.minX)
                        : to.thickness() / from.thickness());
        MOUNTS.put(key, result);
        return result;
    }

    private static synchronized Extent bladeExtent(WavefrontObject model) {
        return BLADE_EXTENTS.computeIfAbsent(model,
                m -> extent(select(m, "blade", Part.BLADE, LegacyCalibrationProfile.DEFAULT)));
    }

    public static Mount inspectMount(WavefrontObject source, WavefrontObject target,
            LegacyCalibrationProfile profile, String part) {
        return mount(source, target, profile, Part.valueOf(part));
    }

    public record Mount(float sourceX, float sourceY, float sourceZ, float sourceMaxX,
            float targetX, float targetY, float targetZ, float lengthScale, float widthScale) {
        public org.joml.Vector3f point(float x, float y, float z, LegacyCalibrationProfile.PartTransform tweak) {
            tweak = tweak.normalized();
            return new org.joml.Matrix4f().translation(targetX + tweak.offsetX() * 40,
                    targetY + tweak.offsetY() * 40, targetZ)
                    .rotateZ((float) Math.toRadians(tweak.rotation()))
                    .scale(lengthScale * tweak.scale() * tweak.length(),
                            widthScale * tweak.scale() * tweak.width(), widthScale * tweak.scale() * tweak.width())
                    .translate(-sourceX, -sourceY, -sourceZ).transformPosition(new org.joml.Vector3f(x, y, z));
        }
    }

    private static Extent extent(List<Face> faces) {
        float ax = Float.POSITIVE_INFINITY, ay = ax, az = ax;
        float bx = Float.NEGATIVE_INFINITY, by = bx, bz = bx;
        for (Face face : faces) if (face.vertices != null) for (Vertex v : face.vertices) {
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

    private static ResourceLocation texture(LegacyImprintKind kind) {
        return kind.texture();
    }

    private static synchronized WavefrontObject view(WavefrontObject source,
            String target, Part part, LegacyCalibrationProfile profile) {
        String key = System.identityHashCode(source) + "|" + target + "|" + part
                + "|" + Float.floatToIntBits(profile.tsubaCenter())
                + "|" + Float.floatToIntBits(profile.tsubaRadius())
                + "|" + Float.floatToIntBits(profile.tsukaCenter())
                + "|" + Float.floatToIntBits(profile.tsukaRadius());
        if (VIEWS.containsKey(key)) {
            return VIEWS.get(key);
        }
        List<Face> faces = select(source, target, part, profile);
        if (faces.isEmpty()) {
            VIEWS.put(key, null);
            return null;
        }
        try {
            WavefrontObject result = RuntimeWavefrontViewFactory.create(target, faces);
            VIEWS.put(key, result);
            return result;
        } catch (RuntimeException exception) {
            VIEWS.put(key, null);
            return null;
        }
    }

    private static List<Face> select(WavefrontObject source, String target,
            Part part, LegacyCalibrationProfile profile) {
        if (target.startsWith("item_")) {
            if (part == Part.HILT) {
                List<Face> combined = new ArrayList<>(selectIcon(source, target, Part.TSUBA, profile));
                combined.addAll(selectIcon(source, target, Part.TSUKA, profile));
                return combined;
            }
            return selectIcon(source, target, part, profile);
        }
        if (part == Part.SAYA) {
            return faces(source, "sheath");
        }
        if (part == Part.HILT) {
            List<Face> handle = faces(source, "handle");
            if (!handle.isEmpty()) return handle;
            // One cut separates the complete hilt from the blade, never guard from grip.
            Bounds full = bounds(faces(source, "blade"));
            return full.valid() ? clip(faces(source, "blade"), full, profile.tsubaMin(), 1) : List.of();
        }
        Bounds axis = bounds(faces(source, "blade"));
        if (!axis.valid()) {
            return List.of();
        }
        if (part == Part.BLADE) {
            return clip(faces(source, target), axis, 0.0F,
                    Math.min(profile.tsubaMin(), profile.tsukaMin()));
        }
        List<Face> handle = faces(source, "handle");
        List<Face> candidates = handle.isEmpty() ? faces(source, target) : handle;
        if (part == Part.TSUBA) {
            return clip(candidates, axis, profile.tsubaMin(), profile.tsubaMax());
        }
        List<Face> result = new ArrayList<>();
        if (profile.tsukaMin() < profile.tsubaMin()) {
            result.addAll(clip(candidates, axis, profile.tsukaMin(),
                    Math.min(profile.tsukaMax(), profile.tsubaMin())));
        }
        if (profile.tsukaMax() > profile.tsubaMax()) {
            result.addAll(clip(candidates, axis,
                    Math.max(profile.tsukaMin(), profile.tsubaMax()), profile.tsukaMax()));
        }
        return result;
    }

    /** Clips source polygons in memory so a large face cannot leak across a selection edge. */
    private static List<Face> clip(List<Face> input, Bounds axis,
            float normalizedMin, float normalizedMax) {
        if (normalizedMax <= normalizedMin) return List.of();
        float minX = axis.min + (axis.max - axis.min) * normalizedMin;
        float maxX = axis.min + (axis.max - axis.min) * normalizedMax;
        List<Face> output = new ArrayList<>();
        for (Face face : input) {
            List<ClipVertex> polygon = polygon(face);
            polygon = clipPlane(polygon, minX, true);
            polygon = clipPlane(polygon, maxX, false);
            if (polygon.size() < 3) continue;
            for (int index = 1; index + 1 < polygon.size(); index++) {
                output.add(face(polygon.get(0), polygon.get(index), polygon.get(index + 1)));
            }
        }
        return output;
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
            result.add(new ClipVertex(face.vertices[index], normal, uv));
        }
        return result;
    }

    private static List<ClipVertex> clipPlane(List<ClipVertex> input,
            float boundary, boolean keepGreater) {
        if (input.isEmpty()) return input;
        List<ClipVertex> output = new ArrayList<>();
        ClipVertex previous = input.get(input.size() - 1);
        boolean previousInside = keepGreater
                ? previous.position.x >= boundary : previous.position.x <= boundary;
        for (ClipVertex current : input) {
            boolean currentInside = keepGreater
                    ? current.position.x >= boundary : current.position.x <= boundary;
            if (currentInside != previousInside) {
                float denominator = current.position.x - previous.position.x;
                float amount = Math.abs(denominator) < 0.000001F
                        ? 0.0F : (boundary - previous.position.x) / denominator;
                output.add(interpolate(previous, current, amount));
            }
            if (currentInside) output.add(current);
            previous = current;
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

    /** Renders the exact face ownership used by the runtime renderer. */
    public static void renderCalibrationPreview(PoseStack poses,
            MultiBufferSource buffer, LegacyCalibrationProfile profile,
            ResourceLocation modelLocation, ResourceLocation texture, int packedLight) {
        WavefrontObject source = BladeModelManager.getInstance().getModel(modelLocation);
        register(source, modelLocation);
        WavefrontObject tsuba = view(source, "blade", Part.TSUBA, profile);
        WavefrontObject tsuka = view(source, "blade", Part.TSUKA, profile);
        try {
            BladeRenderState.setCol(0x553F3B42, false);
            BladeRenderState.renderOverrided(ItemStack.EMPTY, source, "blade", texture,
                    poses, buffer, packedLight);
            if (tsuba != null) {
                BladeRenderState.setCol(0xFFE8A629, false);
                BladeRenderState.renderOverrided(ItemStack.EMPTY, tsuba, "blade", texture,
                        poses, buffer, packedLight);
            }
            if (tsuka != null) {
                BladeRenderState.setCol(0xFF75B260, false);
                BladeRenderState.renderOverrided(ItemStack.EMPTY, tsuka, "blade", texture,
                        poses, buffer, packedLight);
            }
        } finally {
            BladeRenderState.resetCol();
        }
    }

    /** Uses the same clipping and transforms as live blades, mounted on our iron katana. */
    public static void renderAssemblyPreview(PoseStack poses, MultiBufferSource buffer,
            LegacyCalibrationProfile profile, LegacyImprintKind kind, ResourceLocation foxTexture,
            int packedLight, boolean foxGrip, boolean sheathed) {
        WavefrontObject iron = BladeModelManager.getInstance().getModel(IRON_MODEL);
        WavefrontObject fox = BladeModelManager.getInstance().getModel(kind.model());
        register(fox, kind.model());
        poses.pushPose();
        applyBladeTransform(poses, iron, profile);
        if (!sheathed)
        renderPreviewPart(poses, buffer, view(iron, "blade", Part.BLADE,
                LegacyCalibrationProfile.DEFAULT), "blade", IRON_TEXTURE, packedLight,
                LegacyCalibrationProfile.IDENTITY, false);
        poses.popPose();
        if (sheathed) renderAssemblySayaPreview(poses, buffer, profile, kind, packedLight);
        if (!foxGrip) {
            poses.pushPose();
            applyMount(poses, iron, iron, LegacyCalibrationProfile.DEFAULT, Part.TSUKA, fox, profile);
            renderPreviewPart(poses, buffer, view(iron, "blade", Part.TSUKA,
                    LegacyCalibrationProfile.DEFAULT), "blade", IRON_TEXTURE, packedLight,
                    LegacyCalibrationProfile.IDENTITY, false);
            poses.popPose();
        }
        renderMountedPreview(poses, buffer, fox, iron, Part.TSUBA, profile, kind, packedLight);
        if (foxGrip) {
            renderMountedPreview(poses, buffer, fox, iron, Part.TSUKA, profile, kind, packedLight);
        }
    }

    private static void renderMountedPreview(PoseStack poses, MultiBufferSource buffer,
            WavefrontObject source, WavefrontObject target, Part part, LegacyCalibrationProfile profile,
            LegacyImprintKind kind, int light) {
        poses.pushPose();
        try {
            applyMount(poses, source, target, profile, part, source, profile);
            String group = part == Part.SAYA ? "sheath" : "blade";
            renderPreviewPart(poses, buffer, view(source, group, part, profile), group,
                    kind.texture(), light, LegacyCalibrationProfile.IDENTITY, false);
        } finally { poses.popPose(); }
    }

    public static void renderAssemblySayaPreview(PoseStack poses, MultiBufferSource buffer,
            LegacyCalibrationProfile profile, LegacyImprintKind kind, int light) {
        WavefrontObject source = BladeModelManager.getInstance().getModel(kind.model());
        register(source, kind.model());
        renderMountedPreview(poses, buffer, source, BladeModelManager.getInstance().getModel(IRON_MODEL),
                Part.SAYA, profile, kind, light);
    }

    public static float[] previewBounds(ResourceLocation modelLocation) {
        WavefrontObject model = BladeModelManager.getInstance().getModel(modelLocation);
        register(model, modelLocation);
        Bounds axis = bounds(faces(model, "blade"));
        return axis.valid() ? new float[]{(axis.min + axis.max) * .5F, 206F / (axis.max - axis.min)}
                : new float[]{0F, .62F};
    }

    public static float[] ironRoot() {
        Extent blade = bladeExtent(BladeModelManager.getInstance().getModel(IRON_MODEL));
        return new float[]{blade.maxX, blade.centerY()};
    }

    private static void renderPreviewPart(PoseStack poses, MultiBufferSource buffer,
            WavefrontObject model, String target, ResourceLocation texture, int packedLight,
            LegacyCalibrationProfile.PartTransform transform, boolean flipped) {
        if (model == null) return;
        poses.pushPose();
        applyTransform(poses, transform, flipped);
        try {
            BladeRenderState.resetCol();
            BladeRenderState.renderOverrided(ItemStack.EMPTY, model, target, texture,
                    poses, buffer, packedLight);
        } finally {
            poses.popPose();
            BladeRenderState.resetCol();
        }
    }

    public static Validation validate(ResourceLocation modelLocation, LegacyCalibrationProfile profile) {
        WavefrontObject source = BladeModelManager.getInstance().getModel(modelLocation);
        register(source, modelLocation);
        return validateModel(source, profile);
    }

    public static boolean supportsAutomaticHilt(ResourceLocation model, LegacyCalibrationProfile profile) {
        WavefrontObject source = BladeModelManager.getInstance().getModel(model);
        register(source, model);
        return supportsAutomaticHiltModel(source, profile);
    }

    public static boolean supportsAutomaticHiltModel(WavefrontObject model, LegacyCalibrationProfile profile) {
        return extent(select(model, "blade", Part.HILT, profile)).valid()
                && extent(faces(model, "sheath")).valid();
    }

    public static Validation validateModel(WavefrontObject source, LegacyCalibrationProfile profile) {
        int tsuba = select(source, "blade", Part.TSUBA, profile).size();
        int tsuka = select(source, "blade", Part.TSUKA, profile).size();
        int saya = select(source, "sheath", Part.SAYA, profile).size();
        float overlap = Math.max(0.0F, Math.min(profile.tsubaMax(), profile.tsukaMax())
                - Math.max(profile.tsubaMin(), profile.tsukaMin()));
        boolean valid = tsuba >= 2 && tsuka >= 2 && saya >= 2
                && overlap <= Math.min(profile.tsubaRadius(), profile.tsukaRadius()) * .35F;
        return new Validation(tsuba, tsuka, saya, overlap > 0.001F, valid);
    }

    public record Validation(int tsubaFaces, int tsukaFaces, int sayaFaces,
            boolean overlaps, boolean valid) {
    }

    private static List<Face> selectIcon(WavefrontObject source, String target,
            Part requested, LegacyCalibrationProfile profile) {
        List<Face> icon = faces(source, target);
        if (icon.isEmpty() && "item_bladens".equals(target)) {
            icon = faces(source, "item_blade");
        }
        if (icon.isEmpty()) {
            return List.of();
        }
        EnumMap<Part, List<UvPoint>> samples = uvSamples(source, profile);
        return filter(icon, face -> closestPart(uv(face), samples) == requested);
    }

    private static EnumMap<Part, List<UvPoint>> uvSamples(WavefrontObject source,
            LegacyCalibrationProfile profile) {
        EnumMap<Part, List<UvPoint>> result = new EnumMap<>(Part.class);
        for (Part part : Part.values()) {
            if (part == Part.HILT) continue;
            String target = part == Part.SAYA ? "sheath" : "blade";
            List<Face> selected = select(source, target, part, profile);
            result.put(part, selected.stream().map(LegacyModelPartRenderer::uv).toList());
        }
        return result;
    }

    private static Part closestPart(UvPoint point, EnumMap<Part, List<UvPoint>> samples) {
        Part winner = Part.BLADE;
        double best = Double.MAX_VALUE;
        for (Part part : Part.values()) {
            for (UvPoint sample : samples.getOrDefault(part, List.of())) {
                double distance = point.distanceSquared(sample);
                if (distance < best) {
                    best = distance;
                    winner = part;
                }
            }
        }
        return winner;
    }

    private static UvPoint uv(Face face) {
        if (face.textureCoordinates == null || face.textureCoordinates.length == 0) {
            return new UvPoint(0.0F, 0.0F);
        }
        float u = 0.0F;
        float v = 0.0F;
        for (TextureCoordinate coordinate : face.textureCoordinates) {
            u += coordinate.u;
            v += coordinate.v;
        }
        return new UvPoint(u / face.textureCoordinates.length,
                v / face.textureCoordinates.length);
    }

    private static List<Face> faces(WavefrontObject source, String name) {
        LegacyModelAdapter adapter = MODEL_ADAPTERS.getOrDefault(source, LegacyModelAdapter.STANDARD);
        List<String> aliases = switch (name) {
            case "blade" -> adapter.bladeGroups();
            case "handle" -> adapter.hiltGroups();
            case "sheath" -> adapter.sayaGroups();
            default -> List.of(name);
        };
        List<Face> result = new ArrayList<>();
        for (GroupObject group : source.groupObjects) {
            if (aliases.contains(group.name)) result.addAll(group.faces);
        }
        return result;
    }

    private static synchronized void register(WavefrontObject model, ResourceLocation location) {
        MODEL_ADAPTERS.put(model, LegacyModelAdapter.resolve(location));
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

    private static float normalizedX(Face face, Bounds bounds) {
        if (face.vertices == null || face.vertices.length == 0) return 0.0F;
        float x = 0.0F;
        for (Vertex vertex : face.vertices) x += vertex.x;
        return (x / face.vertices.length - bounds.min) / (bounds.max - bounds.min);
    }

    private record Bounds(float min, float max) {
        boolean valid() { return Float.isFinite(min) && Float.isFinite(max) && max > min; }
    }

    private record UvPoint(float u, float v) {
        double distanceSquared(UvPoint other) {
            double du = u - other.u;
            double dv = v - other.v;
            return du * du + dv * dv;
        }
    }

    private record ClipVertex(Vertex position, Vertex normal, TextureCoordinate uv) {
    }

    public static synchronized void clear() {
        VIEWS.clear();
        MOUNTS.clear();
        BLADE_EXTENTS.clear();
        MODEL_ADAPTERS.clear();
    }

    private LegacyModelPartRenderer() {
    }
}
