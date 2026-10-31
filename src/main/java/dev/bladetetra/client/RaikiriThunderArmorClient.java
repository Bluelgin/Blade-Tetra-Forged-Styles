package dev.bladetetra.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.bladetetra.BladeTetra;
import dev.bladetetra.config.ClientVisualConfig;
import dev.bladetetra.easteregg.RaikiriThunderArmorEffect;
import dev.bladetetra.network.AwakenedBladeVfxPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import java.util.*;

/** Bounded, cached local-space arcs. Vanilla effects own attachment; hit packets only accent it. */
@Mod.EventBusSubscriber(modid=BladeTetra.MOD_ID,value=Dist.CLIENT)
public final class RaikiriThunderArmorClient {
    private record Stroke(Vec3[] points,float width) {}
    private record Arc(long born,int life,List<Stroke> body,List<Stroke> hand) {}
    private static final class Owner {
        final List<Arc> arcs=new ArrayList<>(4);
        long next,lastPulse=Long.MIN_VALUE;
    }
    private static final Map<UUID,Owner> OWNERS=new HashMap<>();
    private static ClientLevel current;
    private static int cap() {
        return ClientVisualConfig.BLADE_COMBAT_VFX_QUALITY.get()==0?2:4;
    }
    private static void add(Owner owner,RandomSource random,long now,boolean burst) {
        while(owner.arcs.size()>=cap()) owner.arcs.remove(0);
        double angle=random.nextDouble()*Math.PI*2;
        double radius=.42+random.nextDouble()*.12;
        double height=.45+random.nextDouble()*.95;
        Vec3 start=new Vec3(Math.cos(angle)*radius,height,Math.sin(angle)*radius);
        double rise=height>1.1?-.55-random.nextDouble()*.3:.5+random.nextDouble()*.35;
        Vec3 end=start.add(Math.cos(angle)*.2,rise,Math.sin(angle)*.2);
        boolean branch=ClientVisualConfig.BLADE_COMBAT_VFX_QUALITY.get()>0;
        float width=burst?.019F:.014F;
        List<Stroke> body=shape(random,start,end,width,branch);
        // Camera-space lower corners, away from the reticle, also visible while looking straight ahead.
        double side=random.nextBoolean()?1:-1;
        Vec3 handStart=new Vec3(side*(.29+random.nextDouble()*.12),-.22-random.nextDouble()*.16,.65);
        Vec3 handEnd=handStart.add(side*.11,-.20-random.nextDouble()*.12,.06);
        List<Stroke> hand=shape(random,handStart,handEnd,width*.65F,branch);
        owner.arcs.add(new Arc(now,4+random.nextInt(4),body,hand));
    }
    private static List<Stroke> shape(RandomSource random,Vec3 start,Vec3 end,float width,boolean branch) {
        Vec3[] points=new Vec3[7];points[0]=start;points[6]=end;
        for(int i=1;i<6;i++) {
            double noise=Math.sin(i*Math.PI/6)*.12;
            points[i]=start.lerp(end,i/6D).add((random.nextDouble()-.5)*noise*2,
                    (random.nextDouble()-.5)*noise,(random.nextDouble()-.5)*noise*2);
        }
        if(!branch) return List.of(new Stroke(points,width));
        Vec3 fork=points[3];
        Vec3 tip=fork.add((random.nextDouble()-.5)*.5,(random.nextDouble()-.5)*.5,
                (random.nextDouble()-.5)*.5);
        return List.of(new Stroke(points,width),new Stroke(new Vec3[]{fork,
                fork.lerp(tip,.5).add(.04,-.03,.02),tip},width*.6F));
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e) {
        if(e.phase!=TickEvent.Phase.END) return;
        Minecraft mc=Minecraft.getInstance();
        if(current!=mc.level) { OWNERS.clear();current=mc.level; }
        if(mc.level==null) return;
        if(!ClientVisualConfig.ENABLE_BLADE_COMBAT_VFX.get()
                || ClientVisualConfig.BLADE_COMBAT_VFX_INTENSITY.get()<=0) { OWNERS.clear();return; }
        if(mc.isPaused()) return;
        long now=mc.level.getGameTime();
        double range=Math.min(32,ClientVisualConfig.BLADE_COMBAT_VFX_DISTANCE.get());
        int limit=Math.min(16,ClientVisualConfig.BLADE_COMBAT_VFX_MAX_EFFECTS.get());
        Vec3 eye=mc.gameRenderer.getMainCamera().getPosition();
        Set<UUID> visible=new HashSet<>();
        // Prioritize the local player so crowd limits never hide their feedback.
        var players=new ArrayList<>(mc.level.players());
        players.sort(Comparator.comparingDouble(p->p==mc.player?-1:p.distanceToSqr(eye)));
        for(var player:players) {
            if(visible.size()>=limit) break;
            var effect=player.getEffect(RaikiriThunderArmorEffect.ARMOR.get());
            if(effect==null || !player.isAlive() || player.isInvisible() || player.distanceToSqr(eye)>range*range) continue;
            visible.add(player.getUUID());
            Owner owner=OWNERS.get(player.getUUID());
            var random=RandomSource.create(player.getUUID().getLeastSignificantBits() ^ now*0x9E3779B97F4A7C15L);
            if(owner==null) {
                owner=new Owner();OWNERS.put(player.getUUID(),owner);
                // Tracking an already-running buff is not another activation explosion.
                int count=effect.getDuration()>150?cap():1;
                for(int i=0;i<count;i++) add(owner,random,now,true);
                owner.next=now+2;
            }
            owner.arcs.removeIf(a->now-a.born>=a.life || now<a.born);
            while(owner.arcs.size()>cap()) owner.arcs.remove(0);
            if(now>=owner.next) {
                add(owner,random,now,false);
                if(random.nextFloat()<.18F && cap()>2) add(owner,random,now,true);
                owner.next=now+1+random.nextInt(3);
            }
        }
        OWNERS.keySet().retainAll(visible);
    }
    /** Existing successful armor-hit notification; never predicts damage or pays Rank on the client. */
    static void discharge(AwakenedBladeVfxPacket packet) {
        Minecraft mc=Minecraft.getInstance();
        if(mc.level==null || current!=mc.level || packet.soul()!=4
                || packet.type()!=AwakenedBladeVfxPacket.BOLT || packet.variant()!=2) return;
        Vec3 from=new Vec3(packet.x(),packet.y(),packet.z());
        long now=mc.level.getGameTime();
        for(var player:mc.level.players()) {
            Owner owner=OWNERS.get(player.getUUID());
            if(owner==null || owner.lastPulse==now || !player.hasEffect(RaikiriThunderArmorEffect.ARMOR.get())
                    || player.getBoundingBox().getCenter().distanceToSqr(from)>.16) continue;
            owner.lastPulse=now;
            var random=RandomSource.create(player.getUUID().getMostSignificantBits()^now);
            add(owner,random,now,true);add(owner,random,now,true);
            break;
        }
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent e) {
        if(e.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES
                || !ClientVisualConfig.ENABLE_BLADE_COMBAT_VFX.get()) return;
        Minecraft mc=Minecraft.getInstance();
        if(mc.level==null || current!=mc.level || OWNERS.isEmpty()) return;
        float intensity=ClientVisualConfig.BLADE_COMBAT_VFX_INTENSITY.get().floatValue();
        if(intensity<=0) return;
        Vec3 eye=e.getCamera().getPosition();
        double range=Math.min(32,ClientVisualConfig.BLADE_COMBAT_VFX_DISTANCE.get());
        var poses=e.getPoseStack();var buffers=mc.renderBuffers().bufferSource();
        boolean drew=false;
        poses.pushPose();
        try {
            poses.translate(-eye.x,-eye.y,-eye.z);
            Matrix4f matrix=poses.last().pose();
            for(var player:mc.level.players()) {
                Owner owner=OWNERS.get(player.getUUID());
                if(owner==null || !player.isAlive() || player.isInvisible()
                        || !player.hasEffect(RaikiriThunderArmorEffect.ARMOR.get()) || player.distanceToSqr(eye)>range*range) continue;
                boolean first=player==mc.player && mc.options.getCameraType().isFirstPerson();
                if(!first && !e.getFrustum().isVisible(player.getBoundingBox().inflate(.8))) continue;
                Vec3 base=first?eye:new Vec3(Mth.lerp(e.getPartialTick(),player.xo,player.getX()),
                        Mth.lerp(e.getPartialTick(),player.yo,player.getY()),Mth.lerp(e.getPartialTick(),player.zo,player.getZ()));
                float yaw=Mth.rotLerp(e.getPartialTick(),player.yBodyRotO,player.yBodyRot)*Mth.DEG_TO_RAD;
                int rendered=0;
                for(int arcIndex=owner.arcs.size()-1;arcIndex>=0;arcIndex--) {
                    Arc arc=owner.arcs.get(arcIndex);
                    if(first && rendered>=2 || player.distanceToSqr(eye)>16*16 && rendered>=1) break;
                    double age=mc.level.getGameTime()-arc.born+e.getPartialTick();
                    if(age<0 || age>=arc.life) continue;
                    float alpha=(float)Math.min(1,(arc.life-age)/2)*Math.min(1,intensity)*(first?.8F:1);
                    VertexConsumer vertices=buffers.getBuffer(RenderType.lightning());drew=true;
                    for(Stroke stroke:first?arc.hand:arc.body) {
                        for(int i=1;i<stroke.points.length;i++) {
                            Vec3 a=world(stroke.points[i-1],base,yaw,first,e);
                            Vec3 b=world(stroke.points[i],base,yaw,first,e);
                            double taper=1-.55*i/(stroke.points.length-1D);
                            double width=stroke.width*taper;
                            ribbon(vertices,matrix,eye,a,b,width*3,alpha*.22F,false);
                            ribbon(vertices,matrix,eye,a,b,width,alpha,true);
                        }
                    }
                    rendered++;
                }
            }
        } finally {
            try { if(drew) buffers.endBatch(RenderType.lightning()); }
            finally { poses.popPose(); }
        }
    }
    private static Vec3 world(Vec3 p,Vec3 base,float yaw,boolean first,RenderLevelStageEvent e) {
        if(!first) return base.add(p.yRot(-yaw));
        var camera=e.getCamera();
        return base.add(new Vec3(camera.getLeftVector()).scale(-p.x))
                .add(new Vec3(camera.getUpVector()).scale(p.y))
                .add(new Vec3(camera.getLookVector()).scale(p.z));
    }
    private static void ribbon(VertexConsumer out,Matrix4f matrix,Vec3 eye,Vec3 a,Vec3 b,double width,float alpha,boolean core) {
        Vec3 side=b.subtract(a).cross(eye.subtract(a));
        if(side.lengthSqr()<1e-10) return;
        side=side.normalize().scale(width);
        vertex(out,matrix,a.add(side),alpha,core);vertex(out,matrix,b.add(side),alpha,core);
        vertex(out,matrix,b.subtract(side),alpha,core);vertex(out,matrix,a.subtract(side),alpha,core);
    }
    private static void vertex(VertexConsumer out,Matrix4f matrix,Vec3 p,float alpha,boolean core) {
        out.vertex(matrix,(float)p.x,(float)p.y,(float)p.z).color(core?.9F:.25F,core?.98F:.65F,1F,alpha).endVertex();
    }
    private RaikiriThunderArmorClient() {}
}
