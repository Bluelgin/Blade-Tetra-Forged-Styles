package dev.bladetetra.export;

import com.mojang.logging.LogUtils;
import dev.bladetetra.BladeTetra;
import dev.bladetetra.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.GameType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.nio.file.Files;
import java.util.Set;
import java.util.stream.Collectors;

@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID, value = Dist.CLIENT)
public final class BladeSealClientSmoke {
    private static boolean opened;
    private static Set<String> before;
    private static int waited;

    @SubscribeEvent
    public static void serverTick(TickEvent.ServerTickEvent event) {
        if (!enabled() || opened || event.phase != TickEvent.Phase.END) return;
        ServerPlayer player = event.getServer().getPlayerList().getPlayers().stream().findFirst().orElse(null);
        if (player == null || player.tickCount < 30) return;
        opened = true;
        event.getServer().getPlayerList().op(player.getGameProfile());
        player.setGameMode(GameType.CREATIVE);
        player.setItemInHand(InteractionHand.MAIN_HAND, ModItems.BLADE_SEAL.get().getDefaultInstance());
        player.setItemInHand(InteractionHand.OFF_HAND, ModItems.MODULAR_SLASHBLADE.get().createDefaultStack());
        if (!BladeSealSessions.open(player, InteractionHand.MAIN_HAND)) {
            LogUtils.getLogger().error("BLADE_SEAL_CLIENT_FAIL: server rejected the test player");
            var client = Minecraft.getInstance();
            client.execute(client::stop);
        }
    }

    @SubscribeEvent
    public static void clientTick(TickEvent.ClientTickEvent event) {
        if (!enabled() || event.phase != TickEvent.Phase.END) return;
        var client = Minecraft.getInstance();
        if (!(client.screen instanceof BladeSealScreen screen)) return;
        var output = client.gameDirectory.toPath().resolve("blade-exports");
        try {
            if (before == null) {
                try (var files = Files.list(output)) {
                    before = files.map(path -> path.getFileName().toString()).collect(Collectors.toSet());
                }
                screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                        .filter(button -> button.getMessage().equals(Component.translatable("blade_tetra.export.confirm")))
                        .findFirst().orElseThrow().onPress();
                return;
            }
            if (++waited % 10 != 0) return;
            try (var files = Files.list(output)) {
                var file = files.filter(path -> {
                    String name = path.getFileName().toString();
                    return name.startsWith("bt_export_blade_") && name.endsWith(".jar") && !before.contains(name);
                }).findFirst();
                if (file.isPresent()) {
                    String modId = file.get().getFileName().toString().split("-", 2)[0];
                    BladeExportPackageSmoke.verifyArtwork(file.get(), modId);
                    LogUtils.getLogger().info("BLADE_SEAL_CLIENT_PASS: {}", file.get().toAbsolutePath());
                    client.stop();
                } else if (waited >= 200) {
                    throw new IllegalStateException("Seal confirmation produced no add-on");
                }
            }
        } catch (Exception error) {
            LogUtils.getLogger().error("BLADE_SEAL_CLIENT_FAIL", error);
            client.stop();
        }
    }

    private static boolean enabled() {
        return Boolean.getBoolean("blade_tetra.exportPackageSmoke");
    }

    private BladeSealClientSmoke() {}
}
