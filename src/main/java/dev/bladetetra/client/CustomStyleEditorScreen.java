package dev.bladetetra.client;

import dev.bladetetra.customstyle.CustomStyleSpec;
import dev.bladetetra.network.CustomStyleDraftActionPacket;
import dev.bladetetra.network.CustomStyleDraftSyncPacket;
import dev.bladetetra.network.ModNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/** Textured view of server-owned captured actions, never an executable combo editor. */
public final class CustomStyleEditorScreen extends Screen {
    private String draftName;
    private List<ResourceLocation> actions;
    private int selected = -1, firstRow, visibleRows, left, top, listBottom, panelHeight;
    private EditBox nameBox;
    private Button moveUp, moveDown, remove, undo, clear, complete, rename;
    private final List<Button> rows = new ArrayList<>();
    private CustomStyleDraftActionPacket.Action pending;
    private int pendingPosition, pendingTicks;
    private Component feedback;
    private static final int PANEL_WIDTH = 248, ROW_HEIGHT = 22, LIST_TOP = 61;

    private CustomStyleEditorScreen(CustomStyleDraftSyncPacket packet) {
        super(Component.translatable("screen.blade_tetra.custom_style.title"));
        draftName = packet.name();
        actions = packet.actions();
    }

    public static void accept(CustomStyleDraftSyncPacket packet) {
        Minecraft mc = Minecraft.getInstance();
        if (packet.finished()) {
            CustomStyleFeedback.compiled();
            if (mc.screen instanceof CustomStyleEditorScreen) mc.setScreen(null);
            return;
        }
        if (mc.screen instanceof CustomStyleEditorScreen current) current.update(packet);
        else mc.setScreen(new CustomStyleEditorScreen(packet));
    }

    private void update(CustomStyleDraftSyncPacket packet) {
        var previous = actions;
        draftName = packet.name();
        actions = packet.actions();
        feedback = null;
        // Follow an acknowledged move only when the returned sequence matches it.
        if (pending == CustomStyleDraftActionPacket.Action.MOVE_UP
                || pending == CustomStyleDraftActionPacket.Action.MOVE_DOWN) {
            int target = pendingPosition + (pending == CustomStyleDraftActionPacket.Action.MOVE_UP ? -1 : 1);
            var expected = new ArrayList<>(previous);
            if (target >= 0 && target < expected.size()) {
                java.util.Collections.swap(expected, pendingPosition, target);
                if (actions.equals(expected)) selected = target;
            }
        }
        if (pending == CustomStyleDraftActionPacket.Action.FINISH)
            feedback = Component.translatable("screen.blade_tetra.custom_style.rejected");
        if (pending == CustomStyleDraftActionPacket.Action.RENAME
                && !nameBox.getValue().strip().equals(draftName))
            feedback = Component.translatable("message.blade_tetra.style_exchange.invalid_name");
        if (selected >= actions.size()) selected = actions.size() - 1;
        if (pending == CustomStyleDraftActionPacket.Action.RENAME || !nameBox.isFocused())
            nameBox.setValue(draftName);
        pending = null;
        revealSelection();
        updateButtons();
    }

