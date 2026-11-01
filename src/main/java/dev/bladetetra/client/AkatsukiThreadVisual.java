package dev.bladetetra.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.bladetetra.BladeTetra;
import dev.bladetetra.config.ClientVisualConfig;
import dev.bladetetra.easteregg.AkatsukiBloodBlade;
import dev.bladetetra.network.AkatsukiThreadPacket;
import mods.flammpfeil.slashblade.event.client.RenderOverrideEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.*;
import java.util.*;
import java.lang.Math;
import static dev.bladetetra.network.AkatsukiThreadPacket.Phase.*;

/** Animated endpoint-bound ribbons, not free particles. No world entities or screen shader. */
@Mod.EventBusSubscriber(modid=BladeTetra.MOD_ID,value=Dist.CLIENT)
public final class AkatsukiThreadVisual {
    private static final Map<UUID,State> STATES=new LinkedHashMap<>();
    private static final List<Mist> MISTS=new ArrayList<>();
    private static final Map<Integer,Long> MIST_BODIES=new HashMap<>();
    private static final Matrix4f VIEW=new Matrix4f(),PROJECTION=new Matrix4f();
    private static Vec3 camera=Vec3.ZERO;private static ClientLevel world;private static boolean ready;
    private static int vertices;
    private static final class State {
        AkatsukiThreadPacket packet;long received,phaseAt;Vec3 rootOffset;long anchorAt=-100;
        State(AkatsukiThreadPacket packet,long now){this.packet=packet;received=phaseAt=now;}
    }
    private record Mist(Vec3 center,float width,float height,long at,long seed,boolean lethal) {}
    private static float intensity(){return ClientVisualConfig.ENABLE_BLADE_COMBAT_VFX.get()?ClientVisualConfig.BLADE_COMBAT_VFX_INTENSITY.get().floatValue():0;}
    private static void checkWorld() {
        var level=Minecraft.getInstance().level;
        if(world!=level){world=level;STATES.clear();MISTS.clear();MIST_BODIES.clear();ready=false;}
    }
    public static void accept(AkatsukiThreadPacket packet) {
        checkWorld();if(world==null||!packet.valid()||!world.dimension().location().equals(packet.dimension()))return;
        long now=world.getGameTime();var state=STATES.get(packet.owner());
        if(state!=null&&(packet.sequence()<state.packet.sequence()||packet.sequence()==state.packet.sequence()&&packet.age()<state.packet.age()))return;
        if(packet.phase()==MIST||packet.phase()==SNAP) {
            if(intensity()>0)for(var t:packet.targets()) {
                if(MISTS.size()>=32)MISTS.remove(0);
                MISTS.add(new Mist(t.center(),t.width(),t.height(),now,packet.sequence()+t.id(),packet.phase()==MIST));
                if(packet.phase()==MIST)MIST_BODIES.put(t.id(),now+30);
                world.playLocalSound(t.center().x,t.center().y,t.center().z,packet.phase()==MIST?SoundEvents.GENERIC_EXPLODE:SoundEvents.PLAYER_ATTACK_SWEEP,
                        net.minecraft.sounds.SoundSource.PLAYERS,packet.phase()==MIST?.65F:.3F,packet.phase()==MIST?.65F:1.1F,false);
            }
            return;
        }
        if(state==null||packet.sequence()!=state.packet.sequence()) {
            if(packet.phase()==END)return;
            if(STATES.size()>=64)STATES.remove(STATES.keySet().iterator().next());
            STATES.put(packet.owner(),new State(packet,now));return;
        }
        if(packet.phase()!=state.packet.phase())state.phaseAt=now;
        state.packet=packet;state.received=now;
    }
    /** Called inside the real native model draw. Projection correction also covers first person. */
    static void capture(Player owner,RenderOverrideEvent e,AkatsukiBloodGeometry.Frame frame,float unit,float amount) {
        checkWorld();var state=STATES.get(owner.getUUID());if(state==null||!ready||intensity()<=0)return;
        var tag=e.getStack().getTag();if(tag==null||!tag.getCompound(AkatsukiBloodBlade.TAG).hasUUID("binding")
                ||!state.packet.binding().equals(tag.getCompound(AkatsukiBloodBlade.TAG).getUUID("binding")))return;
        boolean first=SenbonzakuraDispersedRender.firstPersonPass();
        var matrix=new Matrix4f(VIEW).invert();
        if(first)matrix.mul(new Matrix4f(PROJECTION).invert().mul(RenderSystem.getProjectionMatrix()));
        matrix.mul(e.getPoseStack().last().pose());
        var point=matrix.transformPosition(frame.point(2.25F*amount/unit,.75F,0,0));
        Vec3 root=new Vec3(point.x,point.y,point.z).add(camera);
        if(!Double.isFinite(root.x)||root.distanceToSqr(owner.position())>64)return;
        state.rootOffset=root.subtract(owner.position());state.anchorAt=world.getGameTime();
        if(first) {
            vertices=0;
            var output=new Matrix4f(e.getPoseStack().last().pose()).mul(matrix.invert()).translate((float)-camera.x,(float)-camera.y,(float)-camera.z);
            draw(owner,state,e.getBuffer().getBuffer(AkatsukiBloodRenderType.BLOOD),output,Minecraft.getInstance().getPartialTick());
        }
    }
    @SubscribeEvent public static void stage(RenderLevelStageEvent e) {
        checkWorld();if(world==null)return;
        if(e.getStage()==RenderLevelStageEvent.Stage.AFTER_SKY){VIEW.set(e.getPoseStack().last().pose());PROJECTION.set(RenderSystem.getProjectionMatrix());camera=e.getCamera().getPosition();ready=true;return;}
        if(e.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES)return;
        long now=world.getGameTime();
        STATES.values().removeIf(s->now-s.received>60||s.packet.phase()==END&&now-s.phaseAt>10);
        MISTS.removeIf(m->now-m.at>30);
        MIST_BODIES.values().removeIf(until->now>until);
        if(intensity()<=0)return;
        var mc=Minecraft.getInstance();var buffers=mc.renderBuffers().bufferSource();var b=buffers.getBuffer(AkatsukiBloodRenderType.BLOOD);
        var matrix=new Matrix4f(e.getPoseStack().last().pose()).translate((float)-camera.x,(float)-camera.y,(float)-camera.z);
        vertices=0;double range=ClientVisualConfig.BLADE_COMBAT_VFX_DISTANCE.get();
        for(var entry:STATES.entrySet()) {
            Player p=world.getPlayerByUUID(entry.getKey());var state=entry.getValue();
            if(p==null||!p.isAlive()||p.isInvisible()||p.distanceToSqr(camera)>range*range||now-state.anchorAt>2
                    ||p==mc.player&&mc.options.getCameraType().isFirstPerson())continue;
            var tag=p.getMainHandItem().getTag();
            if(tag==null||!tag.getCompound(AkatsukiBloodBlade.TAG).hasUUID("binding")
                    ||!state.packet.binding().equals(tag.getCompound(AkatsukiBloodBlade.TAG).getUUID("binding")))continue;
            draw(p,state,b,matrix,e.getPartialTick());
        }
        for(var mist:MISTS)if(mist.center.distanceToSqr(camera)<=range*range)mist(b,matrix,mist,now+e.getPartialTick()-mist.at);
        buffers.endBatch(AkatsukiBloodRenderType.BLOOD);
    }
    private static Vec3 center(AkatsukiThreadPacket.Target t,float partial) {
        var entity=world.getEntity(t.id());
        if(entity!=null&&entity.isAlive())return new Vec3(net.minecraft.util.Mth.lerp(partial,entity.xOld,entity.getX()),
                net.minecraft.util.Mth.lerp(partial,entity.yOld,entity.getY())+entity.getBbHeight()*.5,
                net.minecraft.util.Mth.lerp(partial,entity.zOld,entity.getZ()));
        return t.center();
    }
    private static void draw(Player owner,State s,VertexConsumer b,Matrix4f matrix,float partial) {
        if(s.rootOffset==null)return;
        double time=world.getGameTime()+partial,phaseAge=time-s.phaseAt;
        float fade=s.packet.phase()==RETURN||s.packet.phase()==END?(float)Math.max(0,1-phaseAge/10):1;
        float grow=(float)Math.min(1,(s.packet.age()+time-s.received)/12);
        fade*=grow*intensity();if(fade<=0)return;
        Vec3 position=new Vec3(net.minecraft.util.Mth.lerp(partial,owner.xOld,owner.getX()),net.minecraft.util.Mth.lerp(partial,owner.yOld,owner.getY()),net.minecraft.util.Mth.lerp(partial,owner.zOld,owner.getZ()));
        Vec3 root=position.add(s.rootOffset),forward=owner.getLookAngle();
        double advance=new Vec3(owner.getX()-owner.xOld,0,owner.getZ()-owner.zOld).dot(forward);
        double movementBend=Math.max(-.45,Math.min(.45,advance*1.5));
        Vec3 right=forward.cross(new Vec3(0,1,0)).normalize();if(right.lengthSqr()<.01)right=new Vec3(1,0,0);
        int quality=ClientVisualConfig.BLADE_COMBAT_VFX_QUALITY.get(),count=quality<=0?10:quality==1?20:28,segments=quality<=0?12:20;
        var targets=s.packet.targets();
        for(int i=0;i<count;i++) {
            double seed=i*2.399963+s.packet.sequence()*.17,beat=time*.19+i*1.7;
            AkatsukiThreadPacket.Target target=targets.isEmpty()?null:targets.get(i%targets.size());
            Vec3 end=target==null?root.add(forward.scale(2.0+Math.sin(seed)*.6)).add(right.scale(Math.sin(beat*.33+seed)*1.4)).add(0,Math.cos(beat*.4+seed)*1.2,0):center(target,partial);
            boolean bind=s.packet.phase()==BIND||s.packet.phase()==RETURN;
            double tension=bind?Math.min(1,phaseAge/10):Math.min(1,Math.pow(Math.max(0,Math.sin(beat)),10)+Math.max(0,-movementBend));
            double radius=target==null?.25:Math.max(.3,target.width()*.65);
            Vec3 offset=right.scale(Math.cos(seed)*radius).add(0,Math.sin(seed)*Math.min(1.3,target==null?1:target.height()*.4),0);
            if(!bind)end=end.add(offset.scale(1-tension*.8));
            Vec3 previous=root;
            for(int j=1;j<=segments;j++) {
                double t=j/(double)segments,envelope=Math.sin(Math.PI*t),wave=Math.sin(t*8+beat)*(.3+.2*(1-s.packet.fuel()))*(1-tension*.85);
                Vec3 at=root.lerp(end,t).add(right.scale(envelope*(Math.cos(seed)*(.45+Math.max(0,movementBend))+wave))).add(0,envelope*Math.cos(t*6-beat)*.35*(1-tension),0);
                if(bind&&target!=null) {
                    double wrap=Math.max(0,(t-.45)/.55),angle=seed+wrap*Math.PI*3;
                    if(wrap>0)at=root.lerp(end,.45).lerp(end,wrap).add(right.scale(Math.cos(angle)*radius*wrap*(1-.7*tension))).add(0,Math.sin(angle)*Math.min(1,target.height()*.4)*wrap,Math.sin(angle)*radius*.4*wrap);
                }
                float width=(float)((.027+.013*Math.sin(seed))*(1-.65*t)*(1+.4*tension)*fade);
                line(b,matrix,previous,at,width,116,3,22,(int)(190*fade));
                line(b,matrix,previous,at,width*.27F,255,(int)(35+95*tension),75,(int)(220*fade));
                if(quality>0&&j==segments/2&&i%2==0) {
                    Vec3 branch=at.add(right.scale(Math.sin(seed+time*.12)*.45)).add(0,.25*Math.cos(seed),0);
                    line(b,matrix,at,branch,width*.24F,245,18,50,(int)(160*fade));
                }
                previous=at;
            }
        }
    }
    private static void mist(VertexConsumer b,Matrix4f matrix,Mist m,float age) {
        float life=age/30;if(life<0||life>1)return;float strength=intensity();
        int count=m.lethal?14:4;
        Vec3 right=new Vec3(VIEW.m00(),VIEW.m10(),VIEW.m20()).normalize(),up=new Vec3(VIEW.m01(),VIEW.m11(),VIEW.m21()).normalize();
        for(int i=0;i<count;i++) {
            double seed=i*2.399+m.seed,spread=Math.sqrt(life)*(m.lethal?1.3:.5);
            Vec3 center=m.center.add(Math.cos(seed)*spread*Math.max(.4,m.width),Math.sin(seed*1.7)*spread*Math.min(1.5,m.height)+life*.45,Math.sin(seed)*spread*Math.max(.4,m.width));
            float radius=(m.lethal?.24F:.12F)+life*(m.lethal?.65F:.25F);
            int alpha=(int)((1-life)*(1-life)*(m.lethal?155:65)*strength);
            for(int j=0;j<16;j++) {
                double a=j*Math.PI/8,c=(j+1)*Math.PI/8;
                Vec3 edge=center.add(right.scale(Math.cos(a)*radius)).add(up.scale(Math.sin(a)*radius));
                Vec3 next=center.add(right.scale(Math.cos(c)*radius)).add(up.scale(Math.sin(c)*radius));
                vertex(b,matrix,center,185,9,42,alpha);vertex(b,matrix,edge,95,0,18,0);
                vertex(b,matrix,next,95,0,18,0);vertex(b,matrix,center,185,9,42,alpha);
            }
            if(age<5){Vec3 d=right.scale(Math.cos(seed)).add(up.scale(Math.sin(seed)));line(b,matrix,m.center.subtract(d.scale(.3)),m.center.add(d.scale(.8)),.014F,255,100,130,(int)(180*(1-age/5)*strength));}
        }
    }
    @SubscribeEvent public static void mistBody(net.minecraftforge.client.event.RenderLivingEvent.Pre<?,?> e) {
        if(intensity()<=0||world==null||e.getEntity().level()!=world||e.getEntity().isAlive())return;
        Long until=MIST_BODIES.get(e.getEntity().getId());if(until!=null&&until>=world.getGameTime())e.setCanceled(true);
    }
    private static void line(VertexConsumer b,Matrix4f matrix,Vec3 a,Vec3 c,float width,int r,int g,int blue,int alpha) {
        Vec3 side=c.subtract(a).cross(camera.subtract(a));if(side.lengthSqr()<1e-8)side=new Vec3(0,1,0);side=side.normalize().scale(width);
        vertex(b,matrix,a.subtract(side),r,g,blue,alpha);vertex(b,matrix,c.subtract(side),r,g,blue,alpha);
        vertex(b,matrix,c.add(side),r,g,blue,alpha);vertex(b,matrix,a.add(side),r,g,blue,alpha);
    }
    private static void vertex(VertexConsumer b,Matrix4f matrix,Vec3 v,int r,int g,int blue,int alpha) {
        if(vertices++>=36000)return;
        b.vertex(matrix,(float)v.x,(float)v.y,(float)v.z).color(r,g,blue,Math.min(255,Math.max(0,alpha))).endVertex();
    }
    private AkatsukiThreadVisual() {}
}
