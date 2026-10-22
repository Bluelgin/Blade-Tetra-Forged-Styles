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
            // Resharped rejects same-ID transitions inside progressCombo, but
            // updateComboSeq intentionally supports restarting the same state.
            // Do not mutate during client-side virtual prediction.
            if (!user.level().isClientSide()) {
                event.setCanceled(true);
                event.getSlashBladeState().updateComboSeq(user, candidate);
            }
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
        if (cursor == null || cursor.blade != blade
                || cursor.fingerprint != spec.hashCode()
                || now < cursor.lastCommitTick
                || now - cursor.lastCommitTick > MAX_COMBO_GAP) {
            cursor = new Cursor(blade, spec.hashCode(), now);
            CURSORS.put(user, cursor);
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
        final ItemStack blade;
        final int fingerprint;
        long lastCommitTick;
        int nextIndex;

        Cursor(ItemStack blade, int fingerprint, long now) {
            this.blade = blade;
            this.fingerprint = fingerprint;
            this.lastCommitTick = now;
        }
    }
    private CustomComboRouter() {}
}