    @Override protected void init() {
        // Four rows at 320x240; all six at taller scales. Footer never shares a row.
        visibleRows = Math.max(1, Math.min(CustomStyleSpec.MAX_ACTIONS, (height - 8 - 141) / ROW_HEIGHT));
        panelHeight = 141 + visibleRows * ROW_HEIGHT;
        left = (width - PANEL_WIDTH) / 2;
        top = Math.max(0, (height - panelHeight) / 2);
        listBottom = top + LIST_TOP + visibleRows * ROW_HEIGHT;
        rows.clear();
        nameBox = addRenderableWidget(new EditBox(font, left + 15, top + 39, 163, 12,
                Component.translatable("screen.blade_tetra.custom_style.name")));
        nameBox.setBordered(false);
        nameBox.setTextColor(0x211D2A);
        nameBox.setTextColorUneditable(0x735747);
        nameBox.setMaxLength(CustomStyleSpec.MAX_NAME_LENGTH);
        nameBox.setValue(draftName);
        nameBox.setResponder(value -> updateButtons());
        rename = button(184, 34, 52, "rename", null,
                b -> send(CustomStyleDraftActionPacket.Action.RENAME, 0, nameBox.getValue()));
        for (int i = 0; i < visibleRows; i++) {
            final int slot = i;
            Button row = new Button(left + 12, top + LIST_TOP + i * ROW_HEIGHT, 224, ROW_HEIGHT,
                    Component.empty(), b -> { selected = firstRow + slot; updateButtons(); }, supplier -> supplier.get()) {
                @Override public void renderWidget(GuiGraphics g, int mx, int my, float delta) {
                    int index = firstRow + slot;
                    if (index >= actions.size()) return;
                    g.blit(CustomStyleArt.texture(index == selected ? "action_row_selected" : "action_row"),
                            getX(), getY(), 0, 0, 224, 22, 224, 22);
                    g.drawString(font, Integer.toString(index + 1), getX() + 7, getY() + 7, 0xF7EDDB, false);
                    g.drawString(font, CustomStyleArt.fit(actionName(actions.get(index)), 189),
                            getX() + 27, getY() + 7, 0x211D2A, false);
                    if (isFocused()) g.renderOutline(getX(), getY(), width, height, 0xFF8A2F2F);
                }
            };
            rows.add(addRenderableWidget(row));
        }
        int toolY = listBottom - top + 4;
        moveUp = button(12, toolY, 44, "move_up", null,
                b -> send(CustomStyleDraftActionPacket.Action.MOVE_UP, selected, ""));
        moveDown = button(60, toolY, 44, "move_down", null,
                b -> send(CustomStyleDraftActionPacket.Action.MOVE_DOWN, selected, ""));
        remove = button(108, toolY, 128, "remove", "clear",
                b -> send(CustomStyleDraftActionPacket.Action.REMOVE, selected, ""));
        int footerY = listBottom - top + 55;
        undo = button(10, footerY, 52, "undo", "undo",
                b -> send(CustomStyleDraftActionPacket.Action.UNDO, 0, ""));
        clear = button(66, footerY, 52, "clear", "clear", b -> {
            draftName = nameBox.getValue();
            minecraft.setScreen(new ConfirmScreen(confirmed -> {
                minecraft.setScreen(this);
                if (confirmed) send(CustomStyleDraftActionPacket.Action.CLEAR, 0, "");
            }, Component.translatable("ui.blade_tetra.custom_style.confirm_clear"), Component.empty()));
        });
        complete = button(122, footerY, 116, "complete", "finish",
                b -> send(CustomStyleDraftActionPacket.Action.FINISH, 0, nameBox.getValue()));
        button(224, 6, 18, "close", null, b -> onClose());
        revealSelection();
        updateButtons();
    }

    private Button button(int x, int y, int width, String key, String icon, Button.OnPress onPress) {
        return addRenderableWidget(new CustomStyleArt.ArtButton(left + x, top + y, width,
                Component.translatable("screen.blade_tetra.custom_style." + key), icon, onPress));
    }

    private Component actionName(ResourceLocation id) {
        // Optional resource-pack labels; retain the real ID in the tooltip.
        String key = "action.blade_tetra." + id.getNamespace() + "." + id.getPath().replace('/', '.');
        return net.minecraft.client.resources.language.I18n.exists(key)
                ? Component.translatable(key) : Component.literal(id.toString());
    }

    private void revealSelection() {
        if (selected >= 0) {
            if (selected < firstRow) firstRow = selected;
            if (selected >= firstRow + visibleRows) firstRow = selected - visibleRows + 1;
        }
        firstRow = Math.max(0, Math.min(firstRow, Math.max(0, actions.size() - visibleRows)));
    }

    private void updateButtons() {
        if (complete == null) return;
        boolean ready = pending == null;
        moveUp.active = ready && selected > 0 && selected < actions.size();
        moveDown.active = ready && selected >= 0 && selected + 1 < actions.size();
        remove.active = ready && selected >= 0 && selected < actions.size();
        undo.active = clear.active = ready && !actions.isEmpty();
        boolean named = !nameBox.getValue().isBlank()
                && nameBox.getValue().chars().noneMatch(Character::isISOControl);
        complete.active = ready && !actions.isEmpty() && named;
        rename.active = ready && named;
        for (int i = 0; i < rows.size(); i++) {
            int index = firstRow + i;
            Button row = rows.get(i);
            row.visible = index < actions.size();
            row.active = ready && row.visible;
            if (row.visible) {
                row.setMessage(Component.literal((index + 1) + ". ").append(actionName(actions.get(index))));
                row.setTooltip(Tooltip.create(actionName(actions.get(index)).copy()
                        .append("\n" + actions.get(index))));
            }
        }
    }

