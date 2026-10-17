package dev.bladetetra.challenge;

import dev.bladetetra.BladeTetra;
import dev.bladetetra.network.ModNetwork;
import dev.bladetetra.network.ModularTechniqueVfxPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

final class MikageCorridorPresentation {
    private final MikageEntity owner;
    private final long cast;
    MikageCorridorPresentation(MikageEntity owner, long cast) { this.owner = owner; this.cast = cast; }
    void send(String kind, Vec3 at, Vec3 end, int slot, int duration, float yaw) {
        var packet = new ModularTechniqueVfxPacket(new ResourceLocation(BladeTetra.MOD_ID,
                "mikage/corridor/" + kind), at.x, at.y, at.z, end.x, end.y, end.z,
                yaw, 1, owner.getId(), slot, duration, (int) cast);
        for (var viewer : ((ServerLevel) owner.level()).players())
            if (ChallengeManager.isParticipant(owner, viewer))
                ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> viewer), packet);
    }
    void pair(MikageCorridorRoute route, int pass, boolean cue) {
        float yaw = (float) Math.toDegrees(Math.atan2(-route.heading().x, route.heading().z));
        send(cue ? "cue" : "gate", route.entry(), route.entry(), pass * 2, 600, yaw);
        send("gate", route.exit(), route.exit(), pass * 2 + 1, 600, yaw);
        if (cue) ((ServerLevel) owner.level()).playSound(null, route.entry().x, route.entry().y + 1,
                route.entry().z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.HOSTILE, 1, 1 + pass * .15F);
    }
    void arrival() {
        send("arrival", owner.position(), owner.position(), -1, 1, owner.getYRot());
        ((ServerLevel) owner.level()).playSound(null, owner.getX(), owner.getY(), owner.getZ(),
                SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, .7F, 1.3F);
    }
    void down(Vec3 heading) {
        send("down", owner.position(), owner.position().add(heading.scale(5)).add(0, -.8, 0), -1, 16, owner.getYRot());
        ((ServerLevel) owner.level()).playSound(null, owner.getX(), owner.getY(), owner.getZ(),
                SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.HOSTILE, 1, .7F);
    }
    void end() { send("end", owner.position(), owner.position(), -1, 1, owner.getYRot()); }
}
