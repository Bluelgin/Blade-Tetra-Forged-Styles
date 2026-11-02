package dev.bladetetra.client;

import com.mojang.logging.LogUtils;
import dev.bladetetra.BladeTetra;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** Opt-in native Patchouli page review, sharing the disposable creative review world. */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID, value = Dist.CLIENT)
public final class ManualArtReview {
    private record View(Object entry, int spread, String file) {}
    private static final List<View> views = new ArrayList<>();
    private static final List<String> overflows = new ArrayList<>();
    private static Object book;
    private static Class<?> bookClass, entryClass;
    private static boolean active, advance, english;
    private static int warmup, index, frames, reviewed, captures;
    private static CompletableFuture<Void> reload;

    static boolean enabled() { return Boolean.getBoolean("blade_tetra.manualArtReview"); }

    static void begin(Object sourceBook) {
        if (!enabled()) return;
        book = sourceBook;
        bookClass = book.getClass();
        try { entryClass = Class.forName("vazkii.patchouli.client.book.BookEntry"); }
        catch (ClassNotFoundException e) { throw new IllegalStateException(e); }
        var mc = Minecraft.getInstance();
        mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
        mc.options.hideGui = true;
        mc.gui.getChat().clearMessages(true);
        var server = mc.getSingleplayerServer();
        var uuid = mc.player.getUUID();
        server.execute(() -> {
            var player = server.getPlayerList().getPlayer(uuid);
            // First prove relic ownership unlocks use instructions without completing a rite.
            player.getInventory().offhand.set(0, dev.bladetetra.registry.ModItems.AKATSUKI_SOUL_FRAGMENT.get().getDefaultInstance());
            dev.bladetetra.lore.ManualDiscovery.sync(player);
            var guide = server.getAdvancements().getAdvancement(new ResourceLocation("blade_tetra", "manual/guide/akatsuki"));
            if (!player.getAdvancements().getOrStartProgress(guide).isDone()
                    || dev.bladetetra.lore.SmithingLore.completed(player, "akatsuki"))
                throw new IllegalStateException("Relic guide unlock changed the rite or failed");
            // The isolated review then unlocks every page for layout capture, not gameplay evidence.
            for (String clue : java.util.stream.Stream.concat(dev.bladetetra.lore.SmithingLore.CLUES.stream(),
                    java.util.stream.Stream.of("nbt_sage")).toList()) {
                var advancement = server.getAdvancements().getAdvancement(new ResourceLocation("blade_tetra", clue));
                if (advancement != null) for (String criterion : advancement.getCriteria().keySet())
                    player.getAdvancements().award(advancement, criterion);
            }
            dev.bladetetra.lore.ManualDiscovery.sync(player);
        });
        active = true;
    }

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (!active || event.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        if (mc.getOverlay() != null) return;
        try {
            if (reload != null) {
                if (!reload.isDone()) return;
                reload.join(); reload = null;
                loadViews(mc);
                return;
            }
            if (warmup < 60) {
                if (++warmup == 60) loadViews(mc);
                return;
            }
            if (!advance) return;
            advance = false;
            if (++index < views.size()) { show(mc); return; }
            if (!english) {
                english = true;
                mc.getLanguageManager().setSelected("en_us");
                mc.options.languageCode = "en_us";
                reload = mc.reloadResourcePacks();
                return;
            }
            if (!overflows.isEmpty()) throw new IllegalStateException("Manual text overflow: " + overflows);
            LogUtils.getLogger().info("MANUAL_ART_REVIEW_PASS: {} native spreads across zh_cn/en_us, {} actual screenshots; shared relic guide unlock", reviewed, captures);
            active = false;
            mc.stop();
        } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
    }

    private static void loadViews(Minecraft mc) throws ReflectiveOperationException {
        bookClass.getMethod("reloadContents", net.minecraft.world.level.Level.class, boolean.class).invoke(book, mc.level, false);
        bookClass.getMethod("reloadLocks", boolean.class).invoke(book, true);
        var contents = bookClass.getMethod("getContents").invoke(book);
        if ((boolean) contents.getClass().getMethod("isErrored").invoke(contents))
            throw new IllegalStateException("Manual compilation failed", (Exception) contents.getClass().getMethod("getException").invoke(contents));
        var entries = (Map<?, ?>) contents.getClass().getField("entries").get(contents);
        views.clear(); index = 0;
        views.add(new View(null, 0, "00-index"));
        var ordered = new ArrayList<>(entries.entrySet());
        ordered.sort(java.util.Comparator.comparing(e -> e.getKey().toString()));
        for (var entry : ordered) {
            if ((boolean) entryClass.getMethod("isLocked").invoke(entry.getValue()))
                throw new IllegalStateException("Review page remained locked: " + entry.getKey());
            var pages = (List<?>) entryClass.getMethod("getPages").invoke(entry.getValue());
            var name = (net.minecraft.network.chat.Component) entryClass.getMethod("getName").invoke(entry.getValue());
            if (mc.font.width(name) > 116) overflows.add((english ? "en_us " : "zh_cn ") + entry.getKey() + " title=" + mc.font.width(name));
            for (int spread = 0; spread * 2 < pages.size(); spread++) {
                String file = captureName(entry.getKey().toString(), spread);
                views.add(new View(entry.getValue(), spread, file));
            }
        }
        show(mc);
    }

