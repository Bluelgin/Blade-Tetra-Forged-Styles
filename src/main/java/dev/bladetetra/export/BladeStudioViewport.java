package dev.bladetetra.export;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.math.Axis;
import com.mojang.logging.LogUtils;
import dev.bladetetra.registry.ModItems;
import mods.flammpfeil.slashblade.client.renderer.model.BladeModelManager;
import mods.flammpfeil.slashblade.client.renderer.model.obj.WavefrontObject;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import mods.flammpfeil.slashblade.client.renderer.util.BladeRenderState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Vector3f;
import org.joml.Matrix4f;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Set;

/** Bakes once per appearance and resource revision, shared by both Studio layouts. */
final class BladeStudioViewport {
    private static final String NAMESPACE = "bt_export_studio_preview";
    private static final ResourceLocation ATLAS = ResourceLocation.fromNamespaceAndPath("blade_tetra", "studio/preview_atlas");
    private static final ResourceLocation PORTRAIT = ResourceLocation.fromNamespaceAndPath("blade_tetra", "studio/preview_portrait");
    private static final Set<String> WORLD_GROUPS = Set.of("blade", "blade_damaged", "sheath");
    private static int resourceRevision;

    private final BladeSealPackets.Preview preview;
    private CustomBackplateImage.Selection backplate;
    private WavefrontObject model;
    private boolean atlasRegistered;
    private boolean portraitRegistered;
    private Component failure;
    private Frame frame;
    private WavefrontObject sourceModel;
    private ResourceLocation sourceTexture;
    private Frame sourceFrame;
    private int sourceRevision = -1;
    private int bakedRevision = -1;
    private float pitch = -12;
    private float yaw = -18;
    private float zoom = 1;
    private boolean drawn;
    private boolean damaged;
    private boolean luminous = true;
    private float roll;
    private float sheathSeparation;
    private int accent = 0xcba66d;
    private int secondary = 0x869bb2;
    private Vector3f bladeTip, bladeRoot;
    private Vector3f screenTip = new Vector3f(), screenRoot = new Vector3f();
    private Vector3f[] bladeSpine, screenSpine;

    BladeStudioViewport(BladeSealPackets.Preview preview) {
        this.preview = preview;
    }

    static void resourcesReloaded() {
        resourceRevision++;
    }

    void prepare(Minecraft minecraft, CustomBackplateImage.Selection selection) {
        if (bakedRevision == resourceRevision && selection.equals(backplate)) return;
        release(minecraft);
        backplate = selection;
        bakedRevision = resourceRevision;
        try {
            var snapshot = BladeSealPackets.decodeSnapshot(preview.projection());
            var projected = new ItemStack(ModItems.MODULAR_SLASHBLADE.get());
            projected.setTag(TagParser.parseTag(preview.appearanceTag()));
            var art = BladeExportArt.bake(NAMESPACE, snapshot, projected, minecraft.getResourceManager(), backplate);
            model = new WavefrontObject(art.model().toString(), new ByteArrayInputStream(art.assets().get(art.model())));
            frame = frameModel(model);
            var bladeFrame = frameGroup(model, Set.of("blade"));
            float mouth = bladeFrame.max.x - (bladeFrame.max.x - bladeFrame.min.x) * .25f;
            if (model.groupObjects.stream().anyMatch(group -> "sheath".equals(group.name) && !group.faces.isEmpty())) {
                mouth = frameGroup(model, Set.of("sheath")).max.x;
            }
            mouth = Math.max(bladeFrame.min.x + (bladeFrame.max.x - bladeFrame.min.x) * .15f,
                    Math.min(mouth, bladeFrame.max.x - (bladeFrame.max.x - bladeFrame.min.x) * .1f));
            prepareSpine(mouth, bladeFrame);
            // Existing effect colour is authoritative; neutral/black colours use a quiet fallback.
            int effect = snapshot.color() & 0xffffff;
            accent = usableColor(effect) ? soften(effect) : 0xd6b37b;
            secondary = 0x869bb2;
            try (var image = NativeImage.read(new ByteArrayInputStream(art.assets().get(art.texture())))) {
                int sampled = sampleColor(image);
                if (usableColor(sampled)) secondary = soften(sampled);
            }
            register(minecraft, ATLAS, art.assets().get(art.texture()));
            atlasRegistered = true;
            byte[] portrait = art.assets().get(ResourceLocation.fromNamespaceAndPath(NAMESPACE, "model/sealed_backplate.png"));
            if (portrait != null) {
                register(minecraft, PORTRAIT, portrait);
                portraitRegistered = true;
            }
            failure = null;
        } catch (IOException | com.mojang.brigadier.exceptions.CommandSyntaxException | RuntimeException error) {
            release(minecraft);
            bakedRevision = resourceRevision;
            LogUtils.getLogger().warn("Could not preview sealed blade", error);
            failure = Component.translatable("blade_tetra.export.blocked", error.getMessage());
        }
    }

