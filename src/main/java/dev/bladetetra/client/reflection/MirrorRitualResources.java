package dev.bladetetra.client.reflection;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import dev.bladetetra.client.awakening.*;
import dev.bladetetra.config.ClientVisualConfig;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.*;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.ModList;
import org.joml.Matrix4f;

/** Small bridge from existing ritual sessions to one global reflection budget. No extra tick subscriber. */
public final class MirrorRitualResources {
    private static final ReflectionRenderer REFLECTION=new ReflectionRenderer();
    private static AwakeningSession current;
    private static ClientLevel world;
    private static long lastCapture;
    private static boolean failed, warned;
    private static Boolean modSupport;
    public static int captureCount() {return REFLECTION.captureCount();}
    public static int targetCount() {return REFLECTION.targetCount();}
    public static boolean hasCapture() {return REFLECTION.ready();}
    public static String status() {return REFLECTION.status()+", stateAudits="+ReflectionRenderState.audits();}
    public static void clear() {
        if(current!=null||REFLECTION.ready()) REFLECTION.close();
        current=null;lastCapture=0;
    }
    public static void reload() {
        clear();failed=false;warned=false;
        AwakeningVisualController.clear();ReflectionPrototype.release();
    }
    public static boolean reflecting(AwakeningSession s) { return ReflectionPass.active()&&current==s; }
    private static boolean supported() {
        if(Minecraft.useShaderTransparency()) return false;
        if(modSupport!=null) return modSupport;
        for(String id:new String[]{"oculus","iris","embeddium","rubidium","sodium","imm_ptl","immersive_portals","simply_mirrors"})
            if(ModList.get().isLoaded(id)) {modSupport=false;return false;}
        try {Class.forName("net.optifine.Config",false,MirrorRitualResources.class.getClassLoader());modSupport=false;}
        catch(ClassNotFoundException absent) {modSupport=true;}
        return modSupport;
    }

    public static void beforeWorld(LevelRenderer renderer,PoseStack pose,float partial,long deadline,
            Camera camera,GameRenderer game,LightTexture light,Matrix4f projection) {
        if(ReflectionPass.active()||camera!=game.getMainCamera()) return;
        if(Boolean.getBoolean("blade_tetra.mirrorPrototype")) {
            ReflectionPrototype.beforeWorld(renderer,pose,partial,deadline,camera,game,light,projection);return;
        }
        var mc=Minecraft.getInstance();
        if(world!=mc.level) {clear();world=mc.level;failed=false;warned=false;}
        var candidate=AwakeningVisualController.mirrorCandidate(partial);
        if(candidate==null) {clear();return;}
        if(candidate!=current) {clear();current=candidate;}
        var quality=ReflectionQuality.of(ClientVisualConfig.MIRROR_REFLECTION_QUALITY.get());
        if(quality.level()==0||failed||!MirrorSurfaceRenderer.available()||!supported()) {
            if(REFLECTION.ready()) REFLECTION.close();
            if(!warned) {warned=true;LogUtils.getLogger().info("Kyouka mirror: approximate compatibility material (requested={}, rendererSupported={}, captureFailed={})",quality.level(),supported(),failed);}
            return;
        }
        if(mc.isPaused()&&REFLECTION.ready()) return; // Frozen world: reuse the bounded image until play resumes.
        float tick=current.age(world,partial);
        var frame=frame(current,partial,tick);
        if(!frame.live()||frame.broken()||!quality.due(System.nanoTime(),lastCapture)
                ||frame.plane().center().distanceToSqr(camera.getPosition())>48*48
                ||!frame.plane().visible(pose.last().pose(),projection,camera.getPosition())) return;
        var session=current;
        try {
            if(REFLECTION.capture(frame.plane(),quality.width(),renderer,pose,partial,deadline,camera,game,light,projection,quality.range(),
                    (view,virtual)-> {
                        view.pushPose();
                        try {
                            var eye=virtual.getPosition();view.translate(-eye.x,-eye.y,-eye.z);
                            FloatingBladeRenderer.renderReflected(session,view,mc.renderBuffers().bufferSource(),partial,tick);
                        } finally {view.popPose();}
                    })) lastCapture=System.nanoTime();
        } catch(RuntimeException error) {
            failed=true;REFLECTION.close();
            LogUtils.getLogger().warn("Kyouka reflection capture failed; disabling captures until world/reload, keeping native main pipeline",error);
        }
    }
    public static MirrorRitualController.Frame frame(AwakeningSession s,float partial,float tick) {
        return MirrorRitualController.frame(tick,FloatingBladeRenderer.root(s,partial),FloatingBladeRenderer.forward(s,partial),s.owner.getEyeHeight());
    }
    public static void draw(AwakeningSession s,Matrix4f view,Vec3 eye,float partial) {
        float tick=s.age((ClientLevel)s.owner.level(),partial);
        var frame=frame(s,partial,tick);if(!frame.live()) return;
        int quality=ClientVisualConfig.MIRROR_REFLECTION_QUALITY.get();
        float alpha=frame.opacity()*Math.min(1,ClientVisualConfig.BLADE_COMBAT_VFX_INTENSITY.get().floatValue());
        ReflectionRenderer reflection=s==current?REFLECTION:null;
        if(!frame.broken()) {
            MirrorSurfaceRenderer.draw(reflection,frame.plane(),view,eye,alpha);
            MirrorSurfaceRenderer.seams(frame.plane(),view,eye,quality,frame.crack(),alpha);
            if(tick>=42&&tick<75) for(int side:new int[]{-1,1}) {
                var p=frame.plane();var small=new MirrorPlane(p.center().add(p.right().scale(side*1.95)),p.right(),p.up(),.28,.7);
                MirrorSurfaceRenderer.draw(reflection,small,view,eye,alpha*.32f);
            }
        } else MirrorShardRenderer.draw(reflection,frame.plane(),view,eye,quality,frame.scatter(),frame.gather(),
                FloatingBladeRenderer.position(s,partial,tick),alpha);
    }
    private MirrorRitualResources() {}
}
