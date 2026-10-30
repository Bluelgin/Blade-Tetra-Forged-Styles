package dev.bladetetra.easteregg;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Pure geometry, timing and conservative budgets. No world or input authority. */
public final class RaikiriRules {
    public record Point(double x, double y, double z) {
        public boolean finite() { return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z); }
        public Point lerp(Point b, double t) { return new Point(x+(b.x-x)*t, y+(b.y-y)*t, z+(b.z-z)*t); }
    }
    public static Point closest(Point p, Point a, Point b) {
        double x=b.x-a.x, y=b.y-a.y, z=b.z-a.z;
        double length=x*x+y*y+z*z;
        double t=length == 0 ? 0 : Math.max(0, Math.min(1,
                ((p.x-a.x)*x+(p.y-a.y)*y+(p.z-a.z)*z)/length));
        return a.lerp(b,t);
    }
    public static double distanceSquared(Point p, Point a, Point b) {
        if (!p.finite() || !a.finite() || !b.finite()) return Double.POSITIVE_INFINITY;
        Point q=closest(p,a,b);
        return (p.x-q.x)*(p.x-q.x)+(p.y-q.y)*(p.y-q.y)+(p.z-q.z)*(p.z-q.z);
    }
    public static boolean continuous(Point a,Point b,long before,long now,double maxDistance) {
        return a.finite() && b.finite() && now-before==1 && maxDistance>0 && Double.isFinite(maxDistance)
                && distanceSquared(b,a,a)<=maxDistance*maxDistance;
    }
    public static int delay(int index, int count, int start, int end) {
        return start + (count <= 1 ? 0 : index * (end-start)/(count-1));
    }
    /** Includes the last observation's delay and a small scheduler margin. */
    public static long trailExpiry(long started,int observationTicks,int delayStart,int delayEnd) {
        return started + observationTicks + Math.max(delayStart,delayEnd) + 2L;
    }
    public static boolean landing(boolean armed, boolean ground, boolean supported, long age) {
        return armed && ground && supported && age >= 0 && age <= 100;
    }
    public static final class Charge {
        private final java.util.Set<UUID> steps=new java.util.HashSet<>();
        private boolean charged;
        public boolean resonate(UUID sequence) {
            if (sequence == null || steps.size() >= 64 || !steps.add(sequence)) return false;
            charged=true; // Saturates at one; never extends anchor lifetime.
            return true;
        }
        public boolean charged() { return charged; }
        public boolean consume() { boolean value=charged; charged=false; return value; }
    }
    /** Attempts consume budget even if protected/cancelled: cannot retry into unlimited work. */
    public static final class Budget {
        private float remaining;
        private final float perTarget;
        private final Map<UUID, Float> used = new HashMap<>();
        public Budget(float total, float perTarget) {
            remaining=Float.isFinite(total) ? Math.max(0,total) : 0;
            this.perTarget=Float.isFinite(perTarget) ? Math.max(0,perTarget) : 0;
        }
        public float reserve(UUID target, float requested) {
            if (target == null || !Float.isFinite(requested) || requested <= 0) return 0;
            float amount=Math.max(0,Math.min(requested,Math.min(remaining,perTarget-used.getOrDefault(target,0F))));
            if (amount > 0) { remaining-=amount; used.merge(target,amount,Float::sum); }
            return amount;
        }
        public float remaining() { return remaining; }
    }
    private RaikiriRules() {}
}
