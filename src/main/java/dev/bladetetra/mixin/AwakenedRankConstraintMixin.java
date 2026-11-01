package dev.bladetetra.mixin;

import dev.bladetetra.kyouka.KyoukaMirrorSessions;
import dev.bladetetra.combat.InheritedCombatDamage;
import dev.bladetetra.easteregg.SoulLegacyDamageGuard;
import dev.bladetetra.easteregg.SakuraCombatVisuals;
import mods.flammpfeil.slashblade.capability.concentrationrank.ConcentrationRank;
import mods.flammpfeil.slashblade.capability.concentrationrank.IConcentrationRank;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import net.minecraft.world.damagesource.DamageSource;

/** Native gain observation and source-scoped Sakura anti-credit. No shadow rank or extra payment. */
@Mixin(value = ConcentrationRank.class, remap = false)
public abstract class AwakenedRankConstraintMixin implements IConcentrationRank {
    @Unique private DamageSource bladeTetra$rankSource;
    @Unique private boolean bladeTetra$slowRank;
    @Override public void addRankPoint(LivingEntity user, long point) {
        if(point>0&&bladeTetra$slowRank)point/=4;
        long now = user.level().getGameTime();
        int before = getRank(now).level;
        IConcentrationRank.super.addRankPoint(user, point);
        KyoukaMirrorSessions.rankChanged(user, before, getRank(now).level, bladeTetra$rankSource);
    }
    @Override public void addRankPoint(DamageSource source) {
        // Return before native timeout combo resolution for every inherited/secondary hit.
        if (InheritedCombatDamage.isInherited(source) || SoulLegacyDamageGuard.isSecondary(source)) return;
        double credit=SakuraCombatVisuals.rankCredit(source);
        if(credit==0)return;
        boolean previousSlow=bladeTetra$slowRank;
        bladeTetra$slowRank=credit<1;
        DamageSource previous = bladeTetra$rankSource;
        bladeTetra$rankSource = source;
        try { IConcentrationRank.super.addRankPoint(source); }
        finally { bladeTetra$rankSource = previous; bladeTetra$slowRank=previousSlow; }
    }
}
