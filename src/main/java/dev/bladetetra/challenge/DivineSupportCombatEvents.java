package dev.bladetetra.challenge;

import dev.bladetetra.BladeTetra;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid=BladeTetra.MOD_ID)
public final class DivineSupportCombatEvents {
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void protect(LivingAttackEvent event) {
        var target=event.getEntity();
        var s=DivineDomainManager.activeSession(target);
        if(target instanceof ServerPlayer player && s!=null && s.support!=null && s.support.protectedPlayer(player)) {
            event.setCanceled(true); return;
        }
        var source=event.getSource().getEntity();
        if(source==null) return;
        var sourceSession=DivineDomainManager.activeSession(source);
        if(sourceSession==null || !sourceSession.enemies.contains(source.getUUID())) return;
        boolean ownCompanion=sourceSession.support!=null && target.getUUID().equals(sourceSession.support.companionId());
        // Ritual mobs cannot damage another party, spectators, pets or unrelated world entities.
        if(sourceSession!=s && !ownCompanion) { event.setCanceled(true); return; }
        var support=sourceSession.support;
        if(support!=null && (support.bound(source) || support.attackingArray(source))) { event.setCanceled(true); return; }
        if(support!=null && event.getSource().getDirectEntity()==source && support.wallSeparates(source,target)) {
            event.setCanceled(true); return;
        }
        if(support!=null && support.inArray(source) && target instanceof ServerPlayer
                && event.getSource().getDirectEntity()==source && !support.allowMelee(source)) event.setCanceled(true);
    }
    @SubscribeEvent(priority=EventPriority.LOW)
    public static void hurt(LivingHurtEvent event) {
        var target=event.getEntity(); var s=DivineDomainManager.activeSession(target);
        if(s==null || s.support==null) return;
        var support=s.support; var source=event.getSource().getEntity();
        if(s.enemies.contains(target.getUUID())) {
            boolean window=support.marked(target) || support.bound(target);
            if(DivineDomainEnemyRole.of(target)==DivineDomainEnemyRole.GUARDIAN && !window)
                event.setAmount(event.getAmount()*.35F);
            if(source instanceof ServerPlayer player && s.players.contains(player.getUUID())) {
                // Windows replace each other; array + mark + final binding never compound.
                boolean eliteInArray=support.inArray(target) && DivineDomainEnemyRole.of(target)!=DivineDomainEnemyRole.ORDINARY;
                event.setAmount(event.getAmount()*DivineSupportRules.playerWindowMultiplier(window,eliteInArray));
            }
            if(source instanceof MikageDivineCompanionEntity)
                event.setAmount(Math.min(event.getAmount(),DivineSupportRules.companionDamage(target.getHealth())));
        } else if(source!=null && s.enemies.contains(source.getUUID())) {
            if(support.bound(source)) { event.setCanceled(true); return; }
            if(support.inArray(source)) event.setAmount(event.getAmount()*.75F);
        }
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void finalDamage(LivingDamageEvent event) {
        var target=event.getEntity(); var s=DivineDomainManager.activeSession(target);
        if(s==null || s.support==null) return;
        if(target instanceof ServerPlayer player && s.support.protectedPlayer(player)) event.setAmount(0);
        if(event.getSource().getEntity() instanceof MikageDivineCompanionEntity)
            event.setAmount(Math.min(event.getAmount(),DivineSupportRules.companionDamage(target.getHealth())));
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void leaveFinalBlowToPlayers(LivingDeathEvent event) {
        var s=DivineDomainManager.activeSession(event.getEntity());
        if(s!=null && s.enemies.contains(event.getEntity().getUUID())
                && event.getSource().getEntity() instanceof MikageDivineCompanionEntity) {
            event.setCanceled(true); event.getEntity().setHealth(1);
        }
    }
    @SubscribeEvent
    public static void scopedTarget(LivingChangeTargetEvent event) {
        var s=DivineDomainManager.activeSession(event.getEntity());
        if(s==null || !s.enemies.contains(event.getEntity().getUUID()) || event.getNewTarget()==null) return;
        var support=s.support;
        if(support!=null && (support.bound(event.getEntity()) || support.attackingArray(event.getEntity()))) {
            event.setCanceled(true); return;
        }
        var target=event.getNewTarget();
        boolean companion=support!=null && target.getUUID().equals(support.companionId());
        if(!target.isAlive() || target.level()!=event.getEntity().level()
                || !s.players.contains(target.getUUID()) && !companion) event.setCanceled(true);
    }
    private DivineSupportCombatEvents() {}
}
