package dev.bladetetra.client.reflection;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

/** One reusable target. No ritual clock, entities, networking or background GL work. */
public final class ReflectionRenderer implements AutoCloseable {
    private static RenderTarget redirected;
    public static RenderTarget redirectTarget() { return redirected; }
    private RenderTarget target;
    private final ReflectedCamera reflectedCamera=new ReflectedCamera();
    private final Matrix4f textureProjection=new Matrix4f();
    private boolean ready;
    private int captures,created,destroyed;
    public String status() {return "captures="+captures+", targets="+(created-destroyed)+", ready="+ready;}
    public boolean ready() { return ready; }
    public int texture() { return ready?target.getColorTextureId():-1; }
    public Matrix4f textureProjection() { return new Matrix4f(textureProjection); }

    @FunctionalInterface public interface CaptureContent { void draw(PoseStack view,Camera camera); }
    public boolean capture(MirrorPlane plane, int resolution, LevelRenderer renderer, PoseStack mainPose,
            float partial, long deadline, Camera camera, GameRenderer game, LightTexture light,
            Matrix4f projection) {
        return capture(plane,resolution,renderer,mainPose,partial,deadline,camera,game,light,projection,64,null);
    }
    public boolean capture(MirrorPlane plane,int resolution,LevelRenderer renderer,PoseStack mainPose,
            float partial,long deadline,Camera camera,GameRenderer game,LightTexture light,
            Matrix4f projection,int range,CaptureContent content) {
        RenderSystem.assertOnRenderThread();
        if(ReflectionPass.active()) return false;
        var mc=Minecraft.getInstance();
        if(mc.level==null||mc.getCameraEntity()==null||Math.abs(plane.distance(camera.getPosition()))<.08) return false;
        if(!plane.visible(mainPose.last().pose(),projection,camera.getPosition())) return false;
        RenderTarget main=mc.getMainRenderTarget();
        boolean smartCull=mc.smartCull;
        int width=Math.max(64,Math.min(1024,resolution)),height=Math.min(1024,Math.max(64,Math.round(width*(float)main.height/main.width)));
        Vec3 eye=camera.getPosition(),reflectedEye=plane.reflectPoint(eye);
        Matrix4f view=plane.reflectedView(mainPose.last().pose());
        Matrix4f clipped;
        try { clipped=plane.clippedProjection(projection,view,eye); }
        catch(IllegalArgumentException transientProjection) { return false; }
        var state=new ReflectionRenderState();
        ReflectionPass.enter(reflectedEye,range);
        try {
            if(target==null||target.width!=width||target.height!=height) {
                close(); target=new TextureTarget(width,height,true,Minecraft.ON_OSX);created++;
                target.setFilterMode(GL11.GL_LINEAR);
            }
            reflectedCamera.prepare(mc,camera,plane,partial);
            PoseStack pose=new PoseStack(); pose.mulPoseMatrix(view);
            pose.last().normal().set(new Matrix3f(view));
            mc.smartCull=false;
            redirected=target;
            RenderSystem.depthMask(true); RenderSystem.enableDepthTest();
            target.setClearColor(.04f,.045f,.05f,1); target.clear(Minecraft.ON_OSX); target.bindWrite(true);
            RenderSystem.setProjectionMatrix(clipped,VertexSorting.DISTANCE_TO_ORIGIN);
            RenderSystem.setInverseViewRotationMatrix(new Matrix3f(view).invert());
            GL11.glFrontFace(GL11.GL_CW);
            renderer.prepareCullFrustum(pose,reflectedEye,clipped);
            // Vanilla terrain and entities; no hand, GUI, post-process or recursive stage effects.
            renderer.renderLevel(pose,partial,deadline,false,reflectedCamera,game,light,clipped);
            if(content!=null) content.draw(pose,reflectedCamera);
            mc.renderBuffers().bufferSource().endBatch();
            Vec3 d=plane.center().subtract(reflectedEye),r=plane.right().scale(plane.halfWidth()),u=plane.up().scale(plane.halfHeight());
            Matrix4f local=new Matrix4f(
                    (float)r.x,(float)r.y,(float)r.z,0,
                    (float)u.x,(float)u.y,(float)u.z,0,
                    0,0,1,0,(float)d.x,(float)d.y,(float)d.z,1);
            textureProjection.set(clipped).mul(view).mul(local);
            ready=true;captures++;
            return true;
        } finally {
            redirected=null;
            mc.smartCull=smartCull;
            try { state.close(); }
            finally {
                ReflectionPass.leave();
                renderer.prepareCullFrustum(mainPose,camera.getPosition(),projection);
                mc.getEntityRenderDispatcher().prepare(mc.level,camera,mc.crosshairPickEntity);
                mc.getBlockEntityRenderDispatcher().prepare(mc.level,camera,mc.hitResult);
                com.mojang.blaze3d.vertex.BufferUploader.invalidate();
            }
        }
    }

    @Override public void close() {
        RenderSystem.assertOnRenderThread();
        ready=false;
        if(target!=null) { target.destroyBuffers(); target=null;destroyed++; }
    }
    private static final class ReflectedCamera extends Camera {
        void prepare(Minecraft mc, Camera source, MirrorPlane plane,float partial) {
            setup(mc.level,mc.getCameraEntity(),true,false,partial);
            Vec3 p=plane.reflectPoint(source.getPosition()); setPosition(p.x,p.y,p.z);
            var v=source.getLookVector(); Vec3 look=plane.reflectDirection(new Vec3(v.x(),v.y(),v.z()));
            setRotation((float)Math.toDegrees(Math.atan2(-look.x,look.z)),
                    (float)Math.toDegrees(-Math.asin(Math.max(-1,Math.min(1,look.y)))));
        }
    }
}
