package dev.bladetetra.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.bladetetra.config.ClientVisualConfig;
import dev.bladetetra.network.AwakenedBladeVfxPacket;
import mods.flammpfeil.slashblade.client.renderer.model.BladeModelManager;
import mods.flammpfeil.slashblade.client.renderer.util.BladeRenderState;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/** Draw helpers inside the existing atlas scheduler; no entities, packets or damage authority. */
final class RaikiriVisualClient {
    private static final ResourceLocation MODEL=new ResourceLocation("slashblade","model/util/ss.obj");
    private static final ResourceLocation TEXTURE=new ResourceLocation("slashblade","model/util/ss.png");
    static void atlas(BufferBuilder b,Matrix4f m,Vec3 eye,AwakenedBladeVfxPacket data,
            float age,int duration,float alpha) {
        Vec3 pos=new Vec3(data.x(),data.y(),data.z());
        double distance=ClientVisualConfig.BLADE_COMBAT_VFX_DISTANCE.get();
        if(eye.distanceToSqr(pos)>distance*distance) return;
        int quality=ClientVisualConfig.BLADE_COMBAT_VFX_QUALITY.get();
        if(data.type()==AwakenedBladeVfxPacket.BOLT) {
            Vec3 end=new Vec3(data.endX(),data.endY(),data.endZ());
            int count=quality==0?2:quality==1?4:6;
            Vec3 prev=pos;
            for(int i=1;i<=count;i++) {
                Vec3 next=pos.lerp(end,i/(double)count);
                if(i<count) next=next.add(Math.sin(i*17+age)*0.12,0,Math.cos(i*13+age)*0.12);
                AwakenedBladeVfxClient.segment(b,m,eye,prev,next,0.08,4,4,alpha);
                prev=next;
            }
            AwakenedBladeVfxClient.billboardTile(b,m,eye,end,0.9,6,4,alpha,0);
        } else if(data.type()==AwakenedBladeVfxPacket.RESONANCE) {
            AwakenedBladeVfxClient.billboardTile(b,m,eye,pos.add(0,0.6,0),
                    0.5+age*0.09,6,4,alpha,age*0.1F);
            for(int i=0;i<(quality==0?2:4);i++) {
                double angle=i*Math.PI/2+age*0.3;
                Vec3 from=pos.add(Math.cos(angle),0.15,Math.sin(angle));
                AwakenedBladeVfxClient.segment(b,m,eye,from,pos.add(0,0.9,0),0.10,4,4,alpha);
            }
        } else if(data.type()==AwakenedBladeVfxPacket.GROUND_SWORD) {
            int count=quality==0?1:quality==1?2:3;
            for(int i=0;i<count;i++) {
                double angle=age*0.11+i*2.1;
                Vec3 at=pos.add(Math.sin(angle)*0.22,0.35+i*0.23,Math.cos(angle)*0.22);
                AwakenedBladeVfxClient.billboardTile(b,m,eye,at,0.35,4,4,alpha*0.65F,(float)angle);
            }
        }
    }
    static void sword(RenderLevelStageEvent event,PoseStack poses,Vec3 eye,
            AwakenedBladeVfxPacket data,float age,int duration,float intensity) {
        Vec3 pos=new Vec3(data.x(),data.y(),data.z());
        double max=ClientVisualConfig.BLADE_COMBAT_VFX_DISTANCE.get();
        if(eye.distanceToSqr(pos)>max*max || !event.getFrustum().isVisible(
                new AABB(pos.add(-0.4,-0.1,-0.4),pos.add(0.4,1.6,0.4)))) return;
        float alpha=Mth.clamp(age/3,0,1)*Mth.clamp((duration-age)/8,0,1)*intensity;
        poses.pushPose();
        try {
            // The native model points along +Z (tip Z=300). Rotate it down,
            // bury its tip by 0.05 blocks; the hilt stays 1.45 blocks above ground.
            poses.translate(pos.x,pos.y+0.85,pos.z);
            poses.mulPose(Axis.XP.rotationDegrees(90));
            poses.scale(0.003F,0.003F,0.003F);
            BladeRenderState.setCol(((int)(alpha*180)<<24)|0xBFEFFF,true);
            var buffers=Minecraft.getInstance().renderBuffers().bufferSource();
            BladeRenderState.renderOverridedLuminous(ItemStack.EMPTY,
                    BladeModelManager.getInstance().getModel(MODEL),"ss",TEXTURE,poses,buffers,0xF000F0);
            buffers.endBatch();
        } finally { BladeRenderState.resetCol();poses.popPose(); }
    }
    private RaikiriVisualClient() {}
}
