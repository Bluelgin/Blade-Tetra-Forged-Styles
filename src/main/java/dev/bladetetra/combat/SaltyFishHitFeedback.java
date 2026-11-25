package dev.bladetetra.combat;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;

/** Feedback for positive direct-player hits with a fishbone blade. */
public final class SaltyFishHitFeedback {
    private static final String NEXT_SPLASH = "blade_tetra_next_fish_splash";

    public static void play(ServerLevel level, ServerPlayer player, LivingEntity target) {
        long now = level.getGameTime();
        var data = player.getPersistentData();
        if (data.getLong(NEXT_SPLASH) > now || level.random.nextInt(5) != 0) return;
        data.putLong(NEXT_SPLASH, now + 12);

        double x = target.getX(), y = target.getY(0.55), z = target.getZ();
        level.playSound(null, x, y, z, SoundEvents.COD_FLOP, SoundSource.PLAYERS, 0.45F, 1.1F);
        level.sendParticles(ParticleTypes.SPLASH, x, y, z, 5, 0.22, 0.18, 0.22, 0.10);
        if (target.isInWater()) {
            level.sendParticles(ParticleTypes.BUBBLE, x, y, z, 4, 0.15, 0.12, 0.15, 0.03);
        }
    }
    private SaltyFishHitFeedback() {}
}
