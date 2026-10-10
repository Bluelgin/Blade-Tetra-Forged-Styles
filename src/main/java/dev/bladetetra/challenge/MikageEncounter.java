package dev.bladetetra.challenge;

import dev.bladetetra.config.GameplayConfig;

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

/** Owns encounter flow, observation, scoped executions and the preserved story bridge. */
final class MikageEncounter {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final MikageEntity owner;
    private final EncounterLifecycle lifecycle = new EncounterLifecycle();
    private final SkillRunner skills = new SkillRunner();
    private final MikagePlayerObserver observer;
    private final MikageStoryBridge story;
    private final MikageSkillSelection selection;
    // Follow-up links add preference, never cancel or shorten the current execution.
    private final ReactiveCombatDirector director = new ReactiveCombatDirector(MikageFollowups.links());
    private boolean victoryPending;

    MikageEncounter(MikageEntity owner) {
        this.owner = owner;
        observer = new MikagePlayerObserver(owner);
        story = new MikageStoryBridge(owner);
        selection = new MikageSkillSelection(owner);
    }

    MikageStoryBridge story() { return story; }
    boolean active() { return skills.active(); }
    boolean isWindingUp() { return skills.windingUp(); }
    MikageThousandGatesExecution gates() { return skills.execution(MikageThousandGatesExecution.class); }
    MikageGateCorridorExecution corridor() { return skills.execution(MikageGateCorridorExecution.class); }
    MikageGateBarrageExecution barrage() { return skills.execution(MikageGateBarrageExecution.class); }
    MikageMoonEchoExecution echo() { return skills.execution(MikageMoonEchoExecution.class); }
    MikageBoundaryExecution boundary() { return skills.execution(MikageBoundaryExecution.class); }
    boolean portalSkill() { return gates() != null || corridor() != null || barrage() != null; }
    Vec3 observedRoute(ServerPlayer player) { return observer.route(player); }
    boolean eligible(ServerPlayer player) { return observer.eligible(player); }
    void observeAttack(ServerPlayer player) { if (!lifecycle.terminal()) observer.attack(player); }
    void observeSlashArt(ServerPlayer player, String id) {
        if (!lifecycle.terminal()) observer.slashArt(player, id);
    }

    boolean canGuardCounter() {
        return lifecycle.state() == EncounterLifecycle.State.COMBAT && !active()
                && !owner.duel().staggered()
                && owner.getAction() == MikageEntity.MikageAction.IDLE;
    }

    boolean footworkAllowed() {
        return canGuardCounter() && owner.combatDirector().phaseProtectionTicks <= 0;
    }

    boolean answerGuard(ServerPlayer player) {
        if (!canGuardCounter()) return false;
        owner.setTarget(player);
        return start(new MikageGuardCounterExecution(owner, player));
    }

    void breakDuelBalance() {
        stop(SkillExecution.StopReason.COUNTERED);
        MikageEncounterCleanup.foreground(owner);
        owner.defenseController().hurtCooldownUntil.clear();
        director.reset();
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

        if (lifecycle.state() != EncounterLifecycle.State.TRANSITION
                && lifecycle.observeHealth(owner.getHealth() / owner.getMaxHealth())) {
            stop(SkillExecution.StopReason.PHASE_CHANGE);
            MikageEncounterCleanup.encounter(owner);
            director.reset();
            owner.setPhase(lifecycle.phase());
            owner.presentation().phaseName(lifecycle.phase());
            owner.combatDirector().phaseProtectionTicks = GameplayConfig.MIKAGE_PHASE_PROTECTION_TICKS.get();
            story.phase(lifecycle.phase());
            owner.presentation().phaseShift(server, lifecycle.phase());
        }
        owner.duel().tick(server);
        owner.bossBar().setProgress(owner.getHealth() / owner.getMaxHealth());
        owner.presentation().syncHud();
        MikageRuntimeCoordinator.tick(owner, server, lifecycle.phase());
        skills.tick();

        var combat = owner.combatDirector();
        if (!isWindingUp() && combat.techniqueCooldown > 0) combat.techniqueCooldown--;
        if (combat.phaseProtectionTicks > 0) return;
        if (lifecycle.state() == EncounterLifecycle.State.TRANSITION) lifecycle.ready();
        if (lifecycle.state() == EncounterLifecycle.State.OPENING) {
            if (combat.techniqueCooldown > 0) return;
            lifecycle.ready();
        }
        if (active() || owner.duel().staggered()) return;

        if (combat.techniqueCooldown > 0) return;
        ServerPlayer target = target(server);
        if (target == null) return;
        var decision = director.choose(observer.observation(target),
                selection.available(target, lifecycle.phase()), () -> owner.getRandom().nextDouble());
        if (decision == null) return;
        var spec = selection.spec(decision.skill());
        owner.setTarget(target);
        owner.lookAt(target, 180, 180);
        if (start(selection.create(decision.skill(), target,
                () -> director.committed(spec, target.getUUID())))) {
            LOGGER.debug("Mikage decision: challenge={}, target={}, skill={}, score={}, reasons={}",
                    owner.getPersistentData().getLong("blade_tetra_challenge"), target.getUUID(),
                    decision.skill(), decision.score(), decision.reasons());
            combat.techniqueCooldown = combat.scaledCooldown(lifecycle.phase() == 1 ? 42 : lifecycle.phase() == 2 ? 30 : 22);
        }
    }

    private ServerPlayer target(ServerLevel level) {
        if (owner.getTarget() instanceof ServerPlayer player && eligible(player)) return player;
        return level.players().stream().filter(this::eligible)
                .min(Comparator.comparingDouble(player -> owner.distanceToSqr(player))).orElse(null);
    }

    private boolean start(SkillExecution next) {
        if (active()) return false;
        owner.movement().pauseCombatFootwork();
        return skills.start(next);
    }

    private void stop(SkillExecution.StopReason reason) {
        skills.stop(reason);
    }

    void beforeDeath() {
        if (owner.level().isClientSide() || !lifecycle.defeat()) return;
        victoryPending = true;
        stop(SkillExecution.StopReason.DEFEATED);
        MikageEncounterCleanup.encounter(owner);
        observer.clear();
        director.reset();
        owner.presentation().defeated();
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
