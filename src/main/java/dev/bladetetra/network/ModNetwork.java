package dev.bladetetra.network;

import dev.bladetetra.BladeTetra;
import dev.bladetetra.export.BladeSealPackets;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;

public final class ModNetwork {
    private static final String PROTOCOL = "awakened2-blood-threads-16";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(BladeTetra.MOD_ID, "main"),
            () -> PROTOCOL,
            PROTOCOL::equals,
            PROTOCOL::equals);

    // Retired wire IDs 1–7, 16, 22 and 23 stay reserved.
    public static void register() {
        CHANNEL.registerMessage(31, BladeSealPackets.Preview.class, BladeSealPackets.Preview::encode,
                BladeSealPackets.Preview::decode, BladeSealPackets.Preview::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(32, BladeSealPackets.Confirm.class, BladeSealPackets.Confirm::encode,
                BladeSealPackets.Confirm::decode, BladeSealPackets.Confirm::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(33, BladeSealPackets.Result.class, BladeSealPackets.Result::encode,
                BladeSealPackets.Result::decode, BladeSealPackets.Result::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(30, AkatsukiThreadPacket.class, AkatsukiThreadPacket::encode,
                AkatsukiThreadPacket::decode, AkatsukiThreadPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(29, SakuraRecallPacket.class, SakuraRecallPacket::encode,
                SakuraRecallPacket::decode, SakuraRecallPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(28, SoulActivityPacket.class, SoulActivityPacket::encode,
                SoulActivityPacket::decode, SoulActivityPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(26, SoulSwitchPacket.class, SoulSwitchPacket::encode,
                SoulSwitchPacket::decode, SoulSwitchPacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(27, SoulSwitchReply.class, SoulSwitchReply::encode,
                SoulSwitchReply::decode, SoulSwitchReply::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(25, KyoukaMirrorPacket.class, KyoukaMirrorPacket::encode,
                KyoukaMirrorPacket::decode, KyoukaMirrorPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(24, AwakeningPresentationPacket.class,
                AwakeningPresentationPacket::encode, AwakeningPresentationPacket::decode,
                AwakeningPresentationPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(
                0,
                IaidoImpactPacket.class,
                IaidoImpactPacket::encode,
                IaidoImpactPacket::decode,
                IaidoImpactPacket::handle,
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