    private static String captureName(String id, int spread) {
        return switch (id + "/" + spread) {
            case "blade_tetra:basics/first_blade/1" -> "01-conversion";
            case "blade_tetra:basics/parts/0" -> "02-fittings";
            case "blade_tetra:styles/style_iaido/0" -> "03-iaido";
            case "blade_tetra:named/named_ingots/0" -> "04-extraction";
            case "blade_tetra:named/fusion/0" -> "05-fusion";
            case "blade_tetra:named/forged_sa/1" -> "06-forged-art";
            case "blade_tetra:souls/soul_akatsuki/2" -> "07-akatsuki";
            case "blade_tetra:souls/soul_kyouka/1" -> "08-kyouka";
            case "blade_tetra:appearance/appearance/0" -> "09-saya-pattern";
            default -> null;
        };
    }

    private static void show(Minecraft mc) throws ReflectiveOperationException {
        var view = views.get(index);
        Screen screen;
        if (view.entry == null) screen = (Screen) Class.forName("vazkii.patchouli.client.book.gui.GuiBookLanding")
                .getConstructor(bookClass).newInstance(book);
        else screen = (Screen) Class.forName("vazkii.patchouli.client.book.gui.GuiBookEntry")
                .getConstructor(bookClass, entryClass, int.class).newInstance(book, view.entry, view.spread);
        mc.setScreen(screen);
        frames = 0;
    }

    @SubscribeEvent public static void frame(TickEvent.RenderTickEvent event) {
        if (active && event.phase == TickEvent.Phase.START) {
            Minecraft.getInstance().getToasts().clear();
            Minecraft.getInstance().gui.getChat().clearMessages(true);
            return;
        }
        if (!active || event.phase != TickEvent.Phase.END || warmup < 60 || reload != null || advance) return;
        var mc = Minecraft.getInstance();
        if (mc.getOverlay() != null || mc.screen == null || !mc.screen.getClass().getName().startsWith("vazkii.patchouli.")) return;
        if (++frames < 4) return;
        try {
            var view = views.get(index);
            for (String side : List.of("leftPage", "rightPage")) {
                Object page = field(mc.screen, side);
                if (page == null) continue;
                audit(field(page, "textRender"), view, side);
                var template = field(page, "template");
                if (template != null) for (Object component : (List<?>) field(template, "components"))
                    audit(field(component, "textRenderer"), view, side + " template");
            }
            if (view.file != null && (!english || view.file.equals("01-conversion"))) {
                try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                    image.writeToFile(mc.gameDirectory.toPath().resolve("manual-" + (english ? "en_us-" : "zh_cn-") + view.file + ".png"));
                    captures++;
                }
            }
            reviewed++; advance = true;
        } catch (ReflectiveOperationException | java.io.IOException e) { throw new IllegalStateException(e); }
    }

    private static void audit(Object renderer, View view, String side) throws ReflectiveOperationException {
        if (renderer == null) return;
        float scale = (float) field(renderer, "scale");
        if (scale < 0.999F) {
            String id = view.entry == null ? "index" : entryClass.getMethod("getId").invoke(view.entry).toString();
            String problem = (english ? "en_us " : "zh_cn ") + id + " spread=" + view.spread + " " + side + " scale=" + scale;
            overflows.add(problem);
            LogUtils.getLogger().error("MANUAL_PAGE_OVERFLOW: {}", problem);
        }
    }

    private static Object field(Object object, String name) throws ReflectiveOperationException {
        for (Class<?> c = object.getClass(); c != null; c = c.getSuperclass()) {
            try { Field f = c.getDeclaredField(name); f.setAccessible(true); return f.get(object); }
            catch (NoSuchFieldException ignored) {}
        }
        return null;
    }
    private ManualArtReview() {}
}
