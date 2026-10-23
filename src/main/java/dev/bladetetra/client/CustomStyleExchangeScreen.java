package dev.bladetetra.client;

import dev.bladetetra.customstyle.*;
import dev.bladetetra.network.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import java.util.List;

/** A small exchange page of the existing editor, with native paste/selection/IME support. */
public final class CustomStyleExchangeScreen extends Screen {
    private final Screen parent;
    private final boolean exporting, hasDraft;
    private final int expectedDraft;
    private MultiLineEditBox codeBox;
    private Button previewButton, confirmButton, draftButton, scrollButton, copyButton;
    private CustomStyleDependencyResolver.Preview preview;
    private String text = "", error = "";
    private String requestedCode = "";
    private int left, top, panelHeight, listTop, listBottom, firstLine, pendingTicks;
    private boolean pending;
    private static int sequence;
    private int requestId;

    CustomStyleExchangeScreen(Screen parent, boolean exporting, StyleRecordingSession.Snapshot draft) {
        super(label(exporting ? "export" : "import"));
        this.parent = parent; this.exporting = exporting; hasDraft = !draft.actions().isEmpty(); expectedDraft = draft.hashCode();
    }
    private static Component label(String key) { return Component.translatable("screen.blade_tetra.exchange." + key); }
    @Override protected void init() {
        panelHeight = Math.min(height - 8, 300); left = (width - 300) / 2; top = (height - panelHeight) / 2;
        codeBox = addRenderableWidget(new MultiLineEditBox(font, left + 12, top + 30, 276, 43,
                label("code"), label("code")));
        codeBox.setCharacterLimit(CustomStyleExchange.MAX_CODE_LENGTH); codeBox.setValue(text);
        codeBox.setValueListener(value -> { text = value; if (!exporting) { preview = null; error = ""; refresh(); } });
        if (exporting) {
            draftButton = button(12, 90, 132, "draft", b -> request(CustomStyleExchangePacket.Mode.EXPORT_DRAFT));
            scrollButton = button(156, 90, 132, "scroll", b -> request(CustomStyleExchangePacket.Mode.EXPORT_SCROLL));
        } else previewButton = button(12, 90, 276, "preview", b -> request(CustomStyleExchangePacket.Mode.PREVIEW));
        listTop = top + 116; listBottom = top + panelHeight - 38;
        button(12, panelHeight - 28, 72, "back", b -> onClose());
        if (exporting) copyButton = button(92, panelHeight - 28, 196, "copy", b -> minecraft.keyboardHandler.setClipboard(text));
        else confirmButton = button(92, panelHeight - 28, 196, "confirm", b -> {
            if (hasDraft) minecraft.setScreen(new ConfirmScreen(yes -> {
                minecraft.setScreen(this); if (yes) request(CustomStyleExchangePacket.Mode.IMPORT);
            }, label("overwrite"), label("overwrite_detail")));
            else request(CustomStyleExchangePacket.Mode.IMPORT);
        });
        refresh();
    }
    private Button button(int x, int y, int w, String key, Button.OnPress press) {
        return addRenderableWidget(new CustomStyleArt.ArtButton(left + x, top + y, w, label(key), null, press));
    }
    private void refresh() {
        if (previewButton != null) previewButton.active = !pending && !text.isBlank();
        if (confirmButton != null) confirmButton.active = !pending && preview != null && preview.allowed();
        if (copyButton != null) copyButton.active = !pending && !text.isEmpty();
        if (draftButton != null) draftButton.active = !pending && hasDraft;
        if (scrollButton != null) scrollButton.active = !pending;
    }
    private void request(CustomStyleExchangePacket.Mode mode) {
        if (pending) return;
        pending = true; pendingTicks = 0; error = ""; requestId = ++sequence; refresh();
        requestedCode = text;
        ModNetwork.CHANNEL.sendToServer(new CustomStyleExchangePacket.Request(requestId, mode, expectedDraft, text));
    }
    public static void accept(CustomStyleExchangePacket.Reply packet) {
        if (!(Minecraft.getInstance().screen instanceof CustomStyleExchangeScreen screen) || screen.requestId != packet.requestId()) return;
        if (packet.mode() == CustomStyleExchangePacket.Mode.PREVIEW && !screen.text.equals(screen.requestedCode)) {
            screen.pending = false; screen.preview = null; screen.refresh(); return;
        }
        screen.pending = false; screen.error = packet.error(); screen.preview = packet.preview(); screen.firstLine = 0;
        if (!packet.code().isEmpty()) { screen.text = packet.code(); screen.codeBox.setValue(packet.code()); }
        screen.refresh();
    }
    @Override public void tick() {
        if (pending && ++pendingTicks >= 100) { pending = false; error = "timeout"; refresh(); }
    }
    private List<Component> lines() {
        List<Component> result = new ArrayList<>();
        if (pending) result.add(label("pending"));
        if (!error.isEmpty()) result.add(label(error));
        if (preview == null) return result;
        if (preview.style() == null) { result.add(Component.translatable("message.blade_tetra.style_exchange." + preview.formatStatus().name().toLowerCase(java.util.Locale.ROOT))); return result; }
        result.add(Component.literal(preview.style().name() + " · BTSTYLE1 · " + preview.actions().size() + "/6"));
        result.add(label(preview.allowed() ? "available" : "blocked"));
        int position = 1;
        for (var info : preview.actions()) {
            result.add(Component.literal(position++ + ". " + info.action()));
            result.add(Component.literal("  " + (info.provider().isEmpty() ? label("unknown").getString() : info.provider()
                    + " [" + info.modId() + "] " + info.installedVersion())));
            result.add(Component.translatable("screen.blade_tetra.exchange.status." + info.status().name().toLowerCase(java.util.Locale.ROOT)));
            if (!info.requiredVersion().isEmpty()) result.add(Component.literal("  " + info.requiredVersion()));
        }
        for (String missing : preview.missingDeclaredMods()) result.add(Component.translatable("screen.blade_tetra.exchange.missing", missing));
        return result;
    }
    @Override public boolean mouseScrolled(double x, double y, double delta) {
        if (x >= left + 12 && x < left + 288 && y >= listTop && y < listBottom) {
            firstLine = Math.max(0, Math.min(Math.max(0, lines().size() - (listBottom - listTop) / 11), firstLine - (int)Math.signum(delta) * 2));
            return true;
        }
        return super.mouseScrolled(x, y, delta);
    }
    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        g.blit(CustomStyleArt.texture("editor_panel"), left, top, 300, panelHeight, 0, 0, 248, 220, 248, 220);
        g.drawString(font, title, left + 14, top + 12, 0x211D2A, false);
        List<Component> lines = lines();
        g.enableScissor(left + 10, listTop, left + 290, listBottom);
        for (int i = firstLine, y = listTop; i < lines.size() && y + 9 <= listBottom; i++, y += 11)
            g.drawString(font, CustomStyleArt.fit(lines.get(i), 270), left + 14, y, 0x211D2A, false);
        g.disableScissor();
        if (lines.size() * 11 > listBottom - listTop) g.drawString(font, "↕", left + 286, listBottom - 10, 0x8A2F2F, false);
        super.render(g, mouseX, mouseY, partialTick);
        int index = firstLine + (mouseY - listTop) / 11;
        if (mouseX >= left + 12 && mouseX < left + 288 && mouseY >= listTop && mouseY < listBottom
                && index >= 0 && index < lines.size() && font.width(lines.get(index)) > 270)
            g.renderTooltip(font, font.split(lines.get(index), 276), mouseX, mouseY);
    }
    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
