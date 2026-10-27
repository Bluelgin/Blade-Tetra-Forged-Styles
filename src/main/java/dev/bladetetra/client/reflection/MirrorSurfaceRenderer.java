package dev.bladetetra.client.reflection;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import java.util.function.Consumer;

/** Bounded mirror geometry and projective material. No world capture or ritual clock. */
public final class MirrorSurfaceRenderer {
    private static ShaderInstance shader;
    public static boolean available() { return shader!=null; }
    public static void draw(ReflectionRenderer reflection,MirrorPlane plane,Matrix4f view,Vec3 eye,float alpha) {
        if(alpha<=0) return;
        if(available()&&reflection!=null&&reflection.ready()) textured(reflection,b->{
            var a=plane.point(-1,1).subtract(eye);var c=plane.point(1,-1).subtract(eye);
            vertex(b,view,a,-1,1,alpha);vertex(b,view,plane.point(-1,-1).subtract(eye),-1,-1,alpha);vertex(b,view,c,1,-1,alpha);
            vertex(b,view,a,-1,1,alpha);vertex(b,view,c,1,-1,alpha);vertex(b,view,plane.point(1,1).subtract(eye),1,1,alpha);
        });
        else solid(b->{ // Explicit approximate compatibility material; no fake world image.
            Vec3 a=plane.point(-1,1),c=plane.point(1,-1);
            triangle(b,view,eye,a,plane.point(-1,-1),c,0x83949F,alpha*.85f);
            triangle(b,view,eye,a,c,plane.point(1,1),0x83949F,alpha*.85f);
        });
    }
    public static void seams(MirrorPlane p,Matrix4f view,Vec3 eye,int quality,float crack,float alpha) {
        solid(b->{
            Vec3 a=p.point(-1,-1),c=p.point(1,1),d=p.point(-1,1),e=p.point(1,-1);
            edge(b,view,eye,a,d,.012,0xE7F1F7,alpha);edge(b,view,eye,d,c,.012,0xE7F1F7,alpha);
            edge(b,view,eye,c,e,.012,0xE7F1F7,alpha);edge(b,view,eye,e,a,.012,0xE7F1F7,alpha);
            if(crack<=0) return;
            for(var shard:MirrorShardMesh.shards(quality)) {
                MirrorShardMesh.UV[] uv={shard.a(),shard.b(),shard.c()};
                for(int i=0;i<3;i++) {
                    var u=uv[i];var v=uv[(i+1)%3];
                    double distance=Math.sqrt(Math.pow((u.u()+v.u())*.5,2)+Math.pow((u.v()+v.v())*.5,2));
                    float grow=Math.max(0,Math.min(1,(crack*1.65f-(float)distance)*8));
                    if(grow<=0) continue;
                    Vec3 start=p.point(u.u(),u.v()).add(p.normal().scale(-.008));
                    Vec3 end=p.point(v.u(),v.v()).add(p.normal().scale(-.008));
                    edge(b,view,eye,start,start.lerp(end,grow),.013,0x182329,alpha*.95f);
                }
            }
        });
    }
    static void textured(ReflectionRenderer reflection,Consumer<BufferBuilder> draw) {
        if(!available()||reflection==null||!reflection.ready()) return;
        try(var state=new ReflectionRenderState()) {
            RenderSystem.setShader(()->shader);RenderSystem.setShaderTexture(0,reflection.texture());
            shader.safeGetUniform("ReflectionMat").set(reflection.textureProjection());
            setup();var b=Tesselator.getInstance().getBuilder();
            b.begin(VertexFormat.Mode.TRIANGLES,DefaultVertexFormat.POSITION_TEX_COLOR);
            draw.accept(b);BufferUploader.drawWithShader(b.end());
        }
    }
    static void solid(Consumer<BufferBuilder> draw) {
        try(var state=new ReflectionRenderState()) {
            RenderSystem.setShader(GameRenderer::getPositionColorShader);setup();
            var b=Tesselator.getInstance().getBuilder();b.begin(VertexFormat.Mode.TRIANGLES,DefaultVertexFormat.POSITION_COLOR);
            draw.accept(b);BufferUploader.drawWithShader(b.end());
        }
    }
    private static void setup() {
        RenderSystem.enableDepthTest();RenderSystem.depthMask(true);RenderSystem.disableCull();
        RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();RenderSystem.setShaderColor(1,1,1,1);
    }
    static void vertex(BufferBuilder b,Matrix4f view,Vec3 p,float u,float v,float alpha) {
        b.vertex(view,(float)p.x,(float)p.y,(float)p.z).uv(u,v).color(1f,1f,1f,alpha).endVertex();
    }
    static void triangle(BufferBuilder b,Matrix4f view,Vec3 eye,Vec3 a,Vec3 c,Vec3 d,int color,float alpha) {
        for(var v:new Vec3[]{a,c,d}) {
            var p=v.subtract(eye);b.vertex(view,(float)p.x,(float)p.y,(float)p.z)
                    .color((color>>16)&255,(color>>8)&255,color&255,Math.round(255*alpha)).endVertex();
        }
    }
    static void edge(BufferBuilder b,Matrix4f view,Vec3 eye,Vec3 a,Vec3 c,double width,int color,float alpha) {
        Vec3 side=c.subtract(a).cross(eye.subtract(a)).normalize().scale(width);
        triangle(b,view,eye,a.subtract(side),c.subtract(side),c.add(side),color,alpha);
        triangle(b,view,eye,a.subtract(side),c.add(side),a.add(side),color,alpha);
    }
    @Mod.EventBusSubscriber(modid="blade_tetra",value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        @SubscribeEvent public static void setup(net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent e) {
            if(Boolean.getBoolean("blade_tetra.mirrorPrototype")||Boolean.getBoolean("blade_tetra.mirrorRitualReview"))
                e.enqueueWork(()->{
                    net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(MirrorPrototypeScene.class);
                    if(Boolean.getBoolean("blade_tetra.mirrorPrototype")) net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(ReflectionPrototype.class);
                });
        }
        @SubscribeEvent public static void shaders(RegisterShadersEvent e) {
            MirrorRitualResources.reload();shader=null;
            try { e.registerShader(new ShaderInstance(e.getResourceProvider(),ResourceLocation.fromNamespaceAndPath("blade_tetra","mirror_surface"),
                    DefaultVertexFormat.POSITION_TEX_COLOR),s->shader=s);
            } catch(java.io.IOException | RuntimeException error) {
                com.mojang.logging.LogUtils.getLogger().warn("Mirror surface shader unavailable; using compatibility material",error);
            }
        }
    }
    private MirrorSurfaceRenderer() {}
}
