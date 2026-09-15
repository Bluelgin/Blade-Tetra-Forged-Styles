package dev.bladetetra.client.vfx;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import static dev.bladetetra.visual.DeadThoughtVisualMath.*;

/** Box-attached slivers: no assumptions about another mod's skeleton, UVs or renderer. */
final class DeadThoughtScarRenderer {
    static void draw(PoseStack p, MultiBufferSource b, LivingEntity target, DeadThoughtVfxClient.Scar scar,
            float partial, int quality, Vec3 camera) {
        float size = scale(target.getBbWidth(), target.getBbHeight());
        Vec3 base = new Vec3(Mth.lerp(partial,target.xo,target.getX()),
                Mth.lerp(partial,target.yo,target.getY()),Mth.lerp(partial,target.zo,target.getZ()));
        Vec3 middle = base.add(0,Math.min(5.5,target.getBbHeight())*.55,0);
        Vec3 facing = camera.subtract(middle).multiply(1,0,1).normalize();
        float yaw = (float)Math.toDegrees(Math.atan2(facing.x,facing.z));
        Vec3 surface = middle.add(facing.scale(Math.min(2.5,target.getBbWidth()*.52)+.04));
        float t = scar.age + partial;
        float flash = 1-ramp(t,3,scar.severity>=2?27:9);
        int layers = quality == 0 ? 1 : scar.severity >= 2 ? 3 : 1;
        for(int i=0;i<layers;i++) {
            DeadThoughtModel.draw(p,b,"scars",surface,yaw,0,0,size,size,1,flash,
                    "scar_"+i,"edge_"+i);
        }
        if(scar.severity>=1) {
            float halo = scar.severity==3 ? .20F : flash*.5F;
            DeadThoughtModel.draw(p,b,"scars",middle.subtract(facing.scale(.25)),yaw,0,25,
                    size*1.1F,size*1.1F,1,halo,"halo");
        }
        if(quality>0 && scar.severity>=2 && flash>.01F) {
            Vec3 edge = surface.add(facing.z*size*.42,0,-facing.x*size*.42);
            DeadThoughtModel.draw(p,b,"rifts",edge,yaw,0,80,size*.4F,size*.7F,1,flash*.75F,
                    "rift_left","rim_left","void_left");
        }
    }
    private DeadThoughtScarRenderer() {}
}
