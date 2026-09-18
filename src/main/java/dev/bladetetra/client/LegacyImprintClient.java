package dev.bladetetra.client;

import dev.bladetetra.forging.LegacyImprintKind;
import dev.bladetetra.forging.NamedLegacyCatalog;
import dev.bladetetra.network.LegacyImprintOpenPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Client gate that keeps unsupported provider geometry out of the imprint UI. */
public final class LegacyImprintClient {
    public static void open(LegacyImprintOpenPacket packet) {
        LegacyImprintKind kind = NamedLegacyCatalog.get(packet.kind());
        if (kind == null) {
            return;
        }
        if (!kind.visualUsable()) {
            if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.displayClientMessage(Component.translatable(
                        "message.blade_tetra.imprint.unsupported_model",
                        Component.translatable(kind.translationKey()), kind.visualFailureReason()), false);
            }
            return;
        }
        LegacyImprintScreen.open(packet);
    }

    private LegacyImprintClient() {}
}
