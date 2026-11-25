package dev.bladetetra.export;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.world.item.ItemStack;

/** Compares the source inventory icon with the actual sealed OBJ, atlas and portrait. */
final class BladeStudioPreviewScreen extends Screen {
    private final BladeSealScreen editor;
    private final ItemStack source;
    private final CustomBackplateImage.Selection backplate;
    private final BladeStudioViewport viewport;
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
        this.viewport = new BladeStudioViewport(preview);
        this.source = source.copy();
        this.backplate = backplate;
    }

    @Override
    protected void init() {
        paneLeft = 12;
        paneRight = width - 12;
        paneBottom = height - 65;
        int buttonWidth = (paneRight - paneLeft - 8) / 3;
        addRenderableWidget(Button.builder(state(drawn ? "drawn" : "sheathed"), button -> {
            drawn = !drawn;
            viewport.toggleDrawn();
            button.setMessage(state(drawn ? "drawn" : "sheathed"));
        }).bounds(paneLeft, height - 57, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(state(damaged ? "damaged" : "healthy"), button -> {
            damaged = !damaged;
            viewport.setDamaged(damaged);
            button.setMessage(state(damaged ? "damaged" : "healthy"));
        }).bounds(paneLeft + buttonWidth + 4, height - 57, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(state(luminous ? "glow_on" : "glow_off"), button -> {
            luminous = !luminous;
            viewport.setLuminous(luminous);
            button.setMessage(state(luminous ? "glow_on" : "glow_off"));
        }).bounds(paneRight - buttonWidth, height - 57, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(state("reset"), button -> {
            viewport.resetCamera();
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
        viewport.prepare(minecraft, backplate);
    }

    boolean hasBakedModel() {
        return viewport.ready();
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
        if (viewport.ready() && paneBottom > 50) {
            viewport.render(graphics, modelLeft + 2, 46, paneRight, paneBottom);
            if (paneBottom >= 97) {
                viewport.renderPortrait(graphics, paneLeft + 5, paneBottom - 30);
            }
        } else {
            graphics.drawWordWrap(font, viewport.failure() == null ? state("loading") : viewport.failure(), modelLeft + 6, 49,
                    Math.max(1, paneRight - modelLeft - 12), 0xffe7b08d);
        }
        super.render(graphics, mouseX, mouseY, delta);
        if (mouseX >= paneLeft && mouseX < modelLeft && mouseY >= 46 && mouseY < paneBottom) {
            graphics.renderTooltip(font, source, mouseX, mouseY);
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
        viewport.rotate(dx, dy);
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!inModel(mouseX, mouseY)) return super.mouseScrolled(mouseX, mouseY, delta);
        viewport.zoom(delta);
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
        viewport.release(minecraft);
    }
}
