package dev.bladetetra.combat;

import dev.bladetetra.BladeTetra;
import dev.bladetetra.item.ModularSlashBladeItem;
import mods.flammpfeil.slashblade.registry.combo.ComboState;
import mods.flammpfeil.slashblade.util.TimeValueHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Keeps one early click for style phases with an unskippable animation window.
 * The buffer is intentionally transient: it is neither serialized to the blade
 * nor synced as extra NBT, and disappears when the entity or combo is gone.
 */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class StyleInputBuffer {
    private static final int BUFFER_LIFETIME_TICKS = 24;
    private static final Map<LivingEntity, BufferedClick> PENDING =
            Collections.synchronizedMap(new WeakHashMap<>());

    public static void queueIfLocked(ItemStack stack, LivingEntity entity) {
        queueIfLocked(stack, entity, false);
    }

    public static void queueIfLocked(ItemStack stack, LivingEntity entity, boolean rightClick) {
        if (!(stack.getItem() instanceof ModularSlashBladeItem)) {
            return;
        }

        ResourceLocation combo = stack.getCapability(ModularSlashBladeItem.BLADESTATE)
                .map(state -> state.getComboSeq())
                .orElse(null);
        BufferedTransition transition = transitionFor(
                StyleResolver.resolve(stack),
                combo,
                entity.level().getGameTime());
        var phase = BranchingStyleCombos.phase(combo);
        if (rightClick && phase != null && !phase.dive()) {
            var intent = StyleBranchRuntime.intent(entity, true);
            int minimum = BranchingStyleCombos.minimumInputTick(phase, intent, entity.onGround(), true);
            var next = BranchingStyleCombos.nextFor(phase, minimum, intent, entity.onGround(), true);
            if (ComboState.getElapsed(entity) < minimum && !next.equals(combo)
                    && !next.equals(mods.flammpfeil.slashblade.registry.ComboStateRegistry.NONE.getId())) {
                transition = new BufferedTransition(combo, next, minimum,
                        entity.level().getGameTime() + BUFFER_LIFETIME_TICKS);
            }
        }
        if (transition != null) {
            long startedAt = stack.getCapability(ModularSlashBladeItem.BLADESTATE)
                    .map(state -> state.getLastActionTime()).orElse(-1L);
            PENDING.putIfAbsent(entity, new BufferedClick(transition, stack, startedAt,
                    phase == null ? StyleBranchRules.Intent.NONE : StyleBranchRuntime.intent(entity, true)));
        }
    }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        BufferedClick click = PENDING.get(entity);
        if (click == null) {
            return;
        }
        BufferedTransition pending = click.transition();

        long now = entity.level().getGameTime();
        ItemStack stack = entity.getMainHandItem();
        if (now > pending.expiresAt() || stack != click.blade() || !entity.isAlive()
                || !(stack.getItem() instanceof ModularSlashBladeItem)) {
            PENDING.remove(entity);
            return;
        }

        stack.getCapability(ModularSlashBladeItem.BLADESTATE).ifPresent(state -> {
            ResourceLocation combo = state.getComboSeq();
            if (!pending.from().equals(combo) || state.getLastActionTime() != click.startedAt()) {
                PENDING.remove(entity);
                return;
            }
            if (ComboState.getElapsed(entity) >= pending.minimumReleaseTick()) {
                var phase = BranchingStyleCombos.phase(combo);
                if (phase != null && !pending.to().equals(BranchingStyleCombos.nextFor(
                        phase, pending.minimumReleaseTick(), click.intent(), entity.onGround(), true))) {
                    PENDING.remove(entity); // Walking off a ledge cannot replay a queued ground attack.
                    return;
                }
                if (phase != null && entity.isUsingItem()
                        && entity.getTicksUsingItem() >= state.getFullChargeTicks(entity)) {
                    PENDING.remove(entity);
                    return;
                }
                CombatBalanceRuntime.ordinaryCombo(entity);
                state.updateComboSeq(entity, pending.to());
                PENDING.remove(entity);
            }
        });
    }

    private static BufferedTransition transitionFor(
            BladeStyle style,
            ResourceLocation combo,
            long now) {
        if (style == BladeStyle.IAIDO && ModComboStates.isIaidoSheathe(combo)) {
            return transition(
                    combo,
                    ModComboStates.getIaidoDrawId(),
                    ModComboStates.IAIDO_SHEATHE_MINIMUM_NEXT_FRAME,
                    now);
        }
        return null;
    }

    private static BufferedTransition transition(
            ResourceLocation from,
            ResourceLocation to,
            int minimumFrame,
            long now) {
        int minimumTick =
                (int) TimeValueHelper.getTicksFromFrames(minimumFrame);
        return new BufferedTransition(
                from,
                to,
                minimumTick,
                now + BUFFER_LIFETIME_TICKS);
    }

    private record BufferedTransition(
            ResourceLocation from,
            ResourceLocation to,
            int minimumReleaseTick,
            long expiresAt) {
    }
    private record BufferedClick(BufferedTransition transition, ItemStack blade, long startedAt,
                                 StyleBranchRules.Intent intent) {}

    private StyleInputBuffer() {
    }
}
