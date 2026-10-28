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
    private static float observed;
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void attack(LivingAttackEvent e) {
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
            KyoukaMirrorSessions.step();
            check(KyoukaMirrorSessions.SESSIONS.isEmpty(), "lock/rank alone do not activate");
            cast(player, false, false); // actual native scheduler rejects released middle button
            KyoukaMirrorSessions.step();
            check(KyoukaMirrorSessions.SESSIONS.isEmpty() && rank.getRawRankPoint()==1600, "failed native cast has no mirror fee");
            cast(player, true, false);
            KyoukaMirrorSessions.step();
            var session = KyoukaMirrorSessions.SESSIONS.get(player.getUUID());
            check(session != null && session.ring.size()==8, "native S-rank batch creates one mirror session");
            check(rank.getRawRankPoint()==1540, "native eight swords pay only one 60 point activation");
            rank.addRankPoint(player, 100); check(rank.getRawRankPoint()==1540, "positive native addition blocked before write");
            rank.setRawRankPoint(1700); check(rank.getRawRankPoint()==1540, "direct setter gain blocked");
            rank.addRankPoint(player, -1); check(rank.getRawRankPoint()==1539, "negative native change preserved");
            long seq=session.action;
            cancelMotion=true; state.updateComboSeq(player,ComboStateRegistry.COMBO_A1.getId()); cancelMotion=false;
            check(session.action==seq, "cancelled motion does not commit");
            state.progressCombo(player,true); check(session.action==seq, "virtual route cannot commit");
            state.updateComboSeq(player,ComboStateRegistry.COMBO_A1.getId());
            check(session.action==seq+1 && session.combo.equals(state.getComboSeq()), "final committed native motion observed exactly once");
            check(!session.shots.isEmpty(), "native click-created slash is independently observed");
            var shot=session.shots.values().iterator().next().entity();
            float baseline=(float)AttackHelper.calculateTotalDamage(player,target,(float)shot.getDamage(),false);
            float health=target.getHealth(); observed=0;
            nativeTick(shot); nativeTick(shot); KyoukaMirrorDamageService.flush();
            check(Math.abs(observed-baseline*.75F)<.001F, "one native window gives total 75 percent: observed="+observed+" base="+baseline+" tick="+shot.tickCount+" alive="+shot.isAlive()+" indirect="+shot.getIndirect()+" owner="+(shot.getShooter()==player)+" valid="+KyoukaMirrorSessions.valid(session)+" windows="+session.budgets.get(session.action).windows+" points="+rank.getRawRankPoint());
            check(target.getHealth()<health, "remote native action damages bound target");
            check(rank.getRawRankPoint()==1521, "one native action pays 18; actual damage cannot earn rank");
            check(session.action==seq+1, "secondary Rank callback cannot resolve/advance native combo");
            long lockedPoints=rank.getRawRankPoint();
            InheritedCombatDamage.apply(() -> { NativeBladeActionObserver.melee(player,.44F,null); return true; });
            KyoukaMirrorDamageService.flush();
            check(rank.getRawRankPoint()==lockedPoints, "inherited recursion cannot create an action debit");
            var snapshot=new dev.bladetetra.network.KyoukaMirrorPacket(session.id,player.getUUID(),session.weapon,
                    target.getUUID(),target.getId(),level.dimension().location(),session.combo,session.started,
                    session.action,session.yaw,false,blade.copy());
            var buffer=new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
            try {
                dev.bladetetra.network.KyoukaMirrorPacket.encode(snapshot,buffer);
                var decoded=dev.bladetetra.network.KyoukaMirrorPacket.decode(buffer);
                check(decoded.valid() && decoded.target().equals(snapshot.target())
                        && decoded.action()==snapshot.action() && !decoded.appearance().isEmpty(),"authoritative snapshot/real appearance round trip");
            } finally {buffer.release();}
            observed=0; nativeTick(shot); nativeTick(shot); KyoukaMirrorDamageService.flush();
            check(observed==0 && rank.getRawRankPoint()==1521, "non-cycle slash later frames cannot inflate budget/fee");
            // A distinct real native action can pay once again; protection sees the secondary hit.
            state.updateComboSeq(player,ComboStateRegistry.COMBO_A1.getId());
            var next=session.shots.values().stream().filter(s -> s.entity()!=shot).findFirst().orElseThrow().entity();
            health=target.getHealth(); protect=true; nativeTick(next);nativeTick(next);KyoukaMirrorDamageService.flush();protect=false;
            check(target.getHealth()==health, "Forge protection cancels remote damage");
            check(rank.getRawRankPoint()==1503, "repeated same-tick committed combo is distinct, not virtual");
            var swords=List.copyOf(session.ring); swords.get(0).doFire();
            check(KyoukaMirrorSessions.valid(session), "partial batch loss keeps remaining ring");
            for(var sword:swords)sword.doFire(); KyoukaMirrorSessions.step();
            check(KyoukaMirrorSessions.SESSIONS.isEmpty(), "last native ring firing ends session");
            rank.addRankPoint(player,10); check(rank.getRawRankPoint()==1513, "rank unlocked after session cleanup");
            for(var sword:swords)sword.discard();
            rank.setRawRankPoint(1199); cast(player,true,false);KyoukaMirrorSessions.step();
            check(KyoukaMirrorSessions.SESSIONS.isEmpty() && rank.getRawRankPoint()==1199, "below A native summon has no extra fee");
            for(var e:List.copyOf(player.getPassengers())) if(e instanceof EntitySpiralSwords)e.discard();
            rank.setRawRankPoint(1600);state.setTargetEntityId(-1);cast(player,true,false);KyoukaMirrorSessions.step();
            check(KyoukaMirrorSessions.SESSIONS.isEmpty() && rank.getRawRankPoint()==1600,"no locked target cannot activate");
            for(var e:List.copyOf(player.getPassengers())) if(e instanceof EntitySpiralSwords)e.discard();
            state.setTargetEntityId(target.getId());
            cast(player,true,true);KyoukaMirrorSessions.step();
            check(KyoukaMirrorSessions.SESSIONS.isEmpty() && rank.getRawRankPoint()==1600,"native Storm Swords cannot activate mirrors");
            castCommands(player,EnumSet.of(InputCommand.M_DOWN,InputCommand.SNEAK,InputCommand.FORWARD));
            KyoukaMirrorSessions.step();
            check(KyoukaMirrorSessions.SESSIONS.isEmpty() && rank.getRawRankPoint()==1600,"native Heavy Rain cannot activate mirrors");
            // Eight real server owners, each with a native batch; this is isolation testing, not human network acceptance.
            for(int i=0;i<8;i++) {
                var peer=FakePlayerFactory.get(level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"mirror"+i));
                peers.add(peer);level.addNewPlayer(peer);peer.setPos(1,200,2+i*.2);peer.setNoGravity(true);
                var copy=blade.copy();peer.setItemSlot(EquipmentSlot.MAINHAND,copy);
                var bs=copy.getCapability(ItemSlashBlade.BLADESTATE).orElseThrow(IllegalStateException::new);
                bs.setTargetEntityId(target.getId());bs.setProudSoulCount(10000);
                var rp=peer.getCapability(CapabilityConcentrationRank.RANK_POINT).orElseThrow(IllegalStateException::new);
                rp.setLastUpdte(now);rp.setRawRankPoint(1600);cast(peer,true,false);
            }
            KyoukaMirrorSessions.step();
            check(KyoukaMirrorSessions.SESSIONS.size()==8,"eight native players have independent sessions");
            check(KyoukaMirrorSessions.SESSIONS.values().stream().map(v -> v.id).distinct().count()==8,
                    "shared copied weapon identity never merges owner sessions");
            check(!KyoukaMirrorSessions.legal(player,peers.get(0)),"PVP/friendly players are never valid mirror targets");
            peers.get(0).setItemSlot(EquipmentSlot.MAINHAND,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STICK));
            KyoukaMirrorSessions.step();
            check(KyoukaMirrorSessions.SESSIONS.size()==7,"one weapon swap cleans only that owner's session");
            var demoted=peers.get(1).getCapability(CapabilityConcentrationRank.RANK_POINT).orElseThrow(IllegalStateException::new);
            demoted.addRankPoint(peers.get(1),-800);KyoukaMirrorSessions.step();
            check(KyoukaMirrorSessions.SESSIONS.size()==6,"falling below native B ends only that owner's session");
            target.discard();KyoukaMirrorSessions.step();
            check(KyoukaMirrorSessions.SESSIONS.isEmpty(),"removed bound target cleans every remaining session without retargeting");
            LogUtils.getLogger().info("KYOUKA_NATIVE_SMOKE_PASS: {} checks, actual native scheduler/commit/Mixin/area windows/Rank/Forge protection",checks);
        } catch(Throwable failure) {
            LogUtils.getLogger().error("KYOUKA_NATIVE_SMOKE_FAIL",failure); throw new IllegalStateException(failure);
        } finally {
            cancelMotion=false;protect=false;
            for(var s:List.copyOf(KyoukaMirrorSessions.SESSIONS.values()))KyoukaMirrorSessions.stop(s);
            for(var peer:peers)peer.discard();
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
