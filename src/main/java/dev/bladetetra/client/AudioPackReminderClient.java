package dev.bladetetra.client;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.logging.LogUtils;
import dev.bladetetra.BladeTetra;
import dev.bladetetra.config.ClientVisualConfig;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.ChatFormatting;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/** Non-blocking discovery for the optional Forged Echoes music and voice pack. */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class AudioPackReminderClient {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String FOLDER_COMMAND = "/blade_tetra_audio_folder";
    private static final String DISMISS_COMMAND = "/blade_tetra_audio_dismiss";
    public static final String DOWNLOAD_URL =
            "https://github.com/Bluelgin/Blade-Tetra-Forged-Styles/releases/tag/forged-echoes-v1.0.6";

    private static final ResourceLocation MUSIC = new ResourceLocation(BladeTetra.MOD_ID,
            "sounds/music/boss/mikage/the_final_trial.ogg");
    private static final ResourceLocation VOICE = new ResourceLocation(BladeTetra.MOD_ID,
            "sounds/voice/mikage/ja_jp/intro_1.ogg");
    private static final int CHECK_DELAY_TICKS = 80;

    private static boolean wasInWorld;
    private static boolean promptedThisSession;
    private static int checkDelay = -1;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        boolean inWorld = minecraft.player != null && minecraft.level != null;
        if (inWorld && !wasInWorld) {
            checkDelay = CHECK_DELAY_TICKS;
        } else if (!inWorld) {
            checkDelay = -1;
        }
        wasInWorld = inWorld;

        if (!inWorld || checkDelay < 0 || promptedThisSession) {
            return;
        }
        if (checkDelay-- > 0) {
            return;
        }
        checkDelay = -1;
        checkAndPrompt(minecraft);
    }

    @SubscribeEvent
    public static void registerClientCommands(RegisterClientCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("blade_tetra_audio_dismiss")
                .executes(context -> {
                    dismissReminder();
                    context.getSource().sendSuccess(
                            () -> Component.translatable("message.blade_tetra.audio_pack.dismissed"), false);
                    return 1;
                }));
        dispatcher.register(Commands.literal("blade_tetra_audio_folder")
                .executes(context -> {
                    openResourcePackDirectory();
                    return 1;
                }));
    }

    @SubscribeEvent
    public static void onChatButtonClicked(ScreenEvent.MouseButtonPressed.Pre event) {
        if (event.getButton() != GLFW.GLFW_MOUSE_BUTTON_LEFT
                || !(event.getScreen() instanceof ChatScreen)) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        Style style = minecraft.gui.getChat()
                .getClickedComponentStyleAt(event.getMouseX(), event.getMouseY());
        ClickEvent clickEvent = style != null ? style.getClickEvent() : null;
        if (clickEvent == null || clickEvent.getAction() != ClickEvent.Action.RUN_COMMAND) {
            return;
        }

        if (FOLDER_COMMAND.equals(clickEvent.getValue())) {
            openResourcePackDirectory();
            event.setCanceled(true);
        } else if (DISMISS_COMMAND.equals(clickEvent.getValue())) {
            dismissReminder();
            if (minecraft.player != null) {
                minecraft.player.sendSystemMessage(
                        Component.translatable("message.blade_tetra.audio_pack.dismissed"));
            }
            event.setCanceled(true);
        }
    }

    private static void checkAndPrompt(Minecraft minecraft) {
        if (!ClientVisualConfig.ENABLE_AUDIO_PACK_REMINDER.get()
                || ClientVisualConfig.AUDIO_PACK_REMINDER_DISMISSED.get()) {
            return;
        }

        boolean hasMusic = minecraft.getResourceManager().getResource(MUSIC).isPresent();
        boolean hasVoice = minecraft.getResourceManager().getResource(VOICE).isPresent();
        if (hasMusic && hasVoice) {
            return;
        }

        promptedThisSession = true;
        boolean installed = hasForgedEchoesFile();
        String state = hasMusic || hasVoice ? "partial" : installed ? "disabled" : "missing";
        Component title = Component.translatable("toast.blade_tetra.audio_pack.title");
        Component detail = Component.translatable("toast.blade_tetra.audio_pack." + state);
        SystemToast.add(minecraft.getToasts(), SystemToast.SystemToastIds.PERIODIC_NOTIFICATION,
                title, detail);

        if (minecraft.player == null) {
            return;
        }
        minecraft.player.sendSystemMessage(Component.translatable(
                "message.blade_tetra.audio_pack." + state));

        MutableComponent actions = Component.empty();
        if (!installed) {
            actions.append(action("message.blade_tetra.audio_pack.download",
                    new ClickEvent(ClickEvent.Action.OPEN_URL, DOWNLOAD_URL)));
            actions.append(Component.literal("  "));
        }
        actions.append(action("message.blade_tetra.audio_pack.folder",
                new ClickEvent(ClickEvent.Action.RUN_COMMAND, FOLDER_COMMAND)));
        actions.append(Component.literal("  "));
        actions.append(action("message.blade_tetra.audio_pack.dismiss",
                new ClickEvent(ClickEvent.Action.RUN_COMMAND, DISMISS_COMMAND)));
        minecraft.player.sendSystemMessage(actions);
    }

    private static MutableComponent action(String key, ClickEvent clickEvent) {
        Style style = Style.EMPTY.withColor(ChatFormatting.AQUA).withUnderlined(true)
                .withClickEvent(clickEvent);
        return Component.translatable(key).withStyle(style);
    }

    private static boolean hasForgedEchoesFile() {
        Path directory = resourcePackDirectory();
        if (!Files.isDirectory(directory)) {
            return false;
        }
        try (var files = Files.list(directory)) {
            return files.anyMatch(path -> {
                String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
                return name.contains("forged-echoes") || name.contains("forged_echoes");
            });
        } catch (IOException exception) {
            LOGGER.debug("Could not inspect resource-pack directory", exception);
            return false;
        }
    }

    private static Path resourcePackDirectory() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("resourcepacks");
    }

    private static void openResourcePackDirectory() {
        Path directory = resourcePackDirectory();
        try {
            Files.createDirectories(directory);
            Util.getPlatform().openFile(directory.toFile());
        } catch (IOException exception) {
            LOGGER.warn("Could not open resource-pack directory", exception);
        }
    }

    private static void dismissReminder() {
        ClientVisualConfig.AUDIO_PACK_REMINDER_DISMISSED.set(true);
        ClientVisualConfig.SPEC.save();
    }

    private AudioPackReminderClient() {
    }
}
