package dev.bladetetra.client.sakura;

import net.minecraft.world.phys.Vec3;
import static dev.bladetetra.client.sakura.SakuraShardTrajectory.*;

/** Fixed client-only objects. A launched index is the SAME shard absent from its home. */
public final class SakuraShardPool {
    public static final int MAX_GROUPS = 6, HISTORY = 6;
    public enum State { IDLE, LAUNCH, SLASH, RETURN }
    public static final class Shard {
        public final int index;
        public State state = State.IDLE;
        public Vec3 position = Vec3.ZERO, previous = Vec3.ZERO, direction = new Vec3(0, 1, 0);
        public final Vec3[] trail = new Vec3[HISTORY];
        public int history;
        Group group;
        double delay;
        Vec3 start, returnStart, returnVelocity;
        double returningAt;
        Shard(int index) { this.index = index; }
        public boolean flying() { return state != State.IDLE; }
    }
    public static final class Group {
        public final int stage;
        public long sequence;
        public final long actionAt;
        public final double startedAt;
        public Vec3 center, goal, hitPosition;
        public final Vec3 forward;
        public int targetId;
        public boolean predicted, canceled, confirmed;
        public double confirmedAt = -100;
        int members;
        Group(int stage, long sequence, long actionAt, double startedAt,
                Vec3 center, Vec3 forward, int targetId, boolean predicted) {
            this.stage=stage; this.sequence=sequence; this.actionAt=actionAt; this.startedAt=startedAt;
            this.center=center; this.goal=center; this.forward=forward; this.targetId=targetId;
            this.predicted=predicted;
        }
    }
    public final Shard[] shards;
    public final Group[] groups = new Group[MAX_GROUPS];
    private long lastSequence;
    private int cursor;
    private double now;
    private SakuraShardFormation.Frame frame;

    public SakuraShardPool(int count, SakuraShardFormation.Frame initial, double time) {
        count = Math.max(12, Math.min(72, count));
        frame=initial; now=time; shards=new Shard[count];
        for(int i=0;i<count;i++) {
            Shard shard = shards[i] = new Shard(i);
            shard.position=shard.previous=SakuraShardFormation.home(frame,i,count,time);
        }
    }
    public boolean launch(int stage, long sequence, long actionAt, double time,
            Vec3 target, int targetId, boolean predicted) {
        if(stage<1||stage>6||(!predicted&&sequence<=lastSequence)) return false;
        if(predicted) for(Group group:groups)
            if(group!=null&&group.stage==stage&&Math.abs(group.actionAt-actionAt)<=5) return true;
        if(!predicted) {
            lastSequence=sequence;
            for(Group group:groups) if(group!=null&&group.predicted&&group.stage==stage
                    &&Math.abs(group.actionAt-actionAt)<=5) {
                group.sequence=sequence; group.predicted=false; group.goal=target; group.targetId=targetId;
                return true; // reconcile the existing departure, never duplicate it
            }
        }
        int free=-1;
        for(int i=0;i<MAX_GROUPS;i++) if(groups[i]==null) {free=i;break;}
        if(free<0) return false; // bounded overlap; never recycle an in-flight blade
        Vec3 forward=SakuraShardFormation.forward(frame);
        Vec3 delta=target.subtract(frame.root());
        if(delta.lengthSqr()>144) target=frame.root().add(0,frame.height()*.8,0).add(forward.scale(3.5));
        Group group=new Group(stage,sequence,actionAt,time,target,forward,targetId,predicted);
        int wanted=Math.max(3,(stage+4)*shards.length/72);
        int start=stage%2==0?shards.length/2:0;
        for(int j=0;j<shards.length&&group.members<wanted;j++) {
            Shard shard=shards[(start+cursor+j)%shards.length];
            if(shard.flying()) continue;
            shard.group=group; shard.state=State.LAUNCH;
            shard.start=shard.position; shard.delay=group.members*.28;
            shard.returnStart=null; shard.history=0;
            group.members++;
        }
        cursor=(cursor+wanted)%shards.length;
        if(group.members==0) return false;
        groups[free]=group; return true;
    }
    public boolean confirm(long sequence, Vec3 target, int targetId, double time) {
        for(Group group:groups) if(group!=null&&group.sequence==sequence&&!group.confirmed) {
            group.confirmed=true; group.goal=target; group.hitPosition=target;
            group.targetId=targetId; group.confirmedAt=time;
            return true;
        }
        return false; // a late hit does not summon a second unrelated volley
    }
    public void tick(SakuraShardFormation.Frame next, double time) {
        frame=next; now=time;
        for(Group group:groups) if(group!=null)
            group.center=approach(group.center,group.goal,.55);
        for(Shard shard:shards) {
            shard.previous=shard.position;
            Vec3 home=SakuraShardFormation.home(frame,shard.index,shards.length,time);
            if(!shard.flying()) { shard.position=home; continue; }
            Group group=shard.group;
            double age=time-group.startedAt-shard.delay;
            if(age<0) { shard.start=home; shard.position=home; continue; }
            if((group.canceled || age>=LAUNCH_TICKS+SLASH_TICKS) && shard.state!=State.RETURN) {
                Vec3 start=group.canceled?shard.position:
                        orbit(group.center,group.forward,shard.index,group.stage,1);
                Vec3 velocity=group.canceled ? start.subtract(shard.history>0?shard.trail[0]:shard.previous)
                        : start.subtract(shard.previous);
                beginReturn(shard,time,start,velocity);
            }
            if(shard.state==State.RETURN) {
                double u=(time-shard.returningAt)/RETURN_TICKS;
                shard.position=returning(shard.returnStart,shard.returnVelocity,home,u);
                if(u>=1) {
                    shard.position=home; shard.state=State.IDLE; shard.group=null;
                    shard.history=0; group.members--;
                }
            } else if(age<LAUNCH_TICKS) {
                shard.position=SakuraShardTrajectory.launch(shard.start,group.center,group.forward,shard.index,group.stage,age/LAUNCH_TICKS);
            } else {
                shard.state=State.SLASH;
                shard.position=orbit(group.center,group.forward,shard.index,group.stage,
                        (age-LAUNCH_TICKS)/SLASH_TICKS);
            }
            Vec3 velocity=shard.position.subtract(shard.previous);
            if(velocity.lengthSqr()>.00001) shard.direction=velocity.normalize();
            if(shard.flying()) {
                for(int i=HISTORY-1;i>0;i--) shard.trail[i]=shard.trail[i-1];
                shard.trail[0]=shard.previous; shard.history=Math.min(HISTORY,shard.history+1);
            }
        }
        for(int i=0;i<MAX_GROUPS;i++) if(groups[i]!=null&&groups[i].members==0) groups[i]=null;
    }
    private static void beginReturn(Shard shard,double time,Vec3 start,Vec3 velocity) {
        shard.returnStart=start;
        shard.returnVelocity=velocity;
        shard.returningAt=time; shard.state=State.RETURN;
    }
    public int idleCount() {
        int result=0; for(Shard shard:shards) if(!shard.flying()) result++; return result;
    }
    public double time() { return now; }
}
