package dev.bladetetra.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.bladetetra.BladeTetra;
import dev.bladetetra.client.awakening.*;
import dev.bladetetra.easteregg.AkatsukiBloodBlade;
import dev.bladetetra.easteregg.AkatsukiBloodRules;
import mods.flammpfeil.slashblade.client.renderer.model.obj.WavefrontObject;
import mods.flammpfeil.slashblade.event.client.RenderOverrideEvent;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;
import java.util.*;

/** Small model-local diamond mesh: no detached entities, shaders or render targets. */
@Mod.EventBusSubscriber(modid=BladeTetra.MOD_ID,value=Dist.CLIENT)
public final class AkatsukiBloodBladeRender {
    private record Key(WavefrontObject model,String target) {}
    private static final Map<Key,Optional<AkatsukiBloodGeometry.Frame>> TIPS=new LinkedHashMap<>(32,.75F,true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Key,Optional<AkatsukiBloodGeometry.Frame>> e) {return size()>64;}
    };
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void render(RenderOverrideEvent e) {
        if(MaterialTextureManager.internalPass()||FloatingBladeRenderer.isRendering()
                ||!(e.getOriginalTarget().equals("blade")||e.getOriginalTarget().equals("blade_damaged")))return;
        var p=SenbonzakuraDispersedRender.owner(e.getStack());
        if(p==null||AwakeningVisualController.presenting(p))return;
        float partial=Minecraft.getInstance().getPartialTick();
        float amount=AkatsukiBloodBlade.visibleFraction(p,e.getStack(),partial);
        var flow=AkatsukiBloodBlade.flow(p,e.getStack(),partial);
        if(amount<=.001F&&flow==null)return;
        var state=e.getStack().getCapability(ItemSlashBlade.BLADESTATE).orElse(null);
        if(state==null)return;
        // Whenever the native held blade is drawn, its remaining blood follows it, including idle/recovery.
        var modelId=state.getModel().orElse(null);
        if(modelId==null||!modelId.getNamespace().equals(BladeTetra.MOD_ID)||!modelId.getPath().startsWith("model/modular/"))return;
        var tip=TIPS.computeIfAbsent(new Key(e.getOriginalModel(),e.getOriginalTarget()),AkatsukiBloodBladeRender::tip).orElse(null);
        if(tip==null)return;
        var matrix=e.getPoseStack().last().pose();
        float unit=matrix.transformDirection(new Vector3f(tip.direction())).length();
        if(!Float.isFinite(unit)||unit<.00001F)return;
        float length=AkatsukiBloodRules.EXTRA_REACH*amount/unit;
        AkatsukiBloodFlowClient.capture(p,e,tip,unit,amount,flow);
        AkatsukiThreadVisual.capture(p,e,tip,unit,amount);
        if(amount<=.001F)return;
        var b=e.getBuffer().getBuffer(AkatsukiBloodRenderType.BLOOD);
        // Six tapered sections keep the point sharp at every charge stage. Root alpha starts at zero.
        float[] positions={-.14F,0,.22F,.55F,.84F,1};
        float[] widths={1,.68F,.65F,.48F,.22F,0};
        for(int i=0;i<positions.length-1;i++)for(int side=0;side<4;side++) {
            Vector3f a=tip.point(length,positions[i],widths[i],side);
            Vector3f c=tip.point(length,positions[i+1],widths[i+1],side);
            Vector3f d=tip.point(length,positions[i+1],widths[i+1],side+1);
            Vector3f f=tip.point(length,positions[i],widths[i],side+1);
            vertex(b,e,a,positions[i],side);vertex(b,e,c,positions[i+1],side);
            vertex(b,e,d,positions[i+1],side+1);vertex(b,e,f,positions[i],side+1);
        }
        // Slim luminous edge ribbons and a pale hot point; the opaque red core remains readable.
        for(int edge:new int[]{0,2})for(int i=0;i<positions.length-1;i++) {
            Vector3f a=tip.point(length,positions[i],widths[i]*1.015F,edge);
            Vector3f c=tip.point(length,positions[i+1],widths[i+1]*1.015F,edge);
            var width=new Vector3f(tip.normal()).mul(tip.thickness()*.25F);
            glow(b,e,new Vector3f(a).sub(width),positions[i]);glow(b,e,new Vector3f(c).sub(width),positions[i+1]);
            glow(b,e,new Vector3f(c).add(width),positions[i+1]);glow(b,e,new Vector3f(a).add(width),positions[i]);
        }
    }
    private static void vertex(VertexConsumer b,RenderOverrideEvent e,Vector3f p,float progress,int side) {
        float hot=AkatsukiBloodRules.smooth((progress-.60F)/.40F);
        int r=(int)(138+117*hot),g=(int)(8+160*hot),blue=(int)(30+155*hot);
        if((side&1)==1&&progress<.6F) {r=82;g=3;blue=18;}
        emit(b,e,p.x,p.y,p.z,r,g,blue,progress<0?0:230);
    }
    private static void glow(VertexConsumer b,RenderOverrideEvent e,Vector3f p,float progress) {
        float hot=AkatsukiBloodRules.smooth((progress-.5F)*2);
        emit(b,e,p.x,p.y,p.z,255,(int)(36+190*hot),(int)(64+170*hot),progress<0?0:220);
    }
    private static void emit(VertexConsumer b,RenderOverrideEvent e,float x,float y,float z,int r,int g,int blue,int a) {
        var pose=e.getPoseStack().last();
        b.vertex(pose.pose(),x,y,z).color(r,g,blue,a).endVertex();
    }
    private static Optional<AkatsukiBloodGeometry.Frame> tip(Key key) {
        var faces=new ArrayList<Vector3f[]>();
        for(var group:key.model.groupObjects)if(group.name.equals(key.target))for(var f:group.faces) {
            if(f.textureCoordinates==null||f.textureCoordinates.length==0)continue;
            boolean steel=true;for(var uv:f.textureCoordinates)steel&=uv.u>=0&&uv.u<=.5F&&uv.v>=0&&uv.v<=.25F;
            if(steel) {
                var vertices=new Vector3f[f.vertices.length];
                for(int i=0;i<vertices.length;i++) {var v=f.vertices[i];vertices[i]=new Vector3f(v.x,v.y,v.z);}
                faces.add(vertices);
            }
        }
        return AkatsukiBloodGeometry.calibrate(faces);
    }
    private AkatsukiBloodBladeRender() {}
}
