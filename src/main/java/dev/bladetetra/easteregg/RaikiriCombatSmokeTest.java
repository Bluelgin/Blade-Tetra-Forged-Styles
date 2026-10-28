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
    private static boolean cancel,rewrite,protect;
    private static int checks,actorSlashes,plainSlashes,motions;
    private static double actorDamage,plainDamage;
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void motion(BladeMotionEvent e) {
        if(e.getEntity()!=actor) return;
        motions++;
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
                if(t<2) check(RaikiriFieldService.trailCount(actor.getUUID())==0,"no field before native execution proof");
            }
            check(actorSlashes==5 && actorSlashes==plainSlashes && actorDamage==plainDamage,"native five attacks, parameters and cadence retained");
            check(actor.getZ()>3.5,"native movement is not clamped to former custom distance");
            check(ComboStateRegistry.RAPID_SLASH.get()==nativeRapid,"native node identity unchanged");
            var samples=RaikiriFieldService.samples(actor.getUUID());
            check(!samples.isEmpty() && samples.size()<=4 && actual.containsAll(samples),"only bounded actually observed positions scheduled");
            check(RaikiriFieldService.trailCount(plain.getUUID())==0,"other swords have no lightning");
            check(RaikiriFieldService.charged(actor.getUUID()),"real native swept movement resonates with sword");
            check(rank(actor).getRankPoint(now())==rank(plain).getRankPoint(now()),"no supplemental rank payment");
            advanceTo(114);
            Zombie victim=victim(0,4.5);victims.add(victim);
            float hp=victim.getHealth();long before=rank(actor).getRankPoint(115);int beforeMotions=motions;
            advanceTo(115);
            check(hp-victim.getHealth()>0 && hp-victim.getHealth()<=4.501,"charged sword retains 1.5x bounded pulse despite amplification");
            check(rank(actor).getRankPoint(now())==before && motions==beforeMotions,"extra lightning grants no rank or native combo");
            check(!RaikiriFieldService.charged(actor.getUUID()),"discharge cannot recursively charge");
            hp=victim.getHealth();protect=true;victim.invulnerableTime=17;before=rank(actor).getRankPoint(130);advanceTo(130);protect=false;
            check(victim.getHealth()==hp && victim.invulnerableTime==17 && rank(actor).getRankPoint(now())==before,"protection cancellation preserves rank and immunity");
            advanceTo(221);check(RaikiriFieldService.swordCount(actor.getUUID())==0,"resonance did not refresh sword lifetime");
            // Native landing observer remains independent of rapid observation.
            RaikiriActionObserver.clear(actor.getUUID(),e.getServer());actor.moveTo(0,160,0,0,0);actor.setOnGround(true);
            land();land();land();land();
            check(RaikiriFieldService.swordCount(actor.getUUID())==3,"native cleave one-per-landing and oldest replacement");
            RaikiriActionObserver.playerTick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END,actor));
            check(RaikiriFieldService.swordCount(actor.getUUID())==3,"duplicate landing event cannot plant again");
            // A fresh native action with a discontinuous external position change must fail closed.
            reset(state);state.progressCombo(actor);tick();time(now()+1);actor.setPos(0,160,20);tick();
            check(RaikiriFieldService.swordCount(actor.getUUID())==0 && RaikiriFieldService.trailCount(actor.getUUID())==0,"abnormal teleport clears anchors and observation");
            actor.moveTo(0,160,0,0,0);actor.setOnGround(true);land();actor.getInventory().selected=1;tick();
            check(RaikiriFieldService.swordCount(actor.getUUID())==0,"slot swap cleanup");actor.getInventory().selected=0;
            land();RaikiriActionObserver.dimension(new net.minecraftforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent(actor,net.minecraft.world.level.Level.OVERWORLD,net.minecraft.world.level.Level.NETHER));
            check(RaikiriFieldService.swordCount(actor.getUUID())==0,"dimension cleanup");
            land();RaikiriActionObserver.death(new net.minecraftforge.event.entity.living.LivingDeathEvent(actor,level.damageSources().generic()));
            check(RaikiriFieldService.swordCount(actor.getUUID())==0,"death cleanup");
            land();RaikiriActionObserver.logout(new net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent(actor));
            check(RaikiriFieldService.swordCount(actor.getUUID())==0,"logout cleanup");
            var dual=blade.copy();dual.getOrCreateTag().putString(ModularSlashBladeItem.AWAKENED_SOUL_INSCRIPTION_MODULE+"_material","awakened_soul_inscription/akatsuki_raikiri");
            check(!RaikiriActionObserver.eligible(dual),"secondary Raikiri cannot enable extension");
            var key=state.getSlashArtsKey();reset(state);state.doChargeAction(actor,40);
            check(Objects.equals(state.getSlashArtsKey(),key),"native Slash Arts key untouched");
            GameplayConfig.ENABLE_AWAKENED_V2.set(false);check(!RaikiriActionObserver.eligible(blade),"legacy mode excludes new observer");GameplayConfig.ENABLE_AWAKENED_V2.set(true);
            LogUtils.getLogger().info("RAIKIRI_COMBAT_SMOKE_PASS: {} checks",checks);
        } catch(Throwable failure) {
            LogUtils.getLogger().error("RAIKIRI_COMBAT_SMOKE_FAIL",failure);throw new IllegalStateException("Raikiri native observer smoke failed",failure);
        } finally {
            GameplayConfig.ENABLE_AWAKENED_V2.set(true);victims.forEach(Zombie::discard);
            if(actor!=null) { RaikiriActionObserver.clear(actor.getUUID(),e.getServer());actor.discard(); }
            if(plain!=null) plain.discard();e.getServer().halt(false);
        }
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
