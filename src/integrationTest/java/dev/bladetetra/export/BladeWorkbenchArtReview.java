package dev.bladetetra.export;

import dev.bladetetra.BladeTetra;
import dev.bladetetra.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.nio.file.Files;

/** Opt-in camera fixture. Uses real block, synced stack and normal world rendering. */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID)
public final class BladeWorkbenchArtReview {
    private static boolean started;
    private static int worldTicks;
    private static BlockPos table;
    private static int captured;
    private static boolean enabled() { return Boolean.getBoolean("blade_tetra.inscriptionArtReview"); }

    @SubscribeEvent public static void server(TickEvent.ServerTickEvent e) {
        if (!enabled() || e.phase != TickEvent.Phase.END) return;
        var player = e.getServer().getPlayerList().getPlayers().stream().findFirst().orElse(null);
        if (player == null) return;
        worldTicks++;
        if (worldTicks == 20) {
            table = player.blockPosition();
            player.serverLevel().setBlockAndUpdate(table, BladeSealWorkbench.BLOCK.get().defaultBlockState());
            player.teleportTo(player.serverLevel(), table.getX()+2.8, table.getY()+1, table.getZ()+3.8, 145, 25);
            player.serverLevel().setDayTime(6000);
        }
        if (worldTicks == 80 && table != null && player.serverLevel().getBlockEntity(table) instanceof BladeSealWorkbenchStorage storage) {
            storage.inventory().setItem(0, ModItems.MODULAR_SLASHBLADE.get().createDefaultStack());
        }
    }

    @SubscribeEvent public static void client(TickEvent.ClientTickEvent e) {
        if (!enabled() || e.phase != TickEvent.Phase.END) return;
        var c = Minecraft.getInstance();
        if (!started && c.screen instanceof TitleScreen && c.getOverlay() == null) {
            started = true;
            c.options.hideGui = true;
            String name = "inscription-art-review";
            if (Files.exists(c.gameDirectory.toPath().resolve("saves/"+name+"/level.dat"))) c.createWorldOpenFlows().loadLevel(c.screen,name);
            else c.createWorldOpenFlows().createFreshLevel(name,
                    new LevelSettings(name, GameType.CREATIVE, false, Difficulty.PEACEFUL, true, new GameRules(), WorldDataConfiguration.DEFAULT),
                    new WorldOptions(158L,false,false), r -> r.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
        }
        if (worldTicks > 150) c.stop();
    }

    @SubscribeEvent public static void frame(TickEvent.RenderTickEvent e) throws java.io.IOException {
        if (!enabled() || e.phase != TickEvent.Phase.END) return;
        var c=Minecraft.getInstance();
        if (c.screen != null || c.level == null) return;
        int phase = worldTicks >= 110 ? 2 : worldTicks >= 50 && worldTicks < 80 ? 1 : 0;
        if (phase <= captured) return;
        c.getToasts().clear();
        try (var shot=net.minecraft.client.Screenshot.takeScreenshot(c.getMainRenderTarget())) {
            shot.writeToFile(c.gameDirectory.toPath().resolve("inscription-workbench-"+phase+".png"));
        }
        captured=phase;
    }
}
