package dev.bladetetra.export;

import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Visual receipt only. Playback and dismissal never invoke the archive writer. */
final class BladeInscriptionScreen extends Screen {
    private final BladeSealScreen editor;
    private final BladeStudioViewport viewport;
    private final CustomBackplateImage.Selection backplate;
    private final Component bladeName;
    private final String inscription;
    private final Path archive;
    private final String command;
    private final BladeInscriptionTimeline timeline = new BladeInscriptionTimeline();
    private final List<SimpleSoundInstance> sounds = new ArrayList<>();
    private long started = -1;
    private boolean complete;
    private List<FormattedCharSequence> lines = List.of();
    private int textScroll;
    private long renderNanos, maxRenderNanos;
    private int renderedFrames;

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
        lines = font.split(Component.literal(inscription.isBlank() ? bladeName.getString() : inscription), Math.max(40, width - 80));
        textScroll = Mth.clamp(textScroll, 0, Math.max(0, lines.size() - textRows()));
        if (complete) showActions();
        else addRenderableWidget(Button.builder(label("skip"), b -> finish()).bounds(width / 2 - 60, height - 25, 120, 20).build());
    }
    private Component label(String key) { return Component.translatable("blade_tetra.export.inscription." + key); }
    private void stopSounds() {
        sounds.forEach(sound -> minecraft.getSoundManager().stop(sound));
        sounds.clear();
    }
    private void finish() {
        if (complete) return;
        complete = true;
        stopSounds();
        viewport.present(-9, -14, -4, .94f, .16f);
        textScroll = 0;
        showActions();
    }
    private void replay() {
        stopSounds(); complete = false;
        started = viewport.ready() ? Util.getMillis() : -1;
        timeline.reset(); textScroll = 0;
        clearWidgets(); init();
    }
    private void showActions() {
        clearWidgets();
        int w = Math.min(120, (width - 24) / 4), x = width / 2 - w * 2;
        addRenderableWidget(Button.builder(Component.translatable("blade_tetra.export.result.directory"),
                b -> Util.getPlatform().openFile(archive.getParent().toFile())).bounds(x, height - 25, w - 2, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("blade_tetra.export.result.command"),
                b -> minecraft.keyboardHandler.setClipboard(command)).bounds(x + w, height - 25, w - 2, 20).build());
        addRenderableWidget(Button.builder(label("replay"), b -> replay()).bounds(x + w * 2, height - 25, w - 2, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), b -> onClose()).bounds(x + w * 3, height - 25, w - 2, 20).build());
    }
    @Override public void tick() {
        if (!editor.workbenchOpen()) { minecraft.setScreen(null); return; }
        viewport.prepare(minecraft, backplate);
        if (complete) return;
        if (!viewport.ready()) { if (viewport.failure() != null) finish(); return; }
        if (started < 0) started = Util.getMillis();
        if (elapsed() >= BladeInscriptionTimeline.DURATION) finish();
    }
    long elapsed() { return started < 0 ? 0 : Math.max(0, Util.getMillis() - started); }
    boolean completed() { return complete; }
    String renderStats() {
        return "frames=" + renderedFrames + ", meanCpuMs=" + renderNanos / 1e6 / Math.max(1, renderedFrames)
                + ", maxCpuMs=" + maxRenderNanos / 1e6;
    }
    private void sound(int cue) {
        SoundEvent event = switch (cue) {
            case 0 -> SoundEvents.AMETHYST_BLOCK_CHIME;
            case 1 -> SoundEvents.ENCHANTMENT_TABLE_USE;
            case 2 -> SoundEvents.ANVIL_USE;
            default -> SoundEvents.AMETHYST_BLOCK_CHIME;
        };
        float pitch = switch (cue) { case 0 -> .65f; case 1 -> .8f; case 2 -> 1.35f; default -> 1.2f; };
        var sound = SimpleSoundInstance.forUI(event, pitch, cue == 2 ? .28f : .15f);
        sounds.add(sound); minecraft.getSoundManager().play(sound);
    }
    @Override public void render(GuiGraphics g, int mx, int my, float delta) {
        long cpuStart = System.nanoTime();
        var f = BladeInscriptionTimeline.sample(complete ? BladeInscriptionTimeline.DURATION : elapsed());
        // Sound and image sample the same clock, including below the tick rate.
        if (!complete && started >= 0) {
            int cue = timeline.consumeCue(f.millis());
            if (cue >= 0) sound(cue);
            viewport.present(Mth.lerp(f.settle(), -18, -9), Mth.lerp(f.settle(), -34, -21) + f.release() * 7,
                    Mth.lerp(f.settle(), -10, -4), Mth.lerp(f.settle(), .84f, .94f), f.charge() * .16f);
        }
        g.fill(0, 0, width, height, 0xff0e111b);
        g.fillGradient(0, 0, width, height, 0xff1b2030, 0xff100f14);
        int accent = viewport.accent(), secondary = viewport.secondary();
        int top = 42, bottom = Math.max(top + 24, height - 112), cy = (top + bottom) / 2;
        for (int i = 5; i > 0; i--) {
            int r = Math.min(width / 2 - 15, 38 + i * 14);
            g.fill(width / 2 - r, cy - 12 - i * 6, width / 2 + r, cy + 12 + i * 6,
                    rgba(secondary, .008f + f.charge() * .006f));
        }
        if (viewport.ready()) viewport.render(g, 28, top, width - 28, bottom);
        g.pose().pushPose(); g.pose().translate(0, 0, 400);
        if (viewport.ready()) g.drawManaged(() -> renderEnergy(g, f, accent, secondary));
        g.drawCenteredString(font, complete ? title : label(Integer.toString(f.phase())), width / 2, 18, 0xffd6c7ab);
        g.fill(width / 2 - 50, 34, width / 2 + 50, 35, rgba(accent, .2f));
        g.fill(width / 2 - 50, 34, width / 2 - 50 + (int)(100 * f.millis() / BladeInscriptionTimeline.DURATION), 35, rgba(accent, .75f));
        int nameY = bottom + 7;
        if (f.name() > .02f || complete) {
            float scale = Math.min(1.15f, (width - 56f) / Math.max(1, font.width(bladeName)));
            g.pose().pushPose(); g.pose().translate(width / 2f, nameY, 0); g.pose().scale(scale, scale, 1);
            g.drawString(font, bladeName, -font.width(bladeName) / 2, 0, rgba(0xf1e5ce, complete ? 1 : f.name()), false);
            g.pose().popPose();
        }
        renderInscription(g, f, nameY + 17);
        if (complete) {
            var notice = font.split(label("install"), Math.max(40, width - 32));
            int y = height - 28 - notice.size() * font.lineHeight;
            for (var line : notice) { g.drawCenteredString(font, line, width / 2, y, 0xff9d9ca8); y += font.lineHeight; }
        }
        if (viewport.failure() != null) g.drawCenteredString(font, label("preview_failed"), width / 2, cy, 0xffe7b08d);
        g.pose().popPose(); super.render(g, mx, my, delta);
        long cost = System.nanoTime() - cpuStart;
        renderNanos += cost; maxRenderNanos = Math.max(maxRenderNanos, cost); renderedFrames++;
    }
    private void renderEnergy(GuiGraphics g, BladeInscriptionTimeline.Frame f, int accent, int secondary) {
        var root = viewport.screenRoot(); var tip = viewport.screenTip();
        float dx = tip.x - root.x, dy = tip.y - root.y;
        float x = root.x + dx * .18f, y = root.y + dy * .18f;
        float energy = f.charge() * (1 - f.release());
        if (energy > .001f) for (int strand = 0; strand < 6; strand++) {
            float direction = (strand & 1) == 0 ? 1 : -1;
            float previousX = 0, previousY = 0, previousP = 2;
            for (int j = 0; j < 28; j++) {
                float u = j / 27f, motion = (float)(f.millis() / 1500.0 + strand * .137), p = (u + motion) % 1;
                float span = (1 - p) * (1 - f.release());
                float sx = x + dx * span * (.4f + strand * .06f);
                float sy = y + dy * span * .5f + direction * (float)Math.sin(p * Math.PI) * (12 + strand * 3) * span;
                int rgb = (strand & 1) == 0 ? accent : secondary;
                if (p >= previousP) line(g, previousX, previousY, sx, sy, rgba(rgb, energy * (.12f + p * .38f)));
                if (j % 7 == 0) pixel(g, sx - 1, sy - 1, 3, rgba(rgb, energy * p * .12f));
                pixel(g, sx, sy, 1, rgba(rgb, energy * p * .7f));
                previousX = sx; previousY = sy; previousP = p;
            }
        }
        if (f.writing() > 0 && f.release() < 1) {
            float p = f.writing();
            for (int i = 0; i < 22; i++) {
                float trail = Math.max(0, p - i * .007f);
                var spine = viewport.screenSpine();
                float station = trail * (spine.length - 1);
                int index = Math.min(spine.length - 2, (int)station);
                float sx = Mth.lerp(station - index, spine[index].x, spine[index + 1].x);
                float sy = Mth.lerp(station - index, spine[index].y, spine[index + 1].y);
                pixel(g, sx - 2, sy - 2, 5, rgba(accent, (1 - i / 22f) * (1 - f.release()) * .05f));
                pixel(g, sx, sy, i < 3 ? 2 : 1,
                        rgba(i < 3 ? 0xffead1 : accent, (1 - i / 22f) * (1 - f.release()) * .85f));
            }
        }
        if (f.millis() >= BladeInscriptionTimeline.IMPACT && f.release() < 1) {
            float p = (f.millis() - BladeInscriptionTimeline.IMPACT) / 1500f;
            for (int i = 0; i < 42; i++) {
                double angle = i * 2.399963;
                float speed = 18 + i % 7 * 5;
                pixel(g, x + (float)Math.cos(angle) * speed * p, y + (float)Math.sin(angle) * speed * p + p * p * 12,
                        i % 5 == 0 ? 2 : 1, rgba(i % 3 == 0 ? 0xffe5b8 : accent, (1 - p) * .85f));
            }
            float radius = 11 + 8 * (1 - f.release());
            int seal = rgba(accent, (1 - f.release()) * .85f);
            line(g, x - radius, y - radius, x + radius, y - radius, seal);
            line(g, x + radius, y - radius, x + radius, y + radius, seal);
            line(g, x + radius, y + radius, x - radius, y + radius, seal);
            line(g, x - radius, y + radius, x - radius, y - radius, seal);
            line(g, x - 6, y - 6, x + 6, y - 6, seal);
            line(g, x - 6, y - 1, x + 6, y - 1, seal);
            line(g, x, y - 6, x, y + 6, seal);
            line(g, x - 6, y - 1, x - 6, y + 6, seal);
            line(g, x - 6, y + 6, x + 6, y + 6, seal);
            for (int i = 1; i < viewport.screenSpine().length; i++) {
                var a = viewport.screenSpine()[i - 1]; var b = viewport.screenSpine()[i];
                line(g, a.x, a.y, b.x, b.y, rgba(accent, f.impact() * .4f));
            }
        }
    }
    private int textRows() { return Math.max(1, Math.min(3, (height - 170) / font.lineHeight)); }
    private void renderInscription(GuiGraphics g, BladeInscriptionTimeline.Frame f, int y) {
        if (f.writing() <= 0 && !complete) return;
        int rows = textRows();
        float written = (complete ? 1 : f.writing()) * lines.size();
        int first = complete ? textScroll : Mth.clamp((int)written - rows, 0, Math.max(0, lines.size() - rows));
        for (int i = first; i < Math.min(lines.size(), first + rows); i++) {
            var text = lines.get(i);
            int w = font.width(text), x = (width - w) / 2, rowY = y + (i - first) * font.lineHeight;
            float progress = complete ? 1 : Mth.clamp(written - i, 0, 1);
            if (progress <= 0) continue;
            g.enableScissor(Math.max(0, x - 1), rowY, Math.min(width, x + (int)Math.ceil(w * progress) + 1), rowY + font.lineHeight);
            g.drawString(font, text, x, rowY, 0xffc4baa8, false); g.disableScissor();
            if (progress < 1) pixel(g, x + w * progress, rowY + font.lineHeight - 2, 2, 0xffffdf9a);
        }
        if (complete && lines.size() > rows) g.drawCenteredString(font, label("scroll"), width / 2, y + rows * font.lineHeight, 0xff858896);
    }
    private static int rgba(int rgb, float alpha) { return (Mth.clamp((int)(alpha * 255), 0, 255) << 24) | rgb & 0xffffff; }
    private static void pixel(GuiGraphics g, float x, float y, int size, int color) { g.fill((int)x, (int)y, (int)x + size, (int)y + size, color); }
    private static void line(GuiGraphics g, float x, float y, float endX, float endY, int color) {
        int steps = Math.max(1, (int)Math.max(Math.abs(endX - x), Math.abs(endY - y)));
        for (int i = 0; i <= steps; i++) pixel(g, Mth.lerp(i / (float)steps, x, endX), Mth.lerp(i / (float)steps, y, endY), 1, color);
    }
    @Override public boolean mouseClicked(double x, double y, int button) {
        if (complete && button == 0 && y >= 42 && y < height - 112) { setDragging(true); setFocused(null); return true; }
        return super.mouseClicked(x, y, button);
    }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (complete && button == 0 && isDragging() && getFocused() == null) { viewport.rotate(dx, dy); return true; }
        return super.mouseDragged(x, y, button, dx, dy);
    }
    @Override public boolean mouseScrolled(double x, double y, double amount) {
        if (complete && y >= height - 105) { textScroll = Mth.clamp(textScroll - (int)Math.signum(amount), 0, Math.max(0, lines.size() - textRows())); return true; }
        if (complete && y >= 42) { viewport.zoom(amount); return true; }
        return super.mouseScrolled(x, y, amount);
    }
    @Override public void onClose() { if (!complete) finish(); else minecraft.setScreen(editor.workbenchOpen() ? editor : null); }
    @Override public void removed() { stopSounds(); viewport.release(minecraft); }
    @Override public boolean isPauseScreen() { return false; }
}
