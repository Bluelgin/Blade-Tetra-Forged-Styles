package dev.bladetetra.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.bladetetra.forging.LegacyCalibrationProfile;
import dev.bladetetra.forging.LegacyImprintKind;
import dev.bladetetra.network.LegacyImprintOpenPacket;
import dev.bladetetra.network.LegacyImprintResultPacket;
import dev.bladetetra.network.ModNetwork;
import mods.flammpfeil.slashblade.client.renderer.model.BladeModelManager;
import mods.flammpfeil.slashblade.client.renderer.model.obj.WavefrontObject;
import mods.flammpfeil.slashblade.client.renderer.util.BladeRenderState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Three-part, model-agnostic ritual used to study a named SlashBlade. */
public final class LegacyImprintScreen extends Screen {
    private static final int OBSERVE_REVEAL_TICKS = 66;
    private static final int TRACE_POINTS = 25;
    private static final float[] HAMMER_TARGETS = { .19F, .68F, .37F, .82F };

    private enum Phase { OBSERVE, TRACE, HAMMER, RESULT }

    private final LegacyImprintKind kind;
    private final LegacyCalibrationProfile profile;
    private final int booster;
    private final boolean protectedBlade;
    private final int[] observeOrder = new int[3];
    private final List<Spark> sparks = new ArrayList<>();
    private Phase phase = Phase.OBSERVE;
    private int phaseTicks;
    private int observeStep;
    private int observeScore = 100;
    private int traceStep;
    private int traceTotal;
    private int traceMisses;
    private int lastTraceMissTick = -10;
    private boolean tracing;
    private int hammerStep;
    private int hammerTotal;
    private int impactTicks;
    private boolean reducedMotion;
    private boolean sent;
    private Button confirmButton;

    private LegacyImprintScreen(LegacyImprintOpenPacket packet) {
        super(Component.translatable("screen.blade_tetra.imprint.title"));
        kind = dev.bladetetra.forging.NamedLegacyCatalog.get(packet.kind());
        if (kind == null) throw new IllegalArgumentException("Unknown named pattern " + packet.kind());
        profile = kind.defaultProfile().normalized();
        booster = packet.booster();
        protectedBlade = packet.protectedBlade();
        int offset = Math.floorMod(kind.id().hashCode(), 3);
        observeOrder[0] = offset;
        observeOrder[1] = (offset + 2) % 3;
        observeOrder[2] = (offset + 1) % 3;
    }

    public static void open(LegacyImprintOpenPacket packet) {
        Minecraft.getInstance().setScreen(new LegacyImprintScreen(packet));
    }

    @Override
    protected void init() {
        int cx = width / 2;
        int top = panelTop();
        confirmButton = addRenderableWidget(Button.builder(
                        Component.translatable("screen.blade_tetra.imprint.record"), b -> submit())
                .bounds(cx - 60, top + panelHeight() - 28, 120, 20).build());
        addRenderableWidget(Button.builder(motionLabel(), b -> {
                    reducedMotion = !reducedMotion;
                    b.setMessage(motionLabel());
                }).bounds(cx + panelHalfWidth() - 94, top + 8, 82, 18).build());
        updateButtons();
    }

    private Component motionLabel() {
        return Component.translatable(reducedMotion
                ? "screen.blade_tetra.imprint.motion_reduced"
                : "screen.blade_tetra.imprint.motion_full");
    }

    private void updateButtons() {
        if (confirmButton != null) {
            confirmButton.visible = phase == Phase.RESULT && phaseTicks >= 34;
            confirmButton.active = confirmButton.visible && !sent;
        }
    }

