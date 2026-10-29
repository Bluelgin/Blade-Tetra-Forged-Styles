package dev.bladetetra.easteregg;

import net.minecraft.world.phys.Vec3;

/** Shared server/client timing and view coordinates; no entities, registries or input state. */
public final class SakuraShardAttackRules {
    public static final int LOGICAL_SHARDS=24, FIRE_TICK=5, RETURN_TICK=6, FLIGHT_TICKS=12;
    public static final int CONCERTO_VARIANT=100;
    public static int index(int logical,int count) {return logical*count/LOGICAL_SHARDS;}
    public static Vec3 platform(Vec3 eye,Vec3 look,boolean rightSide) {
        Vec3 right=new Vec3(look.z,0,-look.x).normalize();
        if(right.lengthSqr()<.001) right=new Vec3(1,0,0);
        Vec3 up=look.cross(right).normalize();
        return eye.add(right.scale(rightSide?.95:-.95)).add(up.scale(.65)).add(look.scale(.3));
    }
    public static int pulseCount(int stage) {return stage==6?7:stage==5?3:stage>=3?2:1;}
    public static boolean pulse(int stage,int elapsed) {
        return stage==1?elapsed==6:stage==6?elapsed>=0&&elapsed<=6:
                stage==5?elapsed==0||elapsed==2||elapsed==4:stage>=3?elapsed==0||elapsed==3:elapsed==0;
    }
    public static float damage(float nativeDamage,double fraction,double cap) {
        return Float.isFinite(nativeDamage)&&nativeDamage>0?(float)Math.min(cap,nativeDamage*fraction):0;
    }
    private SakuraShardAttackRules() {}
}
