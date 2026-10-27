package dev.bladetetra.client.reflection;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/** Projective sampling keeps surface UVs independent of camera, FOV and target aspect ratio. */
public final class MirrorSurfaceRenderer {
    private static ShaderInstance shader;
    public static boolean available() { return shader!=null; }
    public static void draw(ReflectionRenderer reflection,MirrorPlane plane,Matrix4f view,Vec3 eye,float alpha) {
        if(!available()||!reflection.ready()||alpha<=0) return;
        try(var state=new ReflectionRenderState()) {
            RenderSystem.setShader(()->shader); RenderSystem.setShaderTexture(0,reflection.texture());
            shader.safeGetUniform("ReflectionMat").set(reflection.textureProjection());
            RenderSystem.enableDepthTest(); RenderSystem.depthMask(true); RenderSystem.disableCull();
            RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc();
            RenderSystem.setShaderColor(1,1,1,1);
            var b=Tesselator.getInstance().getBuilder(); b.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_TEX_COLOR);
            vertex(b,view,plane.point(-1,1).subtract(eye),-1,1,alpha);
            vertex(b,view,plane.point(-1,-1).subtract(eye),-1,-1,alpha);
            vertex(b,view,plane.point(1,-1).subtract(eye),1,-1,alpha);
            vertex(b,view,plane.point(1,1).subtract(eye),1,1,alpha);
            BufferUploader.drawWithShader(b.end());
        }
    }
    private static void vertex(BufferBuilder b,Matrix4f view,Vec3 p,float u,float v,float alpha) {
        b.vertex(view,(float)p.x,(float)p.y,(float)p.z).uv(u,v).color(1f,1f,1f,alpha).endVertex();
    }
    @Mod.EventBusSubscriber(modid="blade_tetra",value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        @SubscribeEvent public static void shaders(RegisterShadersEvent e) {
            shader=null;
            try { e.registerShader(new ShaderInstance(e.getResourceProvider(),new ResourceLocation("blade_tetra","mirror_surface"),
                    DefaultVertexFormat.POSITION_TEX_COLOR),s->shader=s);
            } catch(java.io.IOException | RuntimeException error) {
                com.mojang.logging.LogUtils.getLogger().warn("Mirror surface shader unavailable",error);
            }
        }
    }
    private MirrorSurfaceRenderer() {}
}
