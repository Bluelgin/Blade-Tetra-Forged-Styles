package dev.bladetetra.export;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.math.Axis;
import com.mojang.logging.LogUtils;
import dev.bladetetra.registry.ModItems;
import mods.flammpfeil.slashblade.client.renderer.model.obj.WavefrontObject;
import mods.flammpfeil.slashblade.client.renderer.util.BladeRenderState;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3f;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Set;

/** Compares the source inventory icon with the actual sealed OBJ, atlas and portrait. */
final class BladeStudioPreviewScreen extends Screen {
    private static final String NAMESPACE = "bt_export_studio_preview";
    private static final ResourceLocation ATLAS = ResourceLocation.fromNamespaceAndPath("blade_tetra", "studio/preview_atlas");
    private static final ResourceLocation PORTRAIT = ResourceLocation.fromNamespaceAndPath("blade_tetra", "studio/preview_portrait");
    private static final Set<String> WORLD_GROUPS = Set.of("blade", "blade_damaged", "sheath");
    private static int resourceRevision;

    private final BladeSealScreen editor;
    private final BladeSealPackets.Preview preview;
    private final ItemStack source;
    private final CustomBackplateImage.Selection backplate;
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
    private int paneLeft;
    private int paneRight;
    private int paneBottom;

    BladeStudioPreviewScreen(BladeSealScreen editor, BladeSealPackets.Preview preview, ItemStack source,
                             CustomBackplateImage.Selection backplate) {
        super(Component.translatable("blade_tetra.export.preview"));
        this.editor = editor;
        this.preview = preview;
        this.source = source.copy();
        this.backplate = backplate;
    }

    static void resourcesReloaded() {
        resourceRevision++;
    }

