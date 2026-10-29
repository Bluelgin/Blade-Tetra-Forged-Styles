package dev.bladetetra.kyouka;

import com.mojang.logging.LogUtils;
import dev.bladetetra.registry.ModItems;
import dev.bladetetra.item.ModularSlashBladeItem;
import dev.bladetetra.easteregg.KyoukaAwakening;
import dev.bladetetra.combat.InheritedCombatDamage;
import mods.flammpfeil.slashblade.ability.SummonedSwordArts;
import mods.flammpfeil.slashblade.capability.concentrationrank.CapabilityConcentrationRank;
import mods.flammpfeil.slashblade.capability.inputstate.CapabilityInputState;
import mods.flammpfeil.slashblade.event.handler.InputCommandEvent;
import mods.flammpfeil.slashblade.event.BladeMotionEvent;
import mods.flammpfeil.slashblade.entity.EntitySpiralSwords;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import mods.flammpfeil.slashblade.util.AttackHelper;
import mods.flammpfeil.slashblade.util.InputCommand;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/** Actual transformed native API and server events in an opt-in isolated world. No graphical claims. */
@Mod.EventBusSubscriber(modid = "blade_tetra")
public final class KyoukaNativeSmokeTest {
    private static int checks;
    private static Zombie target;
    private static boolean protect, cancelMotion;
    private static float observed, normalObserved;
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void attack(LivingAttackEvent e) {
        if (target == e.getEntity() && !InheritedCombatDamage.isInherited(e.getSource())) normalObserved += e.getAmount();
        if (target == e.getEntity() && InheritedCombatDamage.isInherited(e.getSource())) {
            observed += e.getAmount();
            if (protect) e.setCanceled(true);
        }
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void motion(BladeMotionEvent e) {
        if (cancelMotion && Boolean.getBoolean("blade_tetra.kyoukaNativeSmoke")) e.setCanceled(true);
    }
    @SubscribeEvent public static void started(ServerStartedEvent event) {
        if (!Boolean.getBoolean("blade_tetra.kyoukaNativeSmoke")) return;
        var server = event.getServer(); var level = server.overworld();
        var player = FakePlayerFactory.getMinecraft(level);
        List<ServerPlayer> peers = new ArrayList<>();
        try {
            level.getChunk(0,0); level.getChunk(1,0); level.addNewPlayer(player);
            player.setPos(1,200,1); player.setYRot(-90); player.setNoGravity(true);
            var blade = ModItems.MODULAR_SLASHBLADE.get().createDefaultStack();
            var tag = blade.getOrCreateTag();
            tag.putString(ModularSlashBladeItem.INSCRIPTION_SLOT, ModularSlashBladeItem.AWAKENED_SOUL_INSCRIPTION_MODULE);
            tag.putString(ModularSlashBladeItem.AWAKENED_SOUL_INSCRIPTION_MODULE+"_material", "awakened_soul_inscription/proudsoul_sphere");
            check(KyoukaAwakening.unlockForDebug(blade), "real Kyouka inscription unlock");
            blade.setHoverName(Component.literal("Kyouka smoke")); blade.enchant(Enchantments.POWER_ARROWS,1);
            player.setItemSlot(EquipmentSlot.MAINHAND,blade);
            var state = blade.getCapability(ItemSlashBlade.BLADESTATE).orElseThrow(IllegalStateException::new);
            state.setBroken(false); state.setSealed(false); state.setProudSoulCount(10000);
            target = new Zombie(level); target.setNoAi(true); target.setNoGravity(true); target.setPos(17,200,1);
            target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000); target.setHealth(1000);
            level.addFreshEntity(target); state.setTargetEntityId(target.getId());
            var rank = player.getCapability(CapabilityConcentrationRank.RANK_POINT).orElseThrow(IllegalStateException::new);
            long now = level.getGameTime(); rank.setLastUpdte(now); rank.setRawRankPoint(1600);
            cast(player, false, false); KyoukaMirrorSessions.step();
            check(!player.hasEffect(WaterMirrorEffect.ARMOR.get()), "failed native cast cannot activate armor");
            state.setTargetEntityId(-1);
            cast(player, true, false); KyoukaMirrorSessions.step();
            check(KyoukaMirrorSessions.armored(player), "real native spiral batch activates without locked target");
            check(rank.getRawRankPoint()==1600, "no mirror start fee");
            rank.addRankPoint(player,10);
            check(rank.getRawRankPoint()>1600, "armor preserves native rank gain");
            var session = KyoukaMirrorSessions.STATES.get(player.getUUID());
            int air=player.getAirSupply();
            var breathe=new net.minecraftforge.event.entity.living.LivingBreatheEvent(player,true,1,4,true);
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(breathe);
            check(!breathe.canBreathe(), "land breathing uses native drain branch");
            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.WATER_BREATHING,200));
            breathe=new net.minecraftforge.event.entity.living.LivingBreatheEvent(player,true,1,4,true);
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(breathe);
            check(breathe.canBreathe(), "water breathing preserves native air behavior");
            player.removeEffect(net.minecraft.world.effect.MobEffects.WATER_BREATHING);
            player.setAirSupply(1);
            breathe=new net.minecraftforge.event.entity.living.LivingBreatheEvent(player,true,1,4,true);
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(breathe);
            check(!player.hasEffect(WaterMirrorEffect.ARMOR.get()) && breathe.canBreathe(), "land low air exits before drowning");
            check(player.getAirSupply()==1, "exit does not refill air"); player.setAirSupply(air);
            state.setTargetEntityId(target.getId());
            state.updateComboSeq(player,ComboStateRegistry.COMBO_A1.getId());
            check(session.action!=null, "real committed native action observed");
            var action=session.action; cancelMotion=true;
            state.updateComboSeq(player,ComboStateRegistry.COMBO_A2.getId()); cancelMotion=false;
            check(session.action==action, "cancelled native motion cannot commit");
            state.progressCombo(player,true);
            check(session.action==action, "virtual native routing cannot commit");
            target.invulnerableTime=0; observed=0;
            mods.flammpfeil.slashblade.util.AttackManager.doMeleeAttack(player,target,true,false,1F);
            check(session.last!=null, "successful native managed hit creates one snapshot");
            var last=session.last;
            target.invulnerableTime=0;
            mods.flammpfeil.slashblade.util.AttackManager.doMeleeAttack(player,target,true,false,1F);
            check(session.last==last, "same committed action shares one bounded authorization");
            rank.setLastUpdte(now); rank.setRawRankPoint(1800); rank.addRankPoint(player,10000);
            KyoukaMirrorSessions.step();
            check(session.pending.size()==1 && !session.pending.get(0).water(), "actual SSS promotion defers until completed snapshot");
            var recall=session.pending.get(0);
            KyoukaMirrorSessions.step(); check(session.pending.size()==1,"sustained SSS cannot retrigger");
            KyoukaMirrorDamageService.apply(session,recall);
            check(Math.abs(observed-last.baseline()*.75F)<.01F,"three mirrors share one 75 percent budget");
            float previous=observed; protect=true; KyoukaMirrorDamageService.apply(session,recall); protect=false;
            check(observed>previous,"secondary hit reaches real protection pipeline");
            target.setPos(25,200,1); previous=observed; KyoukaMirrorDamageService.apply(session,recall);
            check(observed==previous,"target outside recorded region misses"); target.setPos(17,200,1);
            // Recasting a live native ring fires it; remove that first ring before asking native code to summon another.
            for (var passenger : List.copyOf(player.getPassengers()))
                if (passenger instanceof EntitySpiralSwords sword) { sword.doFire(); sword.discard(); }
            cast(player,true,false); KyoukaMirrorSessions.step();
            check(KyoukaMirrorSessions.armored(player) && session.pending.isEmpty(),"armor activation cancels pending recall");
            long waterStart=level.getGameTime();
            server.getWorldData().overworldData().setGameTime(waterStart+2);
            state.updateComboSeq(player,ComboStateRegistry.COMBO_A2.getId());
            target.invulnerableTime=0;
            mods.flammpfeil.slashblade.util.AttackManager.doMeleeAttack(player,target,true,false,1F);
            check(session.pending.size()==1 && session.pending.get(0).water(),"armor reserves one bounded water impact");
            var water=session.pending.get(0); observed=0;
            server.getWorldData().overworldData().setGameTime(waterStart+15); KyoukaMirrorSessions.step();
            check(observed==0,"water cannot resolve before fourteen ticks");
            server.getWorldData().overworldData().setGameTime(waterStart+16); KyoukaMirrorSessions.step();
            check(Math.abs(observed-water.slash().baseline()*.40F)<.01F,"fourteen tick water uses forty percent budget");
            check(session.pending.isEmpty(),"water authorization consumed once");
            player.setAirSupply(300); player.setHealth(20); player.invulnerableTime=0;
            player.hurt(player.damageSources().mobAttack(target),10);
            check(Math.abs(player.getHealth()-13)<.01F && player.getAirSupply()==280,"actual final player damage commits thirty percent defense and twenty air");
            int defenseAir=player.getAirSupply(); player.invulnerableTime=0;
            player.hurt(player.damageSources().mobAttack(target),2);
            check(player.getAirSupply()==defenseAir,"second hit cannot bypass defense cooldown");
            var reduction=new WaterMirrorDefenseBudget();
            check(Math.abs(reduction.available(0,20,300,300)-6)<.001F,"full air thirty percent capped at six");
            reduction.commit(0,3); check(reduction.available(59,20,300,300)==0,"defense sixty tick cooldown");
            check(reduction.available(60,20,300,300)==3,"rolling defense remainder authoritative");
            reduction.commit(60,3); check(reduction.available(120,20,300,300)==0,"infinite air cannot exceed rolling cap");
            check(reduction.available(200,20,150,300)==3,"window expiration and half air scale");
            player.setItemSlot(EquipmentSlot.MAINHAND,net.minecraft.world.item.ItemStack.EMPTY);
            KyoukaMirrorSessions.step();
            check(!player.hasEffect(WaterMirrorEffect.ARMOR.get()) && !KyoukaMirrorSessions.STATES.containsKey(player.getUUID()),"weapon swap clears form and scheduled attacks");
            LogUtils.getLogger().info("KYOUKA_NATIVE_SMOKE_PASS: {} checks; actual native casts, commits, managed hits, rank promotion and Forge breathing; no visual claims",checks);
        } catch(Throwable failure) {
            LogUtils.getLogger().error("KYOUKA_NATIVE_SMOKE_FAIL",failure); throw new IllegalStateException(failure);
        } finally {
            cancelMotion=false;protect=false;
            KyoukaMirrorSessions.STATES.clear();
            if(target!=null)target.discard();target=null; player.discard();server.halt(false);
        }
    }
    private static void nativeTick(mods.flammpfeil.slashblade.entity.EntitySlashEffect slash) {
        // ServerLevel increments this outside Entity.tick; isolated smoke must do the same.
        slash.tickCount++; slash.tick();
    }
    private static void cast(ServerPlayer player, boolean held, boolean storm) {
        var commands=EnumSet.noneOf(InputCommand.class);
        if(held)commands.add(InputCommand.M_DOWN);
        if(storm){commands.add(InputCommand.SNEAK);commands.add(InputCommand.BACK);}
        castCommands(player,commands);
    }
    private static void castCommands(ServerPlayer player,EnumSet<InputCommand> commands) {
        var input=player.getCapability(CapabilityInputState.INPUT_STATE).orElseThrow(IllegalStateException::new);
        input.getCommands().clear();input.getCommands().addAll(commands);
        input.getLastPressTimes().put(InputCommand.BACK,player.level().getGameTime()-10);
        input.getLastPressTimes().put(InputCommand.M_DOWN,player.level().getGameTime()-10);
        SummonedSwordArts.getInstance().onInputChange(new InputCommandEvent(player,input,
                EnumSet.noneOf(InputCommand.class),input.getCommands().clone()));
        input.getScheduler().onTick(player);
    }
    private static void check(boolean ok,String message) {
        if(!ok)throw new IllegalStateException(message);checks++;
    }
    private KyoukaNativeSmokeTest() {}
}
