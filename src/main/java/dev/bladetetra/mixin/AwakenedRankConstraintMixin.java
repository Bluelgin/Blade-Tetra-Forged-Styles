package dev.bladetetra.mixin;

import dev.bladetetra.kyouka.KyoukaMirrorSessions;
import dev.bladetetra.combat.InheritedCombatDamage;
import dev.bladetetra.combat.ComponentCombatValues;
import dev.bladetetra.combat.HabakiRankRetention;
import dev.bladetetra.easteregg.SoulLegacyDamageGuard;
import dev.bladetetra.easteregg.AkatsukiBloodTendrils;
import mods.flammpfeil.slashblade.entity.EntitySpiralSwords;
import net.minecraft.server.level.ServerPlayer;
import dev.bladetetra.easteregg.SakuraCombatVisuals;
import mods.flammpfeil.slashblade.capability.concentrationrank.ConcentrationRank;
import mods.flammpfeil.slashblade.capability.concentrationrank.IConcentrationRank;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import net.minecraft.world.damagesource.DamageSource;

/** Native gain observation and source-scoped Sakura anti-credit. No shadow rank or extra payment. */
@Mixin(value = ConcentrationRank.class, remap = false)
public abstract class AwakenedRankConstraintMixin implements IConcentrationRank, HabakiRankRetention.DecayClock {
    @Unique private DamageSource bladeTetra$rankSource;
    @Unique private boolean bladeTetra$slowRank;
    @Unique private long bladeTetra$lastDecayTick = Long.MIN_VALUE;
    @Unique private boolean bladeTetra$heldHabaki;
    @Unique private int bladeTetra$heldDecayTicks;

    @Override public void bladeTetra$tickRankDecay(long now, boolean holdingHabaki) {
        if (now == bladeTetra$lastDecayTick) return;
        if (now == bladeTetra$lastDecayTick + 1 && bladeTetra$heldHabaki) {
            // Each protected tick advances the native decay clock by one, keeping all
            // explicit gains, costs, rank floors and native sync on the same balance.
            if (++bladeTetra$heldDecayTicks == ComponentCombatValues.RANK_DECAY_INTERVAL) {
                if (getLastUpdate() < now) setLastUpdte(getLastUpdate() + 1);
                bladeTetra$heldDecayTicks = 0;
            }
        } else {
            bladeTetra$heldDecayTicks = 0;
        }
        bladeTetra$lastDecayTick = now;
        bladeTetra$heldHabaki = holdingHabaki;
    }
    @Override public void addRankPoint(LivingEntity user, long point) {
        if(point>0&&bladeTetra$slowRank)point/=4;
        long now = user.level().getGameTime();
        int before = getRank(now).level;
        boolean orbitingHit = point > 0 && user instanceof ServerPlayer player
                && bladeTetra$rankSource != null
                && bladeTetra$rankSource.getDirectEntity() instanceof EntitySpiralSwords sword
                && sword.getClass() == EntitySpiralSwords.class && sword.getOwner() == player && !sword.itFired();
        long beforePoints = orbitingHit ? Math.max(0, getRankPoint(now)) : 0;
        IConcentrationRank.super.addRankPoint(user, point);
        if (orbitingHit) {
            AkatsukiBloodTendrils.orbitingSwordRankGain((ServerPlayer) user, getRankPoint(now) - beforePoints);
        }
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
