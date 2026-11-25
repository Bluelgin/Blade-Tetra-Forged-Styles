package dev.bladetetra.export;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.math.Axis;
import com.mojang.logging.LogUtils;
import dev.bladetetra.registry.ModItems;
import mods.flammpfeil.slashblade.client.renderer.model.obj.WavefrontObject;
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
    private Vector3f center;
    private float diameter;
    private int bakedRevision = -1;
    private float pitch = -12;
    private float yaw = -18;
    private float zoom = 1;
    private boolean drawn;
    private boolean damaged;
    private boolean luminous = true;

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
            frameModel();
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

    private void frameModel() throws IOException {
        var min = new Vector3f(Float.POSITIVE_INFINITY);
        var max = new Vector3f(Float.NEGATIVE_INFINITY);
        for (var group : model.groupObjects) {
            if (!WORLD_GROUPS.contains(group.name)) continue;
            for (var face : group.faces) {
                for (var vertex : face.vertices) {
                    min.min(new Vector3f(vertex.x, vertex.y, vertex.z));
                    max.max(new Vector3f(vertex.x, vertex.y, vertex.z));
                }
            }
        }
        diameter = max.distance(min);
        if (!(diameter > 0) || !Float.isFinite(diameter)) throw new IOException("Invalid preview model bounds");
        center = min.lerp(max, .5F);
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
        graphics.flush();
        graphics.enableScissor(left, top, right, bottom);
        var pose = graphics.pose();
        pose.pushPose();
        try {
            pose.translate((left + right) / 2F, (top + bottom) / 2F, 110);
            float scale = Math.max(1, Math.min(right - left - 12, bottom - top - 8)) * zoom / diameter;
            pose.scale(scale, -scale, scale);
            pose.mulPose(Axis.XP.rotationDegrees(pitch));
            pose.mulPose(Axis.YP.rotationDegrees(yaw));
            pose.translate(-center.x, -center.y, -center.z);
            if (drawn || damaged) {
                renderGroup(graphics, damaged ? "blade_damaged" : "blade");
            } else {
                renderGroup(graphics, "blade");
                renderGroup(graphics, "sheath");
            }
            graphics.flush();
        } finally {
            pose.popPose();
            graphics.disableScissor();
        }
    }

    private void renderGroup(GuiGraphics graphics, String group) {
        var buffer = graphics.bufferSource();
        var pose = graphics.pose();
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
        bakedRevision = -1;
    }
}
