package dev.bladetetra.client.sakura;

import com.mojang.blaze3d.vertex.BufferBuilder;
import dev.bladetetra.BladeTetra;
import dev.bladetetra.client.awakening.AwakeningVisualController;
import dev.bladetetra.config.ClientVisualConfig;
import dev.bladetetra.easteregg.SakuraEndlessFlow;
import dev.bladetetra.easteregg.SakuraStanceController;
import dev.bladetetra.easteregg.SakuraFlowRules;
import dev.bladetetra.network.AwakenedBladeVfxPacket;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Coordinates the existing packet + atlas pipeline; contains no draw primitives or damage. */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID, value = Dist.CLIENT)
public final class SakuraCombatVisualController {
    public static final int MAX_OWNERS = 8;
    private static final Map<UUID, View> VIEWS = new LinkedHashMap<>();
    private static final List<View> DRAW_ORDER = new ArrayList<>(MAX_OWNERS);
    private static final List<Pending> PENDING = new ArrayList<>();
    private static ClientLevel world;
    public static void clear() { VIEWS.clear(); DRAW_ORDER.clear(); PENDING.clear(); world=null; SakuraBladeAnchor.clear(); }
    private static void syncWorld() {
        var next=Minecraft.getInstance().level;
        if(world!=next) { clear(); world=next; }
    }
    private static boolean enabled() {
        return ClientVisualConfig.ENABLE_BLADE_COMBAT_VFX.get()
                && ClientVisualConfig.BLADE_COMBAT_VFX_INTENSITY.get()>0;
    }
    private static double distance() { return Math.min(64,ClientVisualConfig.BLADE_COMBAT_VFX_DISTANCE.get()); }
    private static String bladeId(Player owner) {
        var tag=owner.getMainHandItem().getTag();
        return tag==null?"":tag.getString("id");
    }
    private static boolean eligible(Player player) {
        var mc=Minecraft.getInstance();
        View existing=VIEWS.get(player.getUUID());
        boolean transition=existing!=null&&existing.bladeId.equals(bladeId(player))
                &&existing.pool.stance()!=null&&existing.pool.stance().active(player.level().getGameTime());
        return player.isAlive() && SakuraStanceController.supported(player.getMainHandItem())
                && (SakuraStanceController.dispersed(player.getMainHandItem()) || transition)
                && !AwakeningVisualController.presenting(player) && !bladeId(player).isBlank()
                && (mc.player==null||mc.player.distanceToSqr(player)<=distance()*distance());
    }
    private static boolean stanceEligible(Player p) {
        return p.isAlive()&&SakuraStanceController.supported(p.getMainHandItem())
                &&!AwakeningVisualController.presenting(p)&&!bladeId(p).isBlank()
                &&(Minecraft.getInstance().player==null||Minecraft.getInstance().player.distanceToSqr(p)<=distance()*distance());
    }
    private static float fraction(View v,double now) {
        var t=v.pool.stance();
        return t==null?(SakuraStanceController.dispersed(v.owner.getMainHandItem())?0:1):t.fraction(now);
    }
    public static float bladeFraction(Player owner,float partial) {
        View v=VIEWS.get(owner.getUUID());
        return v!=null&&v.bladeId.equals(bladeId(owner))?fraction(v,owner.level().getGameTime()+partial)
                :SakuraStanceController.dispersed(owner.getMainHandItem())?0:1;
    }
    public static void capture(Player owner,mods.flammpfeil.slashblade.event.client.RenderOverrideEvent e,boolean firstPerson) {
        View v=VIEWS.get(owner.getUUID());
        if(v!=null&&v.bladeId.equals(bladeId(owner))) v.anchor.capture(owner,e,firstPerson);
    }
    private static int count() {
        return switch(ClientVisualConfig.BLADE_COMBAT_VFX_QUALITY.get()) {case 0->24;case 2->72;default->48;};
    }
    private static SakuraShardFormation.Frame frame(Player p) {
        return new SakuraShardFormation.Frame(p.position(),Math.toRadians(p.getYRot()),p.getEyeHeight(),Math.toRadians(p.getXRot()));
    }
    private static View view(Player p) {
        View view=VIEWS.get(p.getUUID());
        String id=bladeId(p);
        if(view!=null&&(!view.bladeId.equals(id)||(p==Minecraft.getInstance().player&&view.slot!=p.getInventory().selected)
                ||view.pool.shards.length!=count())) {VIEWS.remove(p.getUUID());view=null;}
        if(view==null&&VIEWS.size()<MAX_OWNERS) {
            view=new View(p,id,world.getGameTime()); VIEWS.put(p.getUUID(),view);
        }
        return view;
    }
    public static void accept(AwakenedBladeVfxPacket packet) {
        syncWorld();
        if(world==null||!enabled()||!packet.valid()||packet.sakura()==null
                ||!packet.sakura().dimension().equals(world.dimension().location())) return;
        long age=world.getGameTime()-packet.sakura().actionAt();
        if(age < -5 || age > 24) return;
        if(!apply(packet)&&PENDING.size()<32) PENDING.add(new Pending(packet,world.getGameTime()+6));
    }
    private static boolean apply(AwakenedBladeVfxPacket packet) {
        var cue=packet.sakura();
        Player owner=world.getPlayerByUUID(cue.owner());
        boolean stance=packet.type()>=AwakenedBladeVfxPacket.SAKURA_DISPERSE;
        if(owner==null||!(stance?stanceEligible(owner):eligible(owner))||!cue.matchesHeld(owner.getUUID(),bladeId(owner),
                owner.getInventory().selected,owner==Minecraft.getInstance().player)) return false;
        boolean existed=VIEWS.containsKey(owner.getUUID());
        View view=view(owner);
        if(view==null) return true; // outside this client's fixed owner budget
        if(stance) {
            if(cue.sequence()<=view.stanceSequence) return true;
            view.stanceSequence=cue.sequence();
            float from=view.pool.stance()==null
                    ?(packet.type()==AwakenedBladeVfxPacket.SAKURA_DISPERSE?1:0):fraction(view,world.getGameTime());
            boolean disperse=packet.type()==AwakenedBladeVfxPacket.SAKURA_DISPERSE;
            if(disperse&&view.pool.stance()==null) view.pool.hide();
            if(!existed&&!disperse) for(var shard:view.pool.shards) shard.visibility=shard.previousVisibility=1;
            // Start locally from current positions; never fast-forward skipped flight
            // into a teleport when an authoritative packet arrives late.
            view.pool.beginStance(disperse,world.getGameTime(),view.anchor.grip(owner),view.anchor.tip(owner),from);
            java.util.Arrays.fill(view.targets,null);
            return true;
        }
        if(cue.sequence()<=view.stanceSequence) return true; // pre-transition attacks cannot resurrect canceled groups
        Vec3 target=new Vec3(packet.endX(),packet.endY(),packet.endZ());
        if(target.distanceToSqr(owner.position())>144) return true;
        long now=world.getGameTime();
        if(packet.type()==AwakenedBladeVfxPacket.SAKURA_SWING) {
            if(packet.variant()==-1) view.pool.recall(cue.sequence());
            else if(packet.variant()>=dev.bladetetra.easteregg.SakuraShardAttackRules.CONCERTO_VARIANT) {
                int value=packet.variant()-dev.bladetetra.easteregg.SakuraShardAttackRules.CONCERTO_VARIANT;
                if(now-cue.actionAt()<=12) view.pool.concerto(value/2,(value&1)!=0,cue.sequence(),cue.actionAt(),now,
                        target,cue.targetId());
            } else if(now-cue.actionAt()<=12) view.pool.launch(cue.stage(),cue.sequence(),cue.actionAt(),now,
                    target,cue.targetId(),false);
        } else view.pool.confirm(cue.sequence(),target,cue.targetId(),now);
        bindTargets(view);
        return true;
    }
    private static void bindTargets(View view) {
        for(int i=0;i<view.pool.groups.length;i++) {
            var group=view.pool.groups[i];
            if(group!=view.boundGroups[i]) {view.targets[i]=null;view.boundGroups[i]=group;}
            if(group==null) continue;
            if(view.targets[i]!=null&&view.targets[i].getId()!=group.targetId) view.targets[i]=null;
            if(group.targetId<0) continue;
            // Capture the entity object once; ID reuse after unload cannot retarget a volley.
            if(view.targets[i]==null&&world.getEntity(group.targetId) instanceof LivingEntity living)
                view.targets[i]=living;
        }
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e) {
        if(e.phase!=TickEvent.Phase.END) return;
        syncWorld();
        if(world==null||!enabled()) {VIEWS.clear();DRAW_ORDER.clear();PENDING.clear();return;}
        if(Minecraft.getInstance().isPaused()) return;
        long now=world.getGameTime();
        VIEWS.values().removeIf(v->world.getPlayerByUUID(v.owner.getUUID())!=v.owner||!eligible(v.owner));
        // Once per client tick, current main hands only. Nearest eight win the budget.
        var candidates=new ArrayList<Player>();
        for(Player p:world.players()) if(eligible(p)) candidates.add(p);
        Player local=Minecraft.getInstance().player;
        candidates.sort(Comparator.comparingDouble(p->local==null?0:local.distanceToSqr(p)));
        int owners=Math.min(MAX_OWNERS,candidates.size());
        VIEWS.values().removeIf(v->!candidates.subList(0,owners).contains(v.owner));
        DRAW_ORDER.clear();
        for(int i=0;i<owners;i++) {
            View chosen=view(candidates.get(i));
            if(chosen!=null) DRAW_ORDER.add(chosen);
        }
        var pending=PENDING.iterator();
        while(pending.hasNext()) {
            Pending item=pending.next();
            if(now>item.expires||apply(item.packet)) pending.remove();
        }
        for(View v:VIEWS.values()) {
            if(v.owner.position().distanceToSqr(v.lastRoot)>64) {
                // Teleports/world corrections cancel the view instead of drawing a long ribbon.
                v.pool=new SakuraShardPool(count(),frame(v.owner),now);
                java.util.Arrays.fill(v.targets,null);
            }
            v.lastRoot=v.owner.position();

            for(int i=0;i<v.pool.groups.length;i++) {
                var group=v.pool.groups[i];
                var target=v.targets[i];
                if(group==null) {v.targets[i]=null;continue;}
                if(group.targetId<0) continue;
                if(target==null||target.getId()!=group.targetId||!target.isAlive()||target.isRemoved()
                        ||world.getEntity(target.getId())!=target||target.distanceToSqr(v.owner)>144) {
                    // Finish the committed sweep at its last anchor. The next
                    // attack may acquire a new target; flying shards never snap to it.
                    group.targetId=-1;
                    v.targets[i]=null;
                    group.goal=group.center;
                }
                else group.goal=target.getBoundingBox().getCenter();
            }
            v.pool.tick(frame(v.owner),now,v.anchor.grip(v.owner),v.anchor.tip(v.owner));
        }
    }
    public static void draw(BufferBuilder buffer,Matrix4f matrix,Vec3 eye,float partial,
            float intensity,Frustum frustum) {
        if(world==null||!enabled()) return;
        int quality=ClientVisualConfig.BLADE_COMBAT_VFX_QUALITY.get();
        int budget=quality==0?1024:quality==1?3072:6144;
        for(View view:DRAW_ORDER) {
            if(!eligible(view.owner)||view.owner.distanceToSqr(eye)>distance()*distance()) continue;
            var bounds=view.owner.getBoundingBox().inflate(3);
            for(var group:view.pool.groups) if(group!=null)
                bounds=bounds.minmax(new net.minecraft.world.phys.AABB(group.center,group.center).inflate(2));
            if(!frustum.isVisible(bounds)) continue;
            budget=SakuraShardRenderer.draw(buffer,matrix,eye,view.pool,partial,intensity,quality,budget);
            if(budget<=0) break;
        }
    }
    private static final class View {
        final Player owner;
        final String bladeId;
        final int slot;
        long stanceSequence;
        final SakuraBladeAnchor anchor=new SakuraBladeAnchor();
        SakuraShardPool pool;
        final LivingEntity[] targets=new LivingEntity[SakuraShardPool.MAX_GROUPS];
        final SakuraShardPool.Group[] boundGroups=new SakuraShardPool.Group[SakuraShardPool.MAX_GROUPS];
        Vec3 lastRoot;
        long predictedAt=Long.MIN_VALUE;
        int predictedStage;
        View(Player owner,String id,long time) {
            this.owner=owner; bladeId=id; slot=owner.getInventory().selected;
            lastRoot=owner.position(); pool=new SakuraShardPool(count(),frame(owner),time);
            if(!SakuraStanceController.dispersed(owner.getMainHandItem())) pool.hide();
        }
    }
    private record Pending(AwakenedBladeVfxPacket packet,long expires) {}
    private SakuraCombatVisualController() {}
}
