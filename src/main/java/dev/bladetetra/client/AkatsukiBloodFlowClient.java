package dev.bladetetra.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.bladetetra.BladeTetra;
import dev.bladetetra.config.ClientVisualConfig;
import dev.bladetetra.easteregg.AkatsukiBloodBlade;
import dev.bladetetra.easteregg.AkatsukiBloodRules;
import mods.flammpfeil.slashblade.event.client.RenderOverrideEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.util.*;

/** Short, directional blood streams tied to accepted payments/refunds and actual sword transforms. */
@Mod.EventBusSubscriber(modid=BladeTetra.MOD_ID,value=Dist.CLIENT)
public final class AkatsukiBloodFlowClient {
    private record Anchor(Vec3 grip,long at) {}
    private static final Map<UUID,Anchor> ANCHORS=new HashMap<>();
    private static final Matrix4f VIEW=new Matrix4f();
    private static Vec3 camera=Vec3.ZERO;
    private static ClientLevel world;
    private static boolean ready;
    private static float intensity() {
        return ClientVisualConfig.ENABLE_BLADE_COMBAT_VFX.get()
                ?ClientVisualConfig.BLADE_COMBAT_VFX_INTENSITY.get().floatValue():0;
    }
    static void capture(Player p,RenderOverrideEvent e,AkatsukiBloodGeometry.Frame frame,float unit,float amount,AkatsukiBloodBlade.Flow flow) {
        if(flow==null||intensity()<=0)return;
        boolean first=SenbonzakuraDispersedRender.firstPersonPass();
        if(!first&&ready) {
            var v=new Matrix4f(VIEW).invert().mul(e.getPoseStack().last().pose()).transformPosition(new Vector3f(frame.grip()));
            Vec3 grip=new Vec3(v.x,v.y,v.z).add(camera);
            if(Double.isFinite(grip.x)&&grip.distanceToSqr(p.position())<16) {
                if(ANCHORS.size()>128)ANCHORS.clear();
                ANCHORS.put(p.getUUID(),new Anchor(grip,p.level().getGameTime()));
            }
        }
        var b=e.getBuffer().getBuffer(AkatsukiBloodRenderType.BLOOD);
        var matrix=e.getPoseStack().last().pose();
        float head=flow.returning()?flow.progress()/.65F:(flow.progress()-.18F)/.82F;
        Vector3f end=frame.point(AkatsukiBloodRules.EXTRA_REACH*amount/unit,1,0,0);
        Vector3f start=new Vector3f(frame.grip());
        // A short tendon beside the actual first-person hilt makes the hand-to-blade transfer visible.
        if(first)start.fma(-.16F/unit,frame.direction());
        Vector3f mid=new Vector3f(start).lerp(end,.45F).fma(frame.width()*.8F,frame.normal());
        streams(b,matrix,vec(start),vec(mid),vec(end),head,flow.returning(),frame.width()*(flow.returning()?.24:.16),
                frame.normal(),(flow.returning()?.75F+.25F*flow.strength():.65F+.35F*flow.strength())*intensity());
    }
    @SubscribeEvent public static void stage(RenderLevelStageEvent e) {
        var mc=Minecraft.getInstance();
        if(world!=mc.level) {world=mc.level;ANCHORS.clear();ready=false;}
        if(e.getStage()==RenderLevelStageEvent.Stage.AFTER_SKY) {
            VIEW.set(e.getPoseStack().last().pose());camera=e.getCamera().getPosition();ready=true;return;
        }
        if(e.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES||world==null||intensity()<=0)return;
        var buffers=mc.renderBuffers().bufferSource();VertexConsumer b=null;
        float partial=e.getPartialTick();double distance=ClientVisualConfig.BLADE_COMBAT_VFX_DISTANCE.get();
        for(Player p:world.players()) {
            if(!p.isAlive()||p.isInvisible()||p.distanceToSqr(camera)>distance*distance
                    ||p==mc.player&&mc.options.getCameraType().isFirstPerson())continue;
            var flow=AkatsukiBloodBlade.flow(p,p.getMainHandItem(),partial);
            var anchor=ANCHORS.get(p.getUUID());
            if(flow==null||anchor==null||world.getGameTime()-anchor.at>1)continue;
            if(b==null)b=buffers.getBuffer(AkatsukiBloodRenderType.BLOOD);
            double yaw=Math.toRadians(net.minecraft.util.Mth.rotLerp(partial,p.yBodyRotO,p.yBodyRot));
            int hand=p.getMainArm()==HumanoidArm.RIGHT?1:-1;
            Vec3 right=new Vec3(Math.cos(yaw)*hand,0,Math.sin(yaw)*hand);
            Vec3 position=new Vec3(net.minecraft.util.Mth.lerp(partial,p.xOld,p.getX()),
                    net.minecraft.util.Mth.lerp(partial,p.yOld,p.getY()),net.minecraft.util.Mth.lerp(partial,p.zOld,p.getZ()));
            Vec3 chest=position.add(0,p.isCrouching()?1.05:1.28,0).add(right.scale(.12));
            Vec3 handPoint=anchor.grip;
            Vec3 elbow=chest.lerp(handPoint,.48).add(right.scale(.15)).add(0,-.08,0);
            float head=flow.returning()?(flow.progress()-.50F)/.50F:flow.progress()/.43F;
            Vec3 normal=right.cross(handPoint.subtract(chest));
            if(normal.lengthSqr()<.001)normal=new Vec3(0,0,1);else normal=normal.normalize();
            streams(b,e.getPoseStack().last().pose(),chest.subtract(camera),elbow.subtract(camera),handPoint.subtract(camera),
                    head,flow.returning(),flow.returning()?.035:.025,new Vector3f((float)normal.x,(float)normal.y,(float)normal.z),(flow.returning()?.75F+.25F*flow.strength():.65F+.35F*flow.strength())*intensity());
        }
        if(b!=null)buffers.endBatch(AkatsukiBloodRenderType.BLOOD);
        ANCHORS.entrySet().removeIf(a->world.getGameTime()-a.getValue().at>2);
    }
    private static Vec3 vec(Vector3f v) {return new Vec3(v.x,v.y,v.z);}
    private static Vec3 curve(Vec3 a,Vec3 c,Vec3 b,double t) {
        return a.scale((1-t)*(1-t)).add(c.scale(2*t*(1-t))).add(b.scale(t*t));
    }
    private static void streams(VertexConsumer b,Matrix4f matrix,Vec3 start,Vec3 control,Vec3 end,
            float phase,boolean reverse,double width,Vector3f n,float strength) {
        int strands=ClientVisualConfig.BLADE_COMBAT_VFX_QUALITY.get()==0?2:3;
        Vec3 side=vec(n).normalize();
        for(int strand=0;strand<strands;strand++) {
            float head=phase*1.4F-strand*.13F;
            for(int section=0;section<8;section++) {
                double t0=head-section*.06,t1=t0-.06;
                if(t0<=0||t1>=1)continue;t0=Math.min(1,t0);t1=Math.max(0,t1);
                double u0=reverse?1-t0:t0,u1=reverse?1-t1:t1;
                double wobble0=Math.sin(u0*Math.PI*2+strand*2)*width*2*Math.sin(u0*Math.PI);
                double wobble1=Math.sin(u1*Math.PI*2+strand*2)*width*2*Math.sin(u1*Math.PI);
                Vec3 a=curve(start,control,end,u0).add(side.scale(wobble0));
                Vec3 c=curve(start,control,end,u1).add(side.scale(wobble1));
                Vec3 w=side.scale(width*(1-section/10D));
                int alpha=(int)(Math.min(1,strength)*230*(1-section/9F));
                quad(b,matrix,a.add(w),c.add(w),c.subtract(w),a.subtract(w),alpha,section==0);
                // Crossed ribbons retain volume when viewed edge-on, including third-person front.
                Vec3 cross=c.subtract(a).cross(side);
                if(cross.lengthSqr()>.00000001) {
                    cross=cross.normalize().scale(width*.55);
                    quad(b,matrix,a.add(cross),c.add(cross),c.subtract(cross),a.subtract(cross),alpha/2,section==0);
                }
            }
        }
    }
    private static void quad(VertexConsumer b,Matrix4f m,Vec3 a,Vec3 c,Vec3 d,Vec3 f,int alpha,boolean hot) {
        for(Vec3 v:new Vec3[]{a,c,d,f})b.vertex(m,(float)v.x,(float)v.y,(float)v.z)
                .color(hot?255:190,hot?92:8,hot?110:35,alpha).endVertex();
    }
    private AkatsukiBloodFlowClient() {}
}
