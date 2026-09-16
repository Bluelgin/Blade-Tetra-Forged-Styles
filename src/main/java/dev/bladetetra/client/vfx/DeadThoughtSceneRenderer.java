package dev.bladetetra.client.vfx;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;

import static dev.bladetetra.visual.DeadThoughtVisualMath.*;

/** Four-stage geometric composition plus a detached soul-collapse death scene. */
final class DeadThoughtSceneRenderer {
    static void draw(PoseStack p, MultiBufferSource b, DeadThoughtVfxClient.Scene s, float partial, int quality) {
        float t = s.age + partial;
        float scale = scale(s.width, s.height), radius = wheelRadius(scale);
        Vec3 base = s.previous.lerp(s.anchor, partial);
        float height = Math.max(.65F, Math.min(5.5F, s.height));
        Vec3 center = base.add(0, height * .53, 0);
        double yaw = Math.toRadians(s.yaw);
        Vec3 forward = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
        Vec3 wheel = center.add(forward.scale(Math.min(2.8, Math.max(.65, s.width * .65))))
                .add(0, height * .22, 0);
        float finalTime = s.finisher < 0 ? -100 : t - s.finisher;
        float fade = 1 - ramp(t, 36, 8);
        float domainFade = fade * .85F;
        float domainRadius = radius * 1.25F;
        // Reveal separate engraving groups; the ground seal never rotates.
        part(p,b,"domain",base.add(0,.035,0),s.yaw,90,0,domainRadius,domainRadius,.25F,domainFade*ramp(t,0,2),"center");
        part(p,b,"domain",base.add(0,.035,0),s.yaw,90,0,domainRadius,domainRadius,.25F,domainFade*ramp(t,2,2),"cuts","inner");
        part(p,b,"domain",base.add(0,.035,0),s.yaw,90,0,domainRadius,domainRadius,.25F,domainFade*ramp(t,4,3),"outer","petals");
        if (quality > 0 && t < 30) {
            int count = quality > 1 ? 6 : 4;
            for (int i=0;i<count;i++) {
                double a = i * Math.PI * 2 / count + yaw;
                Vec3 at = base.add(Math.cos(a)*radius*1.12,0,Math.sin(a)*radius*1.12);
                float rise = ramp(t,2+i*.35F,3)*(1-ramp(t,24,6));
                part(p,b,"branch_cage",at,(float)-Math.toDegrees(a),0,0,scale*.65F,scale*1.4F*rise,
                        scale*.65F,.85F,"branch","edge","thorns");
            }
        }
        rift(p,b,center,s.yaw,scale,s.left < 0 ? -1 : t-s.left,"left",-28);
        rift(p,b,center.add(0,.035,0),s.yaw,scale,s.right < 0 ? -1 : t-s.right,"right",28);
        if (finalTime < 0) return;
        float collapse = 1-ramp(finalTime,16,8);
        float reveal = ramp(finalTime,0,10);
        float wheelScale = radius * Math.max(.02F, reveal) * Math.max(.01F,collapse);
        // Growth ends at tick 10; ticks 10–12 are a visual hold, not a game freeze.
        part(p,b,"final_wheel",wheel,s.yaw,0,0,wheelScale,wheelScale,scale,fade,"core","core_edges");
        part(p,b,"final_wheel",wheel,s.yaw,0,0,wheelScale,wheelScale,scale,fade*ramp(finalTime,2,2),"inner");
        part(p,b,"final_wheel",wheel,s.yaw,0,0,wheelScale,wheelScale,scale,fade*ramp(finalTime,4,2)*collapse,"petals","veins","shards");
        part(p,b,"final_wheel",wheel,s.yaw,0,0,wheelScale,wheelScale,scale,fade*ramp(finalTime,6,2)*collapse*collapse,"outer","edge");
        float cut = ramp(finalTime,12,1)*(1-ramp(finalTime,15,4));
        part(p,b,"execution_line",center,s.yaw,0,-12,1,height*.8F,1,cut,"black","red","light");
        if (quality > 0 && s.eroded && finalTime >= 12 && finalTime < 24) {
            float pull = ramp(finalTime,12,12);
            Vec3 from = base.add(forward.scale(.3));
            Vec3 at = from.lerp(wheel, pull*pull);
            int count = quality > 1 ? 6 : 3;
            for (int i=0;i<count;i++) {
                float fragment = ramp(pull,.35F,.65F);
                Vec3 offset = new Vec3((i%2==0?-1:1)*fragment*.10, i*.018*fragment,0);
                part(p,b,"life_remnant",at.add(offset),s.yaw,0,(i-2)*fragment*12,
                        Math.max(.03F,scale*(1-pull)),height*(1+pull*.45F)*(1-ramp(pull,.6F,.4F)),
                        .8F,.65F*(1-pull),"remnant_"+i);
            }
        }
    }

