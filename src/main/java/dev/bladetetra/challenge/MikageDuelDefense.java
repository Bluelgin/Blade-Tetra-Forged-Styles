package dev.bladetetra.challenge;

import dev.bladetetra.challenge.mikage.DuelDefenseState;
import mods.flammpfeil.slashblade.entity.EntitySlashEffect;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ToolActions;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Boss guard, player sword parries and shared balance; participant input/protection stays isolated. */
final class MikageDuelDefense {
    private final MikageEntity owner;
    private final DuelDefenseState<ItemStack> state = new DuelDefenseState<>();
    private boolean showingStagger;

    MikageDuelDefense(MikageEntity owner) { this.owner = owner; }
    private long now() { return owner.level().getGameTime(); }
    boolean staggered() { return state.staggered(now()); }
    boolean protects(ServerPlayer player) {
        var pursuit = owner.encounter().gates();
        var corridor = owner.encounter().corridor();
        return eligible(player) && (corridor == null || !corridor.striking(player)) && (pursuit == null || !pursuit.striking(player))
                && state.protects(player.getUUID(), now());
    }
    private boolean eligible(ServerPlayer player) {
        return owner.isAlive() && !owner.isVisitorGuide() && owner.encounter().eligible(player);
    }

    void swing(ServerPlayer player, boolean nativeMotion) {
        ItemStack blade = player.getMainHandItem();
        if (!eligible(player) || !sword(blade)
                || !nativeMotion && player.getAttackStrengthScale(.5F) < .8F) return;
        state.swing(player.getUUID(), now(), blade);
    }

    boolean intercept(DamageSource source) {
        if (state.guarding(now())) return true;
        if (!state.counterReady(now()) || !owner.encounter().canGuardCounter()
                || owner.getPhase() != 1 || source.is(DamageTypeTags.IS_PROJECTILE)
                || !(MikageDamageService.resolveCombatAttacker(source) instanceof ServerPlayer player)
                || !eligible(player) || owner.distanceToSqr(player) > 36 || !front(player.position())) return false;
        Entity traced = MikageBladeAttackTrace.projectile();
        Entity direct = traced == null ? source.getDirectEntity() : traced;
        if (direct != player && (!(direct instanceof EntitySlashEffect slash) || direct.getClass() != EntitySlashEffect.class || slash.getShooter() != player)) return false;
        ItemStack blade = traced == null ? player.getMainHandItem() : MikageBladeAttackTrace.birthBlade(traced);
        if (blade == null || !sword(blade)) return false;
        if (!owner.encounter().answerGuard(player)) return false;
        state.counterStarted(now());
        owner.presentation().swordContact(player, false);
        return true;
    }

    /** Called only for an authored melee hit after its hit box contains the player. */
    boolean parry(ServerPlayer player, Vec3 origin) {
        if (!eligible(player) || protects(player) || staggered()
                || !front(player.position()) || !facing(player.getLookAngle(), origin.subtract(player.position()))
                || !state.consumeSwing(player.getUUID(), now(), player.getMainHandItem())) return false;
        boolean broken = state.parried(player.getUUID(), now());
        owner.presentation().swordContact(player, true);
        player.displayClientMessage(Component.translatable("message.blade_tetra.mikage.duel_parry",
                state.progress(now()), DuelDefenseState.REQUIRED_PARRIES), true);
        owner.combatDirector().techniqueCooldown = Math.max(24, owner.combatDirector().techniqueCooldown);
        if (broken) {
            owner.encounter().breakDuelBalance();
            showingStagger = true;
            owner.setAction(MikageEntity.MikageAction.STAGGERED, state.staggerRemaining(now()));
            owner.presentation().balanceBroken();
        }
        return true;
    }

    void tick(ServerLevel level) {
        Set<UUID> participants = new HashSet<>();
        for (ServerPlayer player : level.players()) if (eligible(player)) participants.add(player.getUUID());
        state.retain(participants, now());
        if (staggered()) {
            owner.getNavigation().stop();
            owner.setDeltaMovement(Vec3.ZERO);
        } else if (showingStagger) {
            showingStagger = false;
            owner.setAction(MikageEntity.MikageAction.IDLE, 1);
            owner.combatDirector().techniqueCooldown = Math.max(20, owner.combatDirector().techniqueCooldown);
        }
    }

    /** New pursuit rounds consume fresh input even inside the previous contact's protection. */
    boolean parryPursuit(ServerPlayer player, Vec3 origin) {
        if (!eligible(player) || !front(player.position())
                || !facing(player.getLookAngle(), origin.subtract(player.position()))
                || !state.consumeSwing(player.getUUID(), now(), player.getMainHandItem())) return false;
        state.protect(player.getUUID(), now());
        owner.presentation().swordContact(player, true);
        return true;
    }

    void breakPursuitBalance() {
        state.breakBalance(now());
        owner.encounter().breakDuelBalance();
        showingStagger = true;
        owner.setAction(MikageEntity.MikageAction.STAGGERED, state.staggerRemaining(now()));
        owner.presentation().balanceBroken();
    }

    void corridorLanded(ServerPlayer player) {
        boolean broken = state.parried(player.getUUID(), now());
        if (!broken) state.openStagger(now(), 40);
        owner.encounter().breakDuelBalance();
        showingStagger = true;
        owner.setAction(MikageEntity.MikageAction.STAGGERED, state.staggerRemaining(now()));
        if (broken) owner.presentation().balanceBroken();
    }

    void hitAccepted() {
        boolean recovering = staggered();
        state.hitAccepted(now());
        if (recovering) owner.setAction(MikageEntity.MikageAction.STAGGERED, state.staggerRemaining(now()));
    }
    void cancelGuard() { state.cancelGuard(); }
    void clear() { state.clear(); showingStagger = false; }
    private boolean front(Vec3 position) { return facing(owner.getLookAngle(), position.subtract(owner.position())); }
    private static boolean facing(Vec3 look, Vec3 towards) {
        return look.multiply(1, 0, 1).normalize().dot(towards.multiply(1, 0, 1).normalize()) >= .25;
    }
    private static boolean sword(ItemStack stack) {
        return stack.getItem() instanceof ItemSlashBlade || stack.canPerformAction(ToolActions.SWORD_SWEEP);
    }
}
