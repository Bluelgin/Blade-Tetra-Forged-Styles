package dev.bladetetra.client.sakura;

import com.mojang.blaze3d.systems.RenderSystem;
import mods.flammpfeil.slashblade.event.client.RenderOverrideEvent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.util.LinkedHashMap;
import java.util.Map;

/** Reads actual native blade endpoints, including first-person projection correction.
 * No model generation, rendering or item writes. Unknown providers use a bounded hand hint.
 */
public final class SakuraBladeAnchor {
    private static final Matrix4f VIEW=new Matrix4f(), PROJECTION=new Matrix4f();
    private static boolean ready;
    private static Vec3 eye=Vec3.ZERO;
    private record Key(mods.flammpfeil.slashblade.client.renderer.model.obj.WavefrontObject model,String target) {}
    private static final Map<Key,float[]> EXTENTS=new LinkedHashMap<>(32,.75F,true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Key,float[]> e) {return size()>128;}
    };
    private Vec3 gripOffset,tipOffset;
    private long capturedAt=-100;
    public static void frame(Matrix4f view,Matrix4f projection,Vec3 camera) {
        VIEW.set(view); PROJECTION.set(projection);eye=camera;ready=true;
    }
    public static void clear() {ready=false;EXTENTS.clear();}
    public void capture(Player owner,RenderOverrideEvent e,boolean firstPerson) {
        if(!ready||e.getOriginalTarget().endsWith("_luminous")) return;
        var key=new Key(e.getOriginalModel(),e.getOriginalTarget());
        float[] bounds=EXTENTS.computeIfAbsent(key,k->bounds(k.model,k.target));
        if(bounds.length==0) return;
        Matrix4f matrix=new Matrix4f(VIEW).invert();
        if(firstPerson) matrix.mul(new Matrix4f(PROJECTION).invert().mul(RenderSystem.getProjectionMatrix()));
        matrix.mul(e.getPoseStack().last().pose());
        Vector3f a=matrix.transformPosition(new Vector3f(bounds[1],bounds[2],bounds[3]));
        Vector3f b=matrix.transformPosition(new Vector3f(bounds[0],bounds[2],bounds[3]));
        Vec3 grip=new Vec3(a.x,a.y,a.z).add(eye),tip=new Vec3(b.x,b.y,b.z).add(eye);
        if(!Double.isFinite(grip.x)||!Double.isFinite(tip.x)||grip.distanceToSqr(owner.position())>16
                ||tip.distanceToSqr(grip)>16||tip.distanceToSqr(grip)<.0001) return;
        gripOffset=grip.subtract(owner.position());tipOffset=tip.subtract(owner.position());
        capturedAt=owner.level().getGameTime();
    }
    private static float[] bounds(mods.flammpfeil.slashblade.client.renderer.model.obj.WavefrontObject model,String group) {
        float min=Float.POSITIVE_INFINITY,max=Float.NEGATIVE_INFINITY,y=0,z=0;int count=0;
        for(var g:model.groupObjects) if(g.name.equals(group)) for(var f:g.faces) {
            boolean steel=f.textureCoordinates!=null&&f.textureCoordinates.length>0;
            if(steel) for(var uv:f.textureCoordinates) steel &= uv.u>=0&&uv.u<=.5&&uv.v>=0&&uv.v<=.25;
            if(!steel) continue;
            for(var v:f.vertices) {min=Math.min(min,v.x);max=Math.max(max,v.x);y+=v.y;z+=v.z;count++;}
        }
        return count==0||max<=min?new float[0]:new float[]{min,max,y/count,z/count};
    }
    public Vec3 grip(Player owner) {
        if(gripOffset!=null&&owner.level().getGameTime()-capturedAt<=2) return owner.position().add(gripOffset);
        return owner.position().add(0,owner.getEyeHeight()*.75,0).add(owner.getLookAngle().scale(.35));
    }
    public Vec3 tip(Player owner) {
        return tipOffset!=null&&owner.level().getGameTime()-capturedAt<=2?owner.position().add(tipOffset):grip(owner).add(0,.9,0);
    }
}
