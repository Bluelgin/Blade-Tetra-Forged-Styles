package dev.bladetetra.easteregg;

import com.mojang.logging.LogUtils;
import mods.flammpfeil.slashblade.capability.concentrationrank.CapabilityConcentrationRank;
import mods.flammpfeil.slashblade.event.BladeMotionEvent;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import java.util.function.Consumer;

/** Native SA, moving channel, effective reach, actual Rank burn and final damage pipeline. */
final class AkatsukiThreadSmoke {
    static void verify(ServerLevel level,ServerPlayer p) {
        var blade=p.getMainHandItem();var state=blade.getCapability(ItemSlashBlade.BLADESTATE).orElseThrow(IllegalStateException::new);
        var rank=p.getCapability(CapabilityConcentrationRank.RANK_POINT).orElseThrow(IllegalStateException::new);long saved=rank.getRankPoint(level.getGameTime());
        var original=p.position();float yaw=p.getYRot(),pitch=p.getXRot();var originalCombo=state.getComboSeq();
        var target=new Zombie(level);target.setNoAi(true);target.setNoGravity(true);target.moveTo(p.getX(),p.getY(),p.getZ()+3,0,0);
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);target.getAttribute(Attributes.ARMOR).setBaseValue(0);target.setHealth(100);level.addFreshEntity(target);
        var bonus=new AttributeModifier(java.util.UUID.randomUUID(),"thread smoke",12-p.getAttributeValue(Attributes.ATTACK_DAMAGE),AttributeModifier.Operation.ADDITION);
        var reach=p.getAttribute(net.minecraftforge.common.ForgeMod.ENTITY_REACH.get());
        var reachBonus=new AttributeModifier(java.util.UUID.randomUUID(),"thread reach smoke",2,AttributeModifier.Operation.ADDITION);
        Consumer<BladeMotionEvent> reject=e->{if(e.getEntity()==p)e.setCanceled(true);};
        Consumer<AttackEntityEvent> protect=e->{if(e.getTarget()==target)e.setCanceled(true);};
        try {
            p.getAttribute(Attributes.ATTACK_DAMAGE).addTransientModifier(bonus);p.setYRot(0);p.setXRot(0);p.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            rank.setRawRankPoint(1200);rank.setLastUpdte(level.getGameTime());
            MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST,false,BladeMotionEvent.class,reject);
            state.doChargeAction(p,40);check(!AkatsukiBloodTendrils.active(p),"canceled native SA cannot start channel");
            MinecraftForge.EVENT_BUS.unregister(reject);
            state.setComboSeq(ComboStateRegistry.NONE.getId());state.doChargeAction(p,40);
            check(AkatsukiBloodTendrils.active(p),"full blood plus real orbit commits native SA");
            check(p.getAttributeValue(Attributes.MOVEMENT_SPEED)==0,"brief preparation holds movement");
            check(p.position().equals(original)&&p.getDeltaMovement().lengthSqr()<.001,"native thrust has no rush movement");
            long at=level.getGameTime();
            MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST,false,AttackEntityEvent.class,protect);
            for(int t=1;t<=30;t++){time(level,at+t);tick(p);}
            check(target.getHealth()==100,"protection rejects thread damage");
            check(p.getAttributeValue(Attributes.MOVEMENT_SPEED)>0&&AkatsukiBloodTendrils.active(p),"movement unlocked during channel");
            MinecraftForge.EVENT_BUS.unregister(protect);
            for(int t=31;t<=35;t++) {
                p.setPos(p.getX()+.2,p.getY()+.05,p.getZ());p.setYRot(t-31);
                time(level,at+t);tick(p);check(AkatsukiBloodTendrils.active(p),"walking jumping and turning preserve channel");
            }
            // Attribute bonuses must change real hits, including while SA owns the combo.
            p.setPos(original);p.setYRot(0);time(level,at+36);tick(p);
            double baseRange=AkatsukiBloodTendrils.range(p);
            reach.addTransientModifier(reachBonus);
            check(Math.abs(AkatsukiBloodTendrils.range(p)-baseRange-3)<.001,"reach bonuses scale by 1.5 during SA");
            target.setPos(p.getX(),p.getY(),p.getZ()+baseRange+1);target.setHealth(100);
            time(level,((at+40)/40+1)*40);tick(p);
            check(target.getHealth()<100,"extended range deals real damage");
            reach.removeModifier(reachBonus);float health=target.getHealth();
            time(level,level.getGameTime()+40);tick(p);
            check(target.getHealth()==health&&AkatsukiBloodTendrils.active(p),"out of range releases target without canceling channel");
            target.setPos(p.getX(),p.getY(),p.getZ()+3);target.setHealth(100);
            // Restart so the entire contribution belongs to this channel, then exhaust real Rank.
            AkatsukiBloodTendrils.cancel(p);state.setComboSeq(ComboStateRegistry.NONE.getId());
            rank.setRawRankPoint(1200);rank.setLastUpdte(level.getGameTime());state.doChargeAction(p,40);at=level.getGameTime();
            long points=1200;int elapsed=0;
            for(int t=1;t<230;t++) {
                elapsed=t;time(level,at+t);tick(p);long next=rank.getRankPoint(level.getGameTime());
                check(next<=points,"channel never earns Rank");points=next;tick(p);
                check(rank.getRankPoint(level.getGameTime())==next,"duplicate same-tick callback cannot debit again");
                if(points==0)break;
            }
            check(points==0&&AkatsukiBloodTendrils.active(p),"exhaustion enters binding instead of disappearing");
            check(target.getHealth()<40&&target.getHealth()>0,"real thread hits build contribution without duplicate credit");
            target.setHealth(8);
            time(level,at+elapsed+10);tick(p);
            check(!target.isAlive(),"eligible enemy dies through final damage pipeline");
            check(AkatsukiBloodBlade.full(p)&&p.getMaxHealth()==11,"finisher retains full blood escrow");
            time(level,at+elapsed+20);tick(p);check(!AkatsukiBloodTendrils.active(p),"normal completion clears controller");
            check(p.getAttribute(Attributes.MOVEMENT_SPEED).getValue()>0,"movement modifier released");
            rank.setRawRankPoint(600);rank.setLastUpdte(level.getGameTime());state.setComboSeq(ComboStateRegistry.NONE.getId());state.doChargeAction(p,40);
            p.setPos(p.getX()+3,p.getY(),p.getZ());time(level,level.getGameTime()+1);tick(p);
            check(!AkatsukiBloodTendrils.active(p),"discontinuous relocation cancels channel");p.setPos(original);
            rank.setRawRankPoint(600);rank.setLastUpdte(level.getGameTime());state.setComboSeq(ComboStateRegistry.NONE.getId());state.doChargeAction(p,40);
            check(AkatsukiBloodTendrils.active(p),"subsequent channel allowed");
            p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND,ItemStack.EMPTY);tick(p);
            check(!AkatsukiBloodTendrils.active(p),"weapon swap cancels without finisher");
            LogUtils.getLogger().info("AKATSUKI_THREAD_SMOKE_PASS: native SA, preparation-only movement lock, walking/jumping/turning, attribute-scaled real reach hits, out-of-range release, teleport fallback, protection, Rank burn, real execution and weapon cancellation");
        } finally {
            MinecraftForge.EVENT_BUS.unregister(reject);MinecraftForge.EVENT_BUS.unregister(protect);AkatsukiBloodTendrils.cancel(p);
            target.discard();p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND,blade);
            p.getAttribute(Attributes.ATTACK_DAMAGE).removeModifier(bonus);p.setYRot(yaw);p.setXRot(pitch);
            reach.removeModifier(reachBonus);p.setPos(original);
            state.setComboSeq(originalCombo);rank.setRawRankPoint(saved);rank.setLastUpdte(level.getGameTime());
            blade.getOrCreateTag().remove("blade_tetra_akatsuki_execution_cooldown");
        }
    }
    private static void tick(ServerPlayer p){AkatsukiBloodTendrils.tick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END,p));}
    private static void time(ServerLevel level,long at){level.getServer().getWorldData().overworldData().setGameTime(at);}
    private static void check(boolean value,String reason){if(!value)throw new IllegalStateException(reason);}
    private AkatsukiThreadSmoke() {}
}
