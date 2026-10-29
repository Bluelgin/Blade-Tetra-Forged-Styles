package dev.bladetetra.network;

import dev.bladetetra.BladeTetra;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;

public final class ModNetwork {
    private static final String PROTOCOL = "awakened2-integrated-1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(BladeTetra.MOD_ID, "main"),
            () -> PROTOCOL,
            PROTOCOL::equals,
            PROTOCOL::equals);

    public static void register() {
        CHANNEL.registerMessage(25, KyoukaMirrorPacket.class, KyoukaMirrorPacket::encode,
                KyoukaMirrorPacket::decode, KyoukaMirrorPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(24, AwakeningPresentationPacket.class,
                AwakeningPresentationPacket::encode, AwakeningPresentationPacket::decode,
                AwakeningPresentationPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(16, DivineSupportStatePacket.class, DivineSupportStatePacket::encode,
                DivineSupportStatePacket::decode, DivineSupportStatePacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(
                0,
                IaidoImpactPacket.class,
                IaidoImpactPacket::encode,
                IaidoImpactPacket::decode,
                IaidoImpactPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(
                1,
                MikageMusicPacket.class,
                MikageMusicPacket::encode,
                MikageMusicPacket::decode,
                MikageMusicPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(
                2,
                MikageBoundaryPacket.class,
                MikageBoundaryPacket::encode,
                MikageBoundaryPacket::decode,
                MikageBoundaryPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(
                3,
                MikageHudPacket.class,
                MikageHudPacket::encode,
                MikageHudPacket::decode,
                MikageHudPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(
                4,
                MikageDialoguePacket.class,
                MikageDialoguePacket::encode,
                MikageDialoguePacket::decode,
                MikageDialoguePacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(
                5,
                MikageVisitorDialoguePacket.class,
                MikageVisitorDialoguePacket::encode,
                MikageVisitorDialoguePacket::decode,
                MikageVisitorDialoguePacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(
                6,
                MikageVisitorChoicePacket.class,
                MikageVisitorChoicePacket::encode,
                MikageVisitorChoicePacket::decode,
                MikageVisitorChoicePacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(
                7,
                BladeCombatVfxPacket.class,
                BladeCombatVfxPacket::encode,
                BladeCombatVfxPacket::decode,
                BladeCombatVfxPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(
                8,
                BladeTechniqueVfxPacket.class,
                BladeTechniqueVfxPacket::encode,
                BladeTechniqueVfxPacket::decode,
                BladeTechniqueVfxPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(
                9,
                RaikiriVfxPacket.class,
                RaikiriVfxPacket::encode,
                RaikiriVfxPacket::decode,
                RaikiriVfxPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(
                10,
                KyoukaVfxPacket.class,
                KyoukaVfxPacket::encode,
                KyoukaVfxPacket::decode,
                KyoukaVfxPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(
                11,
                LegacyImprintOpenPacket.class,
                LegacyImprintOpenPacket::encode,
                LegacyImprintOpenPacket::decode,
                LegacyImprintOpenPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(
                12,
                LegacyImprintResultPacket.class,
                LegacyImprintResultPacket::encode,
                LegacyImprintResultPacket::decode,
                LegacyImprintResultPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(
                13,
                LegacyImprintBeginPacket.class,
                LegacyImprintBeginPacket::encode,
                LegacyImprintBeginPacket::decode,
                LegacyImprintBeginPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(
                14,
                VoidScatteringVfxPacket.class,
                VoidScatteringVfxPacket::encode,
                VoidScatteringVfxPacket::decode,
                VoidScatteringVfxPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(
                15,
                ModularTechniqueVfxPacket.class,
                ModularTechniqueVfxPacket::encode,
                ModularTechniqueVfxPacket::decode,
                ModularTechniqueVfxPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(17, CustomStyleDraftSyncPacket.class,
                CustomStyleDraftSyncPacket::encode, CustomStyleDraftSyncPacket::decode,
                CustomStyleDraftSyncPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(18, CustomStyleDraftActionPacket.class,
                CustomStyleDraftActionPacket::encode, CustomStyleDraftActionPacket::decode,
                CustomStyleDraftActionPacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(19, CustomStyleExchangePacket.Request.class,
                CustomStyleExchangePacket.Request::encode, CustomStyleExchangePacket.Request::decode,
                CustomStyleExchangePacket.Request::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(23, AwakenedMirrorEchoPacket.class,
                AwakenedMirrorEchoPacket::encode, AwakenedMirrorEchoPacket::decode,
                AwakenedMirrorEchoPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(22, AwakenedBladeStatePacket.class,
                AwakenedBladeStatePacket::encode, AwakenedBladeStatePacket::decode,
                AwakenedBladeStatePacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(21, AwakenedBladeVfxPacket.class,
                AwakenedBladeVfxPacket::encode, AwakenedBladeVfxPacket::decode,
                AwakenedBladeVfxPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(20, CustomStyleExchangePacket.Reply.class,
                CustomStyleExchangePacket.Reply::encode, CustomStyleExchangePacket.Reply::decode,
                CustomStyleExchangePacket.Reply::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    private ModNetwork() {
    }
}
