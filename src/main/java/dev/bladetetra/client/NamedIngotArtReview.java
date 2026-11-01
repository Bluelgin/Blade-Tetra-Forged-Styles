package dev.bladetetra.client;

import com.mojang.logging.LogUtils;
import dev.bladetetra.registry.ModItems;
import dev.bladetetra.item.NamedBladeIngotItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.nio.file.Files;

/** Opt-in actual creative inventory screenshot in a disposable integrated world. */
@Mod.EventBusSubscriber(modid = "blade_tetra", value = Dist.CLIENT)
public final class NamedIngotArtReview {
    private static boolean started, opened;
    private static int ticks, ready;

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (!Boolean.getBoolean("blade_tetra.namedIngotArtReview")
                || event.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        if (++ticks > 6000) throw new IllegalStateException("Named ingot review timed out");
        if (!started && mc.screen instanceof TitleScreen && mc.getOverlay() == null) {
            started = true;
            String world = "named-ingot-art-review";
            if (Files.exists(mc.gameDirectory.toPath().resolve("saves/" + world + "/level.dat")))
                mc.createWorldOpenFlows().loadLevel(mc.screen, world);
            else mc.createWorldOpenFlows().createFreshLevel(world,
                    new LevelSettings(world, GameType.CREATIVE, false, Difficulty.PEACEFUL,
                            true, new GameRules(), WorldDataConfiguration.DEFAULT),
                    new WorldOptions(158L, false, false),
                    registries -> registries.registryOrThrow(Registries.WORLD_PRESET)
                            .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
        }
        if (!opened && mc.player != null && mc.level != null && mc.screen == null && ++ready > 40) {
            opened = true;
            mc.options.guiScale().set(4);
            mc.resizeDisplay();
            mc.setScreen(new Review(mc));
        }
    }

    private static final class Review extends CreativeModeInventoryScreen {
        private int frames;
        Review(Minecraft mc) {
            super(mc.player, mc.level.enabledFeatures(), true);
        }
        @Override protected void init() {
            super.init();
            try {
                // Vanilla keeps tab selection private. This development-only
                // adapter selects the real registered tab, without rebuilding UI.
                var select = java.util.Arrays.stream(CreativeModeInventoryScreen.class.getDeclaredMethods())
                        .filter(m -> m.getReturnType() == void.class
                                && java.util.Arrays.equals(m.getParameterTypes(), new Class<?>[]{CreativeModeTab.class}))
                        .findFirst().orElseThrow();
                select.setAccessible(true);
                select.invoke(this, ModItems.MAIN_TAB.get());
                long ingots = menu.slots.stream().map(slot -> slot.getItem())
                        .filter(stack -> stack.getItem() instanceof NamedBladeIngotItem).count();
                if (ingots < 2) throw new IllegalStateException("Named ingot section missing: " + ingots);
                LogUtils.getLogger().info("NAMED_INGOT_CREATIVE_OPEN: {} visible source ingots", ingots);
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException(failure);
            }
        }
        @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partial) {
            super.render(graphics, 0, 0, partial);
            if (++frames == 40) {
                graphics.flush();
                if (NamedBladeIngotRenderer.cachedSources() < 2)
                    throw new IllegalStateException("Source art did not render; fallback is not an art pass");
                var mc = Minecraft.getInstance();
                try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                    image.writeToFile(mc.gameDirectory.toPath().resolve("named-ingot-creative.png"));
                    LogUtils.getLogger().info("NAMED_INGOT_ART_REVIEW_PASS: actual creative inventory, integrated world");
                } catch (java.io.IOException failure) { throw new IllegalStateException(failure); }
                mc.stop();
            }
        }
    }
    private NamedIngotArtReview() {}
}
