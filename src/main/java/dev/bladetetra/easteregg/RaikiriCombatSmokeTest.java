package dev.bladetetra.easteregg;

import com.mojang.logging.LogUtils;
import dev.bladetetra.BladeTetra;
import dev.bladetetra.combat.BladeStyle;
import dev.bladetetra.combat.ModComboStates;
import dev.bladetetra.config.GameplayConfig;
import dev.bladetetra.item.ModularSlashBladeItem;
import dev.bladetetra.registry.ModItems;
import mods.flammpfeil.slashblade.SlashBlade;
import mods.flammpfeil.slashblade.capability.concentrationrank.CapabilityConcentrationRank;
import mods.flammpfeil.slashblade.capability.inputstate.CapabilityInputState;
import mods.flammpfeil.slashblade.entity.EntitySlashEffect;
import mods.flammpfeil.slashblade.event.BladeMotionEvent;
import mods.flammpfeil.slashblade.event.SlashBladeEvent;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import mods.flammpfeil.slashblade.util.InputCommand;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.EnumSet;
import java.util.UUID;

/** Opt-in dedicated Forge/native capability smoke. Controlled time/ground handoff, no visual claims. */
@Mod.EventBusSubscriber(modid=BladeTetra.MOD_ID)
public final class RaikiriCombatSmokeTest {
    private static ServerPlayer actor;
    private static boolean cancelMotion,rewriteMotion,protect;
    private static int checks;
    /** Forge FakePlayer's default connection intentionally NO-OPs teleport.
     * Use vanilla teleport positioning with only socket writes suppressed. */
    private static final class PositioningConnection extends net.minecraft.server.network.ServerGamePacketListenerImpl {
        PositioningConnection(net.minecraft.server.MinecraftServer server,ServerPlayer player) {
            super(server,new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND),player);
        }
        @Override public void send(net.minecraft.network.protocol.Packet<?> packet) { }
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void motion(BladeMotionEvent e) {
        if(e.getEntity()!=actor || !RaikiriRapidCombo.isThunderCombo(e.getCombo())) return;
        if(cancelMotion) e.setCanceled(true);
        if(rewriteMotion) e.setCombo(ComboStateRegistry.COMBO_A1.getId());
    }
    @SubscribeEvent public static void protection(LivingAttackEvent e) {
        if(protect && e.getSource().getEntity()==actor && SoulLegacyDamageGuard.isSecondary(e.getSource())) e.setCanceled(true);
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void amplify(LivingHurtEvent e) {
        if(actor!=null && Boolean.getBoolean("blade_tetra.raikiriCombatSmoke")
                && e.getSource().getEntity()==actor && SoulLegacyDamageGuard.isSecondary(e.getSource()))
            e.setAmount(e.getAmount()*1000);
    }
    public static final class LateProtection {
        @SubscribeEvent(priority=EventPriority.LOWEST) public void damage(LivingDamageEvent e) {
            if(e.getSource().getEntity()==actor) e.setCanceled(true);
        }
    }
    @SubscribeEvent public static void started(ServerStartedEvent e) {
        if(!Boolean.getBoolean("blade_tetra.raikiriCombatSmoke")) return;
        ServerLevel level=e.getServer().overworld();
        Zombie target=null;
        java.util.List<Zombie> extraTargets=new java.util.ArrayList<>();
        try {
            actor=FakePlayerFactory.getMinecraft(level);level.addNewPlayer(actor);
            var noTeleport=actor.connection;
            var positioning=new PositioningConnection(e.getServer(),actor);actor.connection=positioning;
            for(int x=-6;x<24;x++) for(int z=-5;z<=5;z++) {
                level.setBlock(new BlockPos(x,159,z),Blocks.STONE.defaultBlockState(),3);
                for(int y=160;y<=164;y++) level.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),3);
            }
            time(level,100);
            ItemStack blade=blade();actor.setItemSlot(EquipmentSlot.MAINHAND,blade);
            actor.moveTo(0,160,0,-90,0);actor.setOnGround(true);
            var state=blade.getCapability(ModularSlashBladeItem.BLADESTATE).orElseThrow(IllegalStateException::new);
            state.setBroken(false);state.setSealed(false);state.setComboRoot(ModComboStates.getRoot(BladeStyle.STANDARD));
            var input=actor.getCapability(CapabilityInputState.INPUT_STATE).orElseThrow(IllegalStateException::new);
            input.getCommands().clear();input.getCommands().addAll(EnumSet.of(InputCommand.SNEAK,InputCommand.FORWARD,InputCommand.R_CLICK));
            var rank=actor.getCapability(CapabilityConcentrationRank.RANK_POINT).orElseThrow(IllegalStateException::new);
            rank.setRawRankPoint(900);rank.setLastUpdte(level.getGameTime());
            state.setComboSeq(ComboStateRegistry.NONE.getId());state.setLastActionTime(0);
            for(BladeStyle style:BladeStyle.values()) {
                state.setComboRoot(ModComboStates.getRoot(style));state.setComboSeq(ComboStateRegistry.NONE.getId());state.setLastActionTime(0);
                check(RaikiriRapidCombo.isThunderCombo(state.progressCombo(actor,true)),"native grounded directional root: "+style);
                var unrelated=new SlashBladeEvent.NextComboEvent(blade,state,actor,ComboStateRegistry.COMBO_A1.getId());
                RaikiriRapidCombo.onNextCombo(unrelated);
                check(unrelated.getNextCombo().equals(ComboStateRegistry.COMBO_A1.getId()),"explicit normal combo preserved: "+style);
            }
            state.setComboRoot(ModComboStates.getRoot(BladeStyle.STANDARD));state.setComboSeq(ComboStateRegistry.NONE.getId());state.setLastActionTime(0);
            var before=actor.position();long points=rank.getRankPoint(level.getGameTime());
            check(RaikiriRapidCombo.isThunderCombo(state.progressCombo(actor,true)),"native virtual route selects upgrade");
            check(actor.position().equals(before) && rank.getRankPoint(level.getGameTime())==points,"virtual route has no debit or motion");
            cancelMotion=true;state.progressCombo(actor);cancelMotion=false;
            check(actor.position().equals(before) && rank.getRankPoint(level.getGameTime())==points,"cancelled native commit is free");
            rewriteMotion=true;state.progressCombo(actor);rewriteMotion=false;
            check(actor.position().equals(before) && rank.getRankPoint(level.getGameTime())==points,"rewritten native commit is free");
            actor.connection=noTeleport;state.setComboSeq(ComboStateRegistry.NONE.getId());state.setLastActionTime(0);
            state.progressCombo(actor);
            check(actor.position().equals(before) && rank.getRankPoint(level.getGameTime())==points
                    && RaikiriFieldService.trailCount(actor.getUUID())==0,"rejected server relocation refunds and creates no trail");
            actor.connection=positioning;
            state.setComboSeq(ComboStateRegistry.NONE.getId());state.setLastActionTime(0);
            state.progressCombo(actor);
            check(RaikiriRapidCombo.isThunderCombo(state.getComboSeq()),"real native combo committed");
            check(Math.abs(actor.getX()-3.5)<0.01,"exact collision-free 3.5-block step");
            check(rank.getRankPoint(level.getGameTime())==points-75,"real native rank debit once");
            check(!RaikiriStepController.free(actor),"inputs alone cannot authorize followup");
            check(RaikiriFieldService.trailCount(actor.getUUID())==1,"one trail per native transaction");
            time(level,102);RaikiriRapidCombo.THUNDER_RAPID.get().tickAction(actor);
            var slash=level.getEntitiesOfClass(EntitySlashEffect.class,actor.getBoundingBox().inflate(12),
                    effect->effect.getShooter()==actor).stream().filter(effect->effect.getPersistentData()
                            .hasUUID(RaikiriStepController.SEQUENCE)).findFirst().orElseThrow();
            check(slash.getPersistentData().hasUUID(RaikiriStepController.BLADE_ID),"real native tick authors sequence-bound slash");
            target=new Zombie(EntityType.ZOMBIE,level);target.setNoAi(true);target.setNoGravity(true);
            target.setPos(1.5,160,0);target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);
            target.getAttribute(Attributes.ARMOR).setBaseValue(0);target.setHealth(200);level.addFreshEntity(target);
            float health=target.getHealth();time(level,105);RaikiriFieldService.tick(e.getServer());
            check(target.getHealth()==health,"trail never falls before delay");
            time(level,110);for(int t=110;t<=113;t++) { time(level,t);RaikiriFieldService.tick(e.getServer()); }
            check(target.getHealth()<health && health-target.getHealth()<=3.001,"real trail damage is capped per target");
            check(!RaikiriStepController.free(actor),"secondary lightning does not confirm native rapid");
            // A real hurt event attributed to our native slash, with sequence stamped by the controller.
            time(level,114);target.invulnerableTime=0;
            var lateProtection=new LateProtection();MinecraftForge.EVENT_BUS.register(lateProtection);
            try {
                float protectedHealth=target.getHealth();
                target.hurt(level.damageSources().indirectMagic(slash,actor),1);
                check(target.getHealth()==protectedHealth && !RaikiriStepController.free(actor),
                        "late LOWEST protection cannot grant free continuation");
            } finally { MinecraftForge.EVENT_BUS.unregister(lateProtection); }
            target.invulnerableTime=0;
            check(target.hurt(level.damageSources().indirectMagic(slash,actor),1),"native slash source hurts real target");
            check(RaikiriStepController.free(actor),"only stamped native source confirms");
            time(level,115);long secondBefore=rank.getRankPoint(115);state.progressCombo(actor);
            check(Math.abs(actor.getX()-7)<0.01 && rank.getRankPoint(115)==secondBefore,"second native step moves without another debit");
            check(!RaikiriStepController.free(actor),"second step needs its own confirmation");
            target.invulnerableTime=0;target.hurt(level.damageSources().indirectMagic(slash,actor),1);
            check(!RaikiriStepController.free(actor),"late first-sequence slash cannot confirm second step");
            var secondSlash=new EntitySlashEffect(SlashBlade.RegistryEvents.SlashEffect,level);secondSlash.setShooter(actor);
            RaikiriStepController.tag(secondSlash,actor);target.invulnerableTime=0;
            target.hurt(level.damageSources().indirectMagic(secondSlash,actor),1);
            check(RaikiriStepController.free(actor),"second sequence confirms independently");
            time(level,121);long thirdBefore=rank.getRankPoint(121);state.progressCombo(actor);
            check(Math.abs(actor.getX()-10.5)<0.01 && rank.getRankPoint(121)==thirdBefore,"third native step completes without extra debit");
            check(!RaikiriStepController.free(actor),"three-step limit enforced");
            // Collision path checks preserve real wall/shape bounds.
            actor.moveTo(0,160,0.5,-90,0);actor.setOnGround(true);
            level.setBlock(new BlockPos(1,160,0),Blocks.STONE.defaultBlockState(),3);
            level.setBlock(new BlockPos(1,161,0),Blocks.STONE.defaultBlockState(),3);
            check(RaikiriStepController.safeEnd(actor).x<0.7,"solid wall stops the swept player box");
            level.setBlock(new BlockPos(1,160,0),Blocks.AIR.defaultBlockState(),3);
            level.setBlock(new BlockPos(1,161,0),Blocks.AIR.defaultBlockState(),3);
            level.setBlock(new BlockPos(2,160,0),Blocks.IRON_BARS.defaultBlockState(),3);
            level.setBlock(new BlockPos(2,161,0),Blocks.IRON_BARS.defaultBlockState(),3);
            check(RaikiriStepController.safeEnd(actor).x<2.2,"thin collider blocks swept movement");
            level.setBlock(new BlockPos(2,160,0),Blocks.AIR.defaultBlockState(),3);
            level.setBlock(new BlockPos(2,161,0),Blocks.AIR.defaultBlockState(),3);
            actor.moveTo(0,160,0,-90,0);actor.setOnGround(true);
            // Actual native cleave -> loop -> landing; poll must see an airborne native node first.
            RaikiriStepController.clear(actor.getUUID(),e.getServer());time(level,150);
            land(level,blade);
            check(RaikiriFieldService.swordCount(actor.getUUID())==1,"native cleave landing plants without hitting an enemy");
            RaikiriStepController.playerTick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END,actor));
            check(RaikiriFieldService.swordCount(actor.getUUID())==1,"repeated landing cannot duplicate sword");
            state.setComboSeq(ComboStateRegistry.NONE.getId());
            RaikiriStepController.playerTick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END,actor));
            check(RaikiriFieldService.swordCount(actor.getUUID())==1,"ordinary grounded player cannot plant");
            state.setComboSeq(ComboStateRegistry.AERIAL_CLEAVE_LOOP.getId());state.setLastActionTime(0);
            actor.setOnGround(false);RaikiriStepController.playerTick(new TickEvent.PlayerTickEvent(TickEvent.Phase.START,actor));
            actor.setOnGround(true);RaikiriStepController.playerTick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END,actor));
            check(RaikiriFieldService.swordCount(actor.getUUID())==1,"expired raw cleave ID cannot arm an ordinary jump");
            actor.moveTo(0,170,0,-90,0);actor.setOnGround(true);
            RaikiriFieldService.plant(actor,RaikiriStepController.session(actor));
            check(RaikiriFieldService.swordCount(actor.getUUID())==1,"unsupported landing cannot create floating sword");
            actor.moveTo(0,160,0,-90,0);actor.setOnGround(true);
            for(int n=0;n<3;n++) { time(level,151+n);land(level,blade); }
            check(RaikiriFieldService.swordCount(actor.getUUID())==3,"oldest sword replaced at per-owner cap");
            // Cross our anchors with a paid native step, then observe one saturated next-round charge.
            rank.setRawRankPoint(1200);rank.setLastUpdte(level.getGameTime());
            state.setComboSeq(ComboStateRegistry.NONE.getId());state.setLastActionTime(0);state.progressCombo(actor);
            check(RaikiriFieldService.charged(actor.getUUID()),"real accepted path charges owner swords");
            protect=true;target.setPos(0.5,160,0);health=target.getHealth();target.invulnerableTime=17;
            time(level,170);RaikiriFieldService.tick(e.getServer());
            check(target.getHealth()==health && target.invulnerableTime==17,"protection cancels damage and preserves hurt immunity");
            protect=false;time(level,190);RaikiriFieldService.tick(e.getServer());
            check(target.getHealth()<health,"ground sword discharges into real nearby hostile");
            check(!RaikiriFieldService.charged(actor.getUUID()),"discharge consumes charge without recursive recharge");
            health=target.getHealth();time(level,191);RaikiriFieldService.tick(e.getServer());
            check(target.getHealth()==health,"same sword cannot repeat damage before pulse interval");
            var near=victim(level,0.8,0);var far=victim(level,3,0);extraTargets.add(near);extraTargets.add(far);
            time(level,205);RaikiriFieldService.tick(e.getServer());
            check(near.getHealth()<200 && far.getHealth()==200,"each sword selects two nearest legal enemies only");
            target.setPos(20,160,0);near.setPos(20,160,1);far.setPos(2,160,0);
            level.setBlock(new BlockPos(1,160,0),Blocks.STONE.defaultBlockState(),3);
            level.setBlock(new BlockPos(1,161,0),Blocks.STONE.defaultBlockState(),3);
            time(level,220);RaikiriFieldService.tick(e.getServer());
            check(far.getHealth()==200,"ground sword never discharges through wall");
            level.setBlock(new BlockPos(1,160,0),Blocks.AIR.defaultBlockState(),3);
            level.setBlock(new BlockPos(1,161,0),Blocks.AIR.defaultBlockState(),3);
            far.setPos(20,160,2);time(level,235);RaikiriFieldService.tick(e.getServer());
            check(far.getHealth()==200,"ground sword never tracks target beyond its own range");
            time(level,272);RaikiriFieldService.tick(e.getServer());
            check(RaikiriFieldService.swordCount(actor.getUUID())==1,"resonance does not refresh original expiry");
            time(level,274);RaikiriFieldService.tick(e.getServer());
            check(RaikiriFieldService.swordCount(actor.getUUID())==0,"lifetime expires anchors");
            // Native SA remains the registered blade art; no added finisher cost/controller.
            time(level,400);var nativeArt=state.getSlashArtsKey();long raw=rank.getRawRankPoint();
            state.setComboSeq(ComboStateRegistry.NONE.getId());state.setLastActionTime(0);
            var defaultSa=state.doChargeAction(actor,40);
            check(java.util.Objects.equals(state.getSlashArtsKey(),nativeArt) && rank.getRawRankPoint()==raw,
                    "ordinary native SA key and extra Rank unchanged");
            check(defaultSa.equals(ComboStateRegistry.JUDGEMENT_CUT.getId()),"native default judgement cut submits normally");
            var drive=mods.flammpfeil.slashblade.registry.SlashArtsRegistry.DRIVE_HORIZONTAL.getId();
            state.setSlashArtsKey(drive);state.setComboSeq(ComboStateRegistry.NONE.getId());state.setLastActionTime(0);
            check(state.doChargeAction(actor,40).equals(ComboStateRegistry.DRIVE_HORIZONTAL.getId())
                    && state.getSlashArtsKey().equals(drive),"customized native SlashArts key stays native");
            actor.moveTo(0,160,0,0,0);actor.setOnGround(true);target.setPos(0,160,0.25);target.invulnerableTime=0;
            state.setComboSeq(RaikiriRapidCombo.THUNDER_RAPID.getId());state.setLastActionTime(level.getGameTime());
            input.getCommands().add(InputCommand.R_DOWN);
            var reach=actor.getAttribute(net.minecraftforge.common.ForgeMod.ENTITY_REACH.get());
            double reachBefore=reach==null?0:reach.getValue();
            RaikiriRapidCombo.hold(actor,2);
            check(state.getComboSeq().equals(ComboStateRegistry.RISING_STAR.getId()),"native hold adapter preserves Rising Star transition");
            check(reach==null || reach.getValue()==reachBefore,"native hold reach modifier restored");
            input.getCommands().remove(InputCommand.R_DOWN);actor.setOnGround(true);actor.moveTo(0,160,0,-90,0);
            // Secondary Raikiri inscription never turns a legal primary into this controller.
            var dual=blade.copy();dual.getOrCreateTag().putString(ModularSlashBladeItem.AWAKENED_SOUL_INSCRIPTION_MODULE+"_material","awakened_soul_inscription/akatsuki_raikiri");
            check(!RaikiriStepController.eligible(dual),"unformed dormant primary cannot promote secondary Raikiri");
            dual.getOrCreateTag().putBoolean(AkatsukiAwakening.TAG_UNLOCKED,true);
            check(!RaikiriStepController.eligible(dual),"secondary soul cannot enable full Raikiri kit");
            // Real second FakePlayer owns independent sessions/anchors in the same level.
            var other=FakePlayerFactory.get(level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"RaikiriOther"));
            level.addNewPlayer(other);var otherBlade=blade();other.setItemSlot(EquipmentSlot.MAINHAND,otherBlade);
            other.moveTo(1,160,2,0,0);other.setOnGround(true);
            var otherState=otherBlade.getCapability(ModularSlashBladeItem.BLADESTATE).orElseThrow(IllegalStateException::new);
            otherState.setBroken(false);otherState.setSealed(false);
            RaikiriFieldService.plant(other,RaikiriStepController.session(other));
            check(RaikiriFieldService.swordCount(other.getUUID())==1,"second player owns independent anchor");
            land(level,blade);actor.getInventory().selected=1;
            RaikiriStepController.tick(new TickEvent.ServerTickEvent(TickEvent.Phase.END,()->true,e.getServer()));
            check(RaikiriFieldService.swordCount(actor.getUUID())==0,"slot swap removes all old anchors");
            check(RaikiriFieldService.swordCount(other.getUUID())==1,"clearing first owner leaves second owner intact");
            RaikiriStepController.logout(new net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent(other));
            check(RaikiriFieldService.swordCount(other.getUUID())==0,"logout callback removes owned anchors");other.discard();
            actor.getInventory().selected=0;
            land(level,blade);
            RaikiriStepController.dimension(new net.minecraftforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent(
                    actor,net.minecraft.world.level.Level.OVERWORLD,net.minecraft.world.level.Level.NETHER));
            check(RaikiriFieldService.swordCount(actor.getUUID())==0,"dimension transition callback clears session");
            land(level,blade);
            RaikiriStepController.death(new net.minecraftforge.event.entity.living.LivingDeathEvent(actor,level.damageSources().generic()));
            check(RaikiriFieldService.swordCount(actor.getUUID())==0,"death callback clears session");
            land(level,blade);actor.setItemSlot(EquipmentSlot.MAINHAND,blade.copy());
            RaikiriStepController.tick(new TickEvent.ServerTickEvent(TickEvent.Phase.END,()->true,e.getServer()));
            check(RaikiriFieldService.swordCount(actor.getUUID())==0,"weapon object swap invalidates old session even with copied UUID");
            actor.setItemSlot(EquipmentSlot.MAINHAND,blade);
            GameplayConfig.ENABLE_AWAKENED_V2.set(false);
            check(!RaikiriStepController.eligible(blade) && !AwakenedBladeCombat.replacesLegacy(),"V2 off restores legacy selection");
            GameplayConfig.ENABLE_AWAKENED_V2.set(true);
            LogUtils.getLogger().info("RAIKIRI_COMBAT_SMOKE_PASS: {} checks",checks);
        } catch(Throwable failure) {
            LogUtils.getLogger().error("RAIKIRI_COMBAT_SMOKE_FAIL",failure);
            throw new IllegalStateException("Raikiri native smoke failed",failure);
        } finally {
            protect=false;cancelMotion=false;rewriteMotion=false;
            GameplayConfig.ENABLE_AWAKENED_V2.set(true);
            if(target!=null)target.discard();extraTargets.forEach(Zombie::discard);
            if(actor!=null) { RaikiriStepController.clear(actor.getUUID(),e.getServer());actor.discard(); }
            e.getServer().halt(false);
        }
    }
    private static Zombie victim(ServerLevel level,double x,double z) {
        var target=new Zombie(EntityType.ZOMBIE,level);target.setNoAi(true);target.setNoGravity(true);target.setPos(x,160,z);
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);target.getAttribute(Attributes.ARMOR).setBaseValue(0);
        target.setHealth(200);level.addFreshEntity(target);return target;
    }
    private static ItemStack blade() {
        var blade=ModItems.MODULAR_SLASHBLADE.get().createDefaultStack();var tag=blade.getOrCreateTag();
        tag.putString(ModularSlashBladeItem.INSCRIPTION_SLOT,ModularSlashBladeItem.AWAKENED_SOUL_INSCRIPTION_MODULE);
        tag.putString(ModularSlashBladeItem.AWAKENED_SOUL_INSCRIPTION_MODULE+"_material",SoulLegacyState.RAIKIRI_VARIANT);
        tag.putBoolean(BladeLegacyEasterEggs.TAG_RAIKIRI_UNLOCKED,true);return blade;
    }
    private static void land(ServerLevel level,ItemStack blade) {
        var state=blade.getCapability(ModularSlashBladeItem.BLADESTATE).orElseThrow(IllegalStateException::new);
        actor.setOnGround(false);state.updateComboSeq(actor,ComboStateRegistry.AERIAL_CLEAVE.getId());
        RaikiriStepController.playerTick(new TickEvent.PlayerTickEvent(TickEvent.Phase.START,actor));
        state.updateComboSeq(actor,ComboStateRegistry.AERIAL_CLEAVE_LOOP.getId());
        actor.setOnGround(true);ComboStateRegistry.AERIAL_CLEAVE_LOOP.get().tickAction(actor);
        check(state.getComboSeq().equals(ComboStateRegistry.AERIAL_CLEAVE_LANDING.getId()),"native loop commits landing node");
        RaikiriStepController.playerTick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END,actor));
    }
    private static void time(ServerLevel level,long value) { ((ServerLevelData)level.getLevelData()).setGameTime(value); }
    private static void check(boolean pass,String description) { checks++;if(!pass)throw new IllegalStateException(description); }
    private RaikiriCombatSmokeTest() {}
}
