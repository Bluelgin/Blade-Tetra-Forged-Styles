package dev.btsealed.runtime;

import mods.flammpfeil.slashblade.capability.inputstate.CapabilityInputState;
import mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState;
import mods.flammpfeil.slashblade.entity.EntitySlashEffect;
import mods.flammpfeil.slashblade.event.SlashBladeEvent;
import mods.flammpfeil.slashblade.event.client.UserPoseOverrider;
import mods.flammpfeil.slashblade.event.handler.FallHandler;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import mods.flammpfeil.slashblade.registry.SlashBladeItems;
import mods.flammpfeil.slashblade.registry.combo.ComboState;
import mods.flammpfeil.slashblade.util.InputCommand;
import mods.flammpfeil.slashblade.util.KnockBacks;
import mods.flammpfeil.slashblade.util.TimeValueHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingKnockBackEvent;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import static dev.btsealed.runtime.StyleBranchRules.*;

final class PortableDangaku {
    private static final Map<Phase, ResourceLocation> IDS = new EnumMap<>(Phase.class);
    private static final Map<ResourceLocation, Phase> PHASES = new HashMap<>();

    static void register() {
        for (Phase phase : Phase.values()) {
            if (phase.rengeki()) continue;
            var name = switch (phase) {
                case D_HEAVY -> "dangaku_cleave";
                default -> "dangaku_" + phase.name().substring(2).toLowerCase(Locale.ROOT);
            };
            var entry = PortableCombos.COMBOS.register(name, () -> create(phase));
            IDS.put(phase, entry.getId());
            PHASES.put(entry.getId(), phase);
        }
        PortableDangakuSpin.register();
    }

    static ResourceLocation opener(LivingEntity user) {
        var commands = commands(user);
        Intent intent = intent(commands);
        if (intent == Intent.NONE) return id(null);
        var common = StyleCommonCommands.resolve(intent, user.onGround(), commands.contains(InputCommand.R_CLICK));
        return common != null ? common : id(user.onGround() ? Phase.D_SWEEP : Phase.D_AIR_FIRST);
    }

    private static ResourceLocation nextCombo(LivingEntity user, Phase phase) {
        if (user.onGround() && phase.airAttack()) return id(groundedRecovery(phase));
        var commands = commands(user);
        Intent intent = intent(commands);
        long elapsed = ComboState.getElapsed(user);
        var common = StyleCommonCommands.resolve(intent, user.onGround(), commands.contains(InputCommand.R_CLICK));
        return common != null ? (elapsed >= phase.commonMinimumTick() ? common : id(phase))
                : id(next(phase, elapsed, intent, user.onGround()));
    }

    private static ComboState create(Phase phase) {
        var original = switch (phase) {
            case D_SWEEP, D_AIR_FIRST -> ComboStateRegistry.COMBO_A1.get();
            case D_RETURN, D_AIR_SECOND -> ComboStateRegistry.COMBO_A2.get();
            case D_FINISH, D_AIR_FINISH -> ComboStateRegistry.CIRCLE_SLASH.get();
            case D_HEAVY, D_AIR_HEAVY -> ComboStateRegistry.COMBO_A4_EX.get();
            case D_RECOVERY, D_AIR_RECOVERY -> ComboStateRegistry.COMBO_A4_EX_END2.get();
            case D_CIRCLE_RECOVERY, D_AIR_CIRCLE_RECOVERY -> ComboStateRegistry.CIRCLE_SLASH_END.get();
            case D_UPPER -> ComboStateRegistry.UPPERSLASH.get();
            case D_JUMP -> ComboStateRegistry.UPPERSLASH_JUMP.get();
            case D_DIVE -> ComboStateRegistry.AERIAL_CLEAVE_LOOP.get();
            case D_LAND -> ComboStateRegistry.AERIAL_CLEAVE_LANDING.get();
            default -> throw new IllegalArgumentException("Not a Dangaku phase: " + phase);
        };
        var builder = motion(original, phase.duration()).next(user -> nextCombo(user, phase))
                .nextOfTimeout(user -> id(timeout(phase)));
        if (phase.aerial()) builder.aerial();
        if (original.getLoop()) builder.loop();
        if (phase.airAttack()) {
            builder.clickAction(user -> {
                if (!finishIfLanded(user, phase)) {
                    if (phase.recovery()) UserPoseOverrider.resetRot(user);
                    else original.clickAction(user);
                }
            }).addTickAction(user -> {
                if (finishIfLanded(user, phase)) return;
                if (phase.recovery()) UserPoseOverrider.resetRot(user);
                else original.tickAction(user);
                if (!original.isAerial()) FallHandler.fallDecrease(user);
            }).addHitEffect(original::hitEffect);
        } else if (phase.dive()) {
            builder.clickAction(user -> {
                UserPoseOverrider.resetRot(user);
                user.setDeltaMovement(user.getDeltaMovement().multiply(1, 0, 1));
            }).addTickAction(PortableDangaku::tickDive);
        } else if (phase.recovery()) {
            builder.addTickAction(UserPoseOverrider::resetRot);
        } else {
            builder.clickAction(original::clickAction).addTickAction(original::tickAction)
                    .addHitEffect(original::hitEffect);
            if (phase == Phase.D_UPPER) builder.addHoldAction(PortableDangaku::holdUpper);
        }
        return builder.build();
    }