    private void register(Minecraft minecraft, ResourceLocation id, byte[] bytes) throws IOException {
        var texture = new DynamicTexture(NativeImage.read(new ByteArrayInputStream(bytes)));
        try {
            minecraft.getTextureManager().register(id, texture);
        } catch (RuntimeException failure) {
            texture.close();
            throw failure;
        }
    }

    void prepareSource(Minecraft minecraft, ItemStack source) {
        if (sourceRevision == resourceRevision) return;
        sourceRevision = resourceRevision;
        sourceModel = null;
        var state = source.getCapability(ItemSlashBlade.BLADESTATE).orElse(null);
        if (state == null) return;
        var modelId = state.getModel().orElse(null);
        var textureId = state.getTexture().orElse(null);
        var resources = minecraft.getResourceManager();
        if (modelId == null || textureId == null || resources.getResource(modelId).isEmpty()
                || resources.getResource(textureId).isEmpty()) return;
        try {
            var nativeModel = BladeModelManager.getInstance().getModel(modelId);
            sourceFrame = frameModel(nativeModel);
            sourceTexture = textureId;
            sourceModel = nativeModel;
        } catch (IOException | RuntimeException error) {
            LogUtils.getLogger().warn("Could not prepare the original blade preview", error);
        }
    }

    boolean sourceReady() {
        return sourceModel != null;
    }

    private record Frame(Vector3f center, float diameter, Vector3f min, Vector3f max) {}

    private static Frame frameModel(WavefrontObject model) throws IOException {
        return frameGroup(model, WORLD_GROUPS);
    }

    private static Frame frameGroup(WavefrontObject model, Set<String> groups) throws IOException {
        var min = new Vector3f(Float.POSITIVE_INFINITY);
        var max = new Vector3f(Float.NEGATIVE_INFINITY);
        for (var group : model.groupObjects) {
            if (!groups.contains(group.name)) continue;
            for (var face : group.faces) {
                for (var vertex : face.vertices) {
                    min.min(new Vector3f(vertex.x, vertex.y, vertex.z));
                    max.max(new Vector3f(vertex.x, vertex.y, vertex.z));
                }
            }
        }
        float diameter = max.distance(min);
        if (!(diameter > 0) || !Float.isFinite(diameter)) throw new IOException("Invalid preview model bounds");
        return new Frame(new Vector3f(min).lerp(max, .5F), diameter, min, max);
    }

