package dev.bladetetra.client;

import dev.bladetetra.BladeTetra;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import dev.bladetetra.customstyle.*;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Opt-in isolated client input test. Taps vanilla key bindings, never progressCombo. */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID, value = Dist.CLIENT)
public final class CustomStyleInputReview {
    private static int ticks;
    private static int uiStep, waitTicks;
    private static String originalClipboard;
    private static final CustomStyleSpec IMPORT = new CustomStyleSpec("Import review 六招", CustomStyleInputReviewSetup.route(3));
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (!Boolean.getBoolean("blade_tetra.customStyleInputReview") || event.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || (mc.screen != null && ticks < 1060)) return;
        try {
            ++ticks;
            if (ticks >= 100 && ticks < 1060) {
                int scenario = (ticks - 100) / 160, offset = (ticks - 100) % 160;
                if (offset == 0) mc.getConnection().sendCommand("blade_tetra_input_case " + scenario);
                if (offset >= 20 && offset <= 120 && offset % 20 == 0) {
                    KeyMapping.click(mc.options.keyUse.getKey());
                    com.mojang.logging.LogUtils.getLogger().info("CUSTOM_KEY_TAP case={} tick={}", scenario, ticks);
                }
            }
            if (ticks >= 1060) reviewUi(mc);
        } catch (Throwable failure) {
            com.mojang.logging.LogUtils.getLogger().error("CUSTOM_CLIENT_REVIEW_FAIL", failure); mc.stop();
        }
    }

    private static void reviewUi(Minecraft mc) throws Exception {
        if (++waitTicks > 300) throw new AssertionError("UI review timed out step=" + uiStep);
        switch (uiStep) {
            case 0 -> {
                originalClipboard = mc.keyboardHandler.getClipboard();
                mc.getConnection().sendCommand("blade_tetra_style edit"); next();
            }
            case 1 -> { if (mc.screen instanceof CustomStyleEditorScreen) {
                click(mc, "screen.blade_tetra.custom_style.import"); next();
            }}
            case 2 -> { if (mc.screen instanceof CustomStyleExchangeScreen) {
                codeBox(mc).setValue(CustomStyleExchange.export(IMPORT));
                click(mc, "screen.blade_tetra.exchange.preview"); next();
            }}
            case 3 -> { if (button(mc, "screen.blade_tetra.exchange.confirm").active) {
                capture(mc, "import-preview.png"); click(mc, "screen.blade_tetra.exchange.confirm");
                if (!(mc.screen instanceof ConfirmScreen)) throw new AssertionError("Existing draft was not protected by overwrite confirmation");
                next();
            }}
            case 4 -> { click(mc, "gui.yes"); next(); }
            case 5 -> { if (mc.screen instanceof CustomStyleEditorScreen) {
                var name = mc.screen.children().stream().filter(net.minecraft.client.gui.components.EditBox.class::isInstance)
                        .map(net.minecraft.client.gui.components.EditBox.class::cast).findFirst().orElseThrow();
                if (!name.getValue().equals(IMPORT.name())) throw new AssertionError("Imported name was not acknowledged by server");
                capture(mc, "imported-editor.png"); click(mc, "screen.blade_tetra.custom_style.export"); next();
            }}
            case 6 -> { click(mc, "screen.blade_tetra.exchange.draft"); next(); }
            case 7 -> { if (!codeBox(mc).getValue().isEmpty()) {
                if (!IMPORT.equals(CustomStyleExchange.inspect(codeBox(mc).getValue()).style())) throw new AssertionError("Server draft export differs from import");
                capture(mc, "export-code.png"); click(mc, "screen.blade_tetra.exchange.copy");
                if (!mc.keyboardHandler.getClipboard().equals(codeBox(mc).getValue())) throw new AssertionError("Clipboard truncated share code");
                click(mc, "screen.blade_tetra.exchange.scroll"); next();
            }}
            case 8 -> { if (!button(mc, "screen.blade_tetra.exchange.copy").active) return;
                var style = CustomStyleExchange.inspect(codeBox(mc).getValue()).style();
                if (style != null && style.name().equals("Held scroll")) {
                    if (!style.actions().equals(CustomStyleInputReviewSetup.route(1))) throw new AssertionError("Held scroll order changed");
                    com.mojang.logging.LogUtils.getLogger().info("CUSTOM_CLIENT_UI_PASS: server preview, overwrite confirmation, six-action draft import, draft and held-scroll export, full clipboard copy");
                    mc.keyboardHandler.setClipboard(originalClipboard);
                    com.mojang.logging.LogUtils.getLogger().info("CUSTOM_CLIENT_INPUT_REVIEW_DONE"); mc.stop();
                }
            }
        }
    }
    private static void next() { uiStep++; waitTicks = 0; }
    private static Button button(Minecraft mc, String key) {
        return mc.screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(button -> button.getMessage().getContents() instanceof TranslatableContents text && text.getKey().equals(key))
                .findFirst().orElseThrow(() -> new AssertionError("Missing button " + key));
    }
    private static void click(Minecraft mc, String key) {
        Button button = button(mc, key);
        if (!button.active) throw new AssertionError("Inactive button " + key);
        mc.screen.mouseClicked(button.getX() + button.getWidth() / 2., button.getY() + 9, 0);
        mc.screen.mouseReleased(button.getX() + button.getWidth() / 2., button.getY() + 9, 0);
    }
    private static MultiLineEditBox codeBox(Minecraft mc) {
        return mc.screen.children().stream().filter(MultiLineEditBox.class::isInstance)
                .map(MultiLineEditBox.class::cast).findFirst().orElseThrow();
    }
    private static void capture(Minecraft mc, String name) throws Exception {
        var directory = java.nio.file.Path.of(mc.gameDirectory.getAbsolutePath(), "review-screenshots");
        java.nio.file.Files.createDirectories(directory);
        try (var image = net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())) { image.writeToFile(directory.resolve(name)); }
    }
    private CustomStyleInputReview() {}
}