    /**
     * Terminal Soul Collapse owns a transform snapshot and therefore survives the target entity.
     * The six existing life-remnant groups burst out, hold, then converge into the final wheel.
     */
    static void drawCollapse(PoseStack p, MultiBufferSource b, DeadThoughtVfxClient.Collapse c,
            float partial, int quality) {
        float t = c.age + partial;
        float scale = c.scale;
        float radius = wheelRadius(scale);
        float height = Math.max(.65F, Math.min(5.5F, c.height));
        float fade = collapseFade(t);
        Vec3 base = c.anchor;
        Vec3 center = base.add(0, height * .53F, 0);
        double yaw = Math.toRadians(c.yaw);
        Vec3 forward = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
        Vec3 wheel = center.add(forward.scale(Math.min(2.8, Math.max(.65, c.width * .65))))
                .add(0, height * .22F, 0);

        float reveal = ramp(t, 0, 4);
        float wheelScale = radius * (.72F + .28F * reveal) * Math.max(.03F, fade);
        part(p,b,"final_wheel",wheel,c.yaw,0,0,wheelScale,wheelScale,scale,fade,"core","core_edges");
        part(p,b,"final_wheel",wheel,c.yaw,0,0,wheelScale,wheelScale,scale,fade*ramp(t,1,2),"inner");
        part(p,b,"final_wheel",wheel,c.yaw,0,0,wheelScale,wheelScale,scale,fade*ramp(t,2,2),"petals","veins","shards");
        part(p,b,"final_wheel",wheel,c.yaw,0,0,wheelScale,wheelScale,scale,fade*ramp(t,3,2),"outer","edge");

        // The body is not exploded as a game object; two geometric tears sell the rupture.
        rift(p,b,center,c.yaw,scale,t,"left",-34);
        rift(p,b,center.add(0,.04,0),c.yaw,scale,t,"right",34);
        float cut = ramp(t,2,1)*(1-ramp(t,6,3));
        part(p,b,"execution_line",center,c.yaw,0,-12,1,height*.95F,1,cut,"black","red","light");

        float burst = ramp(t,0,4);
        float pull = ramp(t,8,12);
        int count = quality > 0 ? 6 : 3;
        for (int i=0;i<count;i++) {
            int groupIndex = quality > 0 ? i : i * 2;
            double a = yaw + groupIndex * Math.PI * 2.0 / 6.0;
            double vertical = ((groupIndex % 3) - 1) * .18D;
            Vec3 direction = new Vec3(Math.cos(a), vertical, Math.sin(a));
            Vec3 burstAt = center.add(direction.scale(scale * .88F * burst));
            Vec3 at = burstAt.lerp(wheel, pull * pull);
            float fragmentScale = Math.max(.04F, scale * (1.0F - .38F * pull));
            float alpha = fade * (1 - ramp(t,18,8));
            part(p,b,"life_remnant",at,c.yaw,0,(groupIndex-2)*11F + burst*16F,
                    fragmentScale,height*(.95F+.22F*burst)*(1-.55F*pull),.8F,
                    alpha,"remnant_"+groupIndex);
        }
    }

    private static void rift(PoseStack p,MultiBufferSource b,Vec3 at,float yaw,float scale,float age,String side,float roll) {
        float open = opening(age);
        // X/Y scale follows the box; Z remains 0.12–0.151 blocks, even on large bosses.
        part(p,b,"rifts",at,yaw,0,roll,scale*1.65F,scale*open,1,1,
                "rift_"+side,"rim_"+side,"void_"+side);
        part(p,b,"rifts",at,yaw,0,roll,scale*1.65F,scale*open,1,ramp(age,1,2),"vein_"+side);
    }

    private static void part(PoseStack p,MultiBufferSource b,String model,Vec3 at,float yaw,float pitch,
            float roll,float sx,float sy,float sz,float alpha,String... groups) {
        DeadThoughtModel.draw(p,b,model,at,yaw,pitch,roll,sx,sy,sz,alpha,groups);
    }
    private DeadThoughtSceneRenderer() {}
}
