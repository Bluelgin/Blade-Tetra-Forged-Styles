package dev.bladetetra.easteregg;

import mods.flammpfeil.slashblade.capability.concentrationrank.CapabilityConcentrationRank;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.effect.MobEffectInstance;
import dev.bladetetra.config.GameplayConfig;

import dev.bladetetra.network.AwakenedBladeVfxPacket;
import java.util.*;

/** Owned by the existing native-action session. Secondary strikes never schedule actions. */
final class RaikiriThunderArmor {
    private long expires,next;
    boolean active() { return expires!=0; }
    void start(ServerPlayer p) {
        if(active() || p.hasEffect(RaikiriThunderArmorEffect.ARMOR.get())) return;
        var rank=p.getCapability(CapabilityConcentrationRank.RANK_POINT).orElse(null);
        long now=p.level().getGameTime();
        int cost=GameplayConfig.ENABLE_AWAKENED_RANK_COSTS.get()?60:0;
        if(rank==null || !RaikiriRules.armorCanStart(
                rank.getRankPoint(now),rank.getUnitCapacity(),rank.getRank(now).level,cost)) {
            p.displayClientMessage(Component.translatable("message.blade_tetra.raikiri.armor_need_rank"),true);
            return;
        }
        if(!p.addEffect(new MobEffectInstance(RaikiriThunderArmorEffect.ARMOR.get(),160,0,false,false,true))) return;
        rank.addRankPoint(p,-cost);
        expires=now+160;next=now+20;
        p.displayClientMessage(Component.translatable("message.blade_tetra.raikiri.armor_start"),true);
    }
    void stop(ServerPlayer p) {
        boolean wasActive=active();expires=0;
        if(p==null) return;
        p.removeEffect(RaikiriThunderArmorEffect.ARMOR.get());
        if(wasActive) p.displayClientMessage(Component.translatable("message.blade_tetra.raikiri.armor_end"),true);
    }
    void tick(ServerPlayer p) {
        if(!active()) return;
        long now=p.level().getGameTime();
        var rank=p.getCapability(CapabilityConcentrationRank.RANK_POINT).orElse(null);
        if(now>=expires || !p.hasEffect(RaikiriThunderArmorEffect.ARMOR.get()) || rank==null || !RaikiriRules.armorCanSustain(
                rank.getRankPoint(now),rank.getUnitCapacity(),rank.getRank(now).level)) { stop(p);return; }
        if(now<next) return;
        next=now+20;
        int cost=GameplayConfig.ENABLE_AWAKENED_RANK_COSTS.get()?30:0;
        // Refuse the whole pulse before payment if it would cross the B floor.
        if(!RaikiriRules.armorCanSustain(rank.getRankPoint(now)-cost,rank.getUnitCapacity(),
                rank.getRank(now).level)) { stop(p);return; }
        rank.addRankPoint(p,-cost);
        var primary=RaikiriFieldService.targets(p,p.getBoundingBox().getCenter(),4,2);
        Set<UUID> hit=new HashSet<>();primary.forEach(t->hit.add(t.getUUID()));
        boolean conducted=false;
        for(var target:primary) {
            if(!strike(p,p,target,2)) continue;
            // Actual local precipitation: roofs and dry biomes cannot conduct.
            if(conducted || !p.serverLevel().isRainingAt(target.blockPosition())) continue;
            for(var other:RaikiriFieldService.targets(p,target.getBoundingBox().getCenter(),3,32)) {
                if(hit.contains(other.getUUID()) || !p.serverLevel().isRainingAt(other.blockPosition())) continue;
                hit.add(other.getUUID());conducted=true;strike(p,target,other,1);break;
            }
        }
    }
    private boolean strike(ServerPlayer p,LivingEntity from,LivingEntity target,float amount) {
        if(!RaikiriFieldService.damage(p,target,amount,RaikiriFieldService.DamageKind.ARMOR_DISCHARGE)) return false;
        AwakenedBladeCombat.visual(p.serverLevel(),SoulLegacyState.Legacy.RAIKIRI,
                AwakenedBladeVfxPacket.BOLT,from.getBoundingBox().getCenter(),target.getBoundingBox().getCenter(),from==p?2:3);
        return true;
    }
}
