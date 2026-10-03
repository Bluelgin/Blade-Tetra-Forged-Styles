package dev.bladetetra.challenge;

import dev.bladetetra.BladeTetra;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Party isolation is independent of rescue. Fire stops ritual melee, never ranged/environmental damage. */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID)
public final class DivineSupportCombatEvents {
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void protect(LivingAttackEvent event) {
        var source = event.getSource().getEntity();
        if (source == null) return;
        var session = DivineDomainManager.activeSession(source);
        if (session == null || !session.enemies.contains(source.getUUID())) return;
        var target = event.getEntity();
        if (!session.players.contains(target.getUUID()) || DivineDomainManager.activeSession(target) != session) {
            event.setCanceled(true);
            return;
        }
        if (target instanceof ServerPlayer player && session.support != null
                && event.getSource().getDirectEntity() == source && session.support.blocksMelee(player))
            event.setCanceled(true);
    }

    @SubscribeEvent
    public static void scopedTarget(LivingChangeTargetEvent event) {
        var session = DivineDomainManager.activeSession(event.getEntity());
        if (session == null || !session.enemies.contains(event.getEntity().getUUID()) || event.getNewTarget() == null) return;
        var target = event.getNewTarget();
        if (!target.isAlive() || target.level() != event.getEntity().level()
                || !session.players.contains(target.getUUID())) event.setCanceled(true);
    }
    private DivineSupportCombatEvents() {}
}
