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
        LegacyResearchabilityClient.Result result = LegacyResearchabilityClient.inspect(kind);
        if (!result.researchable()) {
            if (kind != null && Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.displayClientMessage(Component.translatable(
                        "message.blade_tetra.imprint.unsupported_model",
                        Component.translatable(kind.translationKey()), result.reason()), false);
            }
            return;
        }
        LegacyImprintScreen.open(packet);
    }

    private LegacyImprintClient() {}
}
