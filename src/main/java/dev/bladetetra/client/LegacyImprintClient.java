package dev.bladetetra.client;

import dev.bladetetra.network.LegacyImprintOpenPacket;

/** Retired research entry point; retain the packet handler without opening a screen. */
public final class LegacyImprintClient {
    public static void open(LegacyImprintOpenPacket packet) {
        // Historical packets must not reopen the retired research minigame.
    }

    private LegacyImprintClient() {}
}
