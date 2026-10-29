package dev.bladetetra.client.awakening;

import net.minecraft.world.phys.Vec3;

/** Native blade, silver edged facets, then the same shards absorbed into the blade. */
final class KyoukaAwakeningVisual implements AwakeningVisualProfile {
    public double forward() { return 2.2; }
    public double height() { return 2.15; }
    public float angle() { return -12; }
    public void draw(RitualEffectBatch f) {
        if(f.tick>=85&&f.tick<94) {
            float flash=f.window(85,86,87,94);
            f.tile(f.center,.8,3,flash*.65F,0);
        }
    }
    static void drawMirror(RitualEffectBatch f) {
        if(f.tick<24||f.tick>=112) return;
        Vec3 offset=f.center.subtract(f.mirrorOrigin());
        Vec3 blade=new Vec3(offset.dot(f.right),offset.y,offset.dot(f.forward));
        for(var facet:MirrorRitualController.FACETS) {
            float formed=MirrorRitualController.formation(f.tick,facet.index());
            float gather=MirrorRitualController.gather(f.tick,facet.index());
            if(formed<=0||gather>=1) continue;
            Vec3 a=f.mirrorWorld(MirrorRitualController.vertex(facet,facet.a(),f.tick,blade));
            Vec3 b=f.mirrorWorld(MirrorRitualController.vertex(facet,facet.b(),f.tick,blade));
            Vec3 c=f.mirrorWorld(MirrorRitualController.vertex(facet,facet.c(),f.tick,blade));
            float opacity=formed*(1-gather);
            int tint=switch(facet.index()%4) {
                case 0->0x617D91;case 1->0x334A60;case 2->0xA2B8C5;default->0x526D83;
            };
            f.solidTriangle(a,b,c,tint,opacity*.94F);
            // A moving silver glint supplies the mirror impression without sampling the world.
            float glint=(float)Math.pow(Math.max(0,Math.sin(f.tick*.08+facet.index()*.7)),12);
            Vec3 middle=a.add(b).add(c).scale(1.0/3);
            if(f.quality>0&&glint>.1)
                f.solidTriangle(middle.lerp(a,.8),middle.lerp(b,.8),middle.lerp(c,.8),0xE2F6FF,opacity*glint*.55F);
            float crack=MirrorRitualController.crack(f.tick);
            float edge=opacity*(.25F+.7F*crack);
            double width=.006+crack*.009;
            f.solidLine(a,b,width,0xD8F3FF,edge);
            f.solidLine(b,c,width,0xD8F3FF,edge);
            if(f.quality>0) f.solidLine(c,a,width,0xD8F3FF,edge);
            if(f.tick>=98&&f.quality>0) {
                Vec3 previous=f.mirrorWorld(MirrorRitualController.vertex(facet,facet.center(),f.tick-1.2F,blade));
                f.solidLine(previous,middle,.009,0xB1E7FF,opacity*.55F);
            }
        }
    }
}
