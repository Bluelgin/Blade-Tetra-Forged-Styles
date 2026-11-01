package dev.bladetetra.client.sakura;

import net.minecraft.world.phys.Vec3;
import dev.bladetetra.easteregg.SakuraRecallSequence;
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
        public Vec3 center, goal, hitPosition, targetOffset=Vec3.ZERO;
        public final Vec3 forward;
        public int targetId;
        public boolean predicted, canceled, confirmed;
        public boolean concerto, right;
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
    private double danceUntil, deployment;
    private SakuraStanceTransition stance;
    private Vec3 recallTarget,recallOrigin,recallGoal;
    private boolean recallCanceled;
    private double recallReturnAge,recallRelease,recallHitAt=-100,recallBloomAt=-100;
    private int recallTier;
    private float recallHeight=3;
    public Vec3 recallTarget() {return stance!=null&&stance.striking()&&stance.active(now)?recallTarget:null;}
    public Vec3 recallCrown() {return recallTarget==null?null:recallTarget.add(0,recallHeight,0);}
    public boolean recallCanceled() {return recallCanceled;}
    public double releaseAge(double time) {return stance==null?-100:time-stance.startedAt()-recallRelease;}
    public int recallTier() {return recallTier;}
    public double recallBloomAge(double time) {return time-recallBloomAt;}
    public double recallHitAge(double time) {return time-recallHitAt;}
    public boolean charging(double time) {
        return stance!=null&&stance.striking()&&!recallCanceled&&time-stance.startedAt()>=SakuraRecallSequence.APPROACH
                &&time-stance.startedAt()<recallRelease;
    }
    public void strikeRecall(Vec3 target,int maxTier,float height) {strikeRecall(target,maxTier,height,SakuraRecallSequence.release(maxTier));}
    public void strikeRecall(Vec3 target,int maxTier,float height,int releaseTicks) {
        recallTarget=recallOrigin=recallGoal=target;recallHeight=height;recallTier=0;recallHitAt=recallBloomAt=-100;
        recallCanceled=false;recallRelease=releaseTicks;
        recallReturnAge=recallRelease+SakuraRecallSequence.RUSH+SakuraRecallSequence.BLOOM;
        stance=new SakuraStanceTransition(false,stance.startedAt(),stance.fromFraction(),true,
                recallReturnAge,(int)recallReturnAge+SakuraRecallSequence.RETURN);
    }
    public void recallStage(int tier) {recallTier=Math.max(recallTier,Math.min(3,tier));}
    public void releaseRecall(Vec3 anchor,double time,int tier) {
        if(stance==null||!stance.striking()||recallCanceled)return;
        recallTarget=recallGoal=anchor;recallTier=tier;recallRelease=time-stance.startedAt();
        recallReturnAge=recallRelease+SakuraRecallSequence.RUSH+SakuraRecallSequence.BLOOM;
        stance=new SakuraStanceTransition(false,stance.startedAt(),stance.fromFraction(),true,
                recallReturnAge,(int)Math.ceil(recallReturnAge)+SakuraRecallSequence.RETURN);
    }
    public void confirmBloom(double time) {if(!recallCanceled)recallBloomAt=time;}
    public void confirmRecall(double time) {if(!recallCanceled)recallHitAt=time;}
    public void trackRecall(Vec3 target,double time) {
        if(stance==null||!stance.striking()||recallCanceled) return;
        if(target.distanceToSqr(recallOrigin)>SakuraRecallSequence.LEASH*SakuraRecallSequence.LEASH) {abortRecall(time);return;}
        if(time-stance.startedAt()<recallRelease-SakuraRecallSequence.LOCK)recallGoal=target;
    }
    public void abortRecall(double time) {
        if(stance==null||!stance.striking()||recallCanceled)return;
        recallCanceled=true;recallReturnAge=Math.max(0,time-stance.startedAt());
        stance=new SakuraStanceTransition(false,stance.startedAt(),stance.fromFraction(),true,
                recallReturnAge,(int)Math.ceil(recallReturnAge)+SakuraRecallSequence.RETURN);
        for(Shard s:shards) {s.returnStart=s.position;s.returnVelocity=Vec3.ZERO;}
    }
    public float chargeGlow(int index,double time) {
        if(!charging(time))return 0;
        double progress=(time-stance.startedAt()-SakuraRecallSequence.APPROACH)/Math.max(1,recallRelease-SakuraRecallSequence.APPROACH);
        return (float)Math.min(1,.15+progress*.55+recallTier*.1+Math.sin(time*.9+index)*.07);
    }
    public SakuraStanceTransition stance() { return stance; }
    public void hide() {
        for (Shard s : shards) s.visibility = s.previousVisibility = 0;
    }
    public void beginStance(boolean dispersing, double time, Vec3 grip, Vec3 tip, float fromFraction) {
        partialTransition=false;recallTarget=null;
        stance = new SakuraStanceTransition(dispersing, time, fromFraction);
        danceUntil=0;deployment=0;
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

    private boolean partialTransition;
    private float partialFrom=1,partialTo=1;
    private double partialAt;
    public float partialFraction(double time) {
        return (float)(partialFrom+(partialTo-partialFrom)*SakuraStanceTransition.smooth((time-partialAt)/6));
    }
    public void initialFraction(float fraction) {
        partialFrom=partialTo=fraction;
        for(Shard s:shards) s.visibility=s.previousVisibility=s.index<Math.round((1-fraction)*shards.length)?1:0;
    }
    public void partial(float from,float to,double time,Vec3 grip,Vec3 tip) {
        stance=null;partialTransition=true;partialFrom=from;partialTo=to;partialAt=time;
        java.util.Arrays.fill(groups,null);
        for(Shard s:shards) {
            s.group=null;s.history=0;s.initialVisibility=s.visibility;
            double index=(s.index+.5)/shards.length;
            s.start=s.visibility>.001?s.position:grip.lerp(tip,1-index);
            s.position=s.previous=s.start;s.state=State.TRANSITION;
        }
    }
    private void tickPartial(Shard s,double time,Vec3 home) {
        double t=Math.max(0,Math.min(1,(time-partialAt)/6));
        boolean shown=s.index<Math.round((1-partialTo)*shards.length);
        if(shown) {
            Vec3 forward=SakuraShardFormation.forward(frame);
            Vec3 side=new Vec3(forward.z,0,-forward.x).scale(1.1);
            s.position=cubic(s.start,s.start.add(side),home.add(side.scale(.2)),home,t);
            s.visibility=(float)(s.initialVisibility+(1-s.initialVisibility)*SakuraStanceTransition.smooth(t));
        } else {s.position=home;s.visibility=0;}
        if(t>=1) {s.position=home;s.state=State.IDLE;s.history=0;}
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
        danceUntil=time+24;
        for(Group old:groups) if(old!=null&&old.concerto) old.canceled=true;
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
    /** One stable shared index, never a decorative duplicate outside the pool. */
    public boolean concerto(int logical, boolean right, long sequence, long actionAt, double time,
            Vec3 target,int targetId) {
        if(logical<0||logical>=24||sequence<=lastSequence) return false;
        lastSequence=sequence;
        int free=-1;
        for(int i=0;i<MAX_GROUPS;i++) if(groups[i]==null) {free=i;break;}
        int index=dev.bladetetra.easteregg.SakuraShardAttackRules.index(logical,shards.length);
        Shard shard=shards[index];
        boolean forming=stance!=null&&stance.dispersing()&&shard.state==State.TRANSITION&&shard.visibility>.6F;
        if(free<0||shard.flying()&&!forming) return false;
        Group group=new Group(1,sequence,actionAt,time,target,SakuraShardFormation.forward(frame),targetId,false);
        group.concerto=true;group.right=right;group.members=1;
        shard.group=group;shard.state=State.LAUNCH;shard.start=shard.position;
        shard.returnStart=null;shard.history=0;shard.departureFromHome=!forming;
        groups[free]=group;return true;
    }
    public void recall(long sequence) {
        if(sequence<=lastSequence) return;
        lastSequence=sequence;recall();
    }
    public void recall() {
        danceUntil=0;
        for(Group group:groups) if(group!=null) group.canceled=true;
    }
    private Vec3 home(int index,double time) {
        Vec3 home=SakuraShardFormation.home(frame,index,shards.length,time);
        return home.add(SakuraShardFormation.forward(frame).scale(-deployment*(index%3)*.27))
                .add(0,deployment*(index%3)*.32,0);
    }
    private void tickConcerto(Shard s,Group g,Vec3 home,double time) {
        double age=time-g.startedAt;
        Vec3 platform=g.center;
        if((g.canceled||age>=dev.bladetetra.easteregg.SakuraShardAttackRules.RETURN_TICK)&&s.state!=State.RETURN)
            beginReturn(s,time,s.position,s.position.subtract(s.previous));
        if(s.state==State.RETURN) {
            double u=(time-s.returningAt)/6;
            s.position=cubic(s.returnStart,s.returnStart.add(s.returnVelocity.scale(2)),home,home,u);
            if(u>=1) {s.position=home;s.state=State.IDLE;s.group=null;s.history=0;g.members--;}
        } else if(age<6) {
            s.position=cubic(s.start,s.start.add(0,.45,0),platform.add(0,.15,0),platform,age/6);
        } else {s.state=State.SLASH;s.position=platform;}
        recordMotion(s);
        if(s.state==State.SLASH) s.direction=g.targetOffset.lengthSqr()>.001?g.targetOffset.scale(-1).normalize():new Vec3(0,-1,0);
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
        if(stance!=null&&stance.striking()&&!recallCanceled&&time-stance.startedAt()<recallRelease-SakuraRecallSequence.LOCK)
            recallTarget=approach(recallTarget,recallGoal,SakuraRecallSequence.TRACK_STEP);
        deployment=Math.max(0,Math.min(1,deployment+(time<danceUntil?.12:-.12)));
        for(Group group:groups) if(group!=null)
            group.center=approach(group.center,group.goal,.55);
        for(Shard shard:shards) {
            shard.previous=shard.position;
            shard.previousVisibility=shard.visibility;
            Vec3 home=home(shard.index,time);
            if(shard.state==State.TRANSITION) {
                if(partialTransition) tickPartial(shard,time,home);
                else tickStance(shard,time,home,grip,tip);
                recordMotion(shard);
                if(stance!=null&&stance.striking()&&!recallCanceled&&time-stance.startedAt()<recallRelease-SakuraRecallSequence.LOCK)
                    shard.direction=new Vec3(0,-1,0);
                continue;
            }
            // Forming shards can join a follow-up before fully opaque. Their
            // transition no longer ticks, so finish the fade while they attack.
            if(stance!=null&&stance.dispersing()) shard.visibility=Math.min(1,shard.visibility+.2F);
            if(!shard.flying()) { shard.position=home; continue; }
            Group group=shard.group;
            if(group.concerto) {tickConcerto(shard,group,home,time);continue;}
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
            if(stance.striking()&&!recallCanceled&&age<=recallReturnAge) {
                double charge=Math.max(0,Math.min(1,(age-SakuraRecallSequence.APPROACH)/
                        Math.max(1,recallRelease-SakuraRecallSequence.APPROACH)));
                Vec3 crown=recallCrown();
                double angle=index*Math.PI*6+age*(.055+.13*charge)+recallTier*.12;
                double radius=(.75-charge*.42)+(s.index%3)*.065;
                double wobble=(.018+recallTier*.015)*Math.sin(age*1.7+s.index*2.3);
                Vec3 orbit=crown.add(Math.cos(angle)*(radius+wobble),(index-.5)*.65,
                        Math.sin(angle)*(radius+wobble));
                // A few original shards briefly slip outward and are pulled back into the unstable core.
                double leak=Math.max(0,Math.sin(age*.65+(s.index/6)*2.17));
                if(recallTier>0&&s.index%6==0&&age<recallRelease)
                    orbit=orbit.add(Math.cos(angle)*leak*leak*.22*recallTier,leak*.08,
                            Math.sin(angle)*leak*leak*.22*recallTier);
                if(age<SakuraRecallSequence.APPROACH) {
                    s.position=cubic(s.start,s.start.add(0,1,0),orbit.add(0,.4,0),orbit,
                            age/SakuraRecallSequence.APPROACH);
                } else if(age<recallRelease)s.position=orbit;
                else {
                    double rush=Math.min(1,(age-recallRelease)/SakuraRecallSequence.RUSH);
                    Vec3 through=recallTarget.add(Math.cos(angle)*.12,-.15,Math.sin(angle)*.12);
                    s.position=orbit.lerp(through,rush*rush);
                    if(age>recallRelease+SakuraRecallSequence.RUSH) {
                        double bloom=(age-recallRelease-SakuraRecallSequence.RUSH)/SakuraRecallSequence.BLOOM;
                        s.position=s.position.add(Math.cos(angle)*bloom*.48,bloom*.16,Math.sin(angle)*bloom*.48);
                    }
                }
                s.returnStart=s.position;
                Vec3 velocity=s.position.subtract(s.previous);
                s.returnVelocity=velocity.lengthSqr()>.04?velocity.normalize().scale(.2):velocity;
                s.visibility=s.initialVisibility;return;
            }
            if(stance.striking())u=Math.max(0,Math.min(1,(age-recallReturnAge)/(stance.duration()-recallReturnAge)));
            Vec3 along=tip.subtract(grip);
            Vec3 end=grip.add(along.scale(index*stance.fraction(time)));
            Vec3 side=new Vec3(-along.z,0,along.x).normalize();
            if(side.lengthSqr()<.001) side=new Vec3(1,0,0);
            double swirl=Math.sin(u*Math.PI)*(.12+(s.index%4)*.04);
            Vec3 orbit=side.scale(Math.cos(u*Math.PI*2+index*6.28)*swirl)
                    .add(0,Math.sin(u*Math.PI*2+index*6.28)*swirl,0);
            Vec3 start=stance.striking()&&s.returnStart!=null?s.returnStart:s.start;
            s.position=cubic(start,start.add(s.returnVelocity.scale(2)),end.add(0,-.25,0),end,u).add(orbit);
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
