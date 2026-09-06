package dev.bladetetra.client;

import net.minecraft.client.gui.screens.Screen;

/** Client-only keyboard state used by compact item tooltips. */
public final class ClientTooltipKeyState {
    public static boolean isAltDown() {
        return Screen.hasAltDown();
    }

    private ClientTooltipKeyState() {
    }
}
