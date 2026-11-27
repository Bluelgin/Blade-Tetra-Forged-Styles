package dev.btsealed.runtime;

import mods.flammpfeil.slashblade.event.client.UserPoseOverrider;
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
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

final class PortableDangakuSpin {
    private static final UUID SLOW_ID = UUID.fromString("7ab2a9ba-4910-4682-a209-5ef56f6c684a");
    private static final Map<Player, Hold> HOLDS = new WeakHashMap<>();
    private static final ResourceLocation SPIN = ResourceLocation.fromNamespaceAndPath(PortableBladeRuntime.ID, "dangaku_spin");
    private static final ResourceLocation RECOVERY = ResourceLocation.fromNamespaceAndPath(PortableBladeRuntime.ID, "dangaku_spin_recovery");

    static void register() {
        PortableCombos.COMBOS.register(SPIN.getPath(), () -> {
            var circle = ComboStateRegistry.CIRCLE_SLASH.get();
            return PortableDangaku.motion(circle, DangakuSpinRules.CIRCLE_TICKS + 4).loop()
                    .next(user -> PortableDangaku.commands(user).contains(InputCommand.R_CLICK)
                            ? ComboStateRegistry.NONE.getId() : SPIN)
                    .nextOfTimeout(user -> RECOVERY)
                    .clickAction(circle::clickAction)
                    .addTickAction(user -> animate(user, circle))
                    .build();
        });
        PortableCombos.COMBOS.register(RECOVERY.getPath(), () ->
                PortableDangaku.motion(ComboStateRegistry.CIRCLE_SLASH_END.get(), DangakuSpinRules.RECOVERY_TICKS)
                        .next(user -> ComboState.getElapsed(user) >= 7 ? ComboStateRegistry.NONE.getId() : RECOVERY)
                        .nextOfTimeout(user -> ComboStateRegistry.NONE.getId())
                        .addTickAction(UserPoseOverrider::resetRot)
                        .build());
    }

    static boolean isSpin(ResourceLocation combo) {
        return SPIN.equals(combo);
    }

    static boolean ownsCombo(LivingEntity user) {
        var state = PortableDangaku.state(user);
        return state != null && (isSpin(state.getComboSeq()) || RECOVERY.equals(state.getComboSeq()));
    }

    static void attack(AttackEntityEvent event) {
        var player = event.getEntity();
        var state = PortableDangaku.state(player);
        if (state != null && (isSpin(state.getComboSeq()) || RECOVERY.equals(state.getComboSeq())) && !state.onClick()) {
            event.setCanceled(true);
        }
    }

    static void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.START || !(event.player instanceof ServerPlayer player)) return;
        var blade = player.getMainHandItem();
        var state = PortableDangaku.state(player);
        if (state == null || !player.isAlive() || player.isSpectator() || state.isBroken() || state.isSealed()) {
            clear(player);
            return;
        }
        var commands = PortableDangaku.commands(player);
        var combo = state.getComboSeq();
        boolean busy = commands.contains(InputCommand.R_DOWN) || player.isUsingItem();
        boolean canContinue = !busy && !player.getAbilities().flying && !player.isPassenger();
        boolean repeat = DangakuSpinRules.canRepeat(commands.contains(InputCommand.L_DOWN),
                player.onGround(), busy, player.getAbilities().flying, player.isPassenger());

        var hold = HOLDS.get(player);
        if (hold != null && hold.blade != blade) {
            clear(player);
            hold = null;
        }
        if (isSpin(combo)) {
            if (hold == null || !canContinue) {
                state.updateComboSeq(player, RECOVERY);
                clear(player);
                return;
            }
            if (!repeat) hold.finish = true;
            slow(player, true);
            player.setSprinting(false);
            if (ComboState.getElapsed(player) >= DangakuSpinRules.CIRCLE_TICKS) {
                if (!hold.finish && repeat) state.updateComboSeq(player, SPIN);
                else {
                    state.updateComboSeq(player, RECOVERY);
                    clear(player);
                }
            }
            return;
        }
        slow(player, false);
        if (!repeat || !canStart(combo, Long.MAX_VALUE)) {
            HOLDS.remove(player);
            return;
        }
        if (hold == null) {
            hold = new Hold(blade);
            HOLDS.put(player, hold);
        }
        if (++hold.ticks >= DangakuSpinRules.HOLD_TICKS && canStart(combo, ComboState.getElapsed(player))) {
            state.updateComboSeq(player, SPIN);
            slow(player, true);
            player.setSprinting(false);
        }
    }

    private static boolean canStart(ResourceLocation combo, long elapsed) {
        if (ComboStateRegistry.NONE.getId().equals(combo)) return true;
        var phase = PortableDangaku.phase(combo);
        return phase != null && !phase.aerial() && elapsed >= phase.minimumTick()
                && (phase == StyleBranchRules.Phase.D_SWEEP || phase == StyleBranchRules.Phase.D_RETURN || phase.recovery());
    }

    private static void animate(LivingEntity user, ComboState nativeCircle) {
        if (!(user instanceof ServerPlayer player)) return;
        var hold = HOLDS.get(player);
        if (hold != null && hold.blade == player.getMainHandItem() && player.isAlive()) nativeCircle.tickAction(player);
    }

    private static void slow(Player player, boolean active) {
        var movement = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (movement == null) return;
        if (active && movement.getModifier(SLOW_ID) == null) {
            movement.addTransientModifier(new AttributeModifier(SLOW_ID, "Dangaku held circle",
                    DangakuSpinRules.MOVEMENT_PENALTY, AttributeModifier.Operation.MULTIPLY_TOTAL));
        } else if (!active) {
            movement.removeModifier(SLOW_ID);
        }
    }

    static void logout(PlayerEvent.PlayerLoggedOutEvent event) { clear(event.getEntity()); }
    static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) { clear(event.getEntity()); }
    static void clone(PlayerEvent.Clone event) { clear(event.getOriginal()); clear(event.getEntity()); }

    static void stop(ServerStoppedEvent event) {
        HOLDS.keySet().forEach(player -> slow(player, false));
        HOLDS.clear();
    }

    private static void clear(Player player) {
        if (HOLDS.remove(player) != null) slow(player, false);
    }

    private static final class Hold {
        final ItemStack blade;
        int ticks;
        boolean finish;
        Hold(ItemStack blade) { this.blade = blade; }
    }

    private PortableDangakuSpin() {}
}
