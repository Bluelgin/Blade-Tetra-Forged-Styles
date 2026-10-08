package dev.bladetetra.challenge;

import dev.bladetetra.challenge.mikage.*;
import dev.bladetetra.registry.ModEntities;
import mods.flammpfeil.slashblade.entity.EntitySlashEffect;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.sounds.*;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.ToolActions;
import java.util.*;

/** Giant gate, continuous native sword pressure, and three real near-core sword inputs. */
final class MikageGateBarrageExecution implements SkillExecution {
    private final MikageEntity owner;
    private UUID target;
    private final Runnable committed;
    private final GateBarrageSequence<ItemStack> sequence = new GateBarrageSequence<>();
    private CastScope scope;
    private MikageGateBarragePlacement gate;
    private MikageGateCoreEntity core;
    private MikageGateBarrageShots shots;
    private boolean restored;
    private int ticks;
    MikageGateBarrageExecution(MikageEntity owner, ServerPlayer player, Runnable committed) {
        this.owner = owner; target = player.getUUID(); this.committed = committed;
    }
    @Override public void start(CastScope scope) {
        this.scope = scope; scope.own(this::restore);
        ServerPlayer player = target((ServerLevel) owner.level());
        gate = player == null ? null : MikageGateBarragePlacement.choose(owner, player);
        if (gate != null) {
            ServerLevel level = (ServerLevel) owner.level();
            owner.swordWheel().recallSwordWheel(level, 600);
            owner.setAction(MikageEntity.MikageAction.CAST_READY, 40);
            core = new MikageGateCoreEntity(ModEntities.MIKAGE_GATE_CORE.get(), level);
            float yaw = (float) Math.toDegrees(Math.atan2(-gate.forward().x, gate.forward().z));
            core.configure(owner, scope.id(), gate.base().add(0, .8, 0), yaw);
            shots = new MikageGateBarrageShots(owner, scope.id(), player.position().add(0, 1, 0));
            if (!level.addFreshEntity(core)) gate = null;
            if (gate != null) {
                sound(SoundEvents.ENDERMAN_TELEPORT, 1, .65F);
                for (ServerPlayer viewer : level.players()) if (eligible(viewer))
                    viewer.displayClientMessage(Component.translatable("message.blade_tetra.mikage.gate_barrage"), true);
            }
        }
        committed.run();
    }
    boolean eligible(ServerPlayer player) { return owner.isAlive() && owner.encounter().eligible(player); }
    boolean owns(MikageGateSwordEntity sword, long cast) {
        return !restored && scope != null && scope.id() == cast && shots != null && shots.owns(sword);
    }
    boolean owns(MikageGateCoreEntity objective, long cast) {
        return !restored && scope != null && scope.id() == cast && objective == core;
    }
    private ServerPlayer target(ServerLevel level) {
        if (level.getEntity(target) instanceof ServerPlayer player && eligible(player)) return player;
        ServerPlayer next = level.players().stream().filter(this::eligible)
                .min(Comparator.comparingDouble(p -> owner.distanceToSqr(p))).orElse(null);
        if (next != null) target = next.getUUID();
        return next;
    }
    @Override public boolean windingUp() { return true; }
    @Override public Status tick() {
        if (gate == null || core == null || core.isRemoved() || !core.isAlive()) return Status.COMPLETE;
        ServerLevel level = (ServerLevel) owner.level(); ServerPlayer player = target(level);
        if (player == null) return Status.TARGET_LOST;
        if (++ticks % 20 == 0 && !gate.safe(owner)) return Status.COMPLETE;
        owner.setTarget(player); owner.getNavigation().stop(); owner.setDeltaMovement(Vec3.ZERO);
        owner.lookAt(player, 180, 180); shots.tick(player);
        Set<UUID> present = new HashSet<>();
        for (ServerPlayer p : level.players()) if (eligible(p)) present.add(p.getUUID());
        sequence.retain(present, level.getGameTime());
        var previous = sequence.stage(); sequence.tick();
        if (previous != sequence.stage() && sequence.stage() == GateBarrageSequence.Stage.FIRING) {
            core.sync(3, 1); sound(SoundEvents.AMETHYST_BLOCK_CHIME, 1, 1.4F);
        }
        if (sequence.volley(level.getGameTime(), shots.liveCount())) shots.volley(gate);
        if (sequence.stage() == GateBarrageSequence.Stage.COMPLETE) {
            owner.duel().gateCoreBroken(); return Status.COUNTERED;
        }
        return Status.RUNNING;
    }
    void swing(ServerPlayer player, boolean nativeMotion) {
        if (nativeMotion && !player.getCapability(mods.flammpfeil.slashblade.capability.inputstate.CapabilityInputState.INPUT_STATE)
                .map(input -> input.getCommands().contains(mods.flammpfeil.slashblade.util.InputCommand.L_CLICK)
                        || input.getCommands().contains(mods.flammpfeil.slashblade.util.InputCommand.R_CLICK)).orElse(false)) return;
        ItemStack blade = player.getMainHandItem();
        if (eligible(player) && sword(blade) && (nativeMotion || player.getAttackStrengthScale(.5F) >= .8F))
            sequence.swing(player.getUUID(), owner.level().getGameTime(), blade);
    }
    boolean hitCore(DamageSource source) {
        if (!(source.getEntity() instanceof ServerPlayer player) || !eligible(player) || core == null
                || player.position().multiply(1, 0, 1).distanceToSqr(gate.base().multiply(1, 0, 1)) > 2.75 * 2.75
                || Math.abs(player.getY() - gate.base().y) > 2
                || owner.level().clip(new ClipContext(player.getEyePosition(), core.position().add(0, .7, 0),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)).getType() != HitResult.Type.MISS) return false;
        Entity traced = MikageBladeAttackTrace.projectile();
        Entity direct = traced == null ? source.getDirectEntity() : traced;
        if (direct != player && (!(direct instanceof EntitySlashEffect slash)
                || direct.getClass() != EntitySlashEffect.class || slash.getShooter() != player)) return false;
        ItemStack blade = traced == null ? player.getMainHandItem() : MikageBladeAttackTrace.birthBlade(traced);
        if (blade == null || blade != player.getMainHandItem() || !sword(blade)
                || !sequence.coreHit(player.getUUID(), owner.level().getGameTime(), blade)) return false;
        core.sync(sequence.remainingHits(), sequence.stage() == GateBarrageSequence.Stage.BREAK ? 2 : 1);
        player.displayClientMessage(Component.translatable("message.blade_tetra.mikage.gate_core",
                3 - sequence.remainingHits(), 3), true);
        if (sequence.stage() == GateBarrageSequence.Stage.BREAK) { shots.clear(); sound(SoundEvents.GLASS_BREAK, 1.3F, .7F); }
        else sound(SoundEvents.AMETHYST_BLOCK_BREAK, 1, 1 + (3 - sequence.remainingHits()) * .12F);
        return true;
    }
    void hitPlayer(ServerPlayer player) {
        long now = owner.level().getGameTime();
        if (eligible(player) && sequence.mayDamage(player.getUUID(), now)
                && owner.dealTrialDamage(player, (float) owner.getAttributeValue(Attributes.ATTACK_DAMAGE) * .22F, false))
            sequence.damaged(player.getUUID(), now);
    }
    private static boolean sword(ItemStack stack) { return stack.getItem() instanceof ItemSlashBlade || stack.canPerformAction(ToolActions.SWORD_SWEEP); }
    private void sound(SoundEvent event, float volume, float pitch) {
        ((ServerLevel) owner.level()).playSound(null, gate.base().x, gate.base().y + 1.5, gate.base().z,
                event, SoundSource.HOSTILE, volume, pitch);
    }
    private void restore() {
        if (restored) return; restored = true;
        if (shots != null) shots.clear(); if (core != null) core.discard();
        owner.getNavigation().stop(); owner.setDeltaMovement(Vec3.ZERO);
    }
    @Override public void stop(StopReason reason) {
        restore(); if (!owner.duel().staggered()) owner.setAction(MikageEntity.MikageAction.IDLE, 1);
        owner.combatDirector().techniqueCooldown = Math.max(40, owner.combatDirector().techniqueCooldown);
    }
}
