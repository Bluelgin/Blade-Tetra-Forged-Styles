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
    private static final Map<LivingEntity, BufferedTransition> PENDING =
            Collections.synchronizedMap(new WeakHashMap<>());

    public static void queueIfLocked(ItemStack stack, LivingEntity entity) {
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
        if (transition != null) {
            PENDING.put(entity, transition);
        }
    }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        BufferedTransition pending = PENDING.get(entity);
        if (pending == null) {
            return;
        }

        long now = entity.level().getGameTime();
        ItemStack stack = entity.getMainHandItem();
        if (now > pending.expiresAt()
                || !(stack.getItem() instanceof ModularSlashBladeItem)) {
            PENDING.remove(entity);
            return;
        }

        stack.getCapability(ModularSlashBladeItem.BLADESTATE).ifPresent(state -> {
            ResourceLocation combo = state.getComboSeq();
            if (!pending.from().equals(combo)) {
                PENDING.remove(entity);
                return;
            }
            if (ComboState.getElapsed(entity) >= pending.minimumReleaseTick()) {
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
        if (style == BladeStyle.DANGAKU && ModComboStates.isDangakuCleave(combo)) {
            return transition(
                    combo,
                    ModComboStates.getDangakuSweepId(),
                    22,
                    now);
        }
        if (style == BladeStyle.DANGAKU && ModComboStates.isDangakuSweep(combo)) {
            return transition(
                    combo,
                    ModComboStates.getDangakuCleaveId(),
                    5,
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

    private StyleInputBuffer() {
    }
}
