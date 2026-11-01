package dev.bladetetra.easteregg;

import dev.bladetetra.network.AwakenedBladeVfxPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.*;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** One short gathering window and one bounded impact, owned and cleaned by the observed native trail. */
final class RaikiriNeedleBurst {
    private final Vec3 anchor;
    private final long due;
    private int needles;
    private boolean resolved;
    RaikiriNeedleBurst(Vec3 anchor,long now) { this.anchor=anchor;due=now+3; }
    boolean accepts(long now) { return !resolved && now<due && needles<3; }
    boolean resolved() { return resolved; }
    void collect(ServerPlayer player,Vec3 needle) {
        needles++;
        AwakenedBladeCombat.visual(player.serverLevel(),SoulLegacyState.Legacy.RAIKIRI,
                AwakenedBladeVfxPacket.NEEDLE_CHARGE,needle.add(0,.6,0),anchor.add(0,.6,0),needles);
    }
    void tick(ServerPlayer p,long now) {
        if(resolved || now<due) return;
        resolved=true;
        Vec3 center=anchor.add(0,.4,0);
        double radius=RaikiriConfig.BURST_RADIUS.get();
        if(!RaikiriActionObserver.loaded(p.serverLevel(),new AABB(center,center).inflate(radius))) return;
        float base=RaikiriConfig.BURST_BUDGET.get().floatValue();
        float total=base*(1+.375F*(needles-1));
        float cap=RaikiriConfig.BURST_TARGET_CAP.get().floatValue()*(1+(needles-1)/6F);
        var budget=new RaikiriRules.Budget(total,cap);
        var rain=new RaikiriRules.Budget(3,1.5F);
        var targets=RaikiriFieldService.targets(p,center,radius,4);
        Set<UUID> hit=new HashSet<>();targets.forEach(t->hit.add(t.getUUID()));
        AwakenedBladeCombat.visual(p.serverLevel(),SoulLegacyState.Legacy.RAIKIRI,
                AwakenedBladeVfxPacket.NEEDLE_BURST,center,center.add(radius,0,0),needles);
        p.serverLevel().playSound(null,anchor.x,anchor.y,anchor.z,SoundEvents.LIGHTNING_BOLT_IMPACT,
                SoundSource.PLAYERS,.65F,1.45F);
        for(var target:targets) {
            float falloff=(float)(1-.5*Math.min(1,target.getBoundingBox().getCenter().distanceTo(center)/radius));
            float amount=budget.reserve(target.getUUID(),cap*falloff);
            if(!RaikiriFieldService.damage(p,target,amount,RaikiriFieldService.DamageKind.NEEDLE_BURST)) continue;
            if(target instanceof Monster && !(target instanceof WitherBoss) && !target.getType().is(BOSS_TAG))
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,6,2,false,false));
            conduct(p,target,hit,rain);
        }
    }
    private static final net.minecraft.tags.TagKey<net.minecraft.world.entity.EntityType<?>> BOSS_TAG=
            net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ENTITY_TYPE,
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("forge","bosses"));
    private static void conduct(ServerPlayer p,LivingEntity from,Set<UUID> hit,RaikiriRules.Budget budget) {
        if(budget.remaining()<=0 || !p.serverLevel().isRainingAt(from.blockPosition())) return;
        for(var other:RaikiriFieldService.targets(p,from.getBoundingBox().getCenter(),3,32)) {
            if(hit.contains(other.getUUID()) || !p.serverLevel().isRainingAt(other.blockPosition())) continue;
            hit.add(other.getUUID());
            float amount=budget.reserve(other.getUUID(),1.5F);
            if(RaikiriFieldService.damage(p,other,amount,RaikiriFieldService.DamageKind.NEEDLE_BURST))
                AwakenedBladeCombat.visual(p.serverLevel(),SoulLegacyState.Legacy.RAIKIRI,
                        AwakenedBladeVfxPacket.BOLT,from.getBoundingBox().getCenter(),other.getBoundingBox().getCenter(),3);
            if(budget.remaining()<=0) break;
        }
    }
}