    @Override
    public void tick() {
        phaseTicks++;
        if (impactTicks > 0) impactTicks--;
        Iterator<Spark> iterator = sparks.iterator();
        while (iterator.hasNext()) {
            Spark spark = iterator.next();
            spark.tick();
            if (spark.life <= 0) iterator.remove();
        }
        updateButtons();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        int cx = width / 2;
        int top = panelTop();
        int half = panelHalfWidth();
        int bottom = top + panelHeight();
        graphics.fill(cx - half, top, cx + half, bottom, 0xF0181114);
        graphics.fill(cx - half + 3, top + 3, cx + half - 3, bottom - 3, 0xFFF0E2C5);
        graphics.fill(cx - half + 7, top + 30, cx + half - 7, top + 31, 0xFF8D2634);
        renderPaperGrain(graphics, cx, top, partialTick);
        graphics.drawCenteredString(font, title, cx, top + 9, 0x3A2522);
        graphics.drawCenteredString(font, Component.translatable(kind.translationKey()), cx,
                top + 21, kind.id().contains("black") ? 0x423941 : 0x8B5357);
        renderStageRail(graphics, cx, top);
        renderStatus(graphics, cx, top);
        switch (phase) {
            case OBSERVE -> renderObserve(graphics, cx, top, partialTick);
            case TRACE -> renderTrace(graphics, cx, top, partialTick);
            case HAMMER -> renderHammer(graphics, cx, top, partialTick);
            case RESULT -> renderResult(graphics, cx, top, partialTick);
        }
        renderSparks(graphics, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderStageRail(GuiGraphics graphics, int cx, int top) {
        int x = cx - panelHalfWidth() + 15;
        int y = top + 51;
        String[] names = {"stage_observe", "stage_trace", "stage_hammer"};
        for (int i = 0; i < names.length; i++) {
            boolean done = phase.ordinal() > i;
            boolean current = phase.ordinal() == i;
            int color = done ? 0x3E7A55 : current ? 0x9D2432 : 0x8A7769;
            graphics.fill(x, y + i * 25, x + 7, y + 7 + i * 25, 0xFF000000 | color);
            graphics.drawString(font, Component.translatable("screen.blade_tetra.imprint." + names[i]),
                    x + 12, y - 1 + i * 25, color, false);
        }
    }

    private void renderStatus(GuiGraphics graphics, int cx, int top) {
        int x = cx + panelHalfWidth() - 105;
        int y = top + 51;
        graphics.drawString(font, Component.translatable("screen.blade_tetra.imprint.affinity", liveScore()),
                x, y, 0x70443B, false);
        graphics.drawString(font, Component.translatable("screen.blade_tetra.imprint.booster", booster),
                x, y + 16, booster > 0 ? 0x876C3F : 0x8A7769, false);
        graphics.drawString(font, Component.translatable(protectedBlade
                        ? "screen.blade_tetra.imprint.protected" : "screen.blade_tetra.imprint.unprotected"),
                x, y + 32, protectedBlade ? 0x3E7A55 : 0xA3323E, false);
    }

    private void renderObserve(GuiGraphics graphics, int cx, int top, float partialTick) {
        renderSourcePart(graphics, cx, top + 111, "blade", partialTick);
        renderSourcePart(graphics, cx, top + 150, "sheath", partialTick);
        if (phaseTicks < OBSERVE_REVEAL_TICKS) {
            int reveal = Math.min(2, phaseTicks / 22);
            drawPartSeal(graphics, partX(cx, observeOrder[reveal]), partY(top, observeOrder[reveal]),
                    0xFFE6B84A, 11 + (int) (2 * Math.sin((phaseTicks + partialTick) * .28F)));
        } else {
            for (int i = 0; i < 3; i++) {
                int part = observeOrder[i];
                int color = i < observeStep ? 0xFF4B8A5D : i == observeStep ? 0xFF9D2432 : 0xFF806D61;
                drawPartSeal(graphics, partX(cx, part), partY(top, part), color, i == observeStep ? 12 : 8);
            }
        }
        graphics.drawCenteredString(font, Component.translatable(phaseTicks < OBSERVE_REVEAL_TICKS
                        ? "screen.blade_tetra.imprint.observe_watch" : "screen.blade_tetra.imprint.observe_repeat"),
                cx, top + 190, 0x66534B);
    }

    private void renderTrace(GuiGraphics graphics, int cx, int top, float partialTick) {
        renderSourcePart(graphics, cx, top + 104, "blade", partialTick);
        int lastX = traceX(cx, 0);
        int lastY = traceY(top, 0);
        for (int i = 1; i < TRACE_POINTS; i++) {
            int x = traceX(cx, i);
            int y = traceY(top, i);
            drawLine(graphics, lastX, lastY, x, y, i <= traceStep ? 0xFFFFCA55 : 0x886E5C53,
                    i <= traceStep ? 2 : 1);
            lastX = x;
            lastY = y;
        }
        if (traceStep < TRACE_POINTS) {
            int pulse = 5 + (int) Math.abs(Math.sin((phaseTicks + partialTick) * .22F) * 3);
            drawPartSeal(graphics, traceX(cx, traceStep), traceY(top, traceStep), 0xFFE8A629, pulse);
        }
        graphics.drawCenteredString(font, Component.translatable("screen.blade_tetra.imprint.trace_help"),
                cx, top + 190, 0x66534B);
        graphics.drawCenteredString(font, Component.translatable("screen.blade_tetra.imprint.trace_progress",
                traceStep, TRACE_POINTS), cx, top + 204, 0x876C3F);
    }

    private void renderHammer(GuiGraphics graphics, int cx, int top, float partialTick) {
        int shake = reducedMotion || impactTicks <= 0 ? 0 : (impactTicks % 2 == 0 ? 2 : -2);
        renderSourcePart(graphics, cx + shake, top + 105, "blade", partialTick);
        int left = cx - 108;
        int right = cx + 108;
        int y = top + 158;
        graphics.fill(left, y, right, y + 5, 0xFF564A42);
        float target = HAMMER_TARGETS[Math.min(hammerStep, HAMMER_TARGETS.length - 1)];
        int tx = Math.round(Mth.lerp(target, left, right));
        graphics.fill(tx - 12, y - 3, tx + 12, y + 8, 0xAA9D2432);
        int cursor = Math.round(Mth.lerp(hammerCursor(partialTick), left, right));
        graphics.fill(cursor - 2, y - 8, cursor + 2, y + 13, 0xFFFFD56A);
        drawPartSeal(graphics, tx, top + 105, 0xFFB42C38, 8);
        graphics.drawCenteredString(font, Component.translatable("screen.blade_tetra.imprint.hammer_help"),
                cx, top + 190, 0x66534B);
        graphics.drawCenteredString(font, Component.translatable("screen.blade_tetra.imprint.hammer_progress",
                hammerStep, HAMMER_TARGETS.length), cx, top + 204, 0x876C3F);
    }

    private void renderResult(GuiGraphics graphics, int cx, int top, float partialTick) {
        int settle = reducedMotion ? 0 : Math.max(0, 18 - phaseTicks);
        renderSourcePart(graphics, cx, top + 110 - settle / 3, "blade", partialTick);
        int score = finalScore();
        int sealColor = score >= 90 ? 0xFFD6A83D : score >= 70 ? 0xFF9D2432 : 0xFF71534D;
        drawDiamond(graphics, cx, top + 152, Math.min(31, Math.max(3, phaseTicks)), sealColor);
        graphics.drawCenteredString(font, Component.translatable("screen.blade_tetra.imprint.result"),
                cx, top + 186, 0x3A2522);
        graphics.drawCenteredString(font, Component.translatable("screen.blade_tetra.imprint.result_score",
                Component.translatable(gradeKey(score)), score), cx, top + 201, sealColor & 0xFFFFFF);
        graphics.drawCenteredString(font, Component.translatable("screen.blade_tetra.imprint.result_parts",
                observeScore, traceScore(), hammerScore()), cx, top + 216, 0x66534B);
    }

    private void renderPaperGrain(GuiGraphics graphics, int cx, int top, float partialTick) {
        if (reducedMotion) return;
        int half = panelHalfWidth();
        int seed = kind.id().hashCode();
        for (int i = 0; i < 18; i++) {
            int x = cx - half + 9 + Math.floorMod(seed + i * 47, half * 2 - 18);
            float drift = Mth.positiveModulo(phaseTicks + partialTick + i * 13, 150.0F);
            int y = top + 35 + Math.round(drift * (panelHeight() - 52) / 150.0F);
            graphics.fill(x, y, x + 1, y + 1, i % 3 == 0 ? 0x55A3323E : 0x337B684F);
        }
    }

    private void renderSparks(GuiGraphics graphics, float partialTick) {
        for (Spark spark : sparks) {
            float age = spark.maxLife - spark.life + partialTick;
            int x = Math.round(spark.x + spark.vx * age);
            int y = Math.round(spark.y + spark.vy * age + age * age * .025F);
            int alpha = Mth.clamp(Math.round(255.0F * spark.life / spark.maxLife), 0, 255);
            graphics.fill(x, y, x + 2, y + 2, (alpha << 24) | spark.color);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button != 0 || phase == Phase.RESULT) return false;
        int cx = width / 2;
        int top = panelTop();
        if (phase == Phase.OBSERVE && phaseTicks >= OBSERVE_REVEAL_TICKS) {
            int clicked = nearestPart(cx, top, mouseX, mouseY);
            if (clicked >= 0) attemptObserve(clicked, cx, top);
            return true;
        }
        if (phase == Phase.TRACE) {
            tracing = near(mouseX, mouseY, traceX(cx, traceStep), traceY(top, traceStep), traceTolerance());
            if (tracing) advanceTrace(mouseX, mouseY, cx, top);
            return true;
        }
        if (phase == Phase.HAMMER) {
            attemptHammer(cx, top);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (phase == Phase.TRACE && tracing && button == 0) {
            advanceTrace(mouseX, mouseY, width / 2, panelTop());
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (tracing) {
            tracing = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (phase == Phase.HAMMER && keyCode == GLFW.GLFW_KEY_SPACE) {
            attemptHammer(width / 2, panelTop());
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void attemptObserve(int clicked, int cx, int top) {
        if (clicked == observeOrder[observeStep]) {
            spawnSparks(partX(cx, clicked), partY(top, clicked), 0xE8B84A, 7);
            play(SoundEvents.AMETHYST_BLOCK_CHIME, .85F + observeStep * .12F);
            observeStep++;
            if (observeStep >= observeOrder.length) beginPhase(Phase.TRACE);
        } else {
            observeScore = Math.max(40, observeScore - 16);
            play(SoundEvents.NOTE_BLOCK_BASS.get(), .55F);
        }
    }

    private void advanceTrace(double mouseX, double mouseY, int cx, int top) {
        if (traceStep >= TRACE_POINTS) return;
        double distance = distance(mouseX, mouseY, traceX(cx, traceStep), traceY(top, traceStep));
        int tolerance = traceTolerance();
        if (distance <= tolerance) {
            traceTotal += Mth.clamp((int) Math.round(100.0D - distance / tolerance * 38.0D), 55, 100);
            if (traceStep % 4 == 0) spawnSparks(traceX(cx, traceStep), traceY(top, traceStep), 0xF4C95D, 3);
            traceStep++;
            if (traceStep >= TRACE_POINTS) {
                play(SoundEvents.PLAYER_ATTACK_SWEEP, 1.15F);
                beginPhase(Phase.HAMMER);
            }
        } else if (phaseTicks - lastTraceMissTick > 2) {
            traceMisses++;
            lastTraceMissTick = phaseTicks;
        }
    }

    private void attemptHammer(int cx, int top) {
        if (hammerStep >= HAMMER_TARGETS.length) return;
        float error = Math.abs(hammerCursor(0.0F) - HAMMER_TARGETS[hammerStep]);
        int hit = Mth.clamp(Math.round(100.0F * (1.0F - error / .22F)), 0, 100);
        hammerTotal += hit;
        impactTicks = 7;
        int x = Math.round(Mth.lerp(HAMMER_TARGETS[hammerStep], cx - 108, cx + 108));
        spawnSparks(x, top + 105, hit >= 90 ? 0xFFF1B0 : 0xD84B38, hit >= 90 ? 16 : 9);
        play(hit >= 90 ? SoundEvents.ANVIL_LAND : SoundEvents.ANVIL_PLACE,
                hit >= 90 ? 1.25F : .86F);
        hammerStep++;
        if (hammerStep >= HAMMER_TARGETS.length) beginPhase(Phase.RESULT);
    }

    private void beginPhase(Phase next) {
        phase = next;
        phaseTicks = 0;
        tracing = false;
        if (next == Phase.RESULT) {
            spawnSparks(width / 2, panelTop() + 150, 0xE8B84A, reducedMotion ? 5 : 28);
            play(SoundEvents.ENCHANTMENT_TABLE_USE, 1.05F);
        } else {
            play(SoundEvents.BOOK_PAGE_TURN, 1.0F);
        }
        updateButtons();
    }

    private int liveScore() {
        return switch (phase) {
            case OBSERVE -> observeScore;
            case TRACE -> Math.round((observeScore + Math.max(0, traceScore())) / 2.0F);
            case HAMMER -> Math.round((observeScore + traceScore() + Math.max(0, hammerScore())) / 3.0F);
            case RESULT -> finalScore();
        };
    }

    private int traceScore() {
        if (traceStep == 0) return 0;
        return Mth.clamp(Math.round(traceTotal / (float) traceStep) - Math.min(30, traceMisses * 2), 0, 100);
    }

    private int hammerScore() {
        return hammerStep == 0 ? 0 : Mth.clamp(Math.round(hammerTotal / (float) hammerStep), 0, 100);
    }

    private int finalScore() {
        return Mth.clamp(Math.round((observeScore + traceScore() + hammerScore()) / 3.0F), 0, 100);
    }

    private String gradeKey(int score) {
        return "screen.blade_tetra.imprint.grade." + (score >= 90 ? "lifelike"
                : score >= 70 ? "exquisite" : score >= 50 ? "mature" : "initial");
    }

    private float hammerCursor(float partialTick) {
        float period = Math.max(30.0F, 51.0F - hammerStep * 4.0F);
        float cycle = Mth.positiveModulo(phaseTicks + partialTick, period);
        float half = period * .5F;
        return cycle <= half ? cycle / half : 2.0F - cycle / half;
    }

    private int traceX(int cx, int point) {
        return cx - 108 + Math.round(point * 216.0F / (TRACE_POINTS - 1));
    }

    private int traceY(int top, int point) {
        float seed = Math.floorMod(kind.id().hashCode(), 31) * .07F;
        return top + 150 + Math.round(Mth.sin(point * .54F + seed) * 9.0F
                + Mth.sin(point * .19F + seed * 2) * 4.0F);
    }

    private int traceTolerance() {
        return 11 + Math.min(6, booster / 5);
    }

    private int nearestPart(int cx, int top, double mouseX, double mouseY) {
        int best = -1;
        double bestDistance = 25.0D;
        for (int part = 0; part < 3; part++) {
            double candidate = distance(mouseX, mouseY, partX(cx, part), partY(top, part));
            if (candidate < bestDistance) {
                bestDistance = candidate;
                best = part;
            }
        }
        return best;
    }

    private int partX(int cx, int part) {
        if (part == 2) return cx;
        float center = part == 0 ? profile.tsubaCenter() : profile.tsukaCenter();
        if (profile.flipped()) center = 1.0F - center;
        return Math.round(Mth.lerp(center, cx - 104, cx + 104));
    }

    private int partY(int top, int part) {
        return part == 2 ? top + 150 : top + 111;
    }

    private static boolean near(double x, double y, int targetX, int targetY, int radius) {
        return distance(x, y, targetX, targetY) <= radius;
    }

    private static double distance(double x, double y, int targetX, int targetY) {
        return Math.sqrt((x - targetX) * (x - targetX) + (y - targetY) * (y - targetY));
    }

    private void spawnSparks(float x, float y, int color, int count) {
        for (int i = 0; i < count; i++) {
            float angle = (float) (Math.PI * 2.0D * i / Math.max(1, count));
            float speed = .45F + (i % 4) * .18F;
            sparks.add(new Spark(x, y, Mth.cos(angle) * speed,
                    Mth.sin(angle) * speed - .25F, 15 + i % 8, color));
        }
    }

    private void play(net.minecraft.sounds.SoundEvent event, float pitch) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(event, pitch));
    }

    private void renderSourcePart(GuiGraphics graphics, int cx, int cy, String group, float partialTick) {
        PoseStack poses = graphics.pose();
        poses.pushPose();
        float[] axis = LegacyModelPartRenderer.previewBounds(kind.model());
        float bob = reducedMotion ? 0.0F : Mth.sin((phaseTicks + partialTick) * .055F) * 1.6F;
        poses.translate(cx, cy + bob, 180.0F);
        float scale = axis[1];
        poses.scale(profile.flipped() ? -scale : scale, -scale, scale);
        poses.translate(-axis[0], 0, 0);
        try {
            WavefrontObject model = BladeModelManager.getInstance().getModel(kind.model());
            BladeRenderState.resetCol();
            BladeRenderState.renderOverrided(ItemStack.EMPTY, model, group, kind.texture(), poses,
                    Minecraft.getInstance().renderBuffers().bufferSource(), 0xF000F0);
            Minecraft.getInstance().renderBuffers().bufferSource().endBatch();
        } finally {
            poses.popPose();
            BladeRenderState.resetCol();
        }
    }

    private static void drawPartSeal(GuiGraphics graphics, int x, int y, int color, int radius) {
        graphics.fill(x - radius, y, x + radius + 1, y + 1, color);
        graphics.fill(x, y - radius, x + 1, y + radius + 1, color);
        int d = Math.max(2, radius / 2);
        graphics.fill(x - d, y - d, x - d + 2, y - d + 2, color);
        graphics.fill(x + d - 1, y - d, x + d + 1, y - d + 2, color);
        graphics.fill(x - d, y + d - 1, x - d + 2, y + d + 1, color);
        graphics.fill(x + d - 1, y + d - 1, x + d + 1, y + d + 1, color);
    }

    private static void drawDiamond(GuiGraphics graphics, int cx, int cy, int radius, int color) {
        for (int y = -radius; y <= radius; y++) {
            int half = radius - Math.abs(y);
            graphics.fill(cx - half, cy + y, cx + half + 1, cy + y + 1, color);
        }
        int inner = Math.max(0, radius - 3);
        for (int y = -inner; y <= inner; y++) {
            int half = inner - Math.abs(y);
            graphics.fill(cx - half, cy + y, cx + half + 1, cy + y + 1, 0x66F0E2C5);
        }
    }

    private static void drawLine(GuiGraphics graphics, int x1, int y1, int x2, int y2,
            int color, int thickness) {
        int steps = Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1));
        for (int i = 0; i <= steps; i++) {
            float t = steps == 0 ? 0 : i / (float) steps;
            int x = Math.round(Mth.lerp(t, x1, x2));
            int y = Math.round(Mth.lerp(t, y1, y2));
            graphics.fill(x, y, x + thickness, y + thickness, color);
        }
    }

    private int panelHalfWidth() {
        return Math.min(220, Math.max(174, width / 2 - 7));
    }

    private int panelHeight() {
        return Math.min(292, height - 14);
    }

    private int panelTop() {
        return Math.max(7, (height - panelHeight()) / 2);
    }

    private void submit() {
        if (sent || phase != Phase.RESULT || phaseTicks < 34) return;
        sent = true;
        ModNetwork.CHANNEL.sendToServer(new LegacyImprintResultPacket(
                finalScore(), profile.tsubaCenter(), profile.tsubaRadius(),
                profile.tsukaCenter(), profile.tsukaRadius(), profile.tsubaTransform(),
                profile.tsukaTransform(), profile.sayaTransform(), profile.bladeTransform(), profile.flipped()));
        onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static final class Spark {
        private final float x;
        private final float y;
        private final float vx;
        private final float vy;
        private final int maxLife;
        private final int color;
        private int life;

        private Spark(float x, float y, float vx, float vy, int life, int color) {
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.vy = vy;
            this.life = life;
            this.maxLife = life;
            this.color = color;
        }

        private void tick() {
            life--;
        }
    }
}
