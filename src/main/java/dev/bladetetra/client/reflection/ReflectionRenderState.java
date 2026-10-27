package dev.bladetetra.client.reflection;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import dev.bladetetra.mixin.client.ReflectionLightingAccessor;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL13;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

/** Scope for the vanilla secondary pass. Restore both RenderSystem caches and raw GL bindings. */
final class ReflectionRenderState implements AutoCloseable {
    private final VertexSorting sorting=RenderSystem.getVertexSorting();
    private static final boolean AUDIT=Boolean.getBoolean("blade_tetra.mirrorRitualReview")||Boolean.getBoolean("blade_tetra.mirrorPrototype");
    private static long audits;
    public static long audits() {return audits;}
    private final Matrix4f modelView=new Matrix4f(RenderSystem.getModelViewStack().last().pose());
    private final Matrix3f modelNormal=new Matrix3f(RenderSystem.getModelViewStack().last().normal());
    private final Matrix4f textureMatrix=new Matrix4f(RenderSystem.getTextureMatrix());
    private final float glint=RenderSystem.getShaderGlintAlpha();
    private final Vector3f light0=copy(ReflectionLightingAccessor.bladeTetra$lightDirections()[0]);
    private final Vector3f light1=copy(ReflectionLightingAccessor.bladeTetra$lightDirections()[1]);
    private static Vector3f copy(Vector3f v) {return v==null?null:new Vector3f(v);}
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
    private final int activeTexture=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
    private final boolean scissor=GL11.glIsEnabled(GL11.GL_SCISSOR_TEST),polygonOffset=GL11.glIsEnabled(GL11.GL_POLYGON_OFFSET_FILL);
    private final float polygonFactor=GL11.glGetFloat(GL11.GL_POLYGON_OFFSET_FACTOR),polygonUnits=GL11.glGetFloat(GL11.GL_POLYGON_OFFSET_UNITS);
    private final float lineWidth=RenderSystem.getShaderLineWidth();
    private final int equationRgb=GL11.glGetInteger(GL20.GL_BLEND_EQUATION_RGB),equationAlpha=GL11.glGetInteger(GL20.GL_BLEND_EQUATION_ALPHA);
    private final int[] viewport=new int[4],scissorBox=new int[4],textures=new int[12],bindings=new int[12];
    private final float[] clearColor=new float[4];
    private final boolean[] colorMask=new boolean[4];
    ReflectionRenderState() {
        GL11.glGetIntegerv(GL11.GL_VIEWPORT,viewport);
        GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX,scissorBox);GL11.glGetFloatv(GL11.GL_COLOR_CLEAR_VALUE,clearColor);
        try(var stack=MemoryStack.stackPush()) {
            var mask=stack.malloc(4);GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK,mask);
            for(int i=0;i<4;i++) colorMask[i]=mask.get(i)!=0;
        }
        for(int i=0;i<textures.length;i++) {
            textures[i]=RenderSystem.getShaderTexture(i);RenderSystem.activeTexture(GL13.GL_TEXTURE0+i);
            bindings[i]=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        }
        RenderSystem.activeTexture(activeTexture);
        RenderSystem.getModelViewStack().pushPose();
    }
    /** A destroyed texture must not be rebound (which could recreate it or cause a GL error). */
    void excludeTexture(int id) {
        if(id<=0) return;
        for(int i=0;i<textures.length;i++) {
            if(textures[i]==id) textures[i]=0;
            if(bindings[i]==id) bindings[i]=0;
        }
    }
    @Override public void close() {
        RenderSystem.getModelViewStack().popPose();
        RenderSystem.getModelViewStack().last().pose().set(modelView);RenderSystem.getModelViewStack().last().normal().set(modelNormal);
        RenderSystem.applyModelViewMatrix();
        RenderSystem.setProjectionMatrix(projection,sorting);
        RenderSystem.setTextureMatrix(textureMatrix);RenderSystem.setShaderGlintAlpha(glint);
        RenderSystem.setShaderLights(light0,light1);
        RenderSystem.setInverseViewRotationMatrix(inverse);
        RenderSystem.setShader(()->shader); RenderSystem.setShaderColor(color[0],color[1],color[2],color[3]);
        RenderSystem.setShaderFogStart(fogStart); RenderSystem.setShaderFogEnd(fogEnd);
        RenderSystem.setShaderFogColor(fog[0],fog[1],fog[2],fog[3]); RenderSystem.setShaderFogShape(fogShape);
        for(int i=0;i<textures.length;i++) {
            RenderSystem.setShaderTexture(i,textures[i]);RenderSystem.activeTexture(GL13.GL_TEXTURE0+i);RenderSystem.bindTexture(bindings[i]);
        }
        RenderSystem.activeTexture(activeTexture);
        RenderSystem.clearColor(clearColor[0],clearColor[1],clearColor[2],clearColor[3]);
        RenderSystem.colorMask(colorMask[0],colorMask[1],colorMask[2],colorMask[3]);
        RenderSystem.enableScissor(scissorBox[0],scissorBox[1],scissorBox[2],scissorBox[3]);
        if(!scissor) RenderSystem.disableScissor();
        RenderSystem.polygonOffset(polygonFactor,polygonUnits);
        if(polygonOffset) RenderSystem.enablePolygonOffset();else RenderSystem.disablePolygonOffset();
        RenderSystem.lineWidth(lineWidth);GL20.glBlendEquationSeparate(equationRgb,equationAlpha);
        if(blend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
        RenderSystem.blendFuncSeparate(srcRgb,dstRgb,srcAlpha,dstAlpha);
        if(depth) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
        if(cull) RenderSystem.enableCull(); else RenderSystem.disableCull();
        RenderSystem.depthMask(depthMask); RenderSystem.depthFunc(depthFunc);
        GL11.glFrontFace(front);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,framebuffer);
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,readFramebuffer);
        RenderSystem.viewport(viewport[0],viewport[1],viewport[2],viewport[3]);
        if(AUDIT) {
            int[] restoredViewport=new int[4];GL11.glGetIntegerv(GL11.GL_VIEWPORT,restoredViewport);
            if(GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)!=framebuffer
                    ||GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING)!=readFramebuffer
                    ||GL11.glGetInteger(GL11.GL_FRONT_FACE)!=front
                    ||GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK)!=depthMask
                    ||GL11.glIsEnabled(GL11.GL_SCISSOR_TEST)!=scissor
                    ||!java.util.Arrays.equals(viewport,restoredViewport)
                    ||!RenderSystem.getProjectionMatrix().equals(projection)
                    ||!RenderSystem.getTextureMatrix().equals(textureMatrix)
                    ||RenderSystem.getShader()!=shader)
                throw new IllegalStateException("Mirror render state restoration audit failed");
            audits++;
        }
    }
}