    static ComboState.Builder motion(ComboState original, int ticks) {
        int animationMs = (int) (TimeValueHelper.getMSecFromFrames(
                Math.abs(original.getEndFrame() - original.getStartFrame())) / original.getSpeed());
        return ComboState.Builder.newInstance().startAndEnd(original.getStartFrame(), original.getEndFrame())
                .motionLoc(original.getMotionLoc()).speed(original.getSpeed()).priority(100)
                .timeout(ticks * 50 - animationMs);
    }

    static void landing(LivingEvent.LivingTickEvent event) {
        var user = event.getEntity();
        if (!user.onGround()) return;
        var phase = phase(combo(user));
        if (phase != null && phase.airAttack()) finishIfLanded(user, phase);
    }

    private static boolean finishIfLanded(LivingEntity user, Phase phase) {
        if (!user.onGround()) return false;
        var state = state(user);
        if (state != null && id(phase).equals(state.getComboSeq())) {
            UserPoseOverrider.resetRot(user);
            state.updateComboSeq(user, id(groundedRecovery(phase)));
        }
        return true;
    }

    private static void tickDive(LivingEntity user) {
        if (user.onGround()) {
            var state = state(user);
            if (state != null) state.updateComboSeq(user, id(Phase.D_LAND));
        } else if (ComboState.getElapsed(user) >= 2) {
            var motion = user.getDeltaMovement();
            user.setDeltaMovement(motion.x * .8, Math.max(-1.2, motion.y - .25), motion.z * .8);
            user.hasImpulse = true;
            user.fallDistance = 1;
        }
    }

    private static void holdUpper(LivingEntity user) {
        if (ComboState.getElapsed(user) != (int) TimeValueHelper.getTicksFromFrames(9) || !user.onGround()
                || !commands(user).containsAll(EnumSet.of(InputCommand.BACK, InputCommand.R_DOWN))) return;
        var state = state(user);
        if (state != null) state.updateComboSeq(user, id(Phase.D_JUMP));
    }

    static void slash(SlashBladeEvent.DoSlashEvent event) {
        var blade = event.getBlade();
        var state = event.getSlashBladeState();
        if (!blade.is(SlashBladeItems.SLASHBLADE.get()) || !isDangaku(state)) return;
        var combo = state.getComboSeq();
        Phase phase = phase(combo);
        if (PortableDangakuSpin.isSpin(combo)) {
            event.setDamage(event.getDamage() * DangakuSpinRules.damageScale(event.isCritical()));
            event.setKnockback(KnockBacks.cancel);
        } else if (DangakuCircleSlashRules.matches(phase)) {
            event.setDamage(event.getDamage() * DangakuCircleSlashRules.damageScale(event.isCritical()));
            event.setKnockback(KnockBacks.cancel);
        } else if (heavy(phase)) {
            event.setDamage(event.getDamage() * DangakuCombatValues.HEAVY_DAMAGE);
            event.setKnockback(KnockBacks.smash);
        } else if (sweep(phase)) {
            event.setDamage(event.getDamage() * DangakuCombatValues.SWEEP_DAMAGE);
        }
    }

