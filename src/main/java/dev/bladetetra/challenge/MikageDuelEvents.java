package dev.bladetetra.challenge;

import dev.bladetetra.BladeTetra;
import dev.bladetetra.combat.BranchingStyleCombos;
import dev.bladetetra.combat.ModComboStates;
import mods.flammpfeil.slashblade.entity.EntityJudgementCut;
import mods.flammpfeil.slashblade.entity.EntitySlashEffect;
import mods.flammpfeil.slashblade.event.BladeMotionEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Only actual server swings arm the copied three-tick parry window. No client damage authority. */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MikageDuelEvents {
    static final String VISUAL_ONLY = "blade_tetra_mikage_duel_visual";

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void attack(AttackEntityEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getTarget() instanceof MikageEntity boss
                && ChallengeManager.isParticipant(boss, player)) boss.duel().swing(player, false);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void motion(BladeMotionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !meleeMotion(event.getCombo())) return;
        MikageEntity boss = ChallengeManager.combatBoss(player);
        if (boss != null) boss.duel().swing(player, true);
    }

    private static boolean meleeMotion(ResourceLocation combo) {
        if (combo == null) return false;
        if ("slashblade".equals(combo.getNamespace())) {
            String path = combo.getPath();
            return (path.startsWith("combo_a") || path.startsWith("combo_b") || path.startsWith("combo_c"))
                    && !path.contains("end") || path.startsWith("aerial_rave")
                    || path.equals("upper_slash") || path.equals("upperslash") || path.equals("rapid_slash")
                    || path.equals("circle_slash") || path.equals("aerial_cleave");
        }
        if (ModComboStates.isIaidoAttack(combo)) return true;
        var phase = BranchingStyleCombos.phase(combo);
        return phase != null && !phase.recovery() && !phase.name().endsWith("JUMP") && !phase.dive();
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void protect(LivingAttackEvent event) {
        var direct = event.getSource().getDirectEntity();
        var traced = MikageBladeAttackTrace.projectile();
        if ((direct != null && direct.getPersistentData().getBoolean(VISUAL_ONLY))
                || (traced != null && traced.getPersistentData().getBoolean(VISUAL_ONLY))) {
            event.setCanceled(true);
            return;
        }
        if (event.getEntity() instanceof ServerPlayer player
                && MikageDamageService.resolveCombatAttacker(event.getSource()) instanceof MikageEntity boss
                && boss.duel().protects(player)) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void capture(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        var entity = event.getEntity();
        var shooter = entity instanceof EntitySlashEffect slash ? slash.getShooter()
                : entity instanceof EntityJudgementCut cut ? cut.getOwner() : null;
        if (shooter instanceof ServerPlayer player && ChallengeManager.isParticipant(player))
            MikageBladeAttackTrace.capture(entity, player.getMainHandItem());
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { MikageBladeAttackTrace.clear(); }
    private MikageDuelEvents() {}
}
