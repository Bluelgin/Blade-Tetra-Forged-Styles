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
    private static int ticks, ready, bladeFrame, bladeView;
    private static boolean sawNamedBlade;
    private static net.minecraft.world.item.ItemStack reviewBlade;

    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST, receiveCanceled = true)
    public static void rendered(mods.flammpfeil.slashblade.event.client.RenderOverrideEvent event) {
        if (reviewBlade != null && event.getStack() == reviewBlade
                && "blade".equals(event.getOriginalTarget()) && event.isCanceled()) {
            sawNamedBlade = true;
        }
    }

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
            if (bladeView < 6) {
                reviewNamedBlade(mc);
                return;
            }
            opened = true;
            if (net.minecraftforge.fml.ModList.get().isLoaded("patchouli")) {
                var server = mc.getSingleplayerServer();
                var id = mc.player.getUUID();
                server.execute(() -> {
                    var player = server.getPlayerList().getPlayer(id);
                    dev.bladetetra.lore.SmithingLore.discover(player, "akatsuki");
                    var original = ModItems.SMITHING_JOURNAL.get().getDefaultInstance();
                    dev.bladetetra.item.SmithingJournalItem.refresh(original, player);
                    if (original.getTag().getInt("BladeTetraKnownClues") != 1)
                        throw new IllegalStateException("Original journal did not share the discovery");
                });
            }
            mc.options.guiScale().set(4);
            mc.resizeDisplay();
            mc.setScreen(new Review(mc));
        }
    }

    private static net.minecraft.world.item.ItemStack namedBlade(boolean longBlade) {
        var item = ModItems.MODULAR_SLASHBLADE.get();
        var stack = item.createDefaultStack();
        String module = longBlade ? item.CUSTOM_LONG_BLADE_MODULE : item.WAKIZASHI_MODULE;
        var tag = stack.getOrCreateTag();
        tag.putString(item.BLADE_SLOT, module);
        tag.putString(module + "_material", module.substring(module.indexOf('/') + 1) + "/named_blade_ingot");
        var source = dev.bladetetra.forging.LegacyImprintKind.BLACK_FOX;
        dev.bladetetra.forging.NamedLegacyImprintStorage.putSource(tag, "blade", source.id());
        if (!dev.bladetetra.forging.NamedLegacyImprintStorage.putSnapshot(stack, "blade", source)
                || !source.visualUsable()) {
            throw new IllegalStateException("Missing named blade source for the render review");
        }
        item.syncDerivedBladeState(stack);
        return stack;
    }

    private static void reviewNamedBlade(Minecraft mc) {
        if (++bladeFrame == 1) {
            var stack = namedBlade(bladeView >= 3);
            var server = mc.getSingleplayerServer();
            var playerId = mc.player.getUUID();
            server.execute(() -> {
                var player = server.getPlayerList().getPlayer(playerId);
                player.getInventory().setItem(player.getInventory().selected, stack.copy());
                player.inventoryMenu.broadcastChanges();
            });
            mc.player.getInventory().setItem(mc.player.getInventory().selected, stack);
            reviewBlade = stack;
            sawNamedBlade = false;
            mc.options.pauseOnLostFocus = false;
            mc.player.setXRot(12);
            mc.options.setCameraType(net.minecraft.client.CameraType.values()[bladeView % 3]);
        }
        if (bladeFrame == 10) {
            reviewBlade = mc.player.getMainHandItem();
            sawNamedBlade = false;
        }
        if (bladeFrame >= 32 && bladeFrame < 40) {
            mc.player.getMainHandItem().getCapability(mods.flammpfeil.slashblade.item.ItemSlashBlade.BLADESTATE)
                    .ifPresent(state -> {
                        state.setComboSeq(mods.flammpfeil.slashblade.registry.ComboStateRegistry.COMBO_A1.getId());
                        state.setLastActionTime(mc.level.getGameTime() - 4);
                    });
        }
        if (bladeFrame != 40) return;
        if (!sawNamedBlade) {
            throw new IllegalStateException("Named blade render hook missed held view " + bladeView);
        }
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            image.writeToFile(mc.gameDirectory.toPath().resolve("named-blade-held-" + bladeView + ".png"));
        } catch (java.io.IOException failure) {
            throw new IllegalStateException(failure);
        }
        bladeFrame = 0;
        if (++bladeView == 6) {
            mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
            reviewBlade = null;
            LogUtils.getLogger().info("NAMED_BLADE_HELD_REVIEW: short and custom long blade with ordinary fittings");
        }
    }

    private static final class Review extends CreativeModeInventoryScreen {
        private int frames, capture;
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
                select.invoke(this, ModItems.NAMED_INGOTS_TAB.get());
                // Also navigate the real Forge tab page, so the selected tab's
                // question-mark icon is visible in the screenshot.
                for (int page = 0; page < 32 && !getCurrentPage().getVisibleTabs()
                        .contains(ModItems.NAMED_INGOTS_TAB.get()); page++) {
                    var next = children().stream()
                            .filter(widget -> widget instanceof net.minecraft.client.gui.components.Button b
                                    && b.getMessage().getString().equals(">"))
                            .map(widget -> (net.minecraft.client.gui.components.Button) widget)
                            .findFirst().orElseThrow();
                    next.onPress();
                }
                if (!getCurrentPage().getVisibleTabs().contains(ModItems.NAMED_INGOTS_TAB.get()))
                    throw new IllegalStateException("Named ingot tab icon not visible");
                var contents = ModItems.NAMED_INGOTS_TAB.get().getDisplayItems();
                if (contents.size() != dev.bladetetra.forging.NamedLegacyCatalog.values().size()
                        || contents.stream().anyMatch(stack -> NamedBladeIngotItem.sourceId(stack) == null))
                    throw new IllegalStateException("Source list incomplete or contains catalog placeholder");
                if (ModItems.MAIN_TAB.get().getDisplayItems().stream()
                        .anyMatch(stack -> stack.getItem() instanceof NamedBladeIngotItem))
                    throw new IllegalStateException("Source ingots duplicated in main tab");
                if (NamedBladeIngotItem.sourceId(ModItems.NAMED_INGOTS_TAB.get().getIconItem()) != null)
                    throw new IllegalStateException("Tab icon must be the question-mark ingot");
                long ingots = menu.slots.stream().map(slot -> slot.getItem())
                        .filter(stack -> stack.getItem() instanceof NamedBladeIngotItem).count();
                if (ingots < 2) throw new IllegalStateException("Named ingot section missing: " + ingots);
                var providers = new java.util.TreeMap<String, Integer>();
                for (var kind : dev.bladetetra.forging.NamedLegacyCatalog.values())
                    providers.merge(kind.name().getNamespace(), 1, Integer::sum);
                LogUtils.getLogger().info("NAMED_INGOT_CREATIVE_OPEN: {} visible / {} total source ingots; providers {}",
                        ingots, contents.size(), providers);
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
                if (capture == 0) verifyBackplates();
                var mc = Minecraft.getInstance();
                try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                    String file = capture == 0 ? "named-ingot-creative.png"
                            : "named-ingot-creative-" + capture + ".png";
                    image.writeToFile(mc.gameDirectory.toPath().resolve(file));
                    LogUtils.getLogger().info("NAMED_INGOT_ART_CAPTURE: {}", file);
                } catch (java.io.IOException failure) { throw new IllegalStateException(failure); }
                if (++capture < 3 && ModItems.NAMED_INGOTS_TAB.get().getDisplayItems().size() > 45) {
                    // Drive vanilla scrolling, so both visible slots and the
                    // native scrollbar position agree in the real screenshot.
                    mouseScrolled(0, 0, capture == 1 ? -4 : -1000);
                    frames = 0;
                } else {
                    verifyManual(mc);
                    LogUtils.getLogger().info("NAMED_INGOT_ART_REVIEW_PASS: actual creative inventory, {} captures", capture);
                    if (!ManualArtReview.enabled()) mc.setScreen(new BladeJointReview());
                }
            }
        }
    }

    /** Draws the exposed world mesh through the normal render hook alongside the real item icon. */
    private static final class BladeJointReview extends net.minecraft.client.gui.screens.Screen {
        private int frames;
        private final java.util.List<net.minecraft.world.item.ItemStack> blades = java.util.List.of(
                namedBlade(false), namedBlade(true));

        BladeJointReview() {
            super(net.minecraft.network.chat.Component.literal("名刀刀身接合检查"));
        }

        @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partial) {
            graphics.fill(0, 0, width, height, 0xff28323c);
            for (int row = 0; row < blades.size(); row++) {
                var stack = blades.get(row);
                int y = height * (row + 1) / 3;
                graphics.drawString(font, stack.getHoverName(), width / 12, y - 33, 0xffffff);
                var poses = graphics.pose();
                graphics.flush();
                poses.pushPose();
                poses.translate(width * .74, y, 150);
                float scale = width * .48F / 440F;
                poses.scale(scale, -scale, scale);
                var state = stack.getCapability(mods.flammpfeil.slashblade.item.ItemSlashBlade.BLADESTATE)
                        .resolve().orElseThrow();
                var model = mods.flammpfeil.slashblade.client.renderer.model.BladeModelManager.getInstance()
                        .getModel(state.getModel().orElseThrow());
                mods.flammpfeil.slashblade.client.renderer.util.BladeRenderState.renderOverrided(
                        stack, model, "blade", state.getTexture().orElseThrow(), poses,
                        graphics.bufferSource(), net.minecraft.client.renderer.LightTexture.FULL_BRIGHT);
                graphics.flush();
                poses.popPose();
                poses.pushPose();
                poses.translate(width * .81, y - 20, 0);
                poses.scale(2.5F, 2.5F, 1);
                graphics.renderItem(stack, 0, 0);
                graphics.flush();
                poses.popPose();
            }
            if (++frames == 20) {
                try (var image = Screenshot.takeScreenshot(minecraft.getMainRenderTarget())) {
                    image.writeToFile(minecraft.gameDirectory.toPath().resolve("named-blade-joints.png"));
                } catch (java.io.IOException failure) {
                    throw new IllegalStateException(failure);
                }
                minecraft.stop();
            }
        }
    }

    private static void verifyBackplates() {
        var catalog = dev.bladetetra.forging.NamedLegacyCatalog.values();
        var mods = net.minecraftforge.fml.ModList.get();
        var sources = mods.isLoaded("slashblade_addon") && mods.isLoaded("yakumoblade")
                ? java.util.List.of("slashblade_addon/murakumo", "yakumoblade/hisonotsurugi")
                : catalog.stream().map(kind -> kind.id())
                        .filter(id -> NamedBackplateArtwork.texture(id) != null).limit(2).toList();
        var textures = sources.stream().map(NamedBackplateArtwork::texture)
                .filter(java.util.Objects::nonNull).distinct().toList();
        if (textures.size() != 2 || NamedBackplateArtwork.texture("blade_tetra/missing_test_source") != null) {
            throw new IllegalStateException("Named backplates did not generate distinct source textures and a missing fallback");
        }

        var mc = Minecraft.getInstance();
        for (String id : sources) {
            var source = NamedBladeIngotItem.sourceFromId(id);
            try (var artwork = NamedBackplateArtwork.bake(mc.getResourceManager(), source)) {
                var colors = new java.util.HashSet<Integer>();
                int visible = 0;
                for (int y = 0; y < artwork.getHeight(); y++) for (int x = 0; x < artwork.getWidth(); x++) {
                    int pixel = artwork.getPixelRGBA(x, y);
                    if ((pixel >>> 24) == 0) continue;
                    if (!AwakenedWeaponArt.insideBackplate(x, y, artwork.getWidth(), artwork.getHeight())) {
                        throw new IllegalStateException("Named artwork crossed the diamond: " + id);
                    }
                    colors.add(pixel);
                    visible++;
                }
                if (colors.size() < 16 || visible < 1000) {
                    throw new IllegalStateException("Named backplate lacks its full design: " + id);
                }
                artwork.writeToFile(mc.gameDirectory.toPath().resolve("named-backplate-" + source.name().getPath() + ".png"));
                LogUtils.getLogger().info("NAMED_BACKPLATE_ART_PASS: {} / {} visible pixels / {} colors",
                        id, visible, colors.size());
            } catch (java.io.IOException failure) {
                throw new IllegalStateException(failure);
            }
        }
    }

    private static void verifyManual(Minecraft mc) {
        if (!net.minecraftforge.fml.ModList.get().isLoaded("patchouli")) return;
        try {
            // Test-only reflection keeps the optional integration API-free.
            var registryClass = Class.forName("vazkii.patchouli.common.book.BookRegistry");
            var registry = registryClass.getField("INSTANCE").get(null);
            var books = (java.util.Map<?, ?>) registryClass.getField("books").get(registry);
            var book = books.get(new net.minecraft.resources.ResourceLocation("blade_tetra", "blade_guide"));
            if (book == null) throw new IllegalStateException("Manual definition not loaded");
            book.getClass().getMethod("reloadContents", Level.class, boolean.class).invoke(book, mc.level, false);
            var contents = book.getClass().getMethod("getContents").invoke(book);
            if ((boolean) contents.getClass().getMethod("isErrored").invoke(contents))
                throw new IllegalStateException("Patchouli failed to compile manual", (Exception)
                        contents.getClass().getMethod("getException").invoke(contents));
            var entries = (java.util.Map<?, ?>) contents.getClass().getField("entries").get(contents);
            var categories = (java.util.Map<?, ?>) contents.getClass().getField("categories").get(contents);
            if (entries.size() < 30 || categories.size() != 6) throw new IllegalStateException("Manual contents incomplete");
            book.getClass().getMethod("reloadLocks", boolean.class).invoke(book, true);
            var known = entries.get(new net.minecraft.resources.ResourceLocation("blade_tetra", "souls/clue_akatsuki"));
            var unknown = entries.get(new net.minecraft.resources.ResourceLocation("blade_tetra", "souls/clue_kyouka"));
            var art = entries.get(new net.minecraft.resources.ResourceLocation("blade_tetra", "souls/soul_akatsuki"));
            if ((boolean) known.getClass().getMethod("isLocked").invoke(known)
                    || !(boolean) unknown.getClass().getMethod("isLocked").invoke(unknown)
                    || !(boolean) art.getClass().getMethod("isLocked").invoke(art))
                throw new IllegalStateException("Manual discovery/completion locks not synchronized");
            var stack = (net.minecraft.world.item.ItemStack) book.getClass().getMethod("getBookItem").invoke(book);
            var model = mc.getItemRenderer().getModel(stack, mc.level, mc.player, 0);
            if (!model.getParticleIcon().contents().name().equals(
                    new net.minecraft.resources.ResourceLocation("blade_tetra", "item/bladecraft_manual")))
                throw new IllegalStateException("Approved manual cover did not resolve through Patchouli");
            if (ModItems.MAIN_TAB.get().getDisplayItems().stream().noneMatch(item ->
                    net.minecraft.world.item.ItemStack.isSameItemSameTags(item, stack)))
                throw new IllegalStateException("Manual missing from main creative tab");
            if (mc.level.getRecipeManager().byKey(new net.minecraft.resources.ResourceLocation("blade_tetra", "blade_guide")).isEmpty())
                throw new IllegalStateException("Manual recipe missing with Patchouli");
            LogUtils.getLogger().info("PATCHOULI_MANUAL_PASS: {} entries, {} categories, native compilation, creative listing, recipe and shared discovery locks", entries.size(), categories.size());
            ManualArtReview.begin(book);
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
    }
    private NamedIngotArtReview() {}
}
