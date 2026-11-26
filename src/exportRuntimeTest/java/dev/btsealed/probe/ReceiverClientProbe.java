package dev.btsealed.probe;

import com.mojang.logging.LogUtils;
import mods.flammpfeil.slashblade.event.client.RenderOverrideEvent;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "bt_export_probe", value = Dist.CLIENT)
public final class ReceiverClientProbe {
    private static final boolean ENABLED = Boolean.getBoolean("blade_tetra.cleanReceiverClient");
    private static boolean started;
    private static boolean finished;
    private static int stage;
    private static int frames;
    private static int equipped = -1;
    private static final String[] IDS = {"bt_export_salty_fish", "bt_export_salty_fish", "bt_export_fusion", "bt_export_fusion"};
    private static final String[] IMAGES = {"fish-first-person", "fish-third-person", "fusion-first-person", "fusion-third-person"};

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        if (!ENABLED || finished || e.phase != TickEvent.Phase.END) return;
        var client = Minecraft.getInstance();
        if (!started && client.screen instanceof TitleScreen && client.getOverlay() == null) {
            started = true;
            if (ModList.get().isLoaded("tetra") || ModList.get().isLoaded("blade_tetra")) {
                throw new IllegalStateException("Clean receiver contains source mods");
            }
            String name = "receiver-client";
            client.createWorldOpenFlows().createFreshLevel(name,
                    new LevelSettings(name, GameType.CREATIVE, false, Difficulty.PEACEFUL, true,
                            new GameRules(), WorldDataConfiguration.DEFAULT), new WorldOptions(158L, false, false),
                    access -> access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT)
                            .value().createWorldDimensions());
        }
        if (client.player == null || client.player.tickCount < 30) return;
        if (stage == IDS.length) {
            finished = true;
            LogUtils.getLogger().info("CLEAN_RECEIVER_CLIENT_RENDER_PASS");
            client.stop();
        } else if (stage != equipped) {
            equipped = stage;
            frames = 0;
            client.options.setCameraType(stage % 2 == 0 ? CameraType.FIRST_PERSON : CameraType.THIRD_PERSON_FRONT);
            var server = client.getSingleplayerServer();
            var id = IDS[stage];
            server.execute(() -> {
                var player = server.getPlayerList().getPlayers().get(0);
                player.setItemInHand(InteractionHand.MAIN_HAND, ExportRuntimeProbe.createNamedBlade(server.overworld(), id));
            });
        }
    }

    @SubscribeEvent
    public static void render(RenderOverrideEvent e) {
        if (!ENABLED || stage >= IDS.length || stage != equipped
                || !IDS[stage].equals(e.getTexture().getNamespace())) return;
        var group = e.getModel().groupObjects.stream().filter(mesh -> mesh.name.equals(e.getTarget())).findFirst();
        if (group.isPresent() && !group.get().faces.isEmpty()) frames++;
    }

    @SubscribeEvent
    public static void save(TickEvent.RenderTickEvent e) throws Exception {
        if (!ENABLED || e.phase != TickEvent.Phase.END || frames < 30 || stage >= IDS.length || stage != equipped) return;
        var client = Minecraft.getInstance();
        try (var screenshot = Screenshot.takeScreenshot(client.getMainRenderTarget())) {
            screenshot.writeToFile(client.gameDirectory.toPath().resolve(IMAGES[stage] + ".png"));
        }
        LogUtils.getLogger().info("CLEAN_RECEIVER_MODEL_PASS {}", IMAGES[stage]);
        stage++;
    }

    private ReceiverClientProbe() {}
}
