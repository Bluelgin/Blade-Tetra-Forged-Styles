package dev.bladetetra.easteregg;

import com.mojang.logging.LogUtils;
import dev.bladetetra.BladeTetra;
import dev.bladetetra.combat.BladeStyle;
import dev.bladetetra.combat.ModComboStates;
import dev.bladetetra.item.ModularSlashBladeItem;
import dev.bladetetra.registry.ModItems;
import mods.flammpfeil.slashblade.capability.concentrationrank.CapabilityConcentrationRank;
import mods.flammpfeil.slashblade.capability.inputstate.CapabilityInputState;
import mods.flammpfeil.slashblade.event.SlashBladeEvent;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import mods.flammpfeil.slashblade.registry.combo.ComboState;
import mods.flammpfeil.slashblade.util.InputCommand;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.ArrayList;
import java.util.List;

/** Opt-in dedicated Forge verification; never runs in ordinary client/server worlds. */
@Mod.EventBusSubscriber(modid=BladeTetra.MOD_ID)
public final class SakuraCombatSmokeTest {
    private static ServerPlayer actor;
    private static final List<Double> HITS=new ArrayList<>();
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void slash(SlashBladeEvent.DoSlashEvent e) {
        if(e.getUser()==actor) HITS.add(e.getDamage());
    }
    @SubscribeEvent public static void started(ServerStartedEvent event) {
        if(!Boolean.getBoolean("blade_tetra.sakuraCombatSmoke")) return;
        try {
            var level=event.getServer().overworld();
            actor=FakePlayerFactory.getMinecraft(level); level.addNewPlayer(actor);
            var blade=ModItems.MODULAR_SLASHBLADE.get().createDefaultStack();
            var tag=blade.getOrCreateTag();
            tag.putString(ModularSlashBladeItem.BLADE_SLOT,ModularSlashBladeItem.WAKIZASHI_MODULE);
            tag.putString(ModularSlashBladeItem.INSCRIPTION_SLOT,ModularSlashBladeItem.AWAKENED_SOUL_INSCRIPTION_MODULE);
            tag.putString(ModularSlashBladeItem.AWAKENED_SOUL_INSCRIPTION_MODULE+"_material",
                    "awakened_soul_inscription/sakura_crystal");
            tag.putBoolean(SenbonzakuraAwakening.TAG_UNLOCKED,true);
            tag.putBoolean("blade_tetra_awakened_v2_sakura_dispersed",true);
            actor.setItemSlot(EquipmentSlot.MAINHAND,blade); actor.setOnGround(true);
            var state=blade.getCapability(ModularSlashBladeItem.BLADESTATE).orElseThrow(IllegalStateException::new);
            state.setBroken(false); state.setSealed(false); state.setComboRoot(ModComboStates.getRoot(BladeStyle.RENGEKI));
            var input=actor.getCapability(CapabilityInputState.INPUT_STATE).orElseThrow(IllegalStateException::new);
            input.getCommands().clear(); input.getCommands().add(InputCommand.R_CLICK);
            var rank=actor.getCapability(CapabilityConcentrationRank.RANK_POINT).orElseThrow(IllegalStateException::new);
            var sixth=SakuraSixthCombo.SIXTH.get();
            var nativeSixth=ComboStateRegistry.COMBO_B6.get();
            check(sixth.getMotionLoc().equals(nativeSixth.getMotionLoc()),"same native motion");
            check(sixth.getStartFrame()==nativeSixth.getStartFrame()&&sixth.getEndFrame()==nativeSixth.getEndFrame(),"same native B6 frames");
            check(sixth.getTimeoutMS()==nativeSixth.getTimeoutMS()+100,"two tick input tail only");
            check(SakuraEndlessFlow.eligible(blade),"real awakened dispersed Tetra weapon");
            long now=Math.max(1000,level.getGameTime());
            event.getServer().getWorldData().overworldData().setGameTime(now);
            rank.setRawRankPoint(rank.getMaxCapacity()); rank.setLastUpdte(now);
            state.setComboSeq(ComboStateRegistry.COMBO_B1.getId()); state.setLastActionTime(now);
            SakuraEndlessFlow.onPlayerTick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END,actor));
            check(SakuraEndlessFlow.isBurning(actor,blade),"SSS starts native Rank burn");
            for(int round=0;round<3;round++) {
                for(int stage=1;stage<=6;stage++) {
                    check(SakuraFlowRules.stage(state.getComboSeq())==stage,"B1..B6 order, round "+round+" stage "+stage);
                    ComboState node=ComboStateRegistry.REGISTRY.get().getValue(state.getComboSeq());
                    long at=level.getGameTime(); state.setLastActionTime(at);
                    HITS.clear();
                    int last=stage==6?6:3;
                    for(int tick=0;tick<=last;tick++) {
                        event.getServer().getWorldData().overworldData().setGameTime(at+tick);
                        node.tickAction(actor);
                        if(stage==6&&tick==4)
                            check(state.progressCombo(actor,true).equals(ComboStateRegistry.NONE.getId()),"B6 early input cannot truncate tick 5/6");
                        SakuraEndlessFlow.onPlayerTick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END,actor));
                    }
                    if(stage==6) {
                        check(HITS.size()==7,"native B6 emits all seven authored slash callbacks, got "+HITS.size());
                        for(double damage:HITS) check(Math.abs(damage-.244*.85)<1e-5,"B6 keeps ordinary Rengeki damage multiplier");
                    }
                    event.getServer().getWorldData().overworldData().setGameTime(at+(stage==6?7:4));
                    long before=rank.getRankPoint(level.getGameTime());
                    for(int query=0;query<10;query++) state.progressCombo(actor,true);
                    check(before==rank.getRankPoint(level.getGameTime()),"virtual routing never debits Rank");
                    if(stage==6) {
                        input.getCommands().clear();
                        check(state.progressCombo(actor,true).equals(ComboStateRegistry.NONE.getId()),"no input means no free B1 restart");
                        input.getCommands().add(InputCommand.R_CLICK);
                    }
                    state.progressCombo(actor);
                    check(!state.getComboSeq().equals(ComboStateRegistry.COMBO_B7.getId()),"B7 excluded");
                }
            }
            rank.setRawRankPoint(0); rank.setLastUpdte(level.getGameTime());
            state.setComboSeq(SakuraSixthCombo.SIXTH.getId()); state.setLastActionTime(level.getGameTime()-7);
            SakuraEndlessFlow.onPlayerTick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END,actor));
            check(!SakuraEndlessFlow.isBurning(actor,blade),"zero Rank stops immediately");
            check(state.progressCombo(actor,true).equals(ComboStateRegistry.NONE.getId()),"zero Rank cannot loop");
            check(tag.getBoolean("blade_tetra_awakened_v2_sakura_dispersed"),"burnout keeps dispersed stance");
            tag.putBoolean("blade_tetra_awakened_v2_sakura_dispersed",false);
            state.setComboSeq(ComboStateRegistry.COMBO_B6.getId()); state.setLastActionTime(level.getGameTime()-4);
            check(state.progressCombo(actor,true).equals(ComboStateRegistry.COMBO_B7.getId()),"ordinary native B6/B7 unchanged");
            LogUtils.getLogger().info("SAKURA_COMBAT_SMOKE_PASS: three B1-B6 cycles, seven native B6 hits, original damage, early input guard, virtual query costs, input release, Rank exhaustion, stance retained, ordinary B7 unchanged");
        } catch(Throwable failure) {
            LogUtils.getLogger().error("SAKURA_COMBAT_SMOKE_FAIL",failure);
            throw new IllegalStateException("Sakura combat smoke failed",failure);
        } finally {actor=null;HITS.clear();event.getServer().halt(false);}
    }
    private static void check(boolean okay,String message) {if(!okay) throw new IllegalStateException(message);}
    private SakuraCombatSmokeTest() {}
}
