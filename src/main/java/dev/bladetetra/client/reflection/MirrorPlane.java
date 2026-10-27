package dev.bladetetra.client.reflection;

import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/** Immutable plane/basis, independent of rituals and GL. All positions are world coordinates. */
public record MirrorPlane(Vec3 center, Vec3 right, Vec3 up, double halfWidth, double halfHeight) {
    public MirrorPlane {
        if (!finite(center) || !finite(right) || !finite(up) || right.lengthSqr()<1e-12
                || up.lengthSqr()<1e-12 || !Double.isFinite(right.lengthSqr()) || !Double.isFinite(up.lengthSqr())
                || Math.abs(right.normalize().dot(up.normalize()))>1e-5
                || !Double.isFinite(halfWidth) || !Double.isFinite(halfHeight)
                || halfWidth<=0 || halfHeight<=0) throw new IllegalArgumentException("Invalid mirror basis");
        right=right.normalize(); up=up.normalize();
    }
    private static boolean finite(Vec3 p) {
        return Double.isFinite(p.x)&&Double.isFinite(p.y)&&Double.isFinite(p.z);
    }
    public Vec3 normal() { return right.cross(up).normalize(); }
    public double distance(Vec3 p) { return p.subtract(center).dot(normal()); }
    public Vec3 reflectPoint(Vec3 p) { return p.subtract(normal().scale(2*distance(p))); }
    public Vec3 reflectDirection(Vec3 d) { return d.subtract(normal().scale(2*d.dot(normal()))); }
    public Vec3 point(double u,double v) {
        return center.add(right.scale(u*halfWidth)).add(up.scale(v*halfHeight));
    }
    /** Conservative homogeneous frustum test; never render an off-screen/behind-camera surface. */
    public boolean visible(Matrix4f view,Matrix4f projection,Vec3 eye) {
        Matrix4f vp=new Matrix4f(projection).mul(view);
        Vector4f[] corners=new Vector4f[4];int i=0;
        for(int u:new int[]{-1,1}) for(int v:new int[]{-1,1}) {
            Vec3 p=point(u,v).subtract(eye);
            var clip=vp.transform(new Vector4f((float)p.x,(float)p.y,(float)p.z,1));
            if(!Float.isFinite(clip.x)||!Float.isFinite(clip.y)||!Float.isFinite(clip.z)||!Float.isFinite(clip.w)) return false;
            corners[i++]=clip;
        }
        for(int side=0;side<7;side++) {
            boolean outside=true;
            for(var p:corners) {
                boolean out=switch(side) {
                    case 0->p.w<=.001f;case 1->p.x < -p.w;case 2->p.x > p.w;
                    case 3->p.y < -p.w;case 4->p.y > p.w;
                    case 5->p.z < -p.w;default->p.z > p.w;
                };
                if(!out) {outside=false;break;}
            }
            if(outside) return false;
        }
        return true;
    }
    /** A reflected camera has opposite handedness. Never replace this with yaw/pitch alone. */
    public Matrix4f reflectedView(Matrix4f view) {
        Vec3 n=normal(); float x=(float)n.x,y=(float)n.y,z=(float)n.z;
        Matrix4f reflection=new Matrix4f(
                1-2*x*x,-2*x*y,-2*x*z,0,
                -2*y*x,1-2*y*y,-2*y*z,0,
                -2*z*x,-2*z*y,1-2*z*z,0,
                0,0,0,1);
        return new Matrix4f(view).mul(reflection);
    }
    /** Oblique near plane, retaining only the real observer's side of the mirror. */
    public Matrix4f clippedProjection(Matrix4f projection, Matrix4f reflectedView, Vec3 eye) {
        Vec3 n=normal().scale(distance(eye)>=0?1:-1), reflectedEye=reflectPoint(eye);
        Vector4f plane=new Vector4f((float)n.x,(float)n.y,(float)n.z,
                (float)(n.dot(reflectedEye.subtract(center))-.015));
        new Matrix4f(reflectedView).invert().transpose().transform(plane);
        Vector4f corner=new Vector4f(Math.copySign(1,plane.x),Math.copySign(1,plane.y),1,1);
        new Matrix4f(projection).invert().transform(corner);
        float denominator=plane.dot(corner);
        if (!Float.isFinite(denominator)||Math.abs(denominator)<1e-5)
            throw new IllegalArgumentException("Degenerate reflection clip plane: denominator="+denominator
                    +" eye="+eye+" view="+reflectedView+" projection="+projection);
        plane.mul(2/denominator);
        return new Matrix4f(projection).m02(plane.x-projection.m03())
                .m12(plane.y-projection.m13()).m22(plane.z-projection.m23())
                .m32(plane.w-projection.m33());
    }
}
