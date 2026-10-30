package dev.bladetetra.easteregg;

import com.mojang.logging.LogUtils;
import mods.flammpfeil.slashblade.entity.EntityAbstractSummonedSword;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import mods.flammpfeil.slashblade.util.AttackManager;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.*;
import java.util.*;

/** Real native melee -> LivingDamage -> native summoned sword flight/collision regression. */
final class SakuraConcertoSmoke {
    private final ServerPlayer actor;
    private final List<EntityAbstractSummonedSword> swords=new ArrayList<>();
    private final List<mods.flammpfeil.slashblade.entity.EntitySlashEffect> slashes=new ArrayList<>();
    private float meleeDamage,swordDamage,saDamage;
    private SakuraConcertoSmoke(ServerPlayer actor) {this.actor=actor;}
    @SubscribeEvent public void join(EntityJoinLevelEvent e) {
        if(e.getEntity() instanceof EntityAbstractSummonedSword sword&&sword.getOwner()==actor
                &&sword.getPersistentData().getBoolean("blade_tetra_sakura_concerto")) swords.add(sword);
        if(e.getEntity() instanceof mods.flammpfeil.slashblade.entity.EntitySlashEffect slash&&slash.getOwner()==actor) slashes.add(slash);
    }
    @SubscribeEvent(priority=EventPriority.HIGH) public void amplify(net.minecraftforge.event.entity.living.LivingHurtEvent e) {
        // Simulate another mod multiplying secondary damage before our final budget guard.
        if(e.getSource().getDirectEntity()==actor&&SoulLegacyDamageGuard.isSecondary(e.getSource())) e.setAmount(e.getAmount()*1000);
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public void hit(LivingDamageEvent e) {
        if(e.getSource().getEntity()!=actor) return;
        if(e.getSource().getDirectEntity() instanceof EntityAbstractSummonedSword) swordDamage+=e.getAmount();
        else if(SoulLegacyDamageGuard.isSecondary(e.getSource())) saDamage+=e.getAmount();
        else meleeDamage=e.getAmount();
    }
    static void verify(ServerLevel level,ServerPlayer actor,ItemStack blade) {
        var probe=new SakuraConcertoSmoke(actor);MinecraftForge.EVENT_BUS.register(probe);
        var target=new Zombie(level);target.setNoAi(true);target.setNoGravity(true);
        target.moveTo(0,100,3,0,0);target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);target.setHealth(1000);
        level.addFreshEntity(target);actor.moveTo(0,100,0,0,0);actor.setOnGround(true);
        var damage=actor.getAttribute(Attributes.ATTACK_DAMAGE);double before=damage.getBaseValue();damage.setBaseValue(20);
        try {
            var state=blade.getCapability(ItemSlashBlade.BLADESTATE).orElseThrow(IllegalStateException::new);
            target.moveTo(0,100,30,0,0);check(!SakuraShardAttackService.validTarget(actor,target),"out-of-range target rejected");
            target.moveTo(0,100,3,0,0);
            var wall=new net.minecraft.core.BlockPos(0,101,1);var original=level.getBlockState(wall);
            try {level.setBlockAndUpdate(wall,net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
                check(!SakuraShardAttackService.validTarget(actor,target),"wall blocks targeting");}
            finally {level.setBlockAndUpdate(wall,original);}
            long at=level.getGameTime()+30;time(level,at);
            state.setComboSeq(ComboStateRegistry.COMBO_B1.getId());state.setLastActionTime(at);
            AttackManager.doMeleeAttack(actor,target,true,false,.244F);
            check(probe.meleeDamage>0,"real native melee damages target");
            for(int t=1;t<=SakuraShardAttackRules.FIRE_TICK;t++) {time(level,at+t);service(actor);}
            check(probe.swords.size()==1,"one native summoned sword emitted after real melee");
            EntityAbstractSummonedSword sword=probe.swords.get(0);
            check(sword.getY()>target.getBoundingBox().maxY,"projectile starts above enemy head");
            check(SoulLegacyDamageGuard.isSecondary(sword.damageSources().indirectMagic(sword,actor)),"explicit secondary source marker");
            for(int t=SakuraShardAttackRules.FIRE_TICK+1;t<=12;t++) {
                time(level,at+t);if(!sword.isRemoved()) sword.tick();service(actor);
                SakuraShardAttackService.serverTick(new TickEvent.ServerTickEvent(TickEvent.Phase.END,()->true,level.getServer()));
            }
            check(probe.swordDamage>0,"native projectile collision deals real server damage");
            check(probe.swordDamage<=Math.min(6,probe.meleeDamage*.25)+.0001,"effective damage respects associated hit fraction and cap");
            check(probe.swords.size()==1,"projectile hit cannot recursively emit another concerto");
            // Multiple native hits in one action cannot authorize further swords.
            for(int i=0;i<10;i++) AttackManager.doMeleeAttack(actor,target,true,false,.244F);
            for(int t=13;t<23;t++) {time(level,at+t);service(actor);}
            check(probe.swords.size()==1,"multihit action deduplicated");
            // An invalid/dead target cancels a pending emission.
            time(level,at+30);state.updateComboSeq(actor,ComboStateRegistry.COMBO_B2.getId());
            AttackManager.doMeleeAttack(actor,target,true,false,.244F);target.discard();
            for(int t=31;t<40;t++) {time(level,at+t);service(actor);}
            check(probe.swords.size()==1,"removed target cancels pending projectile");
            var victim=new Zombie(level);victim.setNoAi(true);victim.setNoGravity(true);victim.moveTo(0,100,2,0,0);
            victim.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);victim.setHealth(1000);level.addFreshEntity(victim);
            try {
                SakuraInfiniteDanceSA.end(actor);
                blade.getOrCreateTag().putBoolean(SakuraStanceRules.DISPERSED,false);SakuraEndlessFlow.reconcile(actor);
                blade.getOrCreateTag().putBoolean(SakuraStanceRules.DISPERSED,true);time(level,at+60);
                actor.getCapability(mods.flammpfeil.slashblade.capability.concentrationrank.CapabilityConcentrationRank.RANK_POINT)
                    .ifPresent(rank->{rank.setRawRankPoint(rank.getMaxCapacity());rank.setLastUpdte(level.getGameTime());});
                state.setComboSeq(ComboStateRegistry.NONE.getId());state.setLastActionTime(level.getGameTime());state.setTargetEntityId(victim.getId());
                state.doChargeAction(actor,40);check(SakuraInfiniteDanceSA.active(actor,blade),"SA for native real damage probe");
                probe.meleeDamage=0;probe.saDamage=0;long start=level.getGameTime();
                for(int t=0;t<=8;t++) {
                    time(level,start+t);SakuraEndlessFlow.reconcile(actor);
                    blade.getItem().inventoryTick(blade,level,actor,actor.getInventory().selected,true);
                    for(var slash:new ArrayList<>(probe.slashes)) if(!slash.isRemoved()) slash.tick();
                    SakuraInfiniteDanceSA.tick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END,actor));
                }
                check(probe.meleeDamage>0,"input lock preserves native scoped melee damage during SA");
                check(probe.saDamage>0&&probe.saDamage<=8.001,"SA extra damage has independent per-stage budget");
                check(probe.swords.size()==1,"SA and normal projectile scheduling are mutually exclusive");
            } finally {victim.discard();SakuraInfiniteDanceSA.end(actor);probe.slashes.forEach(mods.flammpfeil.slashblade.entity.EntitySlashEffect::discard);}
            LogUtils.getLogger().info("SAKURA_CONCERTO_SMOKE_PASS: native melee, real summoned sword collision, associated damage cap, explicit secondary source, multihit deduplication, no recursive projectile, removed-target cancellation");
        } finally {
            SakuraShardAttackService.cancel(actor);target.discard();probe.swords.forEach(EntityAbstractSummonedSword::discard);
            damage.setBaseValue(before);MinecraftForge.EVENT_BUS.unregister(probe);
        }
    }
    private static void service(ServerPlayer p) {SakuraShardAttackService.tick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END,p));}
    private static void time(ServerLevel level,long t) {level.getServer().getWorldData().overworldData().setGameTime(t);}
    private static void check(boolean okay,String message) {if(!okay)throw new IllegalStateException(message);}
}
