package dev.bladetetra.easteregg;

/** Fixed tiers reserve a fraction of maximum life before this reserve is applied. */
public final class AkatsukiBloodRules {
    public static final int STAGES=3, INJECTION_FLOW_TICKS=16, RECALL_TICKS=24, SHEATH_FADE_TICKS=4;
    public static final float EXTRA_REACH=2.25F;
    public static float fraction(int stage) {return Math.max(0,Math.min(STAGES,stage))/(float)STAGES;}
    public static float reserved(int stage) {return switch(Math.max(0,Math.min(STAGES,stage))) {case 1->.10F;case 2->.25F;case 3->.45F;default->0F;};}
    public static float damageBonus(int stage) {return switch(Math.max(0,Math.min(STAGES,stage))) {case 1->.10F;case 2->.20F;case 3->.35F;default->0F;};}
    public static float smooth(float t) {t=Math.max(0,Math.min(1,t));return t*t*(3-2*t);}
    public static float recall(float age) {return 1-smooth(age/(RECALL_TICKS-4));}
    public static float sheathAfterRecall(float age) {return smooth((age-RECALL_TICKS)/SHEATH_FADE_TICKS);}
    private AkatsukiBloodRules() {}
}
