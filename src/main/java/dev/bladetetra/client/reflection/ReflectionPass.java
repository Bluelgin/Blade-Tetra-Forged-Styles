package dev.bladetetra.client.reflection;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Render-thread scope; no asynchronous GL or reflection job queue. */
public final class ReflectionPass {
    private static boolean active;
    private static Vec3 eye=Vec3.ZERO;
    private static int range;
    private static Runnable restoreFog;
    public static boolean active() { return active; }
    public static boolean outside(AABB box) {
        double x=Math.max(box.minX,Math.min(eye.x,box.maxX))-eye.x;
        double y=Math.max(box.minY,Math.min(eye.y,box.maxY))-eye.y;
        double z=Math.max(box.minZ,Math.min(eye.z,box.maxZ))-eye.z;
        return x*x+y*y+z*z>range*range;
    }
    public static void saveFog(Runnable restore) { if(restoreFog==null) restoreFog=restore; }
    static void enter(Vec3 origin,int distance) {
        if(active) throw new IllegalStateException("Recursive world reflection");
        eye=origin;range=Math.max(8,Math.min(64,distance));active=true;
    }
    static void leave() {
        try { if(restoreFog!=null) restoreFog.run(); }
        finally {restoreFog=null;active=false;eye=Vec3.ZERO;range=0;}
    }
    private ReflectionPass() {}
}
