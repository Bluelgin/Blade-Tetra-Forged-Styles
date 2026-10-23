package dev.bladetetra.customstyle;

import dev.bladetetra.BladeTetra;
import dev.bladetetra.combat.BladeStyle;
import dev.bladetetra.combat.StyleResolver;
import dev.bladetetra.item.ModularSlashBladeItem;
import mods.flammpfeil.slashblade.capability.inputstate.CapabilityInputState;
import mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState;
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
import java.util.function.Supplier;

/**
 * Selects the next recorded normal attack without a second input queue.
 * Motion events precede state writes and are replayed by client animation packets.
 * Advance only after a real native progressCombo call has committed its result.
 */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CustomComboRouter {
    private static final Map<net.minecraft.world.level.Level, Map<java.util.UUID, Cursor>> CURSORS =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final int MAX_COMBO_GAP = 40;

    // Entity.equals compares numeric entity IDs: the local player and its server
    // counterpart compare equal in an integrated server. Never share their cursor.
    private static Map<java.util.UUID, Cursor> cursors(LivingEntity user) {
        synchronized (CURSORS) {
            return CURSORS.computeIfAbsent(user.level(), ignored -> new java.util.concurrent.ConcurrentHashMap<>());
        }
    }

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
            cursors(user).remove(user.getUUID());
            return;
        }

        ResourceLocation current = event.getSlashBladeState().resolvCurrentComboState(user);
        CustomStyleSpec spec = spec(blade);
        if (!ComboCompatibilityResolver.playable(spec)) return;
        Cursor cursor = cursor(user, blade, spec);
        if (cursor.lastAction == null && !current.equals(ComboStateRegistry.NONE.getId())
                && current.equals(event.getNextCombo())) return;
        if (cursor.lastAction != null) {
            long elapsed = user.level().getGameTime() - cursor.lastCommitTick;
            var timing = CustomStyleTimingPolicy.timing(cursor.lastAction, CustomBladeLength.from(blade));
            boolean locked = timing == null ? current.equals(event.getNextCombo())
                    : elapsed < timing.effectiveTicks();
            if (locked && !current.equals(ComboStateRegistry.NONE.getId())) {
                event.setNextCombo(current);
                return;
            }
        }

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

    public static ResourceLocation progress(ISlashBladeState state, LivingEntity user, boolean virtual,
            Supplier<ResourceLocation> nativeProgress) {
        ItemStack blade = user.getMainHandItem();
        if (!(blade.getItem() instanceof ModularSlashBladeItem)
                || StyleResolver.resolve(blade) != BladeStyle.CUSTOM) {
            cursors(user).remove(user.getUUID());
            return nativeProgress.get();
        }
        if (nativeSpecial(user) != null) {
            cursors(user).remove(user.getUUID());
            return nativeProgress.get();
        }
        CustomStyleSpec spec = spec(blade);
        if (!ComboCompatibilityResolver.playable(spec)) {
            cursors(user).remove(user.getUUID());
            return nativeProgress.get();
        }
        Cursor cursor = cursor(user, blade, spec);
        ResourceLocation expected = spec.actions().get(cursor.nextIndex);
        ResourceLocation result = nativeProgress.get();
        if (!virtual && result != null && result.equals(expected)
                && state.getComboSeq().equals(result)
                && state.getLastActionTime() == user.level().getGameTime()) {
            cursor.nextIndex = (cursor.nextIndex + 1) % spec.actions().size();
            cursor.lastCommitTick = user.level().getGameTime();
            cursor.lastAction = result;
        }
        if (Boolean.getBoolean("blade_tetra.customStyleDebug")) com.mojang.logging.LogUtils.getLogger().info(
                "CUSTOM_CURSOR side={} tick={} blade={} index={} expected={} result={} virtual={}",
                user.level().isClientSide() ? "client" : "server", user.level().getGameTime(),
                cursor.identifier, cursor.nextIndex, expected, result, virtual);
        return result;
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
        Cursor cursor = cursors(user).get(user.getUUID());
        long now = user.level().getGameTime();
        int slot = user instanceof Player player ? player.getInventory().selected : -1;
        String identifier = blade.getOrCreateTag().getString("id");
        if (cursor == null || !cursor.spec.equals(spec) || !cursor.identifier.equals(identifier)
                || cursor.slot != slot
                || now < cursor.lastCommitTick
                || now - cursor.lastCommitTick > MAX_COMBO_GAP) {
            cursor = new Cursor(spec, identifier, slot, now);
            cursors(user).put(user.getUUID(), cursor);
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

    public static void interrupt(LivingEntity user) { cursors(user).remove(user.getUUID()); }

    @SubscribeEvent public static void held(net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent event) {
        LivingEntity user = event.getEntity();
        Cursor cursor = cursors(user).get(user.getUUID());
        if (cursor == null) return;
        ItemStack blade = user.getMainHandItem();
        int slot = user instanceof Player player ? player.getInventory().selected : -1;
        if (slot != cursor.slot || blade.getTag() == null
                || !cursor.identifier.equals(blade.getTag().getString("id"))
                || StyleResolver.resolve(blade) != BladeStyle.CUSTOM) cursors(user).remove(user.getUUID());
    }

    private static final class Cursor {
        final CustomStyleSpec spec;
        final String identifier;
        final int slot;
        long lastCommitTick;
        int nextIndex;
        ResourceLocation lastAction;

        Cursor(CustomStyleSpec spec, String identifier, int slot, long now) {
            this.spec = spec;
            this.identifier = identifier;
            this.slot = slot;
            this.lastCommitTick = now;
        }
    }
    private CustomComboRouter() {}
}
