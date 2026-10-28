package dev.bladetetra.easteregg;

import com.mojang.logging.LogUtils;
import mods.flammpfeil.slashblade.capability.concentrationrank.CapabilityConcentrationRank;
import mods.flammpfeil.slashblade.capability.inputstate.CapabilityInputState;
import mods.flammpfeil.slashblade.event.BladeMotionEvent;
import mods.flammpfeil.slashblade.event.SlashBladeEvent;
import mods.flammpfeil.slashblade.event.handler.InputCommandEvent;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import mods.flammpfeil.slashblade.util.InputCommand;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import java.util.EnumSet;

/** Additional real Forge input/inventory regression; invoked only by the opt-in smoke. */
public final class SakuraFlowSmoke {
    private final ServerPlayer actor;
    private int sixthHits, zeroHits, completedSixths;
    private boolean cancel;
    private SakuraFlowSmoke(ServerPlayer actor) { this.actor = actor; }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void slash(SlashBladeEvent.DoSlashEvent event) {
        if (event.getUser() != actor) return;
        int stage = SakuraFlowRules.stage(event.getSlashBladeState().getComboSeq());
        if (stage == 6) sixthHits++;
        if (stage >= 2 && event.getSlashBladeState().getElapsedTime(actor) == 0) zeroHits++;
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void motion(BladeMotionEvent event) {
        if (event.getEntity() == actor && cancel) event.setCanceled(true);
    }
    static void verify(ServerLevel level, ServerPlayer actor, ItemStack blade) {
        var probe = new SakuraFlowSmoke(actor);
        MinecraftForge.EVENT_BUS.register(probe);
        try {
            var state = blade.getCapability(ItemSlashBlade.BLADESTATE).orElseThrow(IllegalStateException::new);
            var input = actor.getCapability(CapabilityInputState.INPUT_STATE).orElseThrow(IllegalStateException::new);
            var rank = actor.getCapability(CapabilityConcentrationRank.RANK_POINT).orElseThrow(IllegalStateException::new);
            long at = reset(level, actor, blade);
            // A real press at B6 tick 4 is released before tick 7. Queries cannot consume it.
            state.setComboSeq(SakuraSixthCombo.SIXTH.getId()); state.setLastActionTime(at);
            end(actor);
            for (int tick = 0; tick <= 7; tick++) {
                time(level, at + tick);
                if (tick == 4) {
                    input.getCommands().clear(); input.getCommands().add(InputCommand.L_CLICK);
                    SakuraEndlessFlow.input(new InputCommandEvent(actor, input, EnumSet.noneOf(InputCommand.class), input.getCommands().clone()));
                    input.getCommands().clear();
                    for (int i = 0; i < 10; i++) check(state.progressCombo(actor, true).equals(ComboStateRegistry.NONE.getId()), "early virtual input preserves B6");
                }
                SakuraSixthCombo.SIXTH.get().tickAction(actor);
                end(actor);
            }
            check(probe.sixthHits == 7, "buffered continuation retains all seven B6 hits");
            check(state.getComboSeq().equals(ComboStateRegistry.COMBO_B1.getId()), "released buffered press restarts B1");
            long committed = state.getLastActionTime();
            time(level, at + 8); end(actor);
            check(state.getLastActionTime() == committed, "buffer consumed once");

            // Continuous held attack exercises the actual item inventory path,
            // including tick 0 of nodes committed after inventoryTick has run.
            at = reset(level, actor, blade); probe.sixthHits = 0; probe.zeroHits = 0;
            input.getCommands().clear(); input.getCommands().add(InputCommand.L_CLICK);
            for (int tick = 0; tick < 150; tick++) {
                time(level, at + tick);
                int before = SakuraFlowRules.stage(state.getComboSeq());
                blade.getItem().inventoryTick(blade, level, actor, actor.getInventory().selected, true);
                end(actor);
                if (before == 6 && SakuraFlowRules.stage(state.getComboSeq()) == 1) {
                    probe.completedSixths++;
                    check(probe.sixthHits == 7, "held B6 has exactly seven native callbacks, got " + probe.sixthHits);
                    probe.sixthHits = 0;
                }
                check(SakuraFlowRules.stage(state.getComboSeq()) != 0, "held B1-B6 remains continuous");
            }
            check(probe.completedSixths >= 3, "held input completes three full rounds");
            check(probe.zeroHits >= probe.completedSixths * 5, "automatic B2-B6 delivers native tick zero");

            // Short neutral gap keeps the original SSS admission after demotion.
            at = reset(level, actor, blade);
            rank.setRawRankPoint(500); rank.setLastUpdte(at);
            state.setComboSeq(ComboStateRegistry.NONE.getId());
            time(level, at + 10);
            long beforeGapCharge = rank.getRankPoint(at + 10);
            end(actor);
            check(SakuraEndlessFlow.isBurning(actor, blade), "neutral gap retains admission below A");
            check(SakuraEndlessFlow.initialRank(actor, blade) == 7, "entry efficiency is retained");
            check(rank.getRankPoint(at + 10) == beforeGapCharge - 41, "neutral gap still pays SSS burn in addition to native decay");
            time(level, at + 14);
            state.setComboSeq(ComboStateRegistry.COMBO_B1.getId()); state.setLastActionTime(at + 14); end(actor);
            check(SakuraEndlessFlow.isBurning(actor, blade), "re-enter at grace boundary without A");
            state.setComboSeq(ComboStateRegistry.NONE.getId());
            time(level, at + 29); end(actor);
            check(!SakuraEndlessFlow.isBurning(actor, blade), "long neutral gap ends admission");

            // One canceled buffer is discarded; a held right button remains SA.
            at = reset(level, actor, blade);
            state.setComboSeq(SakuraSixthCombo.SIXTH.getId()); state.setLastActionTime(at);
            time(level, at + 4);
            input.getCommands().clear(); input.getCommands().add(InputCommand.L_CLICK);
            SakuraEndlessFlow.input(new InputCommandEvent(actor, input, EnumSet.noneOf(InputCommand.class), input.getCommands().clone()));
            input.getCommands().clear();
            probe.cancel = true; time(level, at + 7); end(actor); probe.cancel = false;
            time(level, at + 8); end(actor);
            check(state.getComboSeq().equals(SakuraSixthCombo.SIXTH.getId()), "canceled token cannot retry");
            input.getCommands().add(InputCommand.R_CLICK); actor.startUsingItem(InteractionHand.MAIN_HAND);
            time(level, at + 9); end(actor);
            check(state.getComboSeq().equals(SakuraSixthCombo.SIXTH.getId()), "right hold does not auto-chain over SA charge");
            actor.stopUsingItem();
            SakuraEndlessFlow.slashArt(new SlashBladeEvent.PerformSlashArtEvent(actor, 40, state,
                    ComboStateRegistry.COMBO_A1.getId(), mods.flammpfeil.slashblade.slasharts.SlashArts.ArtsType.Success));
            state.updateComboSeq(actor, ComboStateRegistry.COMBO_A1.getId()); end(actor);
            check(!SakuraEndlessFlow.isBurning(actor, blade), "committed SA ends flow admission");
            // Moving the same stack to another selected slot invalidates the flow.
            at = reset(level, actor, blade);
            int originalSlot = actor.getInventory().selected, changedSlot = (originalSlot + 1) % 9;
            actor.getInventory().setItem(originalSlot, ItemStack.EMPTY);
            actor.getInventory().selected = changedSlot;
            actor.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, blade);
            end(actor);
            check(!SakuraEndlessFlow.isBurning(actor, blade), "slot change clears admission and pending input");
            actor.getInventory().setItem(changedSlot, ItemStack.EMPTY);
            actor.getInventory().selected = originalSlot;
            actor.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, blade);
            reset(level, actor, blade);
            SakuraEndlessFlow.death(new net.minecraftforge.event.entity.living.LivingDeathEvent(actor, actor.damageSources().generic()));
            check(!SakuraEndlessFlow.isBurning(actor, blade), "death immediately removes the flow");
            LogUtils.getLogger().info("SAKURA_FLOW_SMOKE_PASS: released early buffer, full B6 callbacks, native tick-zero delivery, held rounds, demotion grace and paid gaps, expiration, canceled commit, right-charge isolation, committed SA, same-object slot change, death cleanup");
        } finally {
            MinecraftForge.EVENT_BUS.unregister(probe);
            actor.stopUsingItem();
            inputClear(actor);
            blade.getOrCreateTag().putBoolean(SakuraStanceRules.DISPERSED, false); end(actor);
        }
    }
    private static long reset(ServerLevel level, ServerPlayer actor, ItemStack blade) {
        actor.stopUsingItem(); inputClear(actor); actor.setOnGround(true);
        blade.getOrCreateTag().putBoolean(SakuraStanceRules.DISPERSED, false); end(actor);
        blade.getOrCreateTag().putBoolean(SakuraStanceRules.DISPERSED, true);
        long now = level.getGameTime() + 30; time(level, now);
        var rank = actor.getCapability(CapabilityConcentrationRank.RANK_POINT).orElseThrow(IllegalStateException::new);
        rank.setRawRankPoint(rank.getMaxCapacity()); rank.setLastUpdte(now);
        var state = blade.getCapability(ItemSlashBlade.BLADESTATE).orElseThrow(IllegalStateException::new);
        state.setComboSeq(ComboStateRegistry.COMBO_B1.getId()); state.setLastActionTime(now); end(actor);
        return now;
    }
    private static void inputClear(ServerPlayer actor) { actor.getCapability(CapabilityInputState.INPUT_STATE).ifPresent(i -> i.getCommands().clear()); }
    private static void end(ServerPlayer actor) { SakuraEndlessFlow.onPlayerTick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, actor)); }
    private static void time(ServerLevel level, long at) { level.getServer().getWorldData().overworldData().setGameTime(at); }
    private static void check(boolean okay, String message) { if (!okay) throw new IllegalStateException(message); }
}
