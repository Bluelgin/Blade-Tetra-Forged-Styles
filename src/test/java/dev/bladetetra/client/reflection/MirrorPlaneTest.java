package dev.bladetetra.client.reflection;

import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MirrorPlaneTest {
    private MirrorPlane plane(double angle) {
        return new MirrorPlane(new Vec3(3,2,8),new Vec3(Math.cos(angle),0,Math.sin(angle)),
                new Vec3(0,1,0),2,3);
    }
    @Test void reflectionIsAnInvolutionAndFlipsOnlyNormalDistance() {
        Vec3 p=new Vec3(2,4,-1);
        for(double a:new double[]{0,.3,1.7,Math.PI}) {
            var m=plane(a); var r=m.reflectPoint(p);
            assertEquals(0,p.distanceTo(m.reflectPoint(r)),1e-10);
            assertEquals(-m.distance(p),m.distance(r),1e-10);
            assertEquals(p.subtract(m.center()).dot(m.right()),r.subtract(m.center()).dot(m.right()),1e-10);
            assertEquals(-1,m.reflectedView(new Matrix4f()).determinant(),1e-5);
        }
    }
    @Test void surfaceProjectsToSamePixelFromBothCamerasIncludingRotationAndFov() {
        Vec3 eye=new Vec3(0,2,0);
        for(double a:new double[]{0,.3,-.5}) for(float fov:new float[]{45,70,110}) {
            var m=plane(a); var view=new Matrix4f().rotateY(.12f).rotateX(.08f);
            var projection=new Matrix4f().perspective((float)Math.toRadians(fov),1.7f,.05f,128);
            for(double u:new double[]{-1,0,1}) for(double v:new double[]{-1,.25,1}) {
                Vec3 p=m.point(u,v), reflectedEye=m.reflectPoint(eye);
                var original=new Vector4f((float)(p.x-eye.x),(float)(p.y-eye.y),(float)(p.z-eye.z),1);
                var reflected=new Vector4f((float)(p.x-reflectedEye.x),(float)(p.y-reflectedEye.y),(float)(p.z-reflectedEye.z),1);
                projection.transform(view.transform(original));
                projection.transform(m.reflectedView(view).transform(reflected));
                assertEquals(original.x/original.w,reflected.x/reflected.w,1e-5);
                assertEquals(original.y/original.w,reflected.y/reflected.w,1e-5);
            }
        }
    }
    @Test void obliqueClipRejectsBehindMirrorAndPreservesScreenCoordinates() {
        var m=new MirrorPlane(new Vec3(0,0,-4),new Vec3(1,0,0),new Vec3(0,1,0),2,3);
        Vec3 eye=Vec3.ZERO, reflectedEye=m.reflectPoint(eye);
        var view=m.reflectedView(new Matrix4f());
        var projection=new Matrix4f().perspective(1.2f,1.7f,.05f,128);
        var clipped=m.clippedProjection(projection,view,eye);
        for(int side:new int[]{-1,1}) {
            Vec3 p=m.center().add(0,0,side);
            var camera=new Vector4f((float)(p.x-reflectedEye.x),0,(float)(p.z-reflectedEye.z),1);
            var before=projection.transform(view.transform(new Vector4f(camera)));
            var after=clipped.transform(view.transform(new Vector4f(camera)));
            assertEquals(before.x/before.w,after.x/after.w,1e-6);
            assertEquals(before.y/before.w,after.y/after.w,1e-6);
            assertEquals(side>0,after.z>=-after.w);
        }
    }
    @Test void worldBorderCoordinatesKeepPlaneDistanceStable() {
        var m=new MirrorPlane(new Vec3(29999900,120,-29999900),new Vec3(.7,0,.3).normalize(),new Vec3(0,1,0),2,3);
        Vec3 eye=m.center().add(1.2,1.6,-3.8);
        assertEquals(-m.distance(eye),m.distance(m.reflectPoint(eye)),1e-8);
        assertEquals(0,eye.distanceTo(m.reflectPoint(m.reflectPoint(eye))),1e-8);
    }
    @Test void visibilityRejectsBehindCameraAndOffScreenMirrors() {
        var projection=new Matrix4f().perspective(1.2f,1.7f,.05f,128);
        var up=new Vec3(0,1,0);var right=new Vec3(1,0,0);
        assertTrue(new MirrorPlane(new Vec3(0,0,-4),right,up,1,1).visible(new Matrix4f(),projection,Vec3.ZERO));
        assertFalse(new MirrorPlane(new Vec3(0,0,4),right,up,1,1).visible(new Matrix4f(),projection,Vec3.ZERO));
        assertFalse(new MirrorPlane(new Vec3(100,0,-4),right,up,1,1).visible(new Matrix4f(),projection,Vec3.ZERO));
    }
    @Test void transientInvalidProjectionDoesNotScheduleWorldRendering() {
        var mirror=plane(0);
        assertFalse(mirror.visible(new Matrix4f(),new Matrix4f().m00(Float.NaN),Vec3.ZERO));
    }
    @Test void invalidGeometryFailsBeforeAnyGlWork() {
        assertThrows(IllegalArgumentException.class,()->new MirrorPlane(Vec3.ZERO,Vec3.ZERO,new Vec3(0,1,0),1,1));
        assertThrows(IllegalArgumentException.class,()->new MirrorPlane(Vec3.ZERO,new Vec3(1,0,0),new Vec3(1,0,0),1,1));
    }
}