    private void send(CustomStyleDraftActionPacket.Action action, int position, String value) {
        if (pending != null) return;
        pending = action;
        pendingPosition = position;
        pendingTicks = 0;
        feedback = null;
        updateButtons();
        ModNetwork.CHANNEL.sendToServer(new CustomStyleDraftActionPacket(action, position, value));
    }

    @Override public void tick() {
        nameBox.tick();
        if (pending != null && ++pendingTicks >= 100) {
            pending = null;
            feedback = Component.translatable("screen.blade_tetra.custom_style.timeout");
            updateButtons();
        }
    }

    @Override public void resize(Minecraft mc, int width, int height) {
        if (nameBox != null) draftName = nameBox.getValue();
        super.resize(mc, width, height);
    }

    @Override public boolean mouseScrolled(double x, double y, double delta) {
        if (x >= left + 12 && x < left + 236 && y >= top + LIST_TOP && y < listBottom) {
            firstRow = Math.max(0, Math.min(Math.max(0, actions.size() - visibleRows),
                    firstRow - (int) Math.signum(delta)));
            updateButtons();
            return true;
        }
        return super.mouseScrolled(x, y, delta);
    }

    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (rows.contains(getFocused()) && pending == null && !actions.isEmpty()
                && (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN)) {
            selected = Math.max(0, Math.min(actions.size() - 1,
                    selected + (key == GLFW.GLFW_KEY_UP ? -1 : 1)));
            revealSelection();
            updateButtons();
            setFocused(rows.get(selected - firstRow));
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        var panel = CustomStyleArt.texture("editor_panel");
        g.blit(panel, left, top, 0, 0, 248, 61, 248, 220);
        // Extend only the undecorated paper body; keep title, input and footer pixels intact.
        g.blit(panel, left, top + 61, 248, panelHeight - 102, 0, 174, 248, 1, 248, 220);
        g.blit(panel, left, top + panelHeight - 41, 0, 179, 248, 41, 248, 220);
        g.blit(CustomStyleArt.texture("style_custom"), left + 12, top + 8, 0, 0, 16, 16, 16, 16);
        g.drawString(font, CustomStyleArt.fit(title, 184), left + 32, top + 12, 0x211D2A, false);
        Component status = pending != null ? Component.translatable("ui.blade_tetra.custom_style.pending")
                : feedback != null ? feedback : Component.translatable("screen.blade_tetra.custom_style.recorded",
                        actions.size(), CustomStyleSpec.MAX_ACTIONS);
        g.drawString(font, CustomStyleArt.fit(status, 224), left + 12, listBottom + 40,
                feedback != null ? 0x8A2F2F : 0x735747, false);
        if (actions.isEmpty()) {
            int y = top + LIST_TOP + 6;
            for (var line : font.split(Component.translatable("screen.blade_tetra.custom_style.empty"), 210)) {
                g.drawString(font, line, left + 18, y, 0x735747, false);
                y += 10;
                if (y + 9 > listBottom) break;
            }
        }
        if (actions.size() > visibleRows) {
            int trackHeight = visibleRows * ROW_HEIGHT;
            int thumb = Math.max(12, trackHeight * visibleRows / actions.size());
            int y = top + LIST_TOP + (trackHeight - thumb) * firstRow / (actions.size() - visibleRows);
            g.fill(left + 237, top + LIST_TOP, left + 240, listBottom, 0xFFC7B697);
            g.fill(left + 237, y, left + 240, y + thumb, 0xFF8A2F2F);
        }
        super.render(g, mouseX, mouseY, partialTick);
        if (nameBox.isFocused()) g.renderOutline(left + 13, top + 36, 168, 17, 0xFF8A2F2F);
        if (mouseX >= left + 12 && mouseX < left + 236
                && mouseY >= listBottom + 37 && mouseY < listBottom + 51)
            g.renderTooltip(font, status, mouseX, mouseY);
    }

    @Override public boolean isPauseScreen() { return false; }
}
