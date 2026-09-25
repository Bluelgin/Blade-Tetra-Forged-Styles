package dev.bladetetra.combat;

import mods.flammpfeil.slashblade.SlashBlade;
import mods.flammpfeil.slashblade.entity.EntityAbstractSummonedSword;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraftforge.entity.PartEntity;

/** Native summoned-sword flight and rendering with one bounded forged hit. */
final class ForgedSummonedSword extends EntityAbstractSummonedSword {
    private final float forgedDamage;
    private boolean hitResolved;

    ForgedSummonedSword(ServerLevel level, double damage) {
        super(SlashBlade.RegistryEvents.SummonedSword, level);
        forgedDamage = (float) Math.max(0.0D, damage);
        // Native summoned-sword damage rounds up and scales with its shooter.
        // The authored hit below owns combat; the parent entity is visual only.
        setDamage(0.0D);
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        if (hitResolved || !(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        hitResolved = true;
        Entity hit = result.getEntity();
        if (hit instanceof PartEntity<?> part) {
            hit = part.getParent();
        }
        if (hit instanceof LivingEntity target
                && getShooter() instanceof ServerPlayer player
                && target.level() == serverLevel) {
            LegacyFusionCombatSupport.hurtPreservingIFrames(
                    serverLevel, player, target, forgedDamage);
        }
        // Do not call super: it clears the target's iframe, scales damage again,
        // and can attach the sword for a later status-effect burst.
        discard();
    }
}
