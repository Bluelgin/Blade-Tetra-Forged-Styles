package dev.bladetetra.compat;

import dev.bladetetra.BladeTetra;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.BackupConfirmScreen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID, value = Dist.CLIENT)
public final class LegacyWorldMigrationClientSmoke {
    private static boolean started;

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !Boolean.getBoolean("blade_tetra.legacyWorldMigrationClientSmoke")) return;
        var client = Minecraft.getInstance();
        if (LegacyWorldMigrationSmoke.done() && client.player != null) {
            client.stop();
        } else if (started && client.screen instanceof BackupConfirmScreen backup) {
            accept(backup, Component.translatable("selectWorld.backupJoinConfirmButton"));
        } else if (started && client.screen instanceof ConfirmScreen confirmation
                && confirmation.getTitle().equals(Component.translatable("selectWorld.backupQuestion.experimental"))) {
            accept(confirmation, CommonComponents.GUI_PROCEED);
        } else if (!started && client.screen instanceof TitleScreen && client.getOverlay() == null) {
            started = true;
            var flows = client.createWorldOpenFlows();
            String name = "legacy-world-migration-smoke";
            if (Boolean.getBoolean("blade_tetra.legacyWorldMigrationReopen")) {
                flows.loadLevel(client.screen, name);
            } else {
                flows.createFreshLevel(name,
                        new LevelSettings(name, GameType.CREATIVE, false, Difficulty.PEACEFUL,
                                true, new GameRules(), WorldDataConfiguration.DEFAULT),
                        new WorldOptions(158L, false, false),
                        registries -> registries.registryOrThrow(Registries.WORLD_PRESET)
                                .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
            }
        }
    }

    private static void accept(Screen screen, Component label) {
        screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(button -> button.getMessage().equals(label)).findFirst().orElseThrow().onPress();
    }

    private LegacyWorldMigrationClientSmoke() {}
}
