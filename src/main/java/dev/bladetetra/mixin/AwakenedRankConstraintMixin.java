package dev.bladetetra.mixin;

import dev.bladetetra.kyouka.KyoukaMirrorSessions;
import dev.bladetetra.combat.InheritedCombatDamage;
import dev.bladetetra.easteregg.SoulLegacyDamageGuard;
import mods.flammpfeil.slashblade.capability.concentrationrank.ConcentrationRank;
import mods.flammpfeil.slashblade.capability.concentrationrank.IConcentrationRank;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;

/** Native gain observation; no positive-gain lock, rank payment or shadow rank. */
@Mixin(value = ConcentrationRank.class, remap = false)
public abstract class AwakenedRankConstraintMixin implements IConcentrationRank {
    @Override public void addRankPoint(LivingEntity user, long point) {
        long now = user.level().getGameTime();
        int before = getRank(now).level;
        IConcentrationRank.super.addRankPoint(user, point);
        KyoukaMirrorSessions.rankChanged(user, before, getRank(now).level);
    }
    @Override public void addRankPoint(net.minecraft.world.damagesource.DamageSource source) {
        // Return before native timeout combo resolution for every inherited/secondary hit.
        if (!InheritedCombatDamage.isInherited(source) && !SoulLegacyDamageGuard.isSecondary(source))
            IConcentrationRank.super.addRankPoint(source);
    }
}
