package dev.bladetetra.client.vfx;

import com.mojang.blaze3d.vertex.BufferBuilder;
import dev.bladetetra.BladeTetra;
import dev.bladetetra.config.ClientVisualConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import static dev.bladetetra.client.vfx.render.VfxPrimitives.*;

/** Existing torii proportions, a closing mirror plane, and the existing mirror-shard atlas. */
final class MikageThousandGatesVfxRenderer {
    static final ResourceLocation SHARDS = new ResourceLocation(BladeTetra.MOD_ID,
            "textures/effect/kyouka/mirror_shards.png");
    static final ResourceLocation SLASH = new ResourceLocation(BladeTetra.MOD_ID,
            "textures/effect/combat/mikage_slash_arc.png");

    static void gate(BufferBuilder b, Matrix4f m, MikageThousandGatesVfxClient.Scene s,
            float partial, Vec3 camera) {
        var d = s.data;
        if (s.kind.equals("slash")) return;
        float t = Mth.clamp((s.age + partial) / d.duration(), 0, 1);
        float intensity = ClientVisualConfig.BLADE_COMBAT_VFX_INTENSITY.get().floatValue();
        float fade = Math.min(1, (1 - t) * 4) * intensity;
        Vec3 at = new Vec3(d.endX(), d.endY(), d.endZ());
        double angle = Math.toRadians(d.yaw());
        Vec3 right = new Vec3(Math.cos(angle), 0, Math.sin(angle));
        double spread = s.kind.equals("enter") ? Math.sin(Math.PI * t) : 1 - t;
        spread = Math.max(.03, spread);
        int red = color(1, .08F, .22F, fade * .8F), white = color(1, .94F, .9F, fade * .8F);
        if (s.kind.equals("break")) {
            ringVertical(b,m,at.add(0,1.2,0),right,.3+t*2.4,.05,
                    color(1,.65F,.18F,fade),32);
            return;
        }
        // Same proportions as BladeTechniqueVfxGeometry.drawTorii, scaled to a character doorway.
        for (int side : new int[]{-1,1}) {
            Vec3 base = at.add(right.scale(side * .81 * spread));
            bandFacing(b,m,base,base.add(0,1.89,0),camera,.065,red);
            bandFacing(b,m,base,base.add(0,1.89,0),camera,.018,white);
        }
        bar(b,m,at.add(0,1.59,0),right,1.05*spread,camera,red,white);
        bar(b,m,at.add(0,1.95,0),right,1.23*spread,camera,red,white);
        Vec3 left = at.add(right.scale(-.76*spread)), r = at.add(right.scale(.76*spread));
        quad(b,m,left.add(0,.02,0),r.add(0,.02,0),r.add(0,1.8,0),left.add(0,1.8,0),
                color(.42F,.65F,1,fade*.13F));
        bandFacing(b,m,at.add(0,.03,0),at.add(0,1.8,0),camera,.018,white);
    }
    private static void bar(BufferBuilder b, Matrix4f m, Vec3 at, Vec3 right,
            double half, Vec3 camera, int red, int white) {
        Vec3 l=at.subtract(right.scale(half)),r=at.add(right.scale(half));
        bandFacing(b,m,l,r,camera,.065,red); bandFacing(b,m,l,r,camera,.018,white);
    }
    static void texture(BufferBuilder b, Matrix4f m, MikageThousandGatesVfxClient.Scene s,
            float partial, Vec3 camera) {
        float t = Mth.clamp((s.age + partial) / s.data.duration(),0,1);
        float alpha = (1-t) * ClientVisualConfig.BLADE_COMBAT_VFX_INTENSITY.get().floatValue();
        int count = ClientVisualConfig.BLADE_COMBAT_VFX_QUALITY.get() <= 0 ? 6 : 12;
        Vec3 origin = new Vec3(s.data.endX(),s.data.endY()+1,s.data.endZ());
        if (s.kind.equals("slash")) {
            double yaw = Math.toRadians(s.data.yaw());
            origin = origin.add(-Math.sin(yaw)*1.7,0,Math.cos(yaw)*1.7);
            rotatedAtlasBillboard(b,m,camera,origin,2.1+t*.25,(float)Math.toRadians(-65),
                    alpha,0xFFFFFF,1,1,0,0);
            return;
        }
        for (int i=0;i<count;i++) {
            double a=i*2.399963+s.data.seed()*.017;
            Vec3 at=origin.add(Math.cos(a)*(t*2+.2),Math.sin(a*1.7)*t*1.5-t*t,
                    Math.sin(a)*(t*2+.2));
            rotatedAtlasBillboard(b,m,camera,at,.15+t*.12,(float)(a+t*3),alpha,
                    0xFFFFFF,4,4,i%4,(i/4)%4);
        }
    }
    private MikageThousandGatesVfxRenderer() {}
}
