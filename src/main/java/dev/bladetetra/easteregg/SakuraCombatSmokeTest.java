package dev.bladetetra.easteregg;

import com.mojang.logging.LogUtils;
import dev.bladetetra.BladeTetra;
import dev.bladetetra.combat.*;
import dev.bladetetra.item.ModularSlashBladeItem;
import dev.bladetetra.registry.ModItems;
import mods.flammpfeil.slashblade.capability.concentrationrank.CapabilityConcentrationRank;
import mods.flammpfeil.slashblade.capability.inputstate.CapabilityInputState;
import mods.flammpfeil.slashblade.event.*;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import mods.flammpfeil.slashblade.slasharts.SlashArts;
import mods.flammpfeil.slashblade.util.InputCommand;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.*;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/** Real Forge/native item callbacks; explicit time stepping is not a graphical client test. */
@Mod.EventBusSubscriber(modid=BladeTetra.MOD_ID)
public final class SakuraCombatSmokeTest {
    private static ServerPlayer actor;
    private static int b1,b6,zero;
    private static boolean cancel, rewrite;
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void slash(SlashBladeEvent.DoSlashEvent e) {
        if(e.getUser()!=actor) return;
        int stage=SakuraFlowRules.stage(e.getSlashBladeState().getComboSeq());
        if(stage==1) b1++;if(stage==6) b6++;
        if(stage>=2&&e.getSlashBladeState().getElapsedTime(actor)==0) zero++;
        if(SakuraDanceCombos.owns(e.getSlashBladeState().getComboSeq()))
            check(Math.abs(e.getDamage()-.244*.85)<1e-5||Math.abs(e.getDamage()-.24*.85)<1e-5,"native damage unchanged");
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void motion(BladeMotionEvent e) {
        if(e.getEntity()==actor&&cancel) e.setCanceled(true);
        if(e.getEntity()==actor&&rewrite&&SakuraDanceCombos.owns(e.getCombo())) e.setCombo(ComboStateRegistry.COMBO_A1.getId());
    }
    @SubscribeEvent public static void started(ServerStartedEvent e) {
        if(!Boolean.getBoolean("blade_tetra.sakuraCombatSmoke")) return;
        try {
            ServerLevel level=e.getServer().overworld();
            actor=FakePlayerFactory.getMinecraft(level);level.addNewPlayer(actor);
            ItemStack blade=ModItems.MODULAR_SLASHBLADE.get().createDefaultStack();
            var tag=blade.getOrCreateTag();
            tag.putString(ModularSlashBladeItem.BLADE_SLOT,ModularSlashBladeItem.WAKIZASHI_MODULE);
            tag.putString(ModularSlashBladeItem.INSCRIPTION_SLOT,ModularSlashBladeItem.AWAKENED_SOUL_INSCRIPTION_MODULE);
            tag.putString(ModularSlashBladeItem.AWAKENED_SOUL_INSCRIPTION_MODULE+"_material","awakened_soul_inscription/sakura_crystal");
            tag.putBoolean(SenbonzakuraAwakening.TAG_UNLOCKED,true);tag.putBoolean(SakuraStanceRules.DISPERSED,true);
            actor.setItemSlot(EquipmentSlot.MAINHAND,blade);actor.setOnGround(true);actor.moveTo(0,100,0,0,0);
            var state=blade.getCapability(ModularSlashBladeItem.BLADESTATE).orElseThrow(IllegalStateException::new);
            state.setBroken(false);state.setSealed(false);state.setComboRoot(ModComboStates.getRoot(BladeStyle.RENGEKI));
            var input=actor.getCapability(CapabilityInputState.INPUT_STATE).orElseThrow(IllegalStateException::new);
            var rank=actor.getCapability(CapabilityConcentrationRank.RANK_POINT).orElseThrow(IllegalStateException::new);
            check(SakuraDanceCombos.compatible(),"typed callback adapter compatible");
            reset(level,blade);
            var originalArt=state.getSlashArtsKey();
            input.getCommands().add(InputCommand.L_DOWN);
            state.updateComboSeq(actor,ComboStateRegistry.COMBO_B1.getId());
            long at=level.getGameTime();long points=rank.getRawRankPoint();
            for(int t=0;t<16;t++) step(level,blade,at+t);
            check(!SakuraDanceCombos.owns(state.getComboSeq()),"held L_DOWN never enters SA");
            check(!SakuraEndlessFlow.isBurning(actor,blade),"ordinary attacks never start Rank burn");
            check(rank.getRawRankPoint()==points,"no extra ordinary Rank debit");
            // Native routing B1 through B7 is unchanged; click commands are temporary.
            for(int stage=1;stage<=6;stage++) {
                time(level,level.getGameTime()+30);state.setComboSeq(new net.minecraft.resources.ResourceLocation("slashblade","combo_b"+stage));
                state.setLastActionTime(level.getGameTime()-(stage==1?9:4));
                input.getCommands().add(InputCommand.R_CLICK);
                check(state.progressCombo(actor,true).equals(new net.minecraft.resources.ResourceLocation("slashblade","combo_b"+(stage+1))),"ordinary B"+stage+" retains native next");
                input.getCommands().remove(InputCommand.R_CLICK);
            }
            // Native charge submission; successful commit creates exactly one controller.
            reset(level,blade);state.doChargeAction(actor,40);
            check(SakuraInfiniteDanceSA.active(actor,blade),"native SA commits controller");
            check(Objects.equals(originalArt,state.getSlashArtsKey()),"per-blade SA key untouched");
            at=level.getGameTime();b1=0;b6=0;zero=0;int rounds=0;
            for(int t=0;t<170;t++) {
                int before=SakuraFlowRules.stage(state.getComboSeq());step(level,blade,at+t);
                if(before==6&&SakuraFlowRules.stage(state.getComboSeq())==1) {
                    check(b6==7,"B6 completes exactly seven native callbacks");b6=0;rounds++;
                }
                if(before==1&&SakuraFlowRules.stage(state.getComboSeq())==2) {
                    check(b1==10,"B1 includes both opening slashes and all eight late hits");b1=0;
                }
                check(!state.getComboSeq().equals(ComboStateRegistry.COMBO_B7.getId()),"SA excludes B7");
                check(state.progressCombo(actor,true).equals(ComboStateRegistry.NONE.getId()),"manual inputs cannot advance SA");
            }
            check(rounds>=3&&zero>=15,"multiple automatic rounds, all node tick zeros");
            check(SakuraInfiniteDanceSA.initialRank(actor,blade)==7,"locked SSS entry efficiency");
            // No repeat cast, no duplicate debit in the same tick.
            long raw=rank.getRawRankPoint();var combo=state.getComboSeq();
            var duplicate=new SlashBladeEvent.PerformSlashArtEvent(actor,40,state,ComboStateRegistry.JUDGEMENT_CUT.getId(),SlashArts.ArtsType.Super);
            SakuraInfiniteDanceSA.art(duplicate);check(duplicate.getComboState().equals(ComboStateRegistry.NONE.getId()),"repeat Super blocked");
            end();check(rank.getRawRankPoint()==raw&&state.getComboSeq().equals(combo),"same tick is idempotent");
            rank.setRawRankPoint(0);rank.setLastUpdte(level.getGameTime());
            for(int t=1;t<=16;t++) step(level,blade,level.getGameTime()+1);
            check(SakuraInfiniteDanceSA.phase(actor)==SakuraInfiniteDanceSA.Phase.IDLE,"exhaustion safely ends");
            check(SakuraStanceController.dispersed(blade),"exhaustion preserves dispersed stance");
            for(int grade=4;grade<=7;grade++) {
                reset(level,blade);long now=level.getGameTime();
                double value=switch(grade){case 4->4.2;case 5->5.1;case 6->5.35;default->5.8;};
                rank.setRawRankPoint((long)(value*rank.getUnitCapacity()));rank.setLastUpdte(now);
                state.doChargeAction(actor,40);check(SakuraInfiniteDanceSA.initialRank(actor,blade)==grade,"entry grade "+grade);
                for(int t=0;t<10;t++) step(level,blade,now+t);
                time(level,now+10);long before=rank.getRankPoint(now+10);end();
                int cost=SakuraFlowRules.halfSecondCost(grade,dev.bladetetra.config.GameplayConfig.AWAKENED_SAKURA_BURN_POINTS_PER_SECOND.get());
                check(rank.getRankPoint(now+10)==before-cost,"exact native Rank debit "+grade);
                long after=rank.getRankPoint(now+10);rank.addRankPoint(actor,100);SakuraEndlessFlow.onPlayerTick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END,actor));
                check(rank.getRankPoint(now+10)<=after,"secondary attacks cannot fund SA");
            }
            reset(level,blade);state.doChargeAction(actor,state.getFullChargeTicks(actor));
            check(SakuraInfiniteDanceSA.active(actor,blade),"native Jackpot starts the same SA once");
            int maxTicks=dev.bladetetra.config.GameplayConfig.SAKURA_SA_MAX_TICKS.get();
            try {
                dev.bladetetra.config.GameplayConfig.SAKURA_SA_MAX_TICKS.set(40);
                reset(level,blade);state.doChargeAction(actor,40);long began=level.getGameTime();
                for(int t=0;t<=55;t++) step(level,blade,began+t);
                check(SakuraInfiniteDanceSA.phase(actor)==SakuraInfiniteDanceSA.Phase.IDLE,"maximum runtime safely unlocks");
            } finally {dev.bladetetra.config.GameplayConfig.SAKURA_SA_MAX_TICKS.set(maxTicks);}
            for(int scenario=0;scenario<7;scenario++) {
                reset(level,blade);state.doChargeAction(actor,40);check(SakuraInfiniteDanceSA.active(actor,blade),"start lifecycle scenario");
                switch(scenario) {
                    case 0 -> actor.setItemSlot(EquipmentSlot.MAINHAND,ItemStack.EMPTY);
                    case 1 -> tag.putBoolean(SakuraStanceRules.DISPERSED,false);
                    case 2 -> SakuraInfiniteDanceSA.death(new net.minecraftforge.event.entity.living.LivingDeathEvent(actor,actor.damageSources().generic()));
                    case 3 -> SakuraInfiniteDanceSA.dimension(new net.minecraftforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent(actor,net.minecraft.world.level.Level.OVERWORLD,net.minecraft.world.level.Level.NETHER));
                    case 4 -> SakuraInfiniteDanceSA.logout(new net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent(actor));
                    case 5 -> SakuraInfiniteDanceSA.hurt(new net.minecraftforge.event.entity.living.LivingDamageEvent(actor,actor.damageSources().generic(),1));
                    default -> actor.getInventory().selected=(actor.getInventory().selected+1)%9;
                }
                end();check(SakuraInfiniteDanceSA.phase(actor)==SakuraInfiniteDanceSA.Phase.IDLE,"lifecycle releases lock "+scenario);
                check(actor.getAttribute(Attributes.MOVEMENT_SPEED).getModifiers().stream().noneMatch(m->m.getName().equals("Sakura infinite dance")),"speed modifier cleaned");
                actor.getInventory().selected=0;actor.setItemSlot(EquipmentSlot.MAINHAND,blade);tag.putBoolean(SakuraStanceRules.DISPERSED,true);
            }
            reset(level,blade);cancel=true;state.doChargeAction(actor,40);cancel=false;end();
            check(!SakuraInfiniteDanceSA.active(actor,blade),"canceled motion never locks");
            reset(level,blade);rewrite=true;state.doChargeAction(actor,40);rewrite=false;end();
            check(!SakuraInfiniteDanceSA.active(actor,blade),"rewritten SA never locks");
            reset(level,blade);rank.setRawRankPoint(0);rank.setLastUpdte(level.getGameTime());state.doChargeAction(actor,40);end();
            check(!SakuraInfiniteDanceSA.active(actor,blade),"insufficient Rank rejected");
            reset(level,blade);tag.putBoolean(SakuraStanceRules.DISPERSED,false);
            var nativeArt=state.getSlashArts().doArts(SlashArts.ArtsType.Success,actor);
            var condensed=new SlashBladeEvent.PerformSlashArtEvent(actor,40,state,nativeArt,SlashArts.ArtsType.Success);
            SakuraInfiniteDanceSA.art(condensed);check(condensed.getComboState().equals(nativeArt),"condensed configured SA unchanged");
            LogUtils.getLogger().info("SAKURA_SA_SMOKE_PASS: native charge commit, three full rounds, B1 ten/B6 seven hits, exact Rank tiers, no repeat debit, manual lock, exhaustion, seven lifecycle paths, cancel/rewrite/fail, configured SA unchanged");
            reset(level,blade);SakuraConcertoSmoke.verify(level,actor,blade);
            SakuraStanceSmoke.verify(level,actor,blade);
            LogUtils.getLogger().info("SAKURA_COMBAT_SMOKE_PASS: ordinary B1-B7, held L_DOWN no automatic loop, no ordinary Rank cost, separate SA lifecycle");
        } catch(Throwable failure) {LogUtils.getLogger().error("SAKURA_COMBAT_SMOKE_FAIL",failure);throw new IllegalStateException(failure);}
        finally {actor=null;e.getServer().halt(false);}
    }
    private static void reset(ServerLevel level,ItemStack blade) {
        SakuraInfiniteDanceSA.end(actor);actor.stopUsingItem();actor.setOnGround(true);
        blade.getOrCreateTag().putBoolean(SakuraStanceRules.DISPERSED,false);
        SakuraEndlessFlow.onPlayerTick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END,actor));
        blade.getOrCreateTag().putBoolean(SakuraStanceRules.DISPERSED,true);
        time(level,Math.max(1000,level.getGameTime()+30));
        var r=actor.getCapability(CapabilityConcentrationRank.RANK_POINT).orElseThrow(IllegalStateException::new);r.setRawRankPoint(r.getMaxCapacity());r.setLastUpdte(level.getGameTime());
        var s=blade.getCapability(ModularSlashBladeItem.BLADESTATE).orElseThrow(IllegalStateException::new);s.setComboSeq(ComboStateRegistry.NONE.getId());s.setLastActionTime(level.getGameTime());
        actor.getCapability(CapabilityInputState.INPUT_STATE).ifPresent(i->{i.getCommands().clear();i.getCommands().add(InputCommand.L_DOWN);});
        SakuraEndlessFlow.onPlayerTick(new TickEvent.PlayerTickEvent(TickEvent.Phase.START,actor));
    }
    private static void step(ServerLevel level,ItemStack blade,long at) {
        time(level,at);SakuraEndlessFlow.onPlayerTick(new TickEvent.PlayerTickEvent(TickEvent.Phase.START,actor));
        blade.getItem().inventoryTick(blade,level,actor,actor.getInventory().selected,true);end();
    }
    private static void end() {SakuraInfiniteDanceSA.tick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END,actor));SakuraEndlessFlow.onPlayerTick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END,actor));}
    private static void time(ServerLevel level,long at) {level.getServer().getWorldData().overworldData().setGameTime(at);}
    private static void check(boolean okay,String message) {if(!okay)throw new IllegalStateException(message);}
    private SakuraCombatSmokeTest() {}
}