    @Override
    protected void init() {
        paneLeft = 12;
        paneRight = width - 12;
        paneBottom = height - 65;
        int buttonWidth = (paneRight - paneLeft - 8) / 3;
        addRenderableWidget(Button.builder(state(drawn ? "drawn" : "sheathed"), button -> {
            drawn = !drawn;
            button.setMessage(state(drawn ? "drawn" : "sheathed"));
        }).bounds(paneLeft, height - 57, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(state(damaged ? "damaged" : "healthy"), button -> {
            damaged = !damaged;
            button.setMessage(state(damaged ? "damaged" : "healthy"));
        }).bounds(paneLeft + buttonWidth + 4, height - 57, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(state(luminous ? "glow_on" : "glow_off"), button -> {
            luminous = !luminous;
            button.setMessage(state(luminous ? "glow_on" : "glow_off"));
        }).bounds(paneRight - buttonWidth, height - 57, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(state("reset"), button -> {
            pitch = -12;
            yaw = -18;
            zoom = 1;
        }).bounds(paneLeft, height - 29, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, button -> onClose())
                .bounds(paneRight - buttonWidth, height - 29, buttonWidth, 20).build());
    }

    private static Component state(String label) {
        return Component.translatable("blade_tetra.export.preview." + label);
    }

    @Override
    public void tick() {
        if (!editor.workbenchOpen()) {
            minecraft.setScreen(null);
            return;
        }
        if (bakedRevision == resourceRevision) return;
        bakedRevision = resourceRevision;
        releaseTextures();
        try {
            var snapshot = BladeSealPackets.decodeSnapshot(preview.projection());
            var projected = new ItemStack(ModItems.MODULAR_SLASHBLADE.get());
            projected.setTag(TagParser.parseTag(preview.appearanceTag()));
            var art = BladeExportArt.bake(NAMESPACE, snapshot, projected, minecraft.getResourceManager(), backplate);
            model = new WavefrontObject(art.model().toString(), new ByteArrayInputStream(art.assets().get(art.model())));
            frameModel();
            register(ATLAS, art.assets().get(art.texture()));
            atlasRegistered = true;
            byte[] portrait = art.assets().get(ResourceLocation.fromNamespaceAndPath(NAMESPACE, "model/sealed_backplate.png"));
            if (portrait != null) {
                register(PORTRAIT, portrait);
                portraitRegistered = true;
            }
            failure = null;
        } catch (IOException | com.mojang.brigadier.exceptions.CommandSyntaxException | RuntimeException error) {
            releaseTextures();
            LogUtils.getLogger().warn("Could not preview sealed blade", error);
            failure = Component.translatable("blade_tetra.export.blocked", error.getMessage());
        }
    }

    private void register(ResourceLocation id, byte[] bytes) throws IOException {
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

    boolean hasBakedModel() {
        return atlasRegistered;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        renderBackground(graphics);
        graphics.drawCenteredString(font, title, width / 2, 10, 0xffe7cdab);
        int modelLeft = paneLeft + Math.min(98, (paneRight - paneLeft) / 3);
        graphics.fill(paneLeft, 27, paneRight, paneBottom, 0xf12f251d);
        graphics.fill(modelLeft, 27, modelLeft + 1, paneBottom, 0xff856a4d);
        graphics.drawCenteredString(font, state("source"), (paneLeft + modelLeft) / 2, 33, 0xffe7cdab);
        graphics.drawCenteredString(font, state("result"), (modelLeft + paneRight) / 2, 33, 0xffe7cdab);
        graphics.enableScissor(paneLeft, 46, modelLeft, paneBottom);
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate((paneLeft + modelLeft) / 2F, Math.min(71, (46 + paneBottom) / 2F), 0);
        pose.scale(2, 2, 1);
        graphics.renderItem(source, -8, -8);
        pose.popPose();
        graphics.disableScissor();
        if (atlasRegistered && paneBottom > 50) {
            renderModel(graphics, modelLeft);
            if (portraitRegistered && paneBottom >= 97) {
                graphics.blit(PORTRAIT, paneLeft + 5, paneBottom - 30, 28, 28, 0, 0, 256, 256, 256, 256);
            }
        } else {
            graphics.drawWordWrap(font, failure == null ? state("loading") : failure, modelLeft + 6, 49,
                    Math.max(1, paneRight - modelLeft - 12), 0xffe7b08d);
        }
        super.render(graphics, mouseX, mouseY, delta);
        if (mouseX >= paneLeft && mouseX < modelLeft && mouseY >= 46 && mouseY < paneBottom) {
            graphics.renderTooltip(font, source, mouseX, mouseY);
        }
    }

    private void renderModel(GuiGraphics graphics, int modelLeft) {
        graphics.flush();
        graphics.enableScissor(modelLeft + 2, 46, paneRight, paneBottom);
        var pose = graphics.pose();
        pose.pushPose();
        try {
            pose.translate((modelLeft + paneRight) / 2F, (46 + paneBottom) / 2F, 110);
            float scale = Math.min(paneRight - modelLeft - 12, paneBottom - 46) * zoom / diameter;
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

    private boolean inModel(double mouseX, double mouseY) {
        return mouseX > paneLeft + Math.min(98, (paneRight - paneLeft) / 3)
                && mouseX < paneRight && mouseY >= 46 && mouseY < paneBottom;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || !inModel(mouseX, mouseY)) return super.mouseClicked(mouseX, mouseY, button);
        setDragging(true);
        setFocused(null);
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (button != 0 || !isDragging() || getFocused() != null) return super.mouseDragged(mouseX, mouseY, button, dx, dy);
        yaw = Mth.wrapDegrees(yaw + (float) dx);
        pitch = Mth.wrapDegrees(pitch + (float) dy);
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!inModel(mouseX, mouseY)) return super.mouseScrolled(mouseX, mouseY, delta);
        zoom = Mth.clamp(zoom * (float) Math.pow(1.15, delta), .5F, 3F);
        return true;
    }

    @Override
    public void onClose() {
        if (editor.workbenchOpen()) minecraft.setScreen(editor);
        else super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        releaseTextures();
        bakedRevision = -1;
    }

    private void releaseTextures() {
        if (atlasRegistered) minecraft.getTextureManager().release(ATLAS);
        if (portraitRegistered) minecraft.getTextureManager().release(PORTRAIT);
        atlasRegistered = false;
        portraitRegistered = false;
        model = null;
    }
}
