package dev.bladetetra.client.sakura;

import net.minecraft.world.phys.Vec3;
import static dev.bladetetra.client.sakura.SakuraShardTrajectory.*;

/** Fixed client-only objects. A launched index is the SAME shard absent from its home. */
public final class SakuraShardPool {
    public static final int MAX_GROUPS = 6, HISTORY = 6;
    public enum State { IDLE, LAUNCH, SLASH, RETURN, TRANSITION }
    public static final class Shard {
        public final int index;
        public State state = State.IDLE;
        public Vec3 position = Vec3.ZERO, previous = Vec3.ZERO, direction = new Vec3(0, 1, 0);
        public final Vec3[] trail = new Vec3[HISTORY];
        public int history;
        public float visibility = 1, previousVisibility = 1;
        float initialVisibility;
        boolean departureFromHome;
        Group group;
        double delay;
        Vec3 start, returnStart, returnVelocity;
        double returningAt;
        int member;
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
    private SakuraStanceTransition stance;
    public SakuraStanceTransition stance() { return stance; }
    public void hide() {
        for (Shard s : shards) s.visibility = s.previousVisibility = 0;
    }
    public void beginStance(boolean dispersing, double time, Vec3 grip, Vec3 tip, float fromFraction) {
        stance = new SakuraStanceTransition(dispersing, time, fromFraction);
        java.util.Arrays.fill(groups, null);
        for (Shard s : shards) {
            s.group = null;
            s.start = s.visibility < .001F && dispersing
                    ? tip.lerp(grip, (s.index + .5) / shards.length) : s.position;
            s.returnVelocity = s.position.subtract(s.previous);
            s.position = s.previous = s.start;
            s.initialVisibility = s.visibility;
            if(s.initialVisibility<=.001F) s.history = 0;
            s.state = State.TRANSITION;
        }
    }

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
        if(stage<1||stage>6||(!predicted&&sequence<=lastSequence)
                || stance != null && !stance.dispersing() && stance.active(time)) return false;
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
        int wanted=SakuraShardChoreography.wanted(stage,shards.length);
        for(int j=0;j<shards.length&&group.members<wanted;j++) {
            Shard shard=shards[SakuraShardChoreography.candidate(stage,j,shards.length,cursor)];
            boolean forming = stance != null && stance.dispersing()
                    && shard.state == State.TRANSITION && shard.visibility > .6F;
            if(shard.flying() && !forming) continue;
            shard.group=group; shard.state=State.LAUNCH;
            shard.departureFromHome=!forming;
            shard.start=shard.position; shard.member=group.members;
            shard.delay=SakuraShardChoreography.delay(stage,group.members,wanted);
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
        Vec3 grip = next.root().add(0,next.height()*.72,0).add(SakuraShardFormation.forward(next).scale(.4));
        tick(next,time,grip,grip.add(0,.9,0));
    }
    public void tick(SakuraShardFormation.Frame next, double time, Vec3 grip, Vec3 tip) {
        frame=next; now=time;
        for(Group group:groups) if(group!=null)
            group.center=approach(group.center,group.goal,.55);
        for(Shard shard:shards) {
            shard.previous=shard.position;
            shard.previousVisibility=shard.visibility;
            Vec3 home=SakuraShardFormation.home(frame,shard.index,shards.length,time);
            if(shard.state==State.TRANSITION) {
                tickStance(shard,time,home,grip,tip);
                recordMotion(shard);
                continue;
            }
            // Forming shards can join a follow-up before fully opaque. Their
            // transition no longer ticks, so finish the fade while they attack.
            if(stance!=null&&stance.dispersing()) shard.visibility=Math.min(1,shard.visibility+.2F);
            if(!shard.flying()) { shard.position=home; continue; }
            Group group=shard.group;
            double age=time-group.startedAt-shard.delay;
            if(age<0) {
                // Idle departures follow their moving home until launch. A
                // forming departure already has a real position along the
                // down-cleave path; snapping it home would break continuity.
                if(shard.departureFromHome) shard.start=home;
                shard.position=shard.start;
                continue;
            }
            double launchTicks=SakuraShardChoreography.launchTicks(group.stage);
            double cutTicks=SakuraShardChoreography.cutTicks(group.stage);
            if((group.canceled || age>=launchTicks+cutTicks) && shard.state!=State.RETURN) {
                Vec3 start=group.canceled?shard.position:
                        SakuraShardChoreography.cut(group.center,group.forward,shard.member,group.stage,1);
                Vec3 velocity=group.canceled ? start.subtract(shard.history>0?shard.trail[0]:shard.previous)
                        : start.subtract(shard.previous);
                beginReturn(shard,time,start,velocity);
            }
            if(shard.state==State.RETURN) {
                double duration=SakuraShardChoreography.returnTicks(group.stage);
                double u=(time-shard.returningAt)/duration;
                shard.position=cubic(shard.returnStart,shard.returnStart.add(shard.returnVelocity.scale(duration/3)),home,home,u);
                if(u>=1) {
                    shard.position=home; shard.state=State.IDLE; shard.group=null;
                    shard.history=0; group.members--;
                }
            } else if(age<launchTicks) {
                shard.position=SakuraShardChoreography.launch(shard.start,group.center,group.forward,shard.member,group.stage,age/launchTicks);
            } else {
                shard.state=State.SLASH;
                shard.position=SakuraShardChoreography.cut(group.center,group.forward,shard.member,group.stage,
                        (age-launchTicks)/cutTicks);
            }
            recordMotion(shard);
        }
        for(int i=0;i<MAX_GROUPS;i++) if(groups[i]!=null&&groups[i].members==0) groups[i]=null;
    }
    private void tickStance(Shard s,double time,Vec3 home,Vec3 grip,Vec3 tip) {
        double age=time-stance.startedAt();
        double index=(s.index+.5)/shards.length;
        if(stance.dispersing()) {
            double u=(age-2-index*6)/8;
            if(u<0) {
                if(s.initialVisibility<.001F) s.position=tip.lerp(grip,index);
                s.start=s.position; s.visibility=s.initialVisibility;
                return;
            }
            Vec3 sweep=SakuraShardFormation.forward(frame).scale(.65).add(0,-.65,0);
            s.position=cubic(s.start,s.start.add(sweep),home.add(0,-.18,0),home,u);
            s.visibility=(float)(s.initialVisibility+(1-s.initialVisibility)*SakuraStanceTransition.smooth(u*3));
            if(u>=1) {s.state=State.IDLE;s.position=home;s.visibility=1;s.history=0;}
        } else {
            double u=Math.max(0,Math.min(1,age/stance.duration()));
            Vec3 along=tip.subtract(grip);
            Vec3 end=grip.add(along.scale(index*stance.fraction(time)));
            Vec3 side=new Vec3(-along.z,0,along.x).normalize();
            if(side.lengthSqr()<.001) side=new Vec3(1,0,0);
            double swirl=Math.sin(u*Math.PI)*(.12+(s.index%4)*.04);
            Vec3 orbit=side.scale(Math.cos(u*Math.PI*2+index*6.28)*swirl)
                    .add(0,Math.sin(u*Math.PI*2+index*6.28)*swirl,0);
            s.position=cubic(s.start,s.start.add(s.returnVelocity.scale(2)),end.add(0,-.25,0),end,u).add(orbit);
            s.visibility=(float)(s.initialVisibility*(1-SakuraStanceTransition.smooth((u-.65)/.35)));
            if(u>=1) {s.position=end;s.visibility=0;s.state=State.IDLE;s.history=0;}
        }
    }
    private static void recordMotion(Shard s) {
        Vec3 velocity=s.position.subtract(s.previous);
        if(velocity.lengthSqr()>.00001) s.direction=velocity.normalize();
        if(s.flying()) {
            for(int i=HISTORY-1;i>0;i--) s.trail[i]=s.trail[i-1];
            s.trail[0]=s.previous;s.history=Math.min(HISTORY,s.history+1);
        }
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
