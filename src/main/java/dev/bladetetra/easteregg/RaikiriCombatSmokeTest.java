package dev.bladetetra.easteregg;

import com.mojang.logging.LogUtils;
import dev.bladetetra.BladeTetra;
import dev.bladetetra.combat.BladeStyle;
import dev.bladetetra.combat.ModComboStates;
import dev.bladetetra.config.GameplayConfig;
import dev.bladetetra.item.ModularSlashBladeItem;
import dev.bladetetra.registry.ModItems;
import mods.flammpfeil.slashblade.capability.concentrationrank.CapabilityConcentrationRank;
import mods.flammpfeil.slashblade.capability.inputstate.CapabilityInputState;
import mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState;
import mods.flammpfeil.slashblade.event.BladeMotionEvent;
import mods.flammpfeil.slashblade.event.SlashBladeEvent;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import mods.flammpfeil.slashblade.util.InputCommand;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/** Real native callbacks + vanilla collision movement, controlled clock/friction; not client physics QA. */
@Mod.EventBusSubscriber(modid=BladeTetra.MOD_ID)
public final class RaikiriCombatSmokeTest {
    private static ServerPlayer actor,plain;
    private static ServerLevel level;
    private static boolean cancel,rewrite,protect,rewriteInto;
    private static int checks,actorSlashes,plainSlashes,motions;
    private static double actorDamage,plainDamage;
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void motion(BladeMotionEvent e) {
        if(e.getEntity()!=actor) return;
        motions++;
        if(rewriteInto) e.setCombo(ComboStateRegistry.RAPID_SLASH.getId());
        if(cancel) e.setCanceled(true);
        if(rewrite && e.getCombo().equals(ComboStateRegistry.RAPID_SLASH.getId())) e.setCombo(ComboStateRegistry.COMBO_A1.getId());
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void slash(SlashBladeEvent.DoSlashEvent e) {
        if(!e.getSlashBladeState().getComboSeq().equals(ComboStateRegistry.RAPID_SLASH.getId())) return;
        if(e.getUser()==actor) { actorSlashes++;actorDamage+=e.getDamage(); }
        if(e.getUser()==plain) { plainSlashes++;plainDamage+=e.getDamage(); }
    }
    @SubscribeEvent public static void protection(LivingAttackEvent e) {
        if(protect && e.getSource().getEntity()==actor && SoulLegacyDamageGuard.isSecondary(e.getSource())) e.setCanceled(true);
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void amplify(LivingHurtEvent e) {
        if(Boolean.getBoolean("blade_tetra.raikiriCombatSmoke") && e.getSource().getEntity()==actor
                && SoulLegacyDamageGuard.isSecondary(e.getSource())) e.setAmount(e.getAmount()*1000);
    }
    @SubscribeEvent public static void started(ServerStartedEvent e) {
        if(!Boolean.getBoolean("blade_tetra.raikiriCombatSmoke")) return;
        List<Zombie> victims=new ArrayList<>();
        try {
            level=e.getServer().overworld();
            actor=FakePlayerFactory.getMinecraft(level);
            plain=FakePlayerFactory.get(level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"NativeRapid"));
            level.addNewPlayer(actor);level.addNewPlayer(plain);
            for(int x=-5;x<=6;x++) for(int z=-5;z<=30;z++) {
                level.setBlock(new BlockPos(x,159,z),Blocks.STONE.defaultBlockState(),3);
                for(int y=160;y<=163;y++) level.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),3);
            }
            ((ServerLevelData)level.getLevelData()).setGameTime(100);
            ItemStack blade=blade(true),ordinary=blade(false);
            actor.setItemSlot(EquipmentSlot.MAINHAND,blade);plain.setItemSlot(EquipmentSlot.MAINHAND,ordinary);
            actor.moveTo(0,160,0,0,0);plain.moveTo(3,160,0,0,0);actor.setOnGround(true);plain.setOnGround(true);
            var state=state(actor);var otherState=state(plain);
            inputs(actor);inputs(plain);rank(actor).setRawRankPoint(500);rank(actor).setLastUpdte(100);
            rank(plain).setRawRankPoint(500);rank(plain).setLastUpdte(100);
            var nativeRapid=ComboStateRegistry.RAPID_SLASH.get();
            check(!ComboStateRegistry.REGISTRY.get().containsKey(new net.minecraft.resources.ResourceLocation("blade_tetra","awakened_raikiri_rapid")),"no replacement combo registered");
            for(BladeStyle style:BladeStyle.values()) {
                state.setComboRoot(ModComboStates.getRoot(style));reset(state);
                check(state.progressCombo(actor,true).equals(ComboStateRegistry.RAPID_SLASH.getId()),"native virtual route unchanged: "+style);
                check(RaikiriFieldService.trailCount(actor.getUUID())==0,"virtual progress cannot emit");
            }
            state.setComboRoot(ModComboStates.getRoot(BladeStyle.STANDARD));otherState.setComboRoot(ModComboStates.getRoot(BladeStyle.STANDARD));
            reset(state);cancel=true;state.progressCombo(actor);cancel=false;tick();
            check(RaikiriFieldService.trailCount(actor.getUUID())==0,"cancelled native commit emits nothing");
            reset(state);rewrite=true;state.progressCombo(actor);rewrite=false;tick();
            check(state.getComboSeq().equals(ComboStateRegistry.COMBO_A1.getId()) && RaikiriFieldService.trailCount(actor.getUUID())==0,"rewritten native commit emits nothing");
            reset(state);net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new BladeMotionEvent(actor,ComboStateRegistry.RAPID_SLASH.getId()));tick();
            check(RaikiriFieldService.trailCount(actor.getUUID())==0,"uncommitted motion event emits nothing");
            RaikiriActionObserver.clear(actor.getUUID(),e.getServer());
            rewriteInto=true;state.updateComboSeq(actor,ComboStateRegistry.COMBO_A1.getId());rewriteInto=false;
            nativeRapid.tickAction(actor);actor.move(MoverType.SELF,actor.getDeltaMovement().add(0,-.08,0));tick();
            check(state.getComboSeq().equals(ComboStateRegistry.RAPID_SLASH.getId())
                    && RaikiriFieldService.trailCount(actor.getUUID())==1,"third-party rewrite into native rapid stays observable without rerouting");
            RaikiriActionObserver.clear(actor.getUUID(),e.getServer());actor.moveTo(0,160,0,0,0);actor.setDeltaMovement(Vec3.ZERO);actor.setOnGround(true);
            var session=RaikiriActionObserver.session(actor);
            actor.setPos(0,160,4);RaikiriFieldService.plant(actor,session);actor.setPos(0,160,0);
            check(RaikiriFieldService.swordCount(actor.getUUID())==1,"ground sword fixture owned by observer session");
            reset(state);reset(otherState);state.progressCombo(actor);otherState.progressCombo(plain);
            check(state.getComboSeq().equals(ComboStateRegistry.RAPID_SLASH.getId()),"real native rapid retained");
            check(actor.position().equals(new Vec3(0,160,0)),"observer does not teleport on commit");
            List<Vec3> actual=new ArrayList<>();actual.add(actor.position());
            for(int t=0;t<=10;t++) {
                if(t>0) time(now()+1);
                nativeRapid.tickAction(actor);nativeRapid.tickAction(plain);
                check(actor.getDeltaMovement().equals(plain.getDeltaMovement()),"unmodified native velocity tick "+t);
                // Both actors use the same vanilla move/collision implementation.
                actor.move(MoverType.SELF,actor.getDeltaMovement().add(0,-.08,0));plain.move(MoverType.SELF,plain.getDeltaMovement().add(0,-.08,0));
                check(Math.abs(actor.getZ()-plain.getZ())<1e-6,"native actual travel unchanged tick "+t);
                actual.add(actor.position());
                actor.setDeltaMovement(actor.getDeltaMovement().scale(.6));plain.setDeltaMovement(plain.getDeltaMovement().scale(.6));
                tick();
                if(t<2) check(RaikiriFieldService.trailCount(actor.getUUID())==1,"committed actual native movement needs no hit or copied attack callback");
            }
            check(actorSlashes==5 && actorSlashes==plainSlashes && actorDamage==plainDamage,"native five attacks, parameters and cadence retained");
            check(actor.getZ()>3.5,"native movement is not clamped to former custom distance");
            check(ComboStateRegistry.RAPID_SLASH.get()==nativeRapid,"native node identity unchanged");
            var samples=RaikiriFieldService.samples(actor.getUUID());
            check(!samples.isEmpty() && samples.size()<=4 && actual.containsAll(samples),"only bounded actually observed positions scheduled");
            tick();check(RaikiriFieldService.samples(actor.getUUID()).equals(samples)
                    && RaikiriFieldService.trailCount(actor.getUUID())==1,"repeated observation does not duplicate native run");
            check(RaikiriFieldService.trailCount(plain.getUUID())==0,"other swords have no lightning");
            check(RaikiriFieldService.charged(actor.getUUID()),"real native swept movement resonates with sword");
            check(rank(actor).getRankPoint(now())==rank(plain).getRankPoint(now()),"no supplemental rank payment");
            advanceTo(119);
            Zombie victim=victim(1.5,4.5);victims.add(victim);
            float hp=victim.getHealth();long before=rank(actor).getRankPoint(120);int beforeMotions=motions;
            Vec3 moving=new Vec3(.12,.2,.07);victim.setOnGround(true);victim.setDeltaMovement(moving);
            advanceTo(120);
            check(victim.getDeltaMovement().equals(moving),"real discharge hurt preserves existing velocity without its own knockback");
            check(hp-victim.getHealth()>0 && hp-victim.getHealth()<=4.501,"charged sword retains 1.5x bounded pulse despite amplification");
            check(rank(actor).getRankPoint(now())==before && motions==beforeMotions,"extra lightning grants no rank or native combo");
            check(!RaikiriFieldService.charged(actor.getUUID()),"discharge cannot recursively charge");
            hp=victim.getHealth();protect=true;victim.invulnerableTime=17;before=rank(actor).getRankPoint(140);advanceTo(140);protect=false;
            check(victim.getHealth()==hp && victim.invulnerableTime==17 && rank(actor).getRankPoint(now())==before,"protection cancellation preserves rank and immunity");
            advanceTo(299);check(RaikiriFieldService.swordCount(actor.getUUID())==1,"sword still present before 200 ticks");
            advanceTo(300);check(RaikiriFieldService.swordCount(actor.getUUID())==0,"resonance did not refresh sword lifetime");
            // Real hurt pipeline: only discharge cancels knockback, never trail or ordinary hurt.
            reset(state);victim.invulnerableTime=0;victim.setDeltaMovement(Vec3.ZERO);
            check(RaikiriFieldService.damage(actor,victim,1,RaikiriFieldService.DamageKind.TRAIL_STRIKE)
                    && victim.getDeltaMovement().lengthSqr()>0,"trail strike retains vanilla knockback");
            victim.invulnerableTime=0;victim.setDeltaMovement(Vec3.ZERO);
            check(victim.hurt(level.damageSources().playerAttack(actor),1)
                    && victim.getDeltaMovement().lengthSqr()>0,"ordinary attack knockback survives discharge scope cleanup");
            victim.discard();
            // Late sample at maximum observation time must survive the old +12 expiry.
            RaikiriActionObserver.clear(actor.getUUID(),e.getServer());
            var lateSession=RaikiriActionObserver.session(actor);UUID lateAction=UUID.randomUUID();long start=now();
            Vec3 origin=actor.position();
            RaikiriFieldService.segment(actor,lateSession,lateAction,origin,origin.add(.4,0,0),start,start);
            advanceTo(start+20);
            RaikiriFieldService.segment(actor,lateSession,lateAction,origin.add(.4,0,0),origin.add(.8,0,0),start+19,start+20);
            advanceTo(start+40);
            Zombie tail=victim(actor.getX()+1.2,actor.getZ());victims.add(tail);
            RaikiriFieldService.segment(actor,lateSession,lateAction,origin.add(.8,0,0),origin.add(1.2,0,0),start+39,start+40);
            RaikiriFieldService.finish(lateAction);float tailHp=tail.getHealth();
            advanceTo(start+61);
            check(tail.getHealth()==tailHp && RaikiriFieldService.trailCount(actor.getUUID())==1,"last sample waits 22 ticks and survives old expiry");
            advanceTo(start+62);
            check(tail.getHealth()<tailHp && RaikiriFieldService.trailCount(actor.getUUID())==0,"last real sample settles before cleanup");tail.discard();
            // Longer residence keeps the original whole-life budget, including empty later rounds.
            RaikiriActionObserver.clear(actor.getUUID(),e.getServer());actor.moveTo(0,160,0,0,0);actor.setOnGround(true);
            long born=now();RaikiriFieldService.plant(actor,RaikiriActionObserver.session(actor));
            Zombie resident=victim(0,1);victims.add(resident);float initial=resident.getHealth();
            advanceTo(born+19);check(resident.getHealth()==initial,"no discharge before 20 ticks");
            advanceTo(born+20);check(Math.abs(initial-resident.getHealth()-3)<.001,"20 tick discharge budget unchanged");
            advanceTo(born+199);check(Math.abs(initial-resident.getHealth()-12)<.001
                    && RaikiriFieldService.swordCount(actor.getUUID())==1,"200 tick visual residence cannot exceed lifetime damage 12");
            advanceTo(born+200);check(RaikiriFieldService.swordCount(actor.getUUID())==0,"natural 200 tick expiry");resident.discard();
            // A later native release owns a distinct stream, still with zero extension routing.
            RaikiriActionObserver.clear(actor.getUUID(),e.getServer());
            actor.moveTo(0,160,0,0,0);actor.setOnGround(true);actor.setDeltaMovement(Vec3.ZERO);
            long secondStamp=now();state.progressCombo(actor);
            check(state.getComboSeq().equals(ComboStateRegistry.RAPID_SLASH.getId()) && state.getLastActionTime()==secondStamp,
                    "consecutive native release commits its own native timestamp");
            for(int t=0;t<4;t++) {
                if(t>0) time(now()+1);nativeRapid.tickAction(actor);
                actor.move(MoverType.SELF,actor.getDeltaMovement().add(0,-.08,0));
                actor.setDeltaMovement(actor.getDeltaMovement().scale(.6));tick();
            }
            check(RaikiriFieldService.trailCount(actor.getUUID())==1,"later native release has exactly one independent stream");
            // Native landing observer remains independent of rapid observation.
            RaikiriActionObserver.clear(actor.getUUID(),e.getServer());actor.moveTo(0,160,0,0,0);actor.setOnGround(true);
            land();land();land();land();
            check(RaikiriFieldService.swordCount(actor.getUUID())==3,"native cleave one-per-landing and oldest replacement");
            RaikiriActionObserver.playerTick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END,actor));
            check(RaikiriFieldService.swordCount(actor.getUUID())==3,"duplicate landing event cannot plant again");
            plain.setItemSlot(EquipmentSlot.MAINHAND,blade(true));plain.moveTo(3,160,0,0,0);plain.setOnGround(true);
            RaikiriFieldService.plant(plain,RaikiriActionObserver.session(plain));
            check(RaikiriFieldService.swordCount(plain.getUUID())==1,"second awakened owner has isolated fields");
            // A fresh native action with a discontinuous external position change must fail closed.
            reset(state);state.progressCombo(actor);tick();time(now()+1);actor.setPos(0,160,20);tick();
            check(RaikiriFieldService.swordCount(actor.getUUID())==0 && RaikiriFieldService.trailCount(actor.getUUID())==0,"abnormal teleport clears anchors and observation");
            check(RaikiriFieldService.swordCount(plain.getUUID())==1,"clearing teleported owner preserves other owner");
            RaikiriActionObserver.logout(new net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent(plain));
            actor.moveTo(0,160,0,0,0);actor.setOnGround(true);land();actor.getInventory().selected=1;tick();
            check(RaikiriFieldService.swordCount(actor.getUUID())==0,"slot swap cleanup");actor.getInventory().selected=0;
            land();RaikiriActionObserver.dimension(new net.minecraftforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent(actor,net.minecraft.world.level.Level.OVERWORLD,net.minecraft.world.level.Level.NETHER));
            check(RaikiriFieldService.swordCount(actor.getUUID())==0,"dimension cleanup");
            land();RaikiriActionObserver.death(new net.minecraftforge.event.entity.living.LivingDeathEvent(actor,level.damageSources().generic()));
            check(RaikiriFieldService.swordCount(actor.getUUID())==0,"death cleanup");
            land();RaikiriActionObserver.logout(new net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent(actor));
            check(RaikiriFieldService.swordCount(actor.getUUID())==0,"logout cleanup");
            var dual=blade.copy();dual.getOrCreateTag().putString(ModularSlashBladeItem.AWAKENED_SOUL_INSCRIPTION_MODULE+"_material","awakened_soul_inscription/akatsuki_raikiri");
            dual.getOrCreateTag().putBoolean(AkatsukiAwakening.TAG_UNLOCKED,true);
            dual.getOrCreateTag().putInt(SoulSkillState.SELECTED,SoulLegacyState.Legacy.AKATSUKI.ordinal());
            check(!RaikiriActionObserver.eligible(dual),"unselected secondary Raikiri cannot enable extension");
            var key=state.getSlashArtsKey();reset(state);state.doChargeAction(actor,40);
            check(Objects.equals(state.getSlashArtsKey(),key),"native Slash Arts key untouched");
            GameplayConfig.ENABLE_AWAKENED_V2.set(false);check(!RaikiriActionObserver.eligible(blade),"legacy mode excludes new observer");GameplayConfig.ENABLE_AWAKENED_V2.set(true);
            victims.forEach(Zombie::discard);
            armorChecks();
            LogUtils.getLogger().info("RAIKIRI_COMBAT_SMOKE_PASS: {} checks",checks);
        } catch(Throwable failure) {
            LogUtils.getLogger().error("RAIKIRI_COMBAT_SMOKE_FAIL",failure);throw new IllegalStateException("Raikiri native observer smoke failed",failure);
        } finally {
            GameplayConfig.ENABLE_AWAKENED_V2.set(true);victims.forEach(Zombie::discard);
            if(actor!=null) { RaikiriActionObserver.clear(actor.getUUID(),e.getServer());actor.discard(); }
            if(plain!=null) plain.discard();e.getServer().halt(false);
        }
    }
    private static void armorChecks() {
        RaikiriActionObserver.clear(actor.getUUID(),level.getServer());
        actor.setItemSlot(EquipmentSlot.MAINHAND,blade(true));actor.moveTo(0,160,0,0,0);
        reset(state(actor));
        var armor=new RaikiriThunderArmor();long t=now();
        double speed=actor.getAttributeValue(Attributes.MOVEMENT_SPEED);
        rank(actor).setLastUpdte(t);rank(actor).setRawRankPoint(1199);armor.start(actor);
        check(!armor.active(),"armor rejects below A");
        rank(actor).setRawRankPoint(1600);armor.start(actor);
        check(armor.active() && rank(actor).getRawRankPoint()==1540,"armor start costs once");
        check(actor.hasEffect(RaikiriThunderArmorEffect.ARMOR.get()),"armor is a real vanilla effect");
        check(Math.abs(actor.getAttributeValue(Attributes.MOVEMENT_SPEED)-speed*1.2)<1e-7,
                "vanilla buff applies exactly twenty percent speed");
        check(actor.getEffect(RaikiriThunderArmorEffect.ARMOR.get()).showIcon()
                && actor.getEffect(RaikiriThunderArmorEffect.ARMOR.get()).getDuration()==160,
                "eight second icon and countdown exposed to vanilla HUD");
        armor.start(actor);check(rank(actor).getRawRankPoint()==1540,"recast does not charge or stack");
        check(actor.getEffect(RaikiriThunderArmorEffect.ARMOR.get()).getDuration()==160
                && Math.abs(actor.getAttributeValue(Attributes.MOVEMENT_SPEED)-speed*1.2)<1e-7,
                "recast cannot amplify speed");
        var a=victim(1,0);var b=victim(3.5,0);var c=victim(5,0);
        try {
            level.setWeatherParameters(0,1000,true,false);level.setRainLevel(1);
            check(level.isRainingAt(a.blockPosition()),"test enemies exposed to rain");
            ((ServerLevelData)level.getLevelData()).setGameTime(t+20);
            rank(actor).setLastUpdte(t+20);float health=c.getHealth();
            armor.tick(actor);
            check(rank(actor).getRawRankPoint()==1510,"armor round fee and no lightning rank gain");
            check(c.getHealth()<health && health-c.getHealth()<=1,"rain conducts once to enemy beyond primary range");
            level.setBlock(new BlockPos(5,164,0),Blocks.STONE.defaultBlockState(),3);
            ((ServerLevelData)level.getLevelData()).setGameTime(t+40);health=c.getHealth();armor.tick(actor);
            check(c.getHealth()==health,"roof blocks rain conduction");
            level.setBlock(new BlockPos(5,164,0),Blocks.AIR.defaultBlockState(),3);
            level.setRainLevel(0);
            ((ServerLevelData)level.getLevelData()).setGameTime(t+60);armor.tick(actor);
            check(c.getHealth()==health,"clear weather has no conduction");
            armor.stop(actor);check(!armor.active() && !actor.hasEffect(RaikiriThunderArmorEffect.ARMOR.get()),"armor cleanup removes speed");
            check(Math.abs(actor.getAttributeValue(Attributes.MOVEMENT_SPEED)-speed)<1e-7,"effect removal restores original speed");
            rank(actor).setRawRankPoint(1600);armor.start(actor);
            ((ServerLevelData)level.getLevelData()).setGameTime(t+220);armor.tick(actor);
            check(!armor.active(),"armor expires at eight seconds");
            rank(actor).setLastUpdte(t+220);rank(actor).setRawRankPoint(1200);armor.start(actor);
            rank(actor).setRawRankPoint(899);armor.tick(actor);
            check(!armor.active(),"below B ends armor");
            rank(actor).setRawRankPoint(1600);armor.start(actor);
            rank(actor).setRawRankPoint(925);time(now()+20);rank(actor).setLastUpdte(now());armor.tick(actor);
            check(!armor.active() && rank(actor).getRawRankPoint()==925,"unaffordable pulse ends before crossing B floor or charging");
            rank(actor).setRawRankPoint(1600);armor.start(actor);
            actor.removeEffect(RaikiriThunderArmorEffect.ARMOR.get());armor.tick(actor);
            check(!armor.active() && Math.abs(actor.getAttributeValue(Attributes.MOVEMENT_SPEED)-speed)<1e-7,"dispelling buff ends its server discharge");
            GameplayConfig.ENABLE_AWAKENED_RANK_COSTS.set(false);
            rank(actor).setRawRankPoint(1600);rank(actor).setLastUpdte(now());
            armor.start(actor);check(armor.active() && rank(actor).getRawRankPoint()==1600,"rank-cost configuration honored");
            time(now()+20);rank(actor).setLastUpdte(now());armor.tick(actor);
            check(rank(actor).getRawRankPoint()==1600,"free-cost pulse does not debit rank");armor.stop(actor);
            GameplayConfig.ENABLE_AWAKENED_RANK_COSTS.set(true);
            rank(actor).setRawRankPoint(1600);inputs(actor);reset(state(actor));
            cancel=true;state(actor).progressCombo(actor);cancel=false;tick();
            check(!actor.hasEffect(RaikiriThunderArmorEffect.ARMOR.get()) && rank(actor).getRawRankPoint()==1600,"cancelled native action cannot activate or charge armor");
            reset(state(actor));rewrite=true;state(actor).progressCombo(actor);rewrite=false;tick();
            check(!actor.hasEffect(RaikiriThunderArmorEffect.ARMOR.get()),"rewritten native action cannot activate armor");
            reset(state(actor));net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new BladeMotionEvent(actor,ComboStateRegistry.RAPID_SLASH.getId()));tick();
            check(!actor.hasEffect(RaikiriThunderArmorEffect.ARMOR.get()),"uncommitted motion cannot activate armor");
            actor.getCapability(CapabilityInputState.INPUT_STATE).ifPresent(i->i.getCommands().clear());
            state(actor).updateComboSeq(actor,ComboStateRegistry.RAPID_SLASH.getId());tick();
            check(!actor.hasEffect(RaikiriThunderArmorEffect.ARMOR.get()),"native motion without sneak-forward-rightclick cannot activate armor");
            time(now()+1);rank(actor).setLastUpdte(now());inputs(actor);reset(state(actor));
            state(actor).updateComboSeq(actor,ComboStateRegistry.RAPID_SLASH.getId());tick();
            check(RaikiriActionObserver.session(actor).armor.active(),"actual committed native rapid activates armor");
            check(rank(actor).getRawRankPoint()==1540,"native activation charges once");
            tick();check(rank(actor).getRawRankPoint()==1540,"duplicate observer does not charge twice");
            actor.setItemSlot(EquipmentSlot.MAINHAND,blade(true));tick();
            check(!actor.hasEffect(RaikiriThunderArmorEffect.ARMOR.get()),
                    "switch blade removes speed immediately");
            rank(actor).setRawRankPoint(1600);time(now()+1);rank(actor).setLastUpdte(now());
            reset(state(actor));state(actor).updateComboSeq(actor,ComboStateRegistry.RAPID_SLASH.getId());tick();
            check(actor.hasEffect(RaikiriThunderArmorEffect.ARMOR.get()),"new eligible blade can activate independently");
            RaikiriActionObserver.dimension(new net.minecraftforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent(actor,net.minecraft.world.level.Level.OVERWORLD,net.minecraft.world.level.Level.NETHER));
            check(!actor.hasEffect(RaikiriThunderArmorEffect.ARMOR.get()),"dimension change removes vanilla armor effect");
            var session=RaikiriActionObserver.session(actor);session.armor.start(actor);
            actor.getInventory().selected=1;tick();actor.getInventory().selected=0;
            check(!actor.hasEffect(RaikiriThunderArmorEffect.ARMOR.get()),"slot switch removes vanilla armor effect");
            session=RaikiriActionObserver.session(actor);session.armor.start(actor);
            RaikiriActionObserver.death(new net.minecraftforge.event.entity.living.LivingDeathEvent(actor,level.damageSources().generic()));
            check(!actor.hasEffect(RaikiriThunderArmorEffect.ARMOR.get()),"death removes armor effect and speed");
            actor.addEffect(new net.minecraft.world.effect.MobEffectInstance(RaikiriThunderArmorEffect.ARMOR.get(),160));
            RaikiriActionObserver.login(new net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent(actor));
            check(!actor.hasEffect(RaikiriThunderArmorEffect.ARMOR.get()),"reconnect cannot retain orphaned saved armor");
            var fusion=blade(true);var tag=fusion.getOrCreateTag();
            tag.putString(ModularSlashBladeItem.AWAKENED_SOUL_INSCRIPTION_MODULE+"_material","awakened_soul_inscription/akatsuki_raikiri");
            tag.putBoolean(AkatsukiAwakening.TAG_UNLOCKED,true);
            tag.putInt(SoulSkillState.SELECTED,SoulLegacyState.Legacy.RAIKIRI.ordinal());
            actor.setItemSlot(EquipmentSlot.MAINHAND,fusion);
            rank(actor).setLastUpdte(now());rank(actor).setRawRankPoint(1600);
            session=RaikiriActionObserver.session(actor);session.armor.start(actor);
            check(actor.hasEffect(RaikiriThunderArmorEffect.ARMOR.get()),"secondary selected Raikiri supports real armor buff");
            SoulSwitchService.tick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END,actor));
            SoulSwitchService.request(actor,new dev.bladetetra.network.SoulSwitchPacket(
                    actor.getInventory().selected,tag.getUUID(SoulSkillState.BLADE_ID),SoulLegacyState.Legacy.RAIKIRI.ordinal()));
            check(SoulSkillState.selected(fusion)==SoulLegacyState.Legacy.AKATSUKI
                    && !actor.hasEffect(RaikiriThunderArmorEffect.ARMOR.get())
                    && Math.abs(actor.getAttributeValue(Attributes.MOVEMENT_SPEED)-speed)<1e-7,
                    "real soul-switch request ends armor and restores speed");
        } finally { GameplayConfig.ENABLE_AWAKENED_RANK_COSTS.set(true);armor.stop(actor);RaikiriActionObserver.clear(actor.getUUID(),level.getServer());a.discard();b.discard();c.discard();level.setWeatherParameters(1000,0,false,false);level.setRainLevel(0); }
    }
    private static void inputs(ServerPlayer p) { p.getCapability(CapabilityInputState.INPUT_STATE).ifPresent(input->{input.getCommands().clear();input.getCommands().addAll(EnumSet.of(InputCommand.SNEAK,InputCommand.FORWARD,InputCommand.R_CLICK));}); }
    private static ISlashBladeState state(ServerPlayer p) { return p.getMainHandItem().getCapability(ModularSlashBladeItem.BLADESTATE).orElseThrow(IllegalStateException::new); }
    private static mods.flammpfeil.slashblade.capability.concentrationrank.IConcentrationRank rank(ServerPlayer p) { return p.getCapability(CapabilityConcentrationRank.RANK_POINT).orElseThrow(IllegalStateException::new); }
    private static void reset(ISlashBladeState s) { s.setComboSeq(ComboStateRegistry.NONE.getId());s.setLastActionTime(now()-100); }
    private static long now() { return level.getGameTime(); }
    private static void time(long at) { ((ServerLevelData)level.getLevelData()).setGameTime(at); }
    private static void tick() {
        // Stand in only for native inventory timeout processing in FakePlayer;
        // production observation must never call this state-mutating resolver.
        for(ServerPlayer p:new ServerPlayer[]{actor,plain}) if(p!=null)
            p.getMainHandItem().getCapability(ModularSlashBladeItem.BLADESTATE).ifPresent(s->s.resolvCurrentComboState(p));
        RaikiriActionObserver.tick(new TickEvent.ServerTickEvent(TickEvent.Phase.END,()->true,level.getServer()));
    }
    private static void advanceTo(long at) { while(now()<at) { time(now()+1);tick(); } }
    private static void land() {
        var s=state(actor);actor.setOnGround(false);s.updateComboSeq(actor,ComboStateRegistry.AERIAL_CLEAVE.getId());
        RaikiriActionObserver.playerTick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END,actor));
        s.updateComboSeq(actor,ComboStateRegistry.AERIAL_CLEAVE_LOOP.getId());actor.setOnGround(true);ComboStateRegistry.AERIAL_CLEAVE_LOOP.get().tickAction(actor);
        RaikiriActionObserver.playerTick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END,actor));
    }
    private static Zombie victim(double x,double z) {
        var target=new Zombie(EntityType.ZOMBIE,level);target.setNoAi(true);target.setNoGravity(true);target.setPos(x,160,z);
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);target.getAttribute(Attributes.ARMOR).setBaseValue(0);target.setHealth(200);level.addFreshEntity(target);return target;
    }
    private static ItemStack blade(boolean awakened) {
        var stack=ModItems.MODULAR_SLASHBLADE.get().createDefaultStack();var tag=stack.getOrCreateTag();
        if(awakened) { tag.putString(ModularSlashBladeItem.INSCRIPTION_SLOT,ModularSlashBladeItem.AWAKENED_SOUL_INSCRIPTION_MODULE);tag.putString(ModularSlashBladeItem.AWAKENED_SOUL_INSCRIPTION_MODULE+"_material",SoulLegacyState.RAIKIRI_VARIANT);tag.putBoolean(BladeLegacyEasterEggs.TAG_RAIKIRI_UNLOCKED,true); }
        stack.getCapability(ModularSlashBladeItem.BLADESTATE).ifPresent(s->{s.setBroken(false);s.setSealed(false);});return stack;
    }
    private static void check(boolean pass,String description) { checks++;if(!pass)throw new IllegalStateException(description); }
    private RaikiriCombatSmokeTest() {}
}