    static void slashGeometry(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || event.loadedFromDisk()
                || !(event.getEntity() instanceof EntitySlashEffect slash)
                || !(slash.getShooter() instanceof LivingEntity owner)) return;
        var combo = combo(owner);
        var phase = phase(combo);
        if (phase != null || PortableDangakuSpin.isSpin(combo)) {
            slash.setBaseSize(heavy(phase) ? DangakuCombatValues.HEAVY_SLASH_SIZE : DangakuCombatValues.SWEEP_SLASH_SIZE);
        }
    }

    static void target(LivingAttackEvent event) {
        if (!(event.getSource().getEntity() instanceof LivingEntity attacker)
                || event.getSource().getDirectEntity() != attacker) return;
        var phase = phase(combo(attacker));
        if (heavy(phase) && !StyleTargeting.isInsideFrontArc(attacker, event.getEntity(),
                DangakuCombatValues.CLEAVE_RANGE, DangakuCombatValues.CLEAVE_MIN_DOT)
                || sweep(phase) && !StyleTargeting.isInsideFrontArc(attacker, event.getEntity(),
                DangakuCombatValues.SWEEP_RANGE, DangakuCombatValues.SWEEP_MIN_DOT)) {
            event.setCanceled(true);
        }
    }

    static void armor(LivingHurtEvent event) {
        if (event.getSource().getEntity() != null && !event.getSource().is(DamageTypeTags.BYPASSES_ARMOR)
                && armorWindow(event.getEntity())) {
            event.setAmount(event.getAmount() * DangakuCombatValues.ARMOR_DAMAGE);
        }
    }

    static void knockback(LivingKnockBackEvent event) {
        if (armorWindow(event.getEntity())) event.setCanceled(true);
    }

    private static boolean armorWindow(LivingEntity user) {
        return heavy(phase(combo(user))) && ComboState.getElapsed(user) <= DangakuCombatValues.ARMOR_END_TICK;
    }

    private static boolean heavy(Phase phase) {
        return phase == Phase.D_HEAVY || phase == Phase.D_AIR_HEAVY;
    }

    private static boolean sweep(Phase phase) {
        return phase == Phase.D_SWEEP || phase == Phase.D_RETURN || phase == Phase.D_LAND
                || phase == Phase.D_AIR_FIRST || phase == Phase.D_AIR_SECOND;
    }

    static EnumSet<InputCommand> commands(LivingEntity user) {
        return user.getCapability(CapabilityInputState.INPUT_STATE).map(input -> input.getCommands(user))
                .orElseGet(() -> EnumSet.noneOf(InputCommand.class));
    }

    private static Intent intent(EnumSet<InputCommand> commands) {
        boolean right = commands.contains(InputCommand.R_CLICK);
        if (!right && !commands.contains(InputCommand.L_CLICK)) return Intent.NONE;
        if (right && commands.contains(InputCommand.SNEAK)) {
            if (commands.contains(InputCommand.BACK)) return Intent.BACK;
            if (commands.contains(InputCommand.FORWARD)) return Intent.FORWARD;
        }
        return Intent.ATTACK;
    }

    static ISlashBladeState state(LivingEntity user) {
        var blade = user.getMainHandItem();
        if (!blade.is(SlashBladeItems.SLASHBLADE.get())) return null;
        return blade.getCapability(ItemSlashBlade.BLADESTATE).filter(PortableDangaku::isDangaku).orElse(null);
    }

    private static boolean isDangaku(ISlashBladeState state) {
        return PortableCombos.DANGAKU.getId().equals(state.getComboRoot());
    }

    private static ResourceLocation combo(LivingEntity user) {
        var state = state(user);
        return state == null ? ComboStateRegistry.NONE.getId() : state.getComboSeq();
    }

    static Phase phase(ResourceLocation id) {
        return PHASES.get(id);
    }

    private static ResourceLocation id(Phase phase) {
        return phase == null ? ComboStateRegistry.NONE.getId() : IDS.get(phase);
    }

    private PortableDangaku() {}
}
