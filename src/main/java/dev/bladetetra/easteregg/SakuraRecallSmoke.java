package dev.bladetetra.easteregg;

import com.mojang.logging.LogUtils;
import mods.flammpfeil.slashblade.capability.concentrationrank.CapabilityConcentrationRank;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;

/** Opt-in real-server regressions for Rank debit/refund, uncapped scaling and protected two-part damage. */
final class SakuraRecallSmoke {
    static void verify(ServerLevel level,ServerPlayer p,ItemStack blade) {
        var victim=zombie(level,3);var nearby=zombie(level,2);
        var rank=p.getCapability(CapabilityConcentrationRank.RANK_POINT).orElseThrow(IllegalStateException::new);
        var state=blade.getCapability(ItemSlashBlade.BLADESTATE).orElseThrow(IllegalStateException::new);
        try {
            SakuraShardAttackService.cancel(p);time(level,level.getGameTime()+120);
            rank.setRawRankPoint(rank.getMaxCapacity());rank.setLastUpdte(level.getGameTime());
            SakuraShardAttackService.targetHit(p,blade,victim);
            start(p,blade);long at=level.getGameTime();
            int release=SakuraRecallService.duration(p)-17;
            check(SakuraRecallService.active(p),"upper starts charged recall");
            check(!SakuraInfiniteDanceSA.active(p,blade),"recall and infinite SA cannot overlap");
            float before=victim.getHealth(),otherBefore=nearby.getHealth();
            double expected=p.getAttributeValue(Attributes.ATTACK_DAMAGE)*14
                    *dev.bladetetra.config.GameplayConfig.SAKURA_RECALL_DAMAGE_SCALE.get();
            for(int t=1;t<=release;t++) {
                time(level,at+t);
                long points=rank.getRankPoint(level.getGameTime());tick(p);
                check(rank.getRankPoint(level.getGameTime())<=points,"burn never creates Rank");
                long paid=rank.getRankPoint(level.getGameTime());tick(p);
                check(rank.getRankPoint(level.getGameTime())==paid,"same tick cannot burn twice");
                check(victim.getHealth()==before,"long charge does not damage prematurely");
            }
            check(rank.getRankPoint(level.getGameTime())==0,"full charge burns remaining Rank to zero");
            victim.invulnerableTime=20;
            time(level,at+release+2);tick(p);
            float piercing=before-victim.getHealth();
            check(Math.abs(piercing-expected*.3)<.01,"actual piercing health loss matches 30 percent budget");
            check(piercing>12&&piercing<=expected*.3+.001,"piercing scales beyond old cap but rejects external amplification");
            check(nearby.getHealth()==otherBefore,"surrounding enemies do not take piercing damage");
            time(level,at+release+5);tick(p);
            float total=before-victim.getHealth();
            check(Math.abs(total-expected)<.01,"actual combined health loss matches complete uncapped finisher");
            LogUtils.getLogger().info("SAKURA_BLOOM_DAMAGE: attack={}, piercing={}, explosion={}, total={}",
                    p.getAttributeValue(Attributes.ATTACK_DAMAGE),piercing,total-piercing,total);
            check(total>piercing&&total<=expected+.001,"bloom is not swallowed by piercing invulnerability and total budget is shared");
            check(nearby.getHealth()<otherBefore&&otherBefore-nearby.getHealth()<=expected*.7+.001,"only bloom splashes nearby hostile");
            long afterRank=rank.getRankPoint(level.getGameTime());tick(p);
            check(victim.getHealth()==before-total&&rank.getRankPoint(level.getGameTime())==afterRank,"no repeated damage or secondary Rank credit");
            time(level,at+release+17);tick(p);
            check(!SakuraRecallService.active(p)&&SakuraStanceController.fracture(blade)==0,"full return restores blade");
            // Snapshot grade/points set the maximum; gaining Rank after admission cannot extend a zero-tier charge.
            time(level,level.getGameTime()+120);rank.setRawRankPoint(0);rank.setLastUpdte(level.getGameTime());
            start(p,blade);at=level.getGameTime();
            check(SakuraRecallService.duration(p)==SakuraRecallSequence.end(0),"zero Rank has a valid base finisher");
            rank.setRawRankPoint(rank.getMaxCapacity());rank.setLastUpdte(level.getGameTime());
            time(level,at+32);tick(p);
            long free=rank.getRankPoint(level.getGameTime());
            time(level,at+33);tick(p);check(rank.getRankPoint(level.getGameTime())<=free,"new Rank cannot buy unplanned stages");
            SakuraShardAttackService.cancel(p);time(level,at+50);tick(p);
            // Losing the target before release refunds only committed payments and never picks another enemy.
            time(level,level.getGameTime()+120);rank.setRawRankPoint(rank.getMaxCapacity());rank.setLastUpdte(level.getGameTime());
            SakuraShardAttackService.targetHit(p,blade,victim);start(p,blade);at=level.getGameTime();
            time(level,at+32);long beforePay=rank.getRankPoint(level.getGameTime());tick(p);long paid=rank.getRankPoint(level.getGameTime());
            float untouched=nearby.getHealth();victim.moveTo(0,100,20,0,0);tick(p);
            check(!SakuraRecallService.active(p)&&rank.getRankPoint(level.getGameTime())==beforePay,"pre-release invalid target refunds paid Rank");
            check(nearby.getHealth()==untouched,"cancellation cannot retarget nearby enemy");
            time(level,at+44);tick(p);
            // An untouched nearest hostile can be selected. Protection rejects the whole blast.
            time(level,level.getGameTime()+120);rank.setRawRankPoint(0);rank.setLastUpdte(level.getGameTime());
            nearby.invulnerableTime=0;start(p,blade);at=level.getGameTime();
            java.util.function.Consumer<AttackEntityEvent> protect=e->{if(e.getEntity()==p&&e.getTarget()==nearby)e.setCanceled(true);};
            MinecraftForge.EVENT_BUS.addListener(protect);
            try {
                for(int t=1;t<=SakuraRecallSequence.end(0);t++){time(level,at+t);tick(p);}
                check(nearby.getHealth()==untouched,"protected target prevents piercing and bloom");
            } finally {MinecraftForge.EVENT_BUS.unregister(protect);}
            time(level,level.getGameTime()+120);nearby.setHealth(1);nearby.invulnerableTime=0;
            rank.setRawRankPoint(360);rank.setLastUpdte(level.getGameTime());
            var locators=new java.util.ArrayList<mods.flammpfeil.slashblade.entity.EntityAbstractSummonedSword>();
            java.util.function.Consumer<net.minecraftforge.event.entity.EntityJoinLevelEvent> observe=e->{
                if(e.getEntity() instanceof mods.flammpfeil.slashblade.entity.EntityAbstractSummonedSword sword
                        &&sword.getOwner()==p&&sword.getPersistentData().getBoolean("blade_tetra_sakura_locator"))locators.add(sword);
            };
            MinecraftForge.EVENT_BUS.addListener(observe);
            try {
                start(p,blade);at=level.getGameTime();int weakRelease=SakuraRecallService.duration(p)-17;
                for(int t=1;t<=weakRelease;t++) {
                    time(level,at+t);tick(p);
                    for(var sword:locators)if(!sword.isRemoved())sword.tick();
                    check(nearby.isAlive(),"locator cannot kill the finisher target during charge");
                }
                check(!locators.isEmpty(),"charging emits actual native locator swords");
                time(level,at+weakRelease+2);tick(p);
                check(!nearby.isAlive(),"piercing can kill the sole remaining target");
                time(level,at+weakRelease+5);tick(p);
                check(SakuraRecallService.active(p),"lethal piercing still executes the bloom and return lifecycle");
                time(level,at+weakRelease+17);tick(p);
                LogUtils.getLogger().info("SAKURA_LOCATOR_SMOKE_PASS: {} native swords, nonlethal charge, lethal piercing followed by bloom",locators.size());
            } finally {MinecraftForge.EVENT_BUS.unregister(observe);locators.forEach(net.minecraft.world.entity.Entity::discard);}
            nearby.moveTo(0,100,20,0,0);time(level,level.getGameTime()+120);
            start(p,blade);check(!SakuraRecallService.active(p),"no nearby enemy gives harmless return");
            time(level,level.getGameTime()+14);tick(p);
            blade.getOrCreateTag().putInt(SakuraStanceController.FRACTURE,2);
            state.updateComboSeq(p,ComboStateRegistry.UPPERSLASH.getId());tick(p);
            check(!SakuraRecallService.active(p),"partial fracture is harmless");
            time(level,level.getGameTime()+14);tick(p);
            LogUtils.getLogger().info("SAKURA_RECALL_SMOKE_PASS: progressive Rank burn, idempotence, long charge, uncapped scaling, two-part hit, bounded splash, no Rank credit, snapshot admission, refund, no retarget, protected target and harmless return");
        } finally {SakuraShardAttackService.cancel(p);victim.discard();nearby.discard();}
    }
    private static Zombie zombie(ServerLevel level,double z) {
        var v=new Zombie(level);v.setNoAi(true);v.setNoGravity(true);v.moveTo(0,100,z,0,0);
        v.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);v.setHealth(1000);
        v.getAttribute(Attributes.ARMOR).setBaseValue(0);level.addFreshEntity(v);return v;
    }
    private static void start(ServerPlayer p,ItemStack blade) {
        blade.getOrCreateTag().putBoolean(SakuraStanceRules.DISPERSED,true);
        blade.getOrCreateTag().putInt(SakuraStanceController.FRACTURE,4);
        blade.getCapability(ItemSlashBlade.BLADESTATE).orElseThrow(IllegalStateException::new)
                .updateComboSeq(p,ComboStateRegistry.UPPERSLASH.getId());tick(p);
    }
    private static void tick(ServerPlayer p) {
        var e=new TickEvent.PlayerTickEvent(TickEvent.Phase.END,p);
        SakuraStanceController.tick(e);SakuraShardAttackService.tick(e);SakuraRecallService.tick(e);
    }
    private static void time(ServerLevel l,long t) {l.getServer().getWorldData().overworldData().setGameTime(t);}
    private static void check(boolean okay,String message) {if(!okay)throw new IllegalStateException(message);}
    private SakuraRecallSmoke() {}
}
