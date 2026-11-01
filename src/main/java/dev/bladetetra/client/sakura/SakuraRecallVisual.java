package dev.bladetetra.client.sakura;

import com.mojang.blaze3d.vertex.BufferBuilder;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import static dev.bladetetra.client.sakura.SakuraShardRenderer.solid;

/** Small bounded energy accents in the existing atlas batch. No emitters, shaders or extra entities. */
final class SakuraRecallVisual {
    static int draw(BufferBuilder b,Matrix4f m,SakuraShardPool pool,double time,float alpha,int quality,int budget) {
        if(pool.recallTarget()==null||pool.recallCanceled())return budget;
        Vec3 crown=pool.recallCrown(),anchor=pool.recallTarget();
        int tier=pool.recallTier();
        if(pool.charging(time)) {
            int strands=quality==0?2:4+tier*2;
            for(int i=0;i<strands&&budget>0;i++) {
                double phase=time*.12+i*2.399;
                Vec3 previous=null;
                for(int j=0;j<=8&&budget>0;j++) {
                    double u=j/8.0,angle=phase+u*Math.PI*3;
                    double radius=(.28+tier*.12)*(1-u)+.1;
                    Vec3 point=anchor.lerp(crown,u).add(Math.cos(angle)*radius,0,Math.sin(angle)*radius);
                    if(previous!=null)budget=line(b,m,previous,point,.013+tier*.004,
                            alpha*(float)(.15+.35*u),i%2==0?0xFF8DC5:0xFFE6F4,budget);
                    previous=point;
                }
            }
            // Target seal pulses in counter-rotation, visually connecting the crown with its marked enemy.
            for(int j=0;j<(quality==0?12:24)&&budget>0;j++) {
                double angle=j*Math.PI*2/(quality==0?12:24)-time*.08;
                Vec3 from=anchor.add(Math.cos(angle)*.65,-.55,Math.sin(angle)*.65);
                Vec3 to=anchor.add(Math.cos(angle+.13)*.65,-.55,Math.sin(angle+.13)*.65);
                budget=line(b,m,from,to,.018,alpha*.4F,0xFFD3EA,budget);
            }
            int rings=quality==0?1:1+(tier>=2?1:0)+(tier==3?1:0);
            int segments=quality==0?12:24;
            for(int ring=0;ring<rings;ring++) {
                double radius=.45+ring*.16+.025*Math.sin(time*.8+ring*2);
                for(int j=0;j<segments&&budget>0;j++) {
                    // Interrupted rings and unsynchronised flicker suggest a core that cannot quite contain its energy.
                    if((j+(int)(time/3)+ring*3)%7==0)continue;
                    double a=j*Math.PI*2/segments+time*(ring%2==0?.07:-.09);
                    double next=a+Math.PI*2/segments*.85;
                    Vec3 from=crown.add(Math.cos(a)*radius,(ring-1)*.18+Math.sin(a*2+time*.2)*.04,Math.sin(a)*radius);
                    Vec3 to=crown.add(Math.cos(next)*radius,(ring-1)*.18+Math.sin(next*2+time*.2)*.04,Math.sin(next)*radius);
                    budget=line(b,m,from,to,.009+tier*.003,alpha*(.22F+tier*.13F),0xFFD2EA,budget);
                }
            }
            int sparks=quality==0?2:2+tier*2;
            for(int i=0;i<sparks&&budget>0;i++) {
                double cycle=time/(9-tier)+i*.618;
                double u=cycle-Math.floor(cycle);
                double a=i*2.399+Math.floor(cycle)*1.731;
                double length=.18+tier*.16;
                Vec3 radial=new Vec3(Math.cos(a),Math.sin(a*3)*.35,Math.sin(a));
                Vec3 from=crown.add(radial.scale(.38+u*length));
                Vec3 to=from.add(radial.scale(.08+u*.14)).add(0,-u*u*.15,0);
                budget=line(b,m,from,to,.012,alpha*(float)Math.sin(u*Math.PI)*(.2F+tier*.16F),
                        i%3==0?0xFFF7FC:0xFF83BE,budget);
            }
            if(tier>=2&&budget>=2) {
                double flicker=.45+.35*Math.sin(time*2.37);
                budget=line(b,m,crown.add(0,-.36,0),crown.add(0,.36,0),.018,alpha*(float)flicker,0xFFF5FB,budget);
                budget=line(b,m,crown.add(-.2,0,0),crown.add(.2,0,0),.012,alpha*.45F,0xFFC6E1,budget);
            }
        }
        double rush=pool.releaseAge(time);
        if(rush>=0&&rush<4&&budget>=2) {
            float a=alpha*(float)Math.sin(Math.min(1,rush/4)*Math.PI);
            budget=line(b,m,crown,anchor,.055+tier*.018,a,0xFFF1FA,budget);
            budget=line(b,m,crown.add(.08,0,0),anchor.add(.08,0,0),.08,a*.35F,0xFF7FB9,budget);
        }
        double hit=pool.recallHitAge(time),burst=pool.recallBloomAge(time);
        if(hit>=0&&hit<3) {
            // Impact compresses first: shrinking bright core and inward streaks, not the explosion itself.
            double radius=.5*(1-hit/3)+.08;
            for(int i=0;i<(quality==0?6:12)&&budget>0;i++) {
                double a=i*2.399;
                Vec3 radial=new Vec3(Math.cos(a),Math.sin(a*1.7)*.6,Math.sin(a));
                budget=line(b,m,anchor.add(radial.scale(radius*1.8)),anchor.add(radial.scale(radius)),
                        .025,alpha,0xFFF2FA,budget);
            }
            budget=line(b,m,anchor.add(0,-radius,0),anchor.add(0,radius,0),radius*.3,alpha,0xFFFFFF,budget);
        }
        if(burst>=0&&burst<12) {
            double u=burst/12,expand=1-Math.pow(1-u,3),size=1.4+tier*.35;
            int segments=quality==0?12:24,rings=quality==0?1:2;
            float fade=alpha*(float)((1-u)*(1-u));
            for(int ring=0;ring<rings;ring++)for(int j=0;j<segments&&budget>0;j++) {
                double a=j*Math.PI*2/segments,next=(j+1)*Math.PI*2/segments;
                double radius=.15+expand*size*(ring==0?1:.72);
                Vec3 from=anchor.add(Math.cos(a)*radius,-.25+ring*.5,Math.sin(a)*radius);
                Vec3 to=anchor.add(Math.cos(next)*radius,-.25+ring*.5,Math.sin(next)*radius);
                budget=line(b,m,from,to,(.07+tier*.018)*(1-u),fade,ring==0?0xFF93C8:0xFFF0FA,budget);
            }
            int rays=quality==0?8:16+tier*4;
            for(int i=0;i<rays&&budget>0;i++) {
                double a=i*2.399,spread=expand*size*(.65+(i%4)*.12);
                // Rising curved blade waves carry the blast upward; petals drain behind their tips.
                Vec3 start=anchor.add(Math.cos(a)*spread*.4,0,Math.sin(a)*spread*.4);
                Vec3 mid=anchor.add(Math.cos(a)*spread*.8,expand*(.5+tier*.25),Math.sin(a)*spread*.8);
                Vec3 tip=anchor.add(Math.cos(a)*spread,expand*(1+tier*.4)-u*u*.5,Math.sin(a)*spread);
                budget=line(b,m,start,mid,.04*(1-u),fade*.7F,0xFF8CBE,budget);
                budget=line(b,m,mid,tip,.025*(1-u),fade,0xFFE1F0,budget);
                if(quality>0&&budget>0) {
                    Vec3 petal=tip.add(0,-u*.6,0),width=new Vec3(.045,0,.025);
                    solid(b,m,petal.add(0,.1,0),petal.add(width),petal.add(0,-.1,0),petal.subtract(width),fade*.7F,0xFFBEDC);
                    budget--;
                }
            }
            if(burst<2) {
                budget=line(b,m,anchor.add(-.8,-.3,0),anchor.add(.8,.3,0),.06,fade,0xFFFFFF,budget);
                budget=line(b,m,anchor.add(.8,-.3,0),anchor.add(-.8,.3,0),.06,fade,0xFFFFFF,budget);
            }
        }
        return budget;
    }
    private static int line(BufferBuilder b,Matrix4f m,Vec3 from,Vec3 to,double width,float alpha,int rgb,int budget) {
        if(budget<=0)return budget;
        Vec3 axis=to.subtract(from),side=axis.cross(new Vec3(0,1,0));
        if(side.lengthSqr()<.0001)side=axis.cross(new Vec3(0,0,1));
        side=side.normalize().scale(width);
        solid(b,m,from.add(side),to.add(side),to.subtract(side),from.subtract(side),alpha,rgb);
        return budget-1;
    }
    private SakuraRecallVisual() {}
}
