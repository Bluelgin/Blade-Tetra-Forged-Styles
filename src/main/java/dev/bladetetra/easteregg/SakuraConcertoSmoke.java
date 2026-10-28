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
    private float meleeDamage,swordDamage;
    private SakuraConcertoSmoke(ServerPlayer actor) {this.actor=actor;}
    @SubscribeEvent public void join(EntityJoinLevelEvent e) {
        if(e.getEntity() instanceof EntityAbstractSummonedSword sword&&sword.getOwner()==actor
                &&sword.getPersistentData().getBoolean("blade_tetra_sakura_concerto")) swords.add(sword);
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public void hit(LivingDamageEvent e) {
        if(e.getSource().getEntity()!=actor) return;
        if(e.getSource().getDirectEntity() instanceof EntityAbstractSummonedSword) swordDamage+=e.getAmount();
        else if(!SoulLegacyDamageGuard.isSecondary(e.getSource())) meleeDamage=e.getAmount();
    }
    static void verify(ServerLevel level,ServerPlayer actor,ItemStack blade) {
        var probe=new SakuraConcertoSmoke(actor);MinecraftForge.EVENT_BUS.register(probe);
        var target=new Zombie(level);target.setNoAi(true);target.setNoGravity(true);
        target.moveTo(0,100,3,0,0);target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);target.setHealth(1000);
        level.addFreshEntity(target);actor.moveTo(0,100,0,0,0);actor.setOnGround(true);
        var damage=actor.getAttribute(Attributes.ATTACK_DAMAGE);double before=damage.getBaseValue();damage.setBaseValue(20);
        try {
            var state=blade.getCapability(ItemSlashBlade.BLADESTATE).orElseThrow(IllegalStateException::new);
            long at=level.getGameTime()+30;time(level,at);
            state.setComboSeq(ComboStateRegistry.COMBO_B1.getId());state.setLastActionTime(at);
            AttackManager.doMeleeAttack(actor,target,true,false,.244F);
            check(probe.meleeDamage>0,"real native melee damages target");
            for(int t=1;t<=5;t++) {time(level,at+t);service(actor);}
            check(probe.swords.size()==1,"one native summoned sword emitted after real melee");
            EntityAbstractSummonedSword sword=probe.swords.get(0);
            check(SoulLegacyDamageGuard.isSecondary(sword.damageSources().indirectMagic(sword,actor)),"explicit secondary source marker");
            for(int t=6;t<=12;t++) {
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
