package dev.bladetetra.combat;

import dev.bladetetra.BladeTetra;
import dev.bladetetra.item.ModularSlashBladeItem;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import mods.flammpfeil.slashblade.registry.combo.ComboState;
import mods.flammpfeil.slashblade.util.InputCommand;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/** Server owns hold/cadence; SlashBlade's existing L_DOWN synchronization owns input. */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID)
public final class DangakuSpinHandler {
    static final UUID SLOW_ID = UUID.fromString("49e0e12a-00e2-47b3-ac7c-6e2ad2aa67a4");
    private static final Map<Player, Hold> HOLDS = new WeakHashMap<>();

    public static boolean ownsCombo(LivingEntity entity, ItemStack blade) {
        return blade.getItem() instanceof ModularSlashBladeItem
                && blade.getCapability(ModularSlashBladeItem.BLADESTATE)
                .map(state -> DangakuSpinCombos.owns(state.getComboSeq())).orElse(false);
    }
    /** Native managed melee sets onClick; let its own hit through, block extra vanilla clicks. */
    public static boolean suppressLeftClick(LivingEntity entity, ItemStack blade) {
        return ownsCombo(entity, blade) && blade.getCapability(ModularSlashBladeItem.BLADESTATE)
                .map(state -> !state.onClick()).orElse(false);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.START || !(event.player instanceof ServerPlayer player)) return;
        ItemStack blade = player.getMainHandItem();
        var state = blade.getCapability(ModularSlashBladeItem.BLADESTATE).orElse(null);
        if (!(blade.getItem() instanceof ModularSlashBladeItem) || state == null
                || StyleResolver.resolve(blade) != BladeStyle.DANGAKU || !player.isAlive()
                || state.isBroken() || state.isSealed()) { clear(player); return; }
        var commands = StyleBranchRuntime.commands(player);
        boolean held = commands.contains(InputCommand.L_DOWN);
        boolean busy = commands.contains(InputCommand.R_DOWN) || player.isUsingItem();
        ResourceLocation combo = state.getComboSeq();
        Hold hold = HOLDS.get(player);
        if (hold != null && hold.blade != blade) {
            clear(player); hold = null;
            // Do not resume a stored spin if another blade is equipped during a hold.
            if (DangakuSpinCombos.owns(combo)) state.updateComboSeq(player, ComboStateRegistry.NONE.getId());
        }
        boolean repeat = DangakuSpinRules.canRepeat(held, player.onGround(), busy,
                player.getAbilities().flying, player.isPassenger());
        if (DangakuSpinCombos.SPIN.getId().equals(combo)) {
            if (hold == null) { // Saved/transferred transient action cannot become an unattended attack.
                state.updateComboSeq(player, DangakuSpinCombos.RECOVERY.getId()); clear(player); return;
            }
            if (busy || player.getAbilities().flying || player.isPassenger()) {
                state.updateComboSeq(player, DangakuSpinCombos.RECOVERY.getId()); clear(player); return;
            }
            if (!repeat) hold.finish = true; // Release/leave ground completes only the current circle.
            slow(player, true);
            player.setSprinting(false);
            if (ComboState.getElapsed(player) >= DangakuSpinRules.CIRCLE_TICKS) {
                if (!hold.finish && repeat) {
                    CombatBalanceRuntime.ordinaryCombo(player);
                    state.updateComboSeq(player, DangakuSpinCombos.SPIN.getId());
                } else {
                    state.updateComboSeq(player, DangakuSpinCombos.RECOVERY.getId()); clear(player);
                }
            }
            return;
        }
        slow(player, false);
        if (DangakuSpinCombos.RECOVERY.getId().equals(combo)) { HOLDS.remove(player); return; }
        if (!repeat || !canStart(combo, Long.MAX_VALUE)) { HOLDS.remove(player); return; }
        if (hold == null) { hold = new Hold(blade); HOLDS.put(player, hold); }
        if (++hold.ticks >= DangakuSpinRules.HOLD_TICKS && canStart(combo, ComboState.getElapsed(player))) {
            CombatBalanceRuntime.ordinaryCombo(player);
            state.updateComboSeq(player, DangakuSpinCombos.SPIN.getId());
            slow(player, true); player.setSprinting(false);
        }
    }

    private static boolean canStart(ResourceLocation combo, long elapsed) {
        if (ComboStateRegistry.NONE.getId().equals(combo)) return true;
        var phase = BranchingStyleCombos.phase(combo);
        return phase != null && !phase.rengeki() && !phase.aerial()
                && elapsed >= phase.minimumTick()
                && (phase == StyleBranchRules.Phase.D_SWEEP || phase == StyleBranchRules.Phase.D_RETURN
                || phase.recovery());
    }

    static void animate(LivingEntity entity, ComboState nativeCircle) {
        if (entity.level().isClientSide()) return;
        Hold hold = entity instanceof ServerPlayer player ? HOLDS.get(player) : null;
        if (hold == null || hold.blade != entity.getMainHandItem() || !entity.isAlive()) return;
        nativeCircle.tickAction(entity); // Native damage, target selection, hunger, durability and reflection.
    }

    private static void slow(Player player, boolean active) {
        var movement = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (movement == null) return;
        if (active && movement.getModifier(SLOW_ID) == null)
            movement.addTransientModifier(new AttributeModifier(SLOW_ID, "Dangaku held circle",
                    DangakuSpinRules.MOVEMENT_PENALTY, AttributeModifier.Operation.MULTIPLY_TOTAL));
        else if (!active) movement.removeModifier(SLOW_ID);
    }
    static void clear(Player player) { HOLDS.remove(player); slow(player, false); }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) { clear(event.getEntity()); }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) { clear(event.getEntity()); }
    @SubscribeEvent public static void clone(PlayerEvent.Clone event) { clear(event.getOriginal()); clear(event.getEntity()); }
    @SubscribeEvent public static void stop(ServerStoppedEvent event) {
        for (Player player : HOLDS.keySet()) slow(player, false);
        HOLDS.clear();
    }
    private static final class Hold {
        final ItemStack blade;
        int ticks;
        boolean finish;
        Hold(ItemStack blade) { this.blade = blade; }
    }
    private DangakuSpinHandler() {}
}
