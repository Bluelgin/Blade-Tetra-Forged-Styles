package dev.bladetetra.client.vfx;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Animation of seven reusable Blockbench meshes. Quality changes decoration, never the marked footprint. */
final class MikageDivineVfxRenderer {
    static void draw(PoseStack p, MultiBufferSource b, MikageDivineVfxEvents.Scene s, float partial, int quality, Vec3 camera) {
        float time = s.age + partial;
        float alpha = Math.min(Mth.clamp(time / 5, 0, 1), Mth.clamp((s.duration - time) / 10, 0, 1));
        float unfold = Mth.clamp(time / 12, .01F, 1);
        Vec3 at = s.previous.lerp(s.at, partial);
        switch (s.kind) {
            case "array" -> {
                model(p,b,"three_sword_anchor",at.add(0,.05,0),0,90,0,5*unfold,5*unfold,1,alpha);
                for (int i=0;i<3;i++) {
                    double angle = -Math.PI/2 + i*Math.PI*2/3;
                    Vec3 node = at.add(Math.cos(angle)*5,0,Math.sin(angle)*5);
                    float descent = (1 - Mth.clamp((time-i*2)/12,0,1))*3;
                    model(p,b,"purification_blade",node.add(0,3+descent+Math.sin(time*.05+i)*.08,0),i*120,0,180,1.5F,1.5F,1.5F,alpha);
                    if (quality>0) model(p,b,"divine_mark",node.add(0,.08,0),0,90,time*.3F,.45F,.45F,1,alpha*.65F);
                }
            }
            case "binding" -> {
                model(p,b,"final_binding",at.add(0,.07,0),0,90,0,6*unfold,6*unfold,1,alpha);
                if (quality>0) model(p,b,"divine_mark",at.add(0,4,0),0,90,-time*.45F,4,4,1,alpha*.5F);
                for (int i=0;i<6;i++) {
                    double angle=i*Math.PI/3;
                    Vec3 node=at.add(Math.cos(angle)*5.1,0,Math.sin(angle)*5.1);
                    float drop=(1-Mth.clamp((time-i)/16,0,1))*5;
                    model(p,b,"purification_blade",node.add(0,4.7+drop,0),i*60,0,180,2.4F,2.4F,2.4F,alpha);
                    if (quality>0) model(p,b,"boundary_cut",node.add(0,3,0),i*60,0,0,.06F,3,1,alpha*.35F);
                }
                billboard(p,b,"divine_mark",at.add(0,s.height*.7,0),camera,1.7F,alpha*.7F);
            }
            case "wall" -> {
                // x = ritual centre, spanning the circular playable island; players can cross.
                model(p,b,"boundary_cut",at.add(0,2.7,0),90,0,0,52*unfold,3.3F,1,alpha*.8F);
                if (quality>0) for (int i=-2;i<=2;i++) model(p,b,"divine_mark",at.add(0,2.7,i*7),90,0,time*.2F,.75F,.75F,1,alpha*.5F);
            }
            case "guard" -> {
                Vec3 centre=at.add(0,s.height*.5,0);
                billboard(p,b,"guard_barrier",centre,camera,1.5F*unfold,alpha);
                if (quality>0) for (int i=0;i<3;i++) model(p,b,"guard_barrier",centre,time+i*60,0,0,1.55F,1.55F,1.55F,alpha*.32F);
                model(p,b,"three_sword_anchor",at.add(0,.06,0),0,90,0,2,2,1,alpha*.65F);
            }
            case "mark" -> billboard(p,b,"divine_mark",at.add(0,s.height*.7,0),camera,1.25F*unfold,alpha*.85F);
            case "hazard" -> {
                float pulse=.6F+.4F*(float)Math.sin(time*.6);
                DivineVfxModel.draw(p,b,"divine_mark",at.add(0,.12,0),0,90,0,2.5F,2.5F,1,alpha*pulse,0xA83242);
                if(time>24) DivineVfxModel.draw(p,b,"boundary_cut",at.add(0,1,0),0,0,0,2.5F,2,1,alpha,0x6D1732);
            }
            case "entrance" -> model(p,b,"boundary_cut",at.add(0,1.5,0),0,0,0,.7F,2.5F,1,alpha);
            case "dash" -> {
                model(p,b,"boundary_cut",at.add(0,1,0),0,0,0,.5F,1.2F,1,alpha*.65F);
                if(quality>0) shards(p,b,at,time,alpha,5,2);
            }
            case "array_end", "anchor_hit" -> {
                model(p,b,"three_sword_anchor",at.add(0,.06,0),0,90,0,5,5,1,alpha*.5F);
                if(quality>0) shards(p,b,at,time,alpha,9,4);
            }
            case "wall_end" -> { if(quality>0) for(int i=-5;i<=5;i++) shards(p,b,at.add(0,1,i*4),time,alpha,3,1.5F); }
            default -> { }
        }
    }
    private static void shards(PoseStack p,MultiBufferSource b,Vec3 at,float time,float alpha,int count,float radius) {
        for(int i=0;i<count;i++) {
            double angle=i*Math.PI*2/count;
            Vec3 point=at.add(Math.cos(angle)*(radius+time*.02),.3+(i%3)*.45+time*.018,Math.sin(angle)*(radius+time*.02));
            model(p,b,"boundary_shard",point,i*73+time*2,20,time*3,1,1,1,alpha*.6F);
        }
    }
    private static void billboard(PoseStack p,MultiBufferSource b,String mesh,Vec3 at,Vec3 camera,float scale,float alpha) {
        Vec3 delta=camera.subtract(at);
        float yaw=(float)Math.toDegrees(Math.atan2(delta.x,delta.z));
        float pitch=-(float)Math.toDegrees(Math.atan2(delta.y,Math.sqrt(delta.x*delta.x+delta.z*delta.z)));
        // Place behind the target from the camera's perspective, preserving its silhouette.
        model(p,b,mesh,at.subtract(delta.normalize().scale(.4)),yaw,pitch,0,scale,scale,1,alpha);
    }
    private static void model(PoseStack p,MultiBufferSource b,String mesh,Vec3 at,float yaw,float pitch,float roll,
            float x,float y,float z,float alpha) {
        DivineVfxModel.draw(p,b,mesh,at,yaw,pitch,roll,x,y,z,alpha,0xFFFFFF);
    }
    private MikageDivineVfxRenderer() {}
}
