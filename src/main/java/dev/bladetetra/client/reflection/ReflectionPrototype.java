package dev.bladetetra.client.reflection;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/** Stage-B gate: opt-in development world only. Not enabled in ordinary play. */
public final class ReflectionPrototype {
    private static final ReflectionRenderer RENDERER=new ReflectionRenderer();
    private static MirrorPlane plane;
    private static boolean failed;
    private static int captures;
    public static void beforeWorld(LevelRenderer renderer,PoseStack pose,float partial,long deadline,
            Camera camera,GameRenderer game,LightTexture light,Matrix4f projection) {
        if(!Boolean.getBoolean("blade_tetra.mirrorPrototype")||ReflectionPass.active()||failed||!MirrorPrototypeScene.ready()) return;
        var mc=Minecraft.getInstance(); if(mc.level==null||mc.player==null) return;
        double a=Math.sin((mc.level.getGameTime()+partial)*.014)*.32;
        plane=new MirrorPlane(new Vec3(0,-58.4,4),new Vec3(Math.cos(a),0,Math.sin(a)),new Vec3(0,1,0),1.7,1.7);
        try {
            var target=mc.getMainRenderTarget();var mainEye=camera.getPosition();
            var before=new Matrix4f(com.mojang.blaze3d.systems.RenderSystem.getProjectionMatrix());
            int front=org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL11.GL_FRONT_FACE);
            RENDERER.capture(plane,512,renderer,pose,partial,deadline,camera,game,light,projection);
            if(target!=mc.getMainRenderTarget()||!mainEye.equals(camera.getPosition())
                    ||!before.equals(com.mojang.blaze3d.systems.RenderSystem.getProjectionMatrix())
                    ||front!=org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL11.GL_FRONT_FACE)
                    ||ReflectionPass.active()) throw new IllegalStateException("Reflection state restoration failed");
            if(RENDERER.ready()&&++captures%60==0)
                LogUtils.getLogger().info("MIRROR_PROTOTYPE_CAPTURE_STATE_OK captures={} eye={} reflectedEye={} angle={}",
                        captures,mainEye,plane.reflectPoint(mainEye),a);
        }
        catch(RuntimeException e) {failed=true;RENDERER.close();LogUtils.getLogger().error("MIRROR_PROTOTYPE_FAILED",e);}
    }
    @SubscribeEvent public static void draw(RenderLevelStageEvent e) {
        if(!Boolean.getBoolean("blade_tetra.mirrorPrototype")||failed||plane==null||ReflectionPass.active()
                ||e.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        MirrorSurfaceRenderer.draw(RENDERER,plane,e.getPoseStack().last().pose(),e.getCamera().getPosition(),1);
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e) {
        if(e.phase==TickEvent.Phase.END&&Minecraft.getInstance().level==null&&RENDERER.ready()) {
            RENDERER.close();plane=null;
        }
    }
    public static void release() { RENDERER.close();plane=null;failed=false;captures=0; }
    private ReflectionPrototype() {}
}
