package dev.bladetetra.combat;

import mods.flammpfeil.slashblade.entity.EntityAbstractSummonedSword;
import mods.flammpfeil.slashblade.entity.EntityJudgementCut;
import mods.flammpfeil.slashblade.entity.EntitySlashEffect;
import mods.flammpfeil.slashblade.entity.IShootable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

/** Limits the total damage of a composed SA while native callbacks stay intact. */
final class ForgedSlashArtDamageBalance {
    private static final String DAMAGE_SCALE_TAG = "blade_tetra_forged_sa_damage_scale";

    // A forged release contains two native signatures.
    static final float PRIMARY_SCALE = 0.85F;
    static final float SECONDARY_SCALE = 0.65F;

    static float phaseScale(ForgedSlashArtHandler.Phase phase) {
        return switch (phase) {
            case PRIMARY -> PRIMARY_SCALE;
            case SECONDARY -> SECONDARY_SCALE;
        };
    }

    static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel)
                || !isNativeAttackEntity(event.getEntity())
                || !(event.getEntity() instanceof IShootable shot)
                || !(shot.getShooter() instanceof ServerPlayer player)) {
            return;
        }
        float scale = ForgedSlashArtHandler.activeDamageScale(player);
        if (scale < 1.0F) {
            // Keep the value on the projectile: it can hit after the player's
            // combo ends or after they switch weapons.
            event.getEntity().getPersistentData().putFloat(DAMAGE_SCALE_TAG, scale);
        }
    }

    static void onLivingHurt(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide()) {
            return;
        }
        Entity direct = event.getSource().getDirectEntity();
        if (direct == null) {
            return;
        }
        if (isNativeAttackEntity(direct)) {
            if (direct.getPersistentData().contains(DAMAGE_SCALE_TAG)) {
                event.setAmount(event.getAmount()
                        * direct.getPersistentData().getFloat(DAMAGE_SCALE_TAG));
            }
        } else if (direct instanceof ServerPlayer player) {
            event.setAmount(event.getAmount()
                    * ForgedSlashArtHandler.activeDamageScale(player));
        }
    }

    private static boolean isNativeAttackEntity(Entity entity) {
        return entity instanceof EntitySlashEffect
                || entity instanceof EntityJudgementCut
                || entity instanceof EntityAbstractSummonedSword;
    }

    private ForgedSlashArtDamageBalance() {
    }
}
