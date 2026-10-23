package dev.bladetetra.client;

import dev.bladetetra.customstyle.CustomStyleSpec;
import dev.bladetetra.network.CustomStyleDraftActionPacket;
import dev.bladetetra.network.CustomStyleDraftSyncPacket;
import dev.bladetetra.network.ModNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Functional placeholder editor. Artwork/GUI textures belong to Astra's PR.
 * This is a list of captured native states, NOT an independent combat simulator.
 */
public final class CustomStyleEditorScreen extends Screen {
    private String draftName;
    private List<ResourceLocation> actions;
    private int selected = -1;
    private EditBox nameBox;
    private Button moveUp;
    private Button moveDown;
    private Button remove;
    private Button complete;
    private int top, left, span;
    private static final int ROW_HEIGHT = 19;

    private CustomStyleEditorScreen(CustomStyleDraftSyncPacket packet) {
        super(Component.translatable("screen.blade_tetra.custom_style.title"));
        this.draftName = packet.name();
        this.actions = packet.actions();
    }

    public static void accept(CustomStyleDraftSyncPacket packet) {
        Minecraft mc = Minecraft.getInstance();
        if (packet.finished()) {
            if (mc.screen instanceof CustomStyleEditorScreen) mc.setScreen(null);
            return;
        }
        if (mc.screen instanceof CustomStyleEditorScreen current) {
            current.update(packet);
        } else {
            mc.setScreen(new CustomStyleEditorScreen(packet));
        }
    }

    private void update(CustomStyleDraftSyncPacket packet) {
        draftName = packet.name();
        actions = packet.actions();
        if (selected >= actions.size()) selected = -1;
        if (nameBox != null && !nameBox.isFocused()) nameBox.setValue(draftName);
        updateButtons();
    }

    @Override
    protected void init() {
        span = Math.min(340, width - 24);
        left = (width - span) / 2;
        top = Math.max(10, (height - 244) / 2);
        nameBox = addRenderableWidget(new EditBox(font, left + 12, top + 32,
                span - 95, 18, Component.translatable("screen.blade_tetra.custom_style.name")));
        nameBox.setMaxLength(CustomStyleSpec.MAX_NAME_LENGTH);
        nameBox.setValue(draftName);
        addRenderableWidget(Button.builder(
                Component.translatable("screen.blade_tetra.custom_style.rename"),
                b -> send(CustomStyleDraftActionPacket.Action.RENAME, 0, nameBox.getValue()))
                .bounds(left + span - 75, top + 32, 63, 18).build());
        moveUp = addRenderableWidget(Button.builder(Component.literal("↑"),
                b -> send(CustomStyleDraftActionPacket.Action.MOVE_UP, selected, ""))
                .bounds(left + 12, top + 166, 31, 18).build());
        moveDown = addRenderableWidget(Button.builder(Component.literal("↓"),
                b -> send(CustomStyleDraftActionPacket.Action.MOVE_DOWN, selected, ""))
                .bounds(left + 47, top + 166, 31, 18).build());
        remove = addRenderableWidget(Button.builder(
                Component.translatable("screen.blade_tetra.custom_style.remove"),
                b -> send(CustomStyleDraftActionPacket.Action.REMOVE, selected, ""))
                .bounds(left + 83, top + 166, 63, 18).build());
        addRenderableWidget(Button.builder(
                Component.translatable("screen.blade_tetra.custom_style.undo"),
                b -> send(CustomStyleDraftActionPacket.Action.UNDO, 0, ""))
                .bounds(left + 12, top + 198, 73, 20).build());
        addRenderableWidget(Button.builder(
                Component.translatable("screen.blade_tetra.custom_style.clear"),
                b -> send(CustomStyleDraftActionPacket.Action.CLEAR, 0, ""))
                .bounds(left + 92, top + 198, 73, 20).build());
        complete = addRenderableWidget(Button.builder(
                Component.translatable("screen.blade_tetra.custom_style.complete"),
                b -> send(CustomStyleDraftActionPacket.Action.FINISH, 0, ""))
                .bounds(left + span - 137, top + 198, 125, 20).build());
        updateButtons();
    }

    private void updateButtons() {
        if (moveUp == null) return;
        moveUp.active = selected > 0 && selected < actions.size();
        moveDown.active = selected >= 0 && selected + 1 < actions.size();
        remove.active = selected >= 0 && selected < actions.size();
        complete.active = !actions.isEmpty();
    }

    private void send(CustomStyleDraftActionPacket.Action action, int position, String value) {
        ModNetwork.CHANNEL.sendToServer(new CustomStyleDraftActionPacket(action, position, value));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        int from = top + 61;
        if (mouseX >= left + 12 && mouseX <= left + span - 12
                && mouseY >= from && mouseY < from + ROW_HEIGHT * actions.size()) {
            selected = (int) ((mouseY - from) / ROW_HEIGHT);
            updateButtons();
            return true;
        }
        return false;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.fill(left, top, left + span, top + 230, 0xE81D1B1B);
        graphics.fill(left + 4, top + 4, left + span - 4, top + 26, 0xFF6B4039);
        graphics.drawCenteredString(font, title, width / 2, top + 11, 0xFFF2E5CF);
        graphics.drawString(font, Component.translatable("screen.blade_tetra.custom_style.recorded",
                actions.size(), CustomStyleSpec.MAX_ACTIONS), left + 12, top + 52, 0xFFD5BD99);
        for (int i = 0; i < actions.size(); i++) {
            int y = top + 64 + i * ROW_HEIGHT;
            if (i == selected) graphics.fill(left + 11, y - 3, left + span - 11, y + 13, 0x99574D37);
            graphics.drawString(font, (i + 1) + ". " + actions.get(i),
                    left + 16, y, i == selected ? 0xFFFFD58E : 0xFFE4D9C7, false);
        }
        if (actions.isEmpty()) graphics.drawCenteredString(font,
                Component.translatable("screen.blade_tetra.custom_style.empty"),
                width / 2, top + 104, 0xFFB9ABA2);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
