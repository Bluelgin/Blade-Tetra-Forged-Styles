package dev.bladetetra.challenge;

import dev.bladetetra.BladeTetra;
import dev.bladetetra.network.ModNetwork;
import dev.bladetetra.network.ModularTechniqueVfxPacket;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

/** Private spatial cues for the pursued player; visual gates are shared with encounter viewers. */
final class MikageThousandGatesPresentation {
    private final MikageEntity owner;
    MikageThousandGatesPresentation(MikageEntity owner) { this.owner = owner; }

    void portal(String kind, long cast, int ticks) {
        if (!(owner.level() instanceof ServerLevel level)) return;
        Vec3 at = owner.position();
        var packet = new ModularTechniqueVfxPacket(
                new ResourceLocation(BladeTetra.MOD_ID, "mikage/gates/" + kind),
                at.x, at.y, at.z, at.x, at.y, at.z, owner.getYRot(), 1,
                owner.getId(), -1, ticks, (int) cast);
        for (ServerPlayer viewer : level.players()) {
            if (ChallengeManager.isParticipant(owner, viewer))
                ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> viewer), packet);
        }
        if (kind.equals("enter")) level.playSound(null, at.x, at.y + 1, at.z,
                SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, .45F, 1.4F);
        if (kind.equals("break")) level.playSound(null, at.x, at.y + 1, at.z,
                SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.HOSTILE, 1, .75F);
    }

    void cue(ServerPlayer player, Vec3 at, int streak) {
        sound(player, at, SoundEvents.AMETHYST_BLOCK_CHIME, 1, .85F + streak * .12F);
    }
    void drawSound(ServerPlayer player, Vec3 at, int streak) {
        sound(player, at, SoundEvents.ARMOR_EQUIP_IRON, .8F, .8F + streak * .1F);
    }
    private void sound(ServerPlayer player, Vec3 at, SoundEvent sound, float volume, float pitch) {
        player.connection.send(new ClientboundSoundPacket(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound),
                SoundSource.HOSTILE, at.x, at.y + 1, at.z, volume, pitch, owner.getRandom().nextLong()));
    }
    void slash(long cast) {
        portal("slash", cast, 6);
        Vec3 at = owner.position();
        ((ServerLevel) owner.level()).playSound(null, at.x, at.y + 1, at.z,
                SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, .8F, .85F);
    }
}
