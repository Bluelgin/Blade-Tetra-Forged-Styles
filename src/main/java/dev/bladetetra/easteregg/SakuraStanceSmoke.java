package dev.bladetetra.easteregg;

import com.mojang.logging.LogUtils;
import mods.flammpfeil.slashblade.capability.concentrationrank.CapabilityConcentrationRank;
import mods.flammpfeil.slashblade.capability.inputstate.CapabilityInputState;
import mods.flammpfeil.slashblade.event.BladeMotionEvent;
import mods.flammpfeil.slashblade.event.SlashBladeEvent;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import mods.flammpfeil.slashblade.slasharts.SlashArts;
import mods.flammpfeil.slashblade.util.InputCommand;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import java.util.ArrayList;
import java.util.List;

/** Called only by the existing opt-in dedicated Sakura smoke, never normal gameplay. */
public final class SakuraStanceSmoke {
    private final ServerPlayer actor;
    private final List<Double> slashes=new ArrayList<>();
    private boolean cancel;
    SakuraStanceSmoke(ServerPlayer actor) {this.actor=actor;}
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public void slash(SlashBladeEvent.DoSlashEvent e) {if(e.getUser()==actor) slashes.add(e.getDamage());}
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public void cancel(BladeMotionEvent e) {if(cancel&&e.getEntity()==actor) e.setCanceled(true);}
    static void verify(ServerLevel level,ServerPlayer actor,ItemStack blade) {
        var probe=new SakuraStanceSmoke(actor);MinecraftForge.EVENT_BUS.register(probe);
        Zombie victim=new Zombie(level);
        actor.moveTo(0,100,0,0,0);actor.setYHeadRot(0);actor.yBodyRot=0;
        victim.moveTo(0,100,1,0,0);victim.setNoAi(true);victim.setNoGravity(true);
        victim.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);victim.setHealth(1000);level.addFreshEntity(victim);
        try {
            var state=blade.getCapability(ItemSlashBlade.BLADESTATE).orElseThrow(IllegalStateException::new);
            var input=actor.getCapability(CapabilityInputState.INPUT_STATE).orElseThrow(IllegalStateException::new);
            input.getCommands().clear();input.getCommands().addAll(java.util.EnumSet.of(InputCommand.SNEAK,InputCommand.BACK,InputCommand.R_CLICK));
            var tag=blade.getOrCreateTag();tag.putBoolean(SakuraStanceRules.DISPERSED,false);
            advance(level,20);actor.setOnGround(false);state.setComboSeq(ComboStateRegistry.NONE.getId());state.setLastActionTime(level.getGameTime());
            check(state.progressCombo(actor,true).equals(ComboStateRegistry.AERIAL_CLEAVE.getId()),"real aerial input selects native cleave");
            end(actor);check(!SakuraStanceController.dispersed(blade),"virtual query cannot disperse");
            state.progressCombo(actor);end(actor);
            check(SakuraStanceController.dispersed(blade),"committed cleave disperses without a hit");
            long began=level.getGameTime();
            for(int t=0;t<4;t++) {
                advanceTo(level,began+t);victim.invulnerableTime=0;
                ComboStateRegistry.REGISTRY.get().getValue(state.resolvCurrentComboState(actor)).tickAction(actor);end(actor);
            }
            check(victim.getHealth()<1000,"native cleave area attack still damages a real victim");
            check(actor.getDeltaMovement().y<0,"native cleave fall callback preserved");
            check(SakuraStanceController.dispersed(blade),"cleave loop cannot toggle back");
            actor.setOnGround(true);victim.invulnerableTime=0;
            ComboStateRegistry.REGISTRY.get().getValue(state.getComboSeq()).tickAction(actor);end(actor);
            check(probe.slashes.stream().anyMatch(d->Math.abs(d-.44)<1e-5),"native landing slash damage retained");
            check(SakuraStanceController.dispersed(blade),"landing is not another form switch");
            actor.setShiftKeyDown(true);
            for(boolean dispersed:new boolean[]{true,false}) {
                tag.putBoolean(SakuraStanceRules.DISPERSED,dispersed);
                MinecraftForge.EVENT_BUS.post(new SlashBladeEvent.PerformSlashArtEvent(actor,40,state,
                        ComboStateRegistry.SAKURA_END_LEFT.getId(),SlashArts.ArtsType.Success));
                check(SakuraStanceController.dispersed(blade)==dispersed,"Shift+successful SA never toggles");
            }
            tag.putBoolean(SakuraStanceRules.DISPERSED,true);
            actor.getCapability(CapabilityConcentrationRank.RANK_POINT).ifPresent(rank->{rank.setRawRankPoint(0);rank.setLastUpdte(level.getGameTime());});
            advance(level,20);state.setComboSeq(ComboStateRegistry.NONE.getId());state.setLastActionTime(level.getGameTime());
            check(state.progressCombo(actor).equals(ComboStateRegistry.UPPERSLASH.getId()),"zero Rank native upper remains available");
            end(actor);check(!SakuraStanceController.dispersed(blade),"zero Rank upper condenses");
            probe.slashes.clear();long upper=level.getGameTime();
            for(int t=0;t<=4;t++) {advanceTo(level,upper+t);ComboStateRegistry.UPPERSLASH.get().tickAction(actor);end(actor);}
            check(probe.slashes.stream().anyMatch(d->Math.abs(d-.50)<1e-5),"native upper slash callback/damage retained");
            // Repeat upper and unrelated committed actions cannot turn a condensed sword back into shards.
            advance(level,20);state.updateComboSeq(actor,ComboStateRegistry.UPPERSLASH.getId());end(actor);
            check(!SakuraStanceController.dispersed(blade),"repeated upper idempotent");
            state.updateComboSeq(actor,ComboStateRegistry.COMBO_B1.getId());end(actor);
            check(!SakuraStanceController.dispersed(blade),"ordinary B1 cannot toggle");
            // A late LOWEST listener cancels after the controller observes the candidate.
            tag.putBoolean(SakuraStanceRules.DISPERSED,true);advance(level,20);
            state.setComboSeq(ComboStateRegistry.NONE.getId());state.setLastActionTime(level.getGameTime()-1);
            probe.cancel=true;state.progressCombo(actor);probe.cancel=false;end(actor);
            check(SakuraStanceController.dispersed(blade),"canceled native commit cannot condense");
            state.updateComboSeq(actor,ComboStateRegistry.UPPERSLASH.getId());
            actor.setItemSlot(EquipmentSlot.MAINHAND,ItemStack.EMPTY);end(actor);
            check(SakuraStanceController.dispersed(blade),"switching sword cancels pending commit");
            actor.setItemSlot(EquipmentSlot.MAINHAND,blade);
            tag.putBoolean(SenbonzakuraAwakening.TAG_UNLOCKED,false);
            state.updateComboSeq(actor,ComboStateRegistry.UPPERSLASH.getId());end(actor);
            check(SakuraStanceController.dispersed(blade),"unawakened sword untouched");
            tag.putBoolean(SenbonzakuraAwakening.TAG_UNLOCKED,true);
            for(int lifecycle=0;lifecycle<3;lifecycle++) {
                tag.putBoolean(SakuraStanceRules.DISPERSED,true);advance(level,20);
                state.updateComboSeq(actor,ComboStateRegistry.UPPERSLASH.getId());
                switch(lifecycle) {
                    case 0 -> SakuraStanceController.dimension(new net.minecraftforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent(
                            actor,net.minecraft.world.level.Level.OVERWORLD,net.minecraft.world.level.Level.NETHER));
                    case 1 -> SakuraStanceController.logout(new net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent(actor));
                    default -> SakuraStanceController.death(new net.minecraftforge.event.entity.living.LivingDeathEvent(actor,level.damageSources().generic()));
                }
                end(actor);
                check(SakuraStanceController.dispersed(blade),"lifecycle event cancels only pending transition "+lifecycle);
            }
            String oldModule=tag.getString(dev.bladetetra.item.ModularSlashBladeItem.BLADE_SLOT);
            tag.putString(dev.bladetetra.item.ModularSlashBladeItem.BLADE_SLOT,dev.bladetetra.item.ModularSlashBladeItem.NODACHI_MODULE);
            advance(level,20);state.updateComboSeq(actor,ComboStateRegistry.UPPERSLASH.getId());end(actor);
            check(SakuraStanceController.dispersed(blade),"other combat style unchanged");
            tag.putString(dev.bladetetra.item.ModularSlashBladeItem.BLADE_SLOT,oldModule);
            check(SakuraStanceRules.action(ComboStateRegistry.AERIAL_CLEAVE_LOOP.getId())==0,"loop excluded");
            LogUtils.getLogger().info("SAKURA_STANCE_SMOKE_PASS: actual native input/commit, air miss, cleave area damage and landing, upper .50, B6 tail at zero Rank, idempotence, virtual/canceled commit, swap, lifecycle cleanup, unawakened/other-style exclusion, Shift+SA removed");
        } finally {MinecraftForge.EVENT_BUS.unregister(probe);victim.discard();actor.setShiftKeyDown(false);}
    }
    private static void advance(ServerLevel level,long ticks) {advanceTo(level,level.getGameTime()+ticks);}
    private static void advanceTo(ServerLevel level,long time) {level.getServer().getWorldData().overworldData().setGameTime(time);}
    private static void end(ServerPlayer actor) {SakuraStanceController.tick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END,actor));}
    private static void check(boolean condition,String message) {if(!condition)throw new IllegalStateException(message);}
}
