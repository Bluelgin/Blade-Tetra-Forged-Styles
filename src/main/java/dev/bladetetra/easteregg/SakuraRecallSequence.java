package dev.bladetetra.easteregg;

/** Shared phase clock and tuning defaults; no entity or rendering dependencies. */
public final class SakuraRecallSequence {
    public static final int APPROACH=8, BASE_CHARGE=24, TIER_TICKS=12, LOCK=6, RUSH=2, BLOOM=3, RETURN=12;
    public static final double SEARCH_RANGE=8, LEASH=6, TRACK_STEP=.25;
    public static int release(int tier) {return APPROACH+BASE_CHARGE+TIER_TICKS*Math.max(0,Math.min(3,tier));}
    public static int impact(int tier) {return release(tier)+RUSH;}
    public static int end(int tier) {return impact(tier)+BLOOM+RETURN;}
    public static double multiplier(int tier) {return switch(tier) {case 1->6.5;case 2->10;case 3->14;default->4;};}
    public static int burnRelease(long points,int rate) {
        return points<=0?release(0):APPROACH+Math.max(BASE_CHARGE,Math.min(120,(int)Math.min(120,(points*20L+Math.max(1,rate)-1)/Math.max(1,rate))));
    }
    public static long burnDue(long points,long age,int release) {
        long duration=Math.max(1,release-APPROACH-LOCK);
        return Math.min(points,Math.max(0,age-APPROACH)*points/duration);
    }
    private SakuraRecallSequence() {}
}
