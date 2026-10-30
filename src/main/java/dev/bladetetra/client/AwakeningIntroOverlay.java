package dev.bladetetra.client;

import dev.bladetetra.BladeTetra;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Own screen overlay, never a vanilla Title message or a world billboard. */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD,
        value = Dist.CLIENT)
public final class AwakeningIntroOverlay {
    @SubscribeEvent
    public static void register(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("awakening_true_name",
                (gui, graphics, partial, width, height) ->
                        AwakeningIntroClient.render(graphics, width, height, partial));
    }

    private AwakeningIntroOverlay() {}
}
