package dev.bladetetra.customstyle;

import dev.bladetetra.BladeTetra;
import dev.bladetetra.combat.BladeStyle;
import dev.bladetetra.combat.StyleResolver;
import dev.bladetetra.item.ModularSlashBladeItem;
import mods.flammpfeil.slashblade.capability.inputstate.CapabilityInputState;
import mods.flammpfeil.slashblade.event.BladeMotionEvent;
import mods.flammpfeil.slashblade.event.SlashBladeEvent;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import mods.flammpfeil.slashblade.util.InputCommand;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Selects the next recorded normal attack without a second input queue.
 * The cursor moves ONLY on a committed BladeMotionEvent, not when the engine
 * asks for a root or previews a NextComboEvent (which can happen more than once).
 */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CustomComboRouter {
    private static final Map<LivingEntity, Cursor> CURSORS =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final int MAX_COMBO_GAP = 40;

    /** Native SlashBlade resolves current-vs-root priority on each actual click. */
    public static ResourceLocation opener(LivingEntity user) {
        ResourceLocation special = nativeSpecial(user);
        if (special != null) return special;
        ResourceLocation candidate = desired(user);
        return candidate == null ? ComboStateRegistry.NONE.getId() : candidate;
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void next(SlashBladeEvent.NextComboEvent event) {
        ItemStack blade = event.getBlade();
        LivingEntity user = event.getUser();
        if (!(blade.getItem() instanceof ModularSlashBladeItem)
                || StyleResolver.resolve(blade) != BladeStyle.CUSTOM) return;
        if (nativeSpecial(user) != null) {
            CURSORS.remove(user);
            return;
        }

        ResourceLocation current = event.getSlashBladeState().resolvCurrentComboState(user);
        // Preserve the original ComboState's minimum input window.
        if (current.equals(event.getNextCombo())) return;

        ResourceLocation candidate = desired(user);
        if (candidate == null) return;
        if (candidate.equals(current)) {
            // NextComboEvent is not cancelable. Choose the normal root fallback
            // to replay the same ComboState after its native minimum window.
            event.setNextCombo(ComboStateRegistry.NONE.getId());
            return;
        }
        event.setNextCombo(candidate);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void committed(BladeMotionEvent event) {
        if (event.isCanceled()) return;
        LivingEntity user = event.getEntity();
        ItemStack blade = user.getMainHandItem();
        if (!(blade.getItem() instanceof ModularSlashBladeItem)
                || StyleResolver.resolve(blade) != BladeStyle.CUSTOM) {
            CURSORS.remove(user);
            return;
        }
        if (nativeSpecial(user) != null) {
            CURSORS.remove(user);
            return;
        }
        CustomStyleSpec spec = spec(blade);
        if (!ComboCompatibilityResolver.playable(spec)) {
            CURSORS.remove(user);
            return;
        }
        Cursor cursor = cursor(user, blade, spec);
        if (cursor != null && event.getCombo().equals(spec.actions().get(cursor.nextIndex))) {
            cursor.nextIndex = (cursor.nextIndex + 1) % spec.actions().size();
            cursor.lastCommitTick = user.level().getGameTime();
        }
    }

    private static ResourceLocation desired(LivingEntity user) {
        ItemStack blade = user.getMainHandItem();
        if (!(blade.getItem() instanceof ModularSlashBladeItem)
                || StyleResolver.resolve(blade) != BladeStyle.CUSTOM) return null;
        CustomStyleSpec spec = spec(blade);
        if (!ComboCompatibilityResolver.playable(spec)) return null;
        Cursor cursor = cursor(user, blade, spec);
        return cursor == null ? null : spec.actions().get(cursor.nextIndex);
    }

    private static CustomStyleSpec spec(ItemStack blade) {
        return blade.getTag() == null ? null
                : CustomStyleSpec.decode(blade.getTag().getCompound(CustomStyleSpec.STACK_KEY));
    }

    private static Cursor cursor(LivingEntity user, ItemStack blade, CustomStyleSpec spec) {
        Cursor cursor = CURSORS.get(user);
        long now = user.level().getGameTime();
        int slot = user instanceof Player player ? player.getInventory().selected : -1;
        if (cursor == null || cursor.fingerprint != spec.hashCode()
                || cursor.slot != slot
                || now < cursor.lastCommitTick
                || now - cursor.lastCommitTick > MAX_COMBO_GAP) {
            cursor = new Cursor(spec.hashCode(), slot, now);
            CURSORS.put(user, cursor);
            // Tetra/SlashBlade may replace the held ItemStack wrapper while
            // updating item capabilities. Recover from the *actual* committed
            // combo instead of treating a new Java object as a new attack.
            var state = blade.getCapability(ModularSlashBladeItem.BLADESTATE);
            state.ifPresent(bladeState -> {
                long start = bladeState.getLastActionTime();
                long elapsed = now - start;
                if (start >= 0 && elapsed >= 0 && elapsed <= MAX_COMBO_GAP) {
                    int previous = spec.actions().indexOf(bladeState.getComboSeq());
                    if (previous >= 0) {
                        cursor.nextIndex = (previous + 1) % spec.actions().size();
                        cursor.lastCommitTick = start;
                    }
                }
            });
        }
        return cursor;
    }

    private static ResourceLocation nativeSpecial(LivingEntity user) {
        EnumSet<InputCommand> commands = user.getCapability(CapabilityInputState.INPUT_STATE)
                .map(state -> state.getCommands(user))
                .orElseGet(() -> EnumSet.noneOf(InputCommand.class));
        if (!user.onGround()) {
            return commands.containsAll(EnumSet.of(InputCommand.SNEAK, InputCommand.BACK, InputCommand.R_CLICK))
                    ? ComboStateRegistry.AERIAL_CLEAVE.getId()
                    : ComboStateRegistry.AERIAL_RAVE_A1.getId();
        }
        if (commands.containsAll(EnumSet.of(InputCommand.SNEAK, InputCommand.FORWARD, InputCommand.R_CLICK)))
            return ComboStateRegistry.RAPID_SLASH.getId();
        if (commands.containsAll(EnumSet.of(InputCommand.SNEAK, InputCommand.BACK, InputCommand.R_CLICK)))
            return ComboStateRegistry.UPPERSLASH.getId();
        return null;
    }

    private static final class Cursor {
        final int fingerprint;
        final int slot;
        long lastCommitTick;
        int nextIndex;

        Cursor(int fingerprint, int slot, long now) {
            this.fingerprint = fingerprint;
            this.slot = slot;
            this.lastCommitTick = now;
        }
    }
    private CustomComboRouter() {}
}
