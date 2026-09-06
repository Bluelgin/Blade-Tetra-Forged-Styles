package dev.bladetetra.item;

import dev.bladetetra.client.ClientTooltipKeyState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;

/** Keeps client input classes out of dedicated-server tooltip paths. */
final class TooltipKeyState {
    static boolean isAltDown() {
        return FMLEnvironment.dist == Dist.CLIENT && ClientTooltipKeyState.isAltDown();
    }

    private TooltipKeyState() {
    }
}
