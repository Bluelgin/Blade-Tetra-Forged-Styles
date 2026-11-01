package dev.bladetetra.easteregg;

import dev.bladetetra.BladeTetra;
import dev.bladetetra.combat.*;
import mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState;
import mods.flammpfeil.slashblade.entity.EntitySpiralSwords;
import mods.flammpfeil.slashblade.event.BladeMotionEvent;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.*;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
import java.util.*;
import static dev.bladetetra.easteregg.AkatsukiBloodRules.*;

/** Online-life escrow belongs to the player. Item NBT is only a disposable visual snapshot. */
@Mod.EventBusSubscriber(modid=BladeTetra.MOD_ID)
public final class AkatsukiBloodBlade {
    public static final String TAG="blade_tetra_blood_blade";
    private static final UUID RESERVE=UUID.fromString("efa39f34-8a9b-46d5-a711-94e04a3b1787");
    private static final Map<UUID,Session> SESSIONS=new HashMap<>();
    private static final ThreadLocal<Motion> MOTION=new ThreadLocal<>();
    private static final class Session {
        ServerPlayer owner; ItemStack blade; final UUID binding=UUID.randomUUID();
        int stage; float paid; long cleaveAt=-1,recallUntil,flowAt,sheathAt;
        int flowKind; float flowStrength,recallFrom,flowHealth;
        String dimension; int entityId;
        Session(ServerPlayer p) {owner=p;blade=p.getMainHandItem();}
        boolean held(Player p) {return p.isAlive()&&!p.isSpectator()&&p.getMainHandItem()==blade&&supported(blade);}
    }
    public static boolean supported(ItemStack blade) {
        return AwakenedBladeCombat.replacesLegacy()&&SoulSkillState.enabled(blade,SoulLegacyState.Legacy.AKATSUKI)
                &&blade.getCapability(ItemSlashBlade.BLADESTATE).map(s->!s.isBroken()&&!s.isSealed()).orElse(false);
    }
    static boolean orbiting(ServerPlayer p) {
        return p.getPassengers().stream().anyMatch(e->e.getClass()==EntitySpiralSwords.class
                &&e instanceof EntitySpiralSwords sword&&sword.isAlive()&&!sword.itFired()&&sword.getOwner()==p
                &&p.level().getEntity(sword.getId())==sword);
    }
    /** The native event precedes commitment and remains mutable. Inspect it after the native call returns. */
    public static final class Motion implements AutoCloseable {
        final LivingEntity user; final Motion previous; BladeMotionEvent event;
        Motion(LivingEntity user) {this.user=user;previous=MOTION.get();MOTION.set(this);}
        public void committed(ISlashBladeState state) {
            if(event!=null&&!event.isCanceled()&&event.getEntity()==user
                    &&state.getComboSeq().equals(event.getCombo())&&state.getLastActionTime()==user.level().getGameTime()
                    &&user instanceof ServerPlayer p) accept(p,state);
        }
        @Override public void close() { if(previous==null) MOTION.remove();else MOTION.set(previous); }
    }
    public static Motion observe(LivingEntity user) { return new Motion(user); }
    @SubscribeEvent(priority=EventPriority.HIGHEST,receiveCanceled=true)
    public static void motion(BladeMotionEvent e) {var m=MOTION.get();if(m!=null&&m.user==e.getEntity())m.event=e;}
    private static void accept(ServerPlayer p,ISlashBladeState state) {
        if(!supported(p.getMainHandItem())||CombatBalanceRuntime.kind(p,p)==CombatBalanceRules.AttackKind.SLASH_ART)return;
        var s=SESSIONS.get(p.getUUID());String id=state.getComboSeq().toString();long now=p.level().getGameTime();
        if(id.equals("slashblade:aerial_cleave")) {
            if(orbiting(p)) {
                if(s==null) {s=new Session(p);SESSIONS.put(p.getUUID(),s);}
                s.cleaveAt=now;
            } else if(s!=null)s.cleaveAt=-1;
            return;
        }
        if(s==null)return;
        if(id.equals("slashblade:aerial_cleave_loop"))return;
        if(id.equals("slashblade:aerial_cleave_landing")) {
            boolean landed=s.cleaveAt>=0&&now-s.cleaveAt<=100&&p.onGround();s.cleaveAt=-1;
            if(landed&&orbiting(p)&&s.stage<STAGES&&now>=s.recallUntil
                    &&(s.stage==0||s.held(p))) deposit(p,s);
            return;
        }
        s.cleaveAt=-1;
        if((id.equals("slashblade:upperslash")||id.equals("slashblade:upperslash_jump"))&&s.stage>0&&orbiting(p)) {
            float from=fraction(s.stage),heal=s.paid;
            stripBound(p,s);s.blade=p.getMainHandItem();s.recallFrom=from;
            s.stage=0;s.paid=0;s.recallUntil=now+RECALL_TICKS+SHEATH_FADE_TICKS;
            s.flowAt=now;s.flowKind=2;s.flowStrength=from;
            float before=p.getHealth();removeReserve(p);p.setHealth(Math.min(p.getMaxHealth(),before+heal));
            s.flowHealth=p.getHealth()-before;
            sync(p,s);SoulActivityService.triggered(p,s.blade,SoulLegacyState.Legacy.AKATSUKI);
        }
    }
    private static void deposit(ServerPlayer p,Session s) {
        float basis=p.getMaxHealth()/(1-reserved(s.stage));
        float cost=basis*(reserved(s.stage+1)-reserved(s.stage));
        float health=p.getHealth();
        if(!Float.isFinite(cost)||cost<=0||health-cost<2)return;
        if(s.stage==0) {strip(s.blade);s.blade=p.getMainHandItem();s.sheathAt=p.level().getGameTime();}
        s.stage++;s.paid+=cost;s.flowHealth=-cost;
        removeReserve(p);
        p.getAttribute(Attributes.MAX_HEALTH).addTransientModifier(new AttributeModifier(RESERVE,
                "Akatsuki blood reserve",-reserved(s.stage),AttributeModifier.Operation.MULTIPLY_TOTAL));
        p.setHealth(health-cost);
        s.flowAt=p.level().getGameTime();s.flowKind=1;s.flowStrength=fraction(s.stage);
        sync(p,s);SoulActivityService.triggered(p,s.blade,SoulLegacyState.Legacy.AKATSUKI);
    }
    private static void sync(ServerPlayer p,Session s) {
        s.owner=p;s.dimension=p.level().dimension().location().toString();s.entityId=p.getId();
        CompoundTag t=new CompoundTag();t.putUUID("owner",p.getUUID());t.putUUID("binding",s.binding);
        t.putInt("entity",p.getId());t.putString("dimension",s.dimension);t.putInt("stage",s.stage);
        t.putLong("flowAt",s.flowAt);t.putInt("flowKind",s.flowKind);t.putFloat("flowStrength",s.flowStrength);
        t.putFloat("flowHealth",s.flowHealth);t.putFloat("from",s.recallFrom);t.putLong("sheathAt",s.sheathAt);
        s.blade.getOrCreateTag().put(TAG,t);p.containerMenu.broadcastChanges();
    }
    private static CompoundTag snapshot(Player p,ItemStack blade) {
        if(!supported(blade)||!blade.hasTag()||!blade.getTag().contains(TAG)||p.getMainHandItem()!=blade)return null;
        var t=blade.getTag().getCompound(TAG);
        if(!t.hasUUID("owner")||!t.getUUID("owner").equals(p.getUUID())||t.getInt("entity")!=p.getId()
                ||!t.getString("dimension").equals(p.level().dimension().location().toString()))return null;
        if(!p.level().isClientSide()) {
            var s=SESSIONS.get(p.getUUID());if(s==null||!s.held(p))return null;
        }
        return t;
    }
    public static float visibleFraction(Player p,ItemStack blade) {return visibleFraction(p,blade,0);}
    public static float visibleFraction(Player p,ItemStack blade,float partial) {
        var t=snapshot(p,blade);if(t==null)return 0;
        return t.getInt("flowKind")==2?t.getFloat("from")*recall(p.level().getGameTime()+partial-t.getLong("flowAt"))
                :fraction(t.getInt("stage"));
    }
    public record Flow(boolean returning,float age,float progress,float strength,float health) {}
    public static Flow flow(Player p,ItemStack blade,float partial) {
        var t=snapshot(p,blade);if(t==null)return null;
        int kind=t.getInt("flowKind");if(kind!=1&&kind!=2)return null;
        float age=p.level().getGameTime()+partial-t.getLong("flowAt");
        int duration=kind==1?INJECTION_FLOW_TICKS:RECALL_TICKS;float strength=t.getFloat("flowStrength");
        return age>=0&&age<duration&&Float.isFinite(strength)&&strength>0
                ?new Flow(kind==2,age,age/duration,Math.min(1,strength),t.getFloat("flowHealth")):null;
    }
    public static float sheathAlpha(Player p,ItemStack blade,float partial) {
        var t=snapshot(p,blade);if(t==null)return 1;
        if(t.getInt("flowKind")==2)return sheathAfterRecall(p.level().getGameTime()+partial-t.getLong("flowAt"));
        return t.getInt("stage")>0?1-smooth((p.level().getGameTime()+partial-t.getLong("sheathAt"))/SHEATH_FADE_TICKS):1;
    }
    public static double extraReach(LivingEntity user) {
        if(!(user instanceof Player p)||CombatBalanceRuntime.kind(p,p)==CombatBalanceRules.AttackKind.SLASH_ART)return 0;
        return meleeExtension(p);
    }
    /** Physical blood length, also readable by skills explicitly based on ordinary melee reach. */
    public static double meleeExtension(Player p) {
        var t=snapshot(p,p.getMainHandItem());return t==null?0:EXTRA_REACH*fraction(t.getInt("stage"));
    }
    /** Scale only the original direct melee hit; never secondary budgets, projectiles or legacy execution. */
    @SubscribeEvent(priority=EventPriority.LOW)
    public static void damage(net.minecraftforge.event.entity.living.LivingHurtEvent e) {
        var source=e.getSource();
        if(e.getEntity().level().isClientSide()||!Float.isFinite(e.getAmount())||e.getAmount()<=0
                ||!(source.getDirectEntity() instanceof ServerPlayer p)||source.getEntity()!=p||e.getEntity()==p
                ||!source.is(net.minecraft.world.damagesource.DamageTypes.PLAYER_ATTACK)
                ||SoulLegacyDamageGuard.isSecondary(source)||InheritedCombatDamage.isInherited(source)
                ||BladeTechniqueHandler.isTechniqueDamage(source)
                ||CombatBalanceRuntime.kind(p,p)==CombatBalanceRules.AttackKind.SLASH_ART)return;
        var s=SESSIONS.get(p.getUUID());
        if(s!=null&&s.held(p)&&s.stage>0)e.setAmount(e.getAmount()*(1+damageBonus(s.stage)));
    }
    public static boolean presenting(ServerPlayer p) {
        var s=SESSIONS.get(p.getUUID());return s!=null&&s.held(p)&&flow(p,s.blade,0)!=null;
    }
    public static boolean full(ServerPlayer p) {
        var s=SESSIONS.get(p.getUUID());return s!=null&&s.held(p)&&s.stage==STAGES;
    }
    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent e) {
        if(e.phase!=TickEvent.Phase.END||!(e.player instanceof ServerPlayer p))return;
        var s=SESSIONS.get(p.getUUID());
        if(s==null) {removeReserve(p);stripOwned(p.getMainHandItem(),p);return;}
        // Container transfers can copy a stack. Adopt it only after the canonical stack has left this inventory.
        if(!contains(p,s.blade)) {
            ItemStack replacement=ItemStack.EMPTY;
            for(var item:p.getInventory().items)if(bound(item,s,p)) {replacement=item;break;}
            if(!replacement.isEmpty()) {strip(s.blade);s.blade=replacement;sync(p,s);}
        }
        if(s.cleaveAt>=0&&p.level().getGameTime()-s.cleaveAt>100)s.cleaveAt=-1;
        if(s.stage==0&&s.cleaveAt<0&&p.level().getGameTime()>=s.recallUntil) {clear(p,false);return;}
        if(s.stage>0&&p.getAttribute(Attributes.MAX_HEALTH).getModifier(RESERVE)==null)
            p.getAttribute(Attributes.MAX_HEALTH).addTransientModifier(new AttributeModifier(RESERVE,"Akatsuki blood reserve",-reserved(s.stage),AttributeModifier.Operation.MULTIPLY_TOTAL));
        if(s.owner!=p||s.entityId!=p.getId()||!p.level().dimension().location().toString().equals(s.dimension)||!bound(s.blade,s,p))sync(p,s);
        var held=p.getMainHandItem();if(held!=s.blade)stripOwned(held,p);
    }
    private static boolean contains(Player p,ItemStack stack) {
        for(var item:p.getInventory().items)if(item==stack)return true;
        for(var item:p.getInventory().offhand)if(item==stack)return true;
        return false;
    }
    private static boolean bound(ItemStack item,Session s,Player p) {
        if(!item.hasTag())return false;var t=item.getTag().getCompound(TAG);
        return t.hasUUID("owner")&&t.getUUID("owner").equals(p.getUUID())&&t.hasUUID("binding")&&t.getUUID("binding").equals(s.binding);
    }
    private static void strip(ItemStack blade) {if(blade.hasTag())blade.getTag().remove(TAG);}
    private static void stripOwned(ItemStack item,Player p) {
        if(!item.hasTag())return;var tag=item.getTag().getCompound(TAG);
        if(tag.hasUUID("owner")&&tag.getUUID("owner").equals(p.getUUID()))strip(item);
    }
    private static void stripBound(Player p,Session s) {
        strip(s.blade);
        for(var item:p.getInventory().items)if(bound(item,s,p))strip(item);
        for(var item:p.getInventory().offhand)if(bound(item,s,p))strip(item);
    }
    private static void removeReserve(Player p) {
        var attr=p.getAttribute(Attributes.MAX_HEALTH);if(attr!=null&&attr.getModifier(RESERVE)!=null)attr.removeModifier(RESERVE);
    }
    private static void clear(Player p,boolean refund) {
        var s=SESSIONS.remove(p.getUUID());removeReserve(p);
        if(s!=null) {
            stripBound(p,s);
            if(refund&&p.isAlive())p.setHealth(Math.min(p.getMaxHealth(),p.getHealth()+s.paid));
        }
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e) {clear(e.getEntity(),true);}
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent e) {clear(e.getEntity(),false);for(var item:e.getEntity().getInventory().items)stripOwned(item,e.getEntity());}
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e) {
        if(e.getEntity() instanceof ServerPlayer p) {var s=SESSIONS.get(p.getUUID());if(s!=null) {s.cleaveAt=-1;sync(p,s);}}
    }
    @SubscribeEvent public static void clone(PlayerEvent.Clone e) {removeReserve(e.getEntity());if(e.isWasDeath())clear(e.getEntity(),false);}
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void death(LivingDeathEvent e) {if(e.getEntity() instanceof Player p)clear(p,false);}
    @SubscribeEvent public static void stopping(ServerStoppingEvent e) {for(var p:e.getServer().getPlayerList().getPlayers())clear(p,true);}
    @SubscribeEvent public static void stop(ServerStoppedEvent e) {for(var s:SESSIONS.values()) {removeReserve(s.owner);strip(s.blade);}SESSIONS.clear();}
    private AkatsukiBloodBlade() {}
}
