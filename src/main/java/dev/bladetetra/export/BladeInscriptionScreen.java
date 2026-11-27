package dev.bladetetra.export;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.Util;
import java.nio.file.Path;

/** Visual receipt only: constructed exclusively after the archive writer returns successfully. */
final class BladeInscriptionScreen extends Screen {
    private final BladeSealScreen editor;
    private final BladeStudioViewport viewport;
    private final CustomBackplateImage.Selection backplate;
    private final Component bladeName;
    private final String inscription;
    private final Path archive;
    private final String command;
    private final long started = Util.getMillis();
    private boolean complete;
    private Button skip;
    private long lastFrame = started;
    private boolean sounded;

    BladeInscriptionScreen(BladeSealScreen editor, BladeSealPackets.Preview preview,
                           CustomBackplateImage.Selection backplate, Component bladeName,
                           String inscription, Path archive, String command) {
        super(Component.translatable("blade_tetra.export.inscription.complete"));
        this.editor = editor;
        this.viewport = new BladeStudioViewport(preview);
        this.backplate = backplate;
        this.bladeName = bladeName;
        this.inscription = inscription;
        this.archive = archive;
        this.command = command;
    }

    @Override protected void init() {
        skip = addRenderableWidget(Button.builder(Component.translatable("blade_tetra.export.inscription.skip"),
                b -> finish()).bounds(width / 2 - 60, height - 28, 120, 20).build());
        if (complete) showActions();
    }

    private void finish() {
        if (complete) return;
        complete = true;
        showActions();
    }

    private void showActions() {
        clearWidgets();
        int w = Math.min(120, (width - 24) / 3);
        addRenderableWidget(Button.builder(Component.translatable("blade_tetra.export.result.directory"),
                b -> net.minecraft.Util.getPlatform().openFile(archive.getParent().toFile()))
                .bounds(width / 2 - w * 3 / 2, height - 28, w - 2, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("blade_tetra.export.result.command"),
                b -> minecraft.keyboardHandler.setClipboard(command))
                .bounds(width / 2 - w / 2, height - 28, w - 2, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), b -> onClose())
                .bounds(width / 2 + w / 2, height - 28, w - 2, 20).build());
    }

    @Override public void tick() {
        if (!editor.workbenchOpen()) { minecraft.setScreen(null); return; }
        viewport.prepare(minecraft, backplate);
        if (!complete && !sounded && Util.getMillis() - started >= 1400) {
            sounded = true;
            minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                    net.minecraft.sounds.SoundEvents.ANVIL_USE, 1.5f, .18f));
        }
        if (!complete && Util.getMillis() - started >= 3000) finish();
    }

    @Override public void render(GuiGraphics g, int mx, int my, float delta) {
        renderBackground(g);
        g.fill(0, 0, width, height, 0xff181513);
        float ticks = complete ? 60 : Math.min(60, (Util.getMillis() - started) / 50f);
        long now = Util.getMillis();
        if (!complete) viewport.rotate(Math.min(100, now - lastFrame) * .015, 0);
        lastFrame = now;
        float reveal = Math.max(0, Math.min(1, (ticks - 28) / 16));
        int cx = width / 2, cy = height / 2 - 12;
        int radius = Math.min(width / 4, height / 3);
        // A quiet fourfold maker's mark; no textures or full-screen flashes.
        if (reveal > 0 && !complete) {
            int c = ((int)(reveal * 110) << 24) | 0xb99761;
            for (int i = -radius; i <= radius; i += 2) {
                int y = radius - Math.abs(i);
                g.fill(cx + i, cy + y, cx + i + 1, cy + y + 1, c);
                g.fill(cx + i, cy - y, cx + i + 1, cy - y + 1, c);
            }
        }
        if (ticks >= 12 && !complete) {
            for (int i = 0; i < 18; i++) {
                double a = i * Math.PI * 2 / 18 + ticks * .025;
                int x = cx + (int)(Math.cos(a) * radius);
                int y = cy + (int)(Math.sin(a) * radius * .5);
                g.fill(x, y, x + 1, y + 1, 0x99cfb782);
            }
        }
        if (viewport.ready()) viewport.render(g, 20, 34, width - 20, height - 85);
        g.drawCenteredString(font, complete ? title : Component.translatable(
                "blade_tetra.export.inscription." + (ticks < 12 ? 0 : ticks < 28 ? 1 : ticks < 44 ? 2 : 3)),
                cx, 18, 0xffdfcba9);
        if (complete || ticks >= 28) {
            g.drawCenteredString(font, bladeName, cx, height - 81, 0xffeee3ce);
            if (complete) {
                g.drawCenteredString(font, font.plainSubstrByWidth(inscription, width - 48), cx, height - 67, 0xffb7aa95);
                g.drawWordWrap(font, Component.translatable("blade_tetra.export.inscription.install"),
                        24, height - 49, width - 48, 0xffb7aa95);
            }
        }
        super.render(g, mx, my, delta);
    }

    @Override public void onClose() {
        if (!complete) finish();
        else minecraft.setScreen(editor.workbenchOpen() ? editor : null);
    }
    @Override public void removed() { viewport.release(minecraft); }
    @Override public boolean isPauseScreen() { return false; }
}
