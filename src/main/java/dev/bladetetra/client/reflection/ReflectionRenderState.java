package dev.bladetetra.client.reflection;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

/** Scope for the vanilla secondary pass. Restore both RenderSystem caches and raw GL bindings. */
final class ReflectionRenderState implements AutoCloseable {
    private final VertexSorting sorting=RenderSystem.getVertexSorting();
    private final Matrix4f projection=new Matrix4f(RenderSystem.getProjectionMatrix());
    private final Matrix3f inverse=new Matrix3f(RenderSystem.getInverseViewRotationMatrix());
    private final ShaderInstance shader=RenderSystem.getShader();
    private final float[] color=RenderSystem.getShaderColor().clone(),fog=RenderSystem.getShaderFogColor().clone();
    private final float fogStart=RenderSystem.getShaderFogStart(),fogEnd=RenderSystem.getShaderFogEnd();
    private final com.mojang.blaze3d.shaders.FogShape fogShape=RenderSystem.getShaderFogShape();
    private final int framebuffer=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
    private final int readFramebuffer=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
    private final int front=GL11.glGetInteger(GL11.GL_FRONT_FACE),depthFunc=GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
    private final int srcRgb=GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB),dstRgb=GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
    private final int srcAlpha=GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA),dstAlpha=GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
    private final boolean blend=GL11.glIsEnabled(GL11.GL_BLEND),depth=GL11.glIsEnabled(GL11.GL_DEPTH_TEST),
            cull=GL11.glIsEnabled(GL11.GL_CULL_FACE),depthMask=GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
    private final int[] viewport=new int[4],textures=new int[12];
    ReflectionRenderState() {
        GL11.glGetIntegerv(GL11.GL_VIEWPORT,viewport);
        for(int i=0;i<textures.length;i++) textures[i]=RenderSystem.getShaderTexture(i);
        RenderSystem.getModelViewStack().pushPose();
    }
    @Override public void close() {
        RenderSystem.getModelViewStack().popPose(); RenderSystem.applyModelViewMatrix();
        RenderSystem.setProjectionMatrix(projection,sorting);
        RenderSystem.setInverseViewRotationMatrix(inverse);
        RenderSystem.setShader(()->shader); RenderSystem.setShaderColor(color[0],color[1],color[2],color[3]);
        RenderSystem.setShaderFogStart(fogStart); RenderSystem.setShaderFogEnd(fogEnd);
        RenderSystem.setShaderFogColor(fog[0],fog[1],fog[2],fog[3]); RenderSystem.setShaderFogShape(fogShape);
        for(int i=0;i<textures.length;i++) RenderSystem.setShaderTexture(i,textures[i]);
        if(blend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
        RenderSystem.blendFuncSeparate(srcRgb,dstRgb,srcAlpha,dstAlpha);
        if(depth) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
        if(cull) RenderSystem.enableCull(); else RenderSystem.disableCull();
        RenderSystem.depthMask(depthMask); RenderSystem.depthFunc(depthFunc);
        GL11.glFrontFace(front);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,framebuffer);
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,readFramebuffer);
        RenderSystem.viewport(viewport[0],viewport[1],viewport[2],viewport[3]);
    }
}