    private static boolean usableColor(int rgb) {
        int r = rgb >> 16 & 255, g = rgb >> 8 & 255, b = rgb & 255;
        return Math.max(r, Math.max(g, b)) > 65 && Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b)) > 18;
    }

    private static int soften(int rgb) {
        return (int)((rgb >> 16 & 255) * .4f + 246 * .6f) << 16
                | (int)((rgb >> 8 & 255) * .4f + 220 * .6f) << 8
                | (int)((rgb & 255) * .4f + 177 * .6f);
    }

    /** Intersect the baked blade at fixed stations once, so the sweep follows curved named blades. */
    private void prepareSpine(float mouth, Frame bounds) {
        bladeSpine = new Vector3f[49]; screenSpine = new Vector3f[49];
        for (int i = 0; i < bladeSpine.length; i++) {
            float x = Mth.lerp(i / 48f, mouth, bounds.min.x);
            float minY = Float.POSITIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY;
            float minZ = Float.POSITIVE_INFINITY, maxZ = Float.NEGATIVE_INFINITY;
            for (var group : model.groupObjects) if ("blade".equals(group.name)) for (var face : group.faces) {
                for (int j = 0; j < face.vertices.length; j++) {
                    var a = face.vertices[j]; var b = face.vertices[(j + 1) % face.vertices.length];
                    if (x < Math.min(a.x, b.x) || x > Math.max(a.x, b.x)) continue;
                    float p = Math.abs(a.x - b.x) < .00001f ? 0 : (x - a.x) / (b.x - a.x);
                    float y = Mth.lerp(p, a.y, b.y), z = Mth.lerp(p, a.z, b.z);
                    minY = Math.min(minY, y); maxY = Math.max(maxY, y);
                    minZ = Math.min(minZ, z); maxZ = Math.max(maxZ, z);
                }
            }
            bladeSpine[i] = Float.isFinite(minY) ? new Vector3f(x, (minY + maxY) / 2, (minZ + maxZ) / 2)
                    : new Vector3f(x, bounds.center.y, bounds.center.z);
            screenSpine[i] = new Vector3f();
        }
        bladeRoot = bladeSpine[0]; bladeTip = bladeSpine[48];
    }

    private static int sampleColor(NativeImage image) {
        long r = 0, g = 0, b = 0, count = 0;
        int step = Math.max(1, Math.min(image.getWidth(), image.getHeight()) / 64);
        for (int y = 0; y < image.getHeight(); y += step) for (int x = 0; x < image.getWidth(); x += step) {
            int c = image.getPixelRGBA(x, y);
            int rgb = (c & 255) << 16 | c & 0xff00 | c >> 16 & 255;
            if ((c >>> 24) < 128 || !usableColor(rgb)) continue;
            r += rgb >> 16 & 255; g += rgb >> 8 & 255; b += rgb & 255; count++;
        }
        return count == 0 ? 0 : (int)(r / count) << 16 | (int)(g / count) << 8 | (int)(b / count);
    }

    int accent() { return accent; }
    int secondary() { return secondary; }
    Vector3f screenTip() { return screenTip; }
    Vector3f screenRoot() { return screenRoot; }
    Vector3f[] screenSpine() { return screenSpine; }

    void present(float pitch, float yaw, float roll, float zoom, float separation) {
        this.pitch = pitch; this.yaw = yaw; this.roll = roll; this.zoom = zoom;
        sheathSeparation = separation;
    }

    boolean ready() {
        return atlasRegistered;
    }

    Component failure() {
        return failure;
    }

    void renderPortrait(GuiGraphics graphics, int x, int y) {
        if (portraitRegistered) graphics.blit(PORTRAIT, x, y, 28, 28, 0, 0, 256, 256, 256, 256);
    }

    void rotate(double dx, double dy) {
        yaw = Mth.wrapDegrees(yaw + (float) dx);
        pitch = Mth.wrapDegrees(pitch + (float) dy);
    }

    void zoom(double delta) {
        zoom = Mth.clamp(zoom * (float) Math.pow(1.15, delta), .5F, 3F);
    }

    void resetCamera() {
        pitch = -12;
        yaw = -18;
        zoom = 1;
    }

    void toggleDrawn() {
        drawn = !drawn;
    }

    void setDamaged(boolean damaged) {
        this.damaged = damaged;
    }

    void setLuminous(boolean luminous) {
        this.luminous = luminous;
    }

    void render(GuiGraphics graphics, int left, int top, int right, int bottom) {
        renderFramed(graphics, left, top, right, bottom, frame, null);
    }

    void renderSource(GuiGraphics graphics, int left, int top, int right, int bottom, ItemStack source) {
        renderFramed(graphics, left, top, right, bottom, sourceFrame, source);
    }

    private void renderFramed(GuiGraphics graphics, int left, int top, int right, int bottom,
                              Frame bounds, ItemStack source) {
        graphics.flush();
        graphics.enableScissor(left, top, right, bottom);
        var pose = graphics.pose();
        pose.pushPose();
        try {
            pose.translate((left + right) / 2F, (top + bottom) / 2F, 110);
            var rotation = new Matrix4f().rotateX((float)Math.toRadians(pitch))
                    .rotateY((float)Math.toRadians(yaw)).rotateZ((float)Math.toRadians(roll));
            var lo = new Vector3f(Float.POSITIVE_INFINITY);
            var hi = new Vector3f(Float.NEGATIVE_INFINITY);
            // Fit all projected corners, including the withdrawn sheath, at this exact camera angle.
            for (int i = 0; i < 8; i++) {
                var p = new Vector3f((i & 1) == 0 ? bounds.min.x : bounds.max.x + bounds.diameter * sheathSeparation,
                        (i & 2) == 0 ? bounds.min.y - bounds.diameter * sheathSeparation * .25f : bounds.max.y,
                        (i & 4) == 0 ? bounds.min.z : bounds.max.z).sub(bounds.center);
                rotation.transformPosition(p); lo.min(p); hi.max(p);
            }
            float scale = Math.max(1, Math.min((right - left - 24) / Math.max(.001f, hi.x - lo.x),
                    (bottom - top - 16) / Math.max(.001f, hi.y - lo.y))) * zoom;
            pose.scale(scale, -scale, scale);
            pose.translate(-(lo.x + hi.x) / 2, -(lo.y + hi.y) / 2, 0);
            pose.mulPose(Axis.XP.rotationDegrees(pitch));
            pose.mulPose(Axis.YP.rotationDegrees(yaw));
            pose.mulPose(Axis.ZP.rotationDegrees(roll));
            var center = bounds.center();
            pose.translate(-center.x, -center.y, -center.z);
            if (source == null && bladeTip != null) {
                pose.last().pose().transformPosition(bladeTip, screenTip);
                pose.last().pose().transformPosition(bladeRoot, screenRoot);
                for (int i = 0; i < bladeSpine.length; i++) pose.last().pose().transformPosition(bladeSpine[i], screenSpine[i]);
            }
            renderGroup(graphics, damaged ? "blade_damaged" : "blade", source);
            if (!drawn && !damaged) {
                pose.pushPose();
                pose.translate(bounds.diameter * sheathSeparation, -bounds.diameter * sheathSeparation * .25f, 0);
                renderGroup(graphics, "sheath", source);
                pose.popPose();
            }
            graphics.flush();
        } finally {
            pose.popPose();
            graphics.disableScissor();
        }
    }

    private void renderGroup(GuiGraphics graphics, String group, ItemStack source) {
        var buffer = graphics.bufferSource();
        var pose = graphics.pose();
        if (source != null) {
            BladeRenderState.resetCol();
            BladeRenderState.renderOverrided(source, sourceModel, group, sourceTexture, pose, buffer,
                    LightTexture.FULL_BRIGHT);
            if (luminous) {
                BladeRenderState.renderOverridedLuminous(source, sourceModel, group + "_luminous", sourceTexture,
                        pose, buffer, LightTexture.FULL_BRIGHT);
            }
            return;
        }
        var solid = BladeRenderState.getSlashBladeBlend(ATLAS);
        model.tessellateOnly(buffer.getBuffer(solid), pose, LightTexture.FULL_BRIGHT, 0xffffffff, group);
        buffer.endBatch(solid);
        if (luminous) {
            var glow = BladeRenderState.getSlashBladeBlendLuminous(ATLAS);
            model.tessellateOnly(buffer.getBuffer(glow), pose, LightTexture.FULL_BRIGHT, 0xffffffff, group + "_luminous");
            buffer.endBatch(glow);
        }
    }

    void release(Minecraft minecraft) {
        if (atlasRegistered) minecraft.getTextureManager().release(ATLAS);
        if (portraitRegistered) minecraft.getTextureManager().release(PORTRAIT);
        atlasRegistered = false;
        portraitRegistered = false;
        model = null;
        frame = null;
        bakedRevision = -1;
    }
}
