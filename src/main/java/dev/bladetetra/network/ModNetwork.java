package dev.bladetetra.network;

import dev.bladetetra.BladeTetra;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class ModNetwork {
    private static final String PROTOCOL = "9";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(BladeTetra.MOD_ID, "main"),
            () -> PROTOCOL,
            PROTOCOL::equals,
            PROTOCOL::equals);

    public static void register() {
        CHANNEL.registerMessage(
                0,
                IaidoImpactPacket.class,
                IaidoImpactPacket::encode,
                IaidoImpactPacket::decode,
                IaidoImpactPacket::handle);
        CHANNEL.registerMessage(
                1,
                MikageMusicPacket.class,
                MikageMusicPacket::encode,
                MikageMusicPacket::decode,
                MikageMusicPacket::handle);
        CHANNEL.registerMessage(
                2,
                MikageBoundaryPacket.class,
                MikageBoundaryPacket::encode,
                MikageBoundaryPacket::decode,
                MikageBoundaryPacket::handle);
        CHANNEL.registerMessage(
                3,
                MikageHudPacket.class,
                MikageHudPacket::encode,
                MikageHudPacket::decode,
                MikageHudPacket::handle);
        CHANNEL.registerMessage(
                4,
                MikageDialoguePacket.class,
                MikageDialoguePacket::encode,
                MikageDialoguePacket::decode,
                MikageDialoguePacket::handle);
        CHANNEL.registerMessage(
                5,
                MikageVisitorDialoguePacket.class,
                MikageVisitorDialoguePacket::encode,
                MikageVisitorDialoguePacket::decode,
                MikageVisitorDialoguePacket::handle);
        CHANNEL.registerMessage(
                6,
                MikageVisitorChoicePacket.class,
                MikageVisitorChoicePacket::encode,
                MikageVisitorChoicePacket::decode,
                MikageVisitorChoicePacket::handle);
        CHANNEL.registerMessage(
                7,
                BladeCombatVfxPacket.class,
                BladeCombatVfxPacket::encode,
                BladeCombatVfxPacket::decode,
                BladeCombatVfxPacket::handle);
        CHANNEL.registerMessage(
                8,
                BladeTechniqueVfxPacket.class,
                BladeTechniqueVfxPacket::encode,
                BladeTechniqueVfxPacket::decode,
                BladeTechniqueVfxPacket::handle);
        CHANNEL.registerMessage(
                9,
                RaikiriVfxPacket.class,
                RaikiriVfxPacket::encode,
                RaikiriVfxPacket::decode,
                RaikiriVfxPacket::handle);
        CHANNEL.registerMessage(
                10,
                KyoukaVfxPacket.class,
                KyoukaVfxPacket::encode,
                KyoukaVfxPacket::decode,
                KyoukaVfxPacket::handle);
        CHANNEL.registerMessage(
                11,
                LegacyImprintOpenPacket.class,
                LegacyImprintOpenPacket::encode,
                LegacyImprintOpenPacket::decode,
                LegacyImprintOpenPacket::handle);
        CHANNEL.registerMessage(
                12,
                LegacyImprintResultPacket.class,
                LegacyImprintResultPacket::encode,
                LegacyImprintResultPacket::decode,
                LegacyImprintResultPacket::handle);
        CHANNEL.registerMessage(
                13,
                LegacyImprintBeginPacket.class,
                LegacyImprintBeginPacket::encode,
                LegacyImprintBeginPacket::decode,
                LegacyImprintBeginPacket::handle);
        CHANNEL.registerMessage(
                14,
                VoidScatteringVfxPacket.class,
                VoidScatteringVfxPacket::encode,
                VoidScatteringVfxPacket::decode,
                VoidScatteringVfxPacket::handle);
    }

    private ModNetwork() {
    }
}
