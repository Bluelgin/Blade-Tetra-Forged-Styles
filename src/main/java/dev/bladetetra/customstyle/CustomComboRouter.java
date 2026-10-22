package dev.bladetetra.customstyle;

import dev.bladetetra.BladeTetra;
import dev.bladetetra.combat.BladeStyle;
import dev.bladetetra.combat.StyleResolver;
import dev.bladetetra.item.ModularSlashBladeItem;
import mods.flammpfeil.slashblade.capability.inputstate.CapabilityInputState;
import mods.flammpfeil.slashblade.event.SlashBladeEvent;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
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
 * Routes only grounded, ordinary clicks. SlashBlade still owns input timing,
 * actual ComboState transitions, hits, motion and damage.
 */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CustomComboRouter {
    private static final Map<LivingEntity, Cursor> CURSORS =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final int MAX_COMBO_GAP = 40;

    /** The root's fallback must agree with NextComboEvent's chosen next state. */
    public static ResourceLocation opener(LivingEntity user) {
        ResourceLocation special = nativeSpecial(user);
        if (special != null) return special;
        ResourceLocation next = peek(user);
        return next != null ? next : ComboStateRegistry.NONE.getId();
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void next(SlashBladeEvent.NextComboEvent event) {
        ItemStack blade = event.getBlade();
        LivingEntity user = event.getUser();
        if (!(blade.getItem() instanceof ModularSlashBladeItem)
                || StyleResolver.resolve(blade) != BladeStyle.CUSTOM) return;
        if (nativeSpecial(user) != null) {
            CURSORS.remove(user);
            return; // Air, backstep/upper, and forward dash stay native.
        }

        ResourceLocation current = event.getSlashBladeState().resolvCurrentComboState(user);
        // Native ComboState may return itself while still in the protected hit
        // timeline. Never turn such an early input into a forced skip.
        if (current.equals(event.getNextCombo())) return;

        ResourceLocation desired = peek(user);
        if (desired == null || !ComboStateRegistry.REGISTRY.get().containsKey(desired)) {
            return;
        }
        if (desired.equals(current)) {
            // The native progressCombo guards against same-id transitions.
            // Enter through its genuine update path; this replays clickAction
            // but does not invent any new hit or animation executor.
            event.setCanceled(true);
            event.getSlashBladeState().updateComboSeq(user, desired);
            return;
        }
        event.setNextCombo(desired);
    }

    private static ResourceLocation peek(LivingEntity user) {
        ItemStack blade = user.getMainHandItem();
        if (!(blade.getItem() instanceof ModularSlashBladeItem)
                || StyleResolver.resolve(blade) != BladeStyle.CUSTOM
                || blade.getTag() == null) return null;

        CustomStyleSpec spec = CustomStyleSpec.decode(
                blade.getTag().getCompound(CustomStyleSpec.STACK_KEY));
        if (spec == null || spec.actions().isEmpty()) return null;

        Cursor cursor = CURSORS.get(user);
        long now = user.level().getGameTime();
        ResourceLocation current = blade.getCapability(ItemSlashBlade.BLADESTATE)
                .map(state -> state.getComboSeq())
                .orElse(ComboStateRegistry.NONE.getId());
        long started = blade.getCapability(ItemSlashBlade.BLADESTATE)
                .map(state -> state.getLastActionTime())
                .orElse(-1L);

        if (cursor == null || cursor.blade != blade
                || cursor.fingerprint != spec.hashCode()
                || now - cursor.lastDispatchTime > MAX_COMBO_GAP
                || (cursor.lastTarget != null && !cursor.lastTarget.equals(current)
                && cursor.lastDispatchTime != started)) {
            cursor = new Cursor(blade, spec.hashCode());
            CURSORS.put(user, cursor);
        } else if (cursor.lastTarget != null
                && cursor.lastTarget.equals(current)
                && started != cursor.lastDispatchTime) {
            cursor.nextIndex = (cursor.nextIndex + 1) % spec.actions().size();
            cursor.lastDispatchTime = started;
            cursor.lastTarget = null;
        }
        ResourceLocation next = spec.actions().get(cursor.nextIndex);
        if (!ComboStateRegistry.REGISTRY.get().containsKey(next)) return null;
        // This is a *candidate*, not an animation frame skip; SlashBlade decides
        // if the input may advance based on the active state.
        cursor.lastTarget = next;
        if (cursor.lastDispatchTime == -1) cursor.lastDispatchTime = started;
        return next;
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
        ResourceLocation lastTarget;
        long lastDispatchTime = -1;
        int nextIndex;
        Cursor(ItemStack blade, int fingerprint) {
            this.blade = blade;
            this.fingerprint = fingerprint;
        }
    }
    private CustomComboRouter() {}
}
