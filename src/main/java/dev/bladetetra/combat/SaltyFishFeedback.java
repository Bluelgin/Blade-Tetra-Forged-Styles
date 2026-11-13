package dev.bladetetra.combat;

import dev.bladetetra.BladeTetra;
import dev.bladetetra.item.ModularSlashBladeItem;
import dev.bladetetra.item.SaltyFishParts;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID)
public final class SaltyFishFeedback {
    private static final String NEXT_SPLASH = "blade_tetra_next_fish_splash";

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDamage(LivingDamageEvent event) {
        var target = event.getEntity();
        if (event.getAmount() <= 0 || !(target.level() instanceof ServerLevel level)
                || !(event.getSource().getDirectEntity() instanceof ServerPlayer player)) return;
        var stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof ModularSlashBladeItem)
                || SaltyFishParts.blade(stack) == null) return;
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

    private SaltyFishFeedback() {}
}
