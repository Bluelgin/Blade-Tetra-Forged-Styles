package dev.bladetetra.challenge;

import dev.bladetetra.challenge.mikage.EncounterLifecycle;
import dev.bladetetra.challenge.mikage.ReactiveCombatDirector;
import dev.bladetetra.challenge.mikage.SkillExecution;
import dev.bladetetra.challenge.mikage.SkillRunner;
import mods.flammpfeil.slashblade.ability.StunManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.util.Comparator;
import java.util.List;

/** Owns encounter flow. Legacy effects are temporary until the new skill pool is authored. */
final class MikageEncounter {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final MikageEntity owner;
    private final EncounterLifecycle lifecycle = new EncounterLifecycle();
    private final SkillRunner skills = new SkillRunner();
    private final MikagePlayerObserver observer;
    private final MikageStoryBridge story;
    // New authored follow-up links are deliberately deferred to the skill-pool discussion.
    private final ReactiveCombatDirector director = new ReactiveCombatDirector(List.of());
    private MikageLegacySkillExecution execution;
    private boolean victoryPending;

    MikageEncounter(MikageEntity owner) {
        this.owner = owner;
        observer = new MikagePlayerObserver(owner);
        story = new MikageStoryBridge(owner);
    }

    MikageStoryBridge story() { return story; }
    boolean active() { return skills.active(); }
    boolean isWindingUp() { return active() && execution != null && execution.windingUp(); }
    boolean eligible(ServerPlayer player) { return observer.eligible(player); }
    void observeAttack(ServerPlayer player) { if (!lifecycle.terminal()) observer.attack(player); }
    void observeSlashArt(ServerPlayer player, String id) {
        if (!lifecycle.terminal()) observer.slashArt(player, id);
    }

    void restorePhase(float fraction) {
        lifecycle.restore(fraction);
        owner.setPhase(lifecycle.phase());
        if (lifecycle.phase() > 1) owner.bossBar().setName(net.minecraft.network.chat.Component.translatable(
                "entity.blade_tetra.mikage.phase" + lifecycle.phase()));
    }

    void tick(ServerLevel server) {
        if (owner.isVisitorGuide()) {
            if (owner.tickCount % 20 == 0 && !ChallengeManager.isCurrentVisitorGuide(owner)) owner.discard();
            owner.setTarget(null);
            owner.setDeltaMovement(Vec3.ZERO);
            owner.bossBar().setVisible(false);
            return;
        }
        if (lifecycle.terminal() || !owner.isAlive()) return;
        observer.tick(server);
        StunManager.removeStun(owner);
        owner.getPersistentData().remove("knockback_factor");
        if (owner.techniqueRuntime().aerialTicks == 0
                && owner.arenaController().boundarySlashDelay == 0 && owner.isNoGravity()) owner.setNoGravity(false);

        if (lifecycle.state() != EncounterLifecycle.State.TRANSITION
                && lifecycle.observeHealth(owner.getHealth() / owner.getMaxHealth())) {
            stop(SkillExecution.StopReason.PHASE_CHANGE);
            MikageEncounterCleanup.encounter(owner);
            director.reset();
            owner.setPhase(lifecycle.phase());
            owner.phaseTransition(lifecycle.phase());
        }
        owner.bossBar().setProgress(owner.getHealth() / owner.getMaxHealth());
        MikageEncounterPresentation.syncHud(owner);
        Runnable effectsTick = () -> MikageRuntimeCoordinator.tick(owner, server, lifecycle.phase());
        if (active() && execution != null) execution.withScope(effectsTick);
        else effectsTick.run();
        skills.tick();
        if (!active()) execution = null;

        var combat = owner.combatDirector();
        if (!isWindingUp() && !owner.legacySkillBusy() && combat.techniqueCooldown > 0) combat.techniqueCooldown--;
        if (combat.phaseProtectionTicks > 0) return;
        if (lifecycle.state() == EncounterLifecycle.State.TRANSITION) lifecycle.ready();
        if (lifecycle.state() == EncounterLifecycle.State.OPENING) {
            if (combat.techniqueCooldown > 0) return;
            lifecycle.ready();
        }
        if (active() || owner.legacySkillBusy()) return;

        var arena = owner.arenaController();
        if (arena.boundaryFlashPending && arena.boundaryFlashReadyTicks <= 0 && lifecycle.phase() == 3) {
            ServerPlayer target = target(server);
            if (target != null) {
                arena.boundaryFlashPending = false;
                start(new MikageLegacySkillExecution(owner, target, () -> owner.beginBoundaryFlash(target, server)));
                return;
            }
        }
        if (owner.techniqueRuntime().pursuitRainCooldown <= 0) {
            ServerPlayer target = owner.selectPursuitTarget(server);
            if (target != null && eligible(target)) {
                start(new MikageLegacySkillExecution(owner, target, () -> owner.beginPursuitRain(target, server)));
                return;
            }
        }
        if (combat.techniqueCooldown > 0) return;
        ServerPlayer target = target(server);
        if (target == null) return;
        var decision = director.choose(observer.observation(target),
                MikageLegacySkillPool.available(owner, target, lifecycle.phase()), () -> owner.getRandom().nextDouble());
        if (decision == null) return;
        var technique = MikageLegacySkillPool.technique(decision.skill());
        var spec = MikageLegacySkillPool.spec(technique);
        owner.setTarget(target);
        owner.legacyEffects().lockBladeTarget(target);
        owner.lookAt(target, 180, 180);
        if (start(new MikageLegacySkillExecution(owner, technique, target,
                () -> director.committed(spec, target.getUUID())))) {
            LOGGER.debug("Mikage decision: challenge={}, target={}, skill={}, score={}, reasons={}",
                    owner.getPersistentData().getLong("blade_tetra_challenge"), target.getUUID(),
                    decision.skill(), decision.score(), decision.reasons());
            combat.techniqueCooldown = combat.scaledCooldown(lifecycle.phase() == 1 ? 48 : lifecycle.phase() == 2 ? 36 : 26);
        }
    }

    private ServerPlayer target(ServerLevel level) {
        if (owner.getTarget() instanceof ServerPlayer player && eligible(player)) return player;
        return level.players().stream().filter(this::eligible)
                .min(Comparator.comparingDouble(player -> owner.distanceToSqr(player))).orElse(null);
    }

    private boolean start(MikageLegacySkillExecution next) {
        if (!skills.start(next)) return false;
        execution = next;
        return true;
    }

    private void stop(SkillExecution.StopReason reason) {
        skills.stop(reason);
        execution = null;
    }

    void beforeDeath() {
        if (owner.level().isClientSide() || !lifecycle.defeat()) return;
        victoryPending = true;
        stop(SkillExecution.StopReason.DEFEATED);
        MikageEncounterCleanup.encounter(owner);
        observer.clear();
        director.reset();
        MikageEncounterPresentation.defeated(owner);
    }

    void defeated() {
        if (!victoryPending) return;
        victoryPending = false;
        story.defeated();
    }

    void cancel() {
        if (owner.level().isClientSide() || !lifecycle.cancel()) return;
        stop(SkillExecution.StopReason.CANCELLED);
        MikageEncounterCleanup.encounter(owner);
        observer.clear();
        director.reset();
    }
}
