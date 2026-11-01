package dev.bladetetra.client.sakura;

import dev.bladetetra.easteregg.SakuraRecallSequence;

/** Client phase clock. Explicit return time lets authoritative early release/cancel remain continuous. */
public record SakuraStanceTransition(boolean dispersing,double startedAt,float fromFraction,boolean striking,
        double returnAt,int totalTicks) {
    public SakuraStanceTransition(boolean down,double at,float from) {this(down,at,from,false,2,down?16:14);}
    public static final int DISPERSE_TICKS=16,CONDENSE_TICKS=14;
    public int duration() {return totalTicks;}
    public boolean active(double now) {return now-startedAt<duration();}
    public float fraction(double now) {
        double age=now-startedAt;
        double t=dispersing?(age-2)/8:(age-returnAt)/(striking?SakuraRecallSequence.RETURN:10);
        return (float)(fromFraction+((dispersing?0:1)-fromFraction)*smooth(t));
    }
    public static double smooth(double t) {t=Math.max(0,Math.min(1,t));return t*t*(3-2*t);}
}
