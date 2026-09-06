package dev.bladetetra.client;

import dev.bladetetra.network.MikageVisitorDialoguePacket;
import net.minecraft.client.Minecraft;

public final class MikageVisitorDialogueClient {
    public static void open(MikageVisitorDialoguePacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (packet.nodeId().equals("__close")) {
            if (minecraft.screen instanceof MikageVisitorScreen) {
                minecraft.setScreen(null);
            }
            return;
        }
        minecraft.setScreen(new MikageVisitorScreen(packet));
        if (!packet.voiceEvent().isBlank()) {
            MikageDialogueClient.show("", packet.voiceEvent(), 80, 0);
        }
    }

    private MikageVisitorDialogueClient() {
    }
}
