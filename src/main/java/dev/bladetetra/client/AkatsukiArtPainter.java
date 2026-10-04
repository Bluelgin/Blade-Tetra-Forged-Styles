package dev.bladetetra.client;

import com.mojang.blaze3d.platform.NativeImage;
import dev.bladetetra.visual.MaterialAppearance;
import dev.bladetetra.visual.TsukaWrapColor;
import static dev.bladetetra.client.MaterialTextureManager.*;
import static dev.bladetetra.client.MaterialTextureStyleEngine.*;

/** A single moon motif and coherent finish, enabled only by the existing awakened state. */
final class AkatsukiArtPainter {
    private static final Palette WRAP = new Palette(0x210E16,0x651D2B,0xB54550);
    private static final Palette METAL = new Palette(0x332229,0x9A7948,0xE8CCA0);
    private static final Palette LACQUER = new Palette(0x100F16,0x28212C,0x5E4450);
    private static final MaterialStyle WRAP_STYLE=new MaterialStyle(WRAP,SurfacePattern.POLISHED_METAL,0);
    private static final MaterialStyle METAL_STYLE=new MaterialStyle(METAL,SurfacePattern.POLISHED_METAL,0);
    private AkatsukiArtPainter() {}

    static void apply(NativeImage image,MaterialAppearance appearance,TextureLayout layout,boolean broken) {
        MaterialStyle gem=AttachmentFinishPainter.socketStyle(appearance.attachments());
        for(int y=0;y<image.getHeight();y++) for(int x=0;x<image.getWidth();x++) {
            int source=image.getPixelRGBA(x,y), a=alpha(source);
            if(a==0) continue;
            float u=logicalCoordinate(x,image.getWidth()), v=logicalCoordinate(y,image.getHeight());
            int original=red(source)<<16|green(source)<<8|blue(source), color=original;
            var blade=bladeCoordinates(layout,x,y,image.getWidth(),image.getHeight(),u,v);
            if(blade.valid()) color=blade(original,blade.bladeX(),blade.bladeY());
            else if(inside(u,v,1,35,63,55)) color=saya(u,v);
            else if(inside(u,v,1,59,47,81)) {
                color=MaterialTextureComponentPainter.applyTsukaProfile(WRAP_STYLE,METAL_STYLE,.5F,u,v,x,y,
                        appearance.tsukaProfile(),TsukaWrapColor.dye(appearance.tsukaWrapColor()));
                color=ComponentDetailPainter.wrap(color,WRAP_STYLE,appearance.attachments().wrap(),u,v);
                color=AttachmentFinishPainter.socket(color,appearance.attachments(),gem,u,v);
            } else if(inside(u,v,52,58,76,82)) {
                float r=(float)Math.hypot((u-64)/11.4,(v-70)/11.4);
                Palette iron=new Palette(0x1A151C,0x42343D,0xAA8F77);
                color=ForgedGuardPainter.color(iron.sample(.46F),iron,u,v,appearance.tsubaProfile());
                double boundary=ForgedGuardModel.outline(appearance.tsubaProfile(),Math.atan2(v-70,u-64));
                if(boundary>0 && r/boundary>.86) color=METAL.sample(.66F);
            } else if(inside(u,v,80,58,96,82)) {
                color=ComponentDetailPainter.fitting(METAL.sample(.6F),METAL,(u-80)/16,(v-58)/24);
            }
            if(color!=original) image.setPixelRGBA(x,y,abgr(a,Palette.lerp(original,color,broken?.48F:.94F)));
        }
    }

    static int blade(int base,float x,float y) {
        float p=clamp01((x-1)/62);
        // Dark mune, red body, pale rose edge. Existing forging planes remain visible.
        int result=y<8.2F?Palette.lerp(base,0x2B1019,.48F):base;
        if(y>27.2F) result=Palette.lerp(result,0xF9CAC1,.35F);
        float vein=13.2F+(float)Math.sin(p*Math.PI)*1.1F;
        if(Math.abs(y-vein)<.9F && p>.13F && p<.89F) result=Palette.lerp(result,0x3A111E,.60F);
        if(Math.abs(y-vein)<.30F && p>.13F && p<.89F) result=Palette.lerp(result,0xFF6870,.66F);
        if(crescent((x-11)/2.2F,(y-20)/3.2F)) result=Palette.lerp(result,0xEAD0A0,.80F);
        return result;
    }
    static int bladeGlow(float x,float y) {
        float p=clamp01((x-1)/62), vein=13.2F+(float)Math.sin(p*Math.PI)*1.1F;
        if(p>.13F && p<.89F && Math.abs(y-vein)<.30F) return 195;
        return crescent((x-11)/2.2F,(y-20)/3.2F)?145:0;
    }
    static int saya(float x,float y) {
        float p=clamp01((x-1)/62), w=clamp01((y-35)/20);
        float shine=(float)Math.sin(w*Math.PI);
        int result=LACQUER.sample(.25F+shine*.35F);
        if(Math.abs(w-.28F)<.035F) result=Palette.lerp(result,0x947080,.26F);
        if(p<.045F || p>.935F) return ComponentDetailPainter.fitting(METAL.sample(.57F),METAL,
                p<.045F?p/.045F:(p-.935F)/.065F,w);
        // Two slim gold inlay tracks frame the moon, with room for the dark lacquer.
        if(p>.15F && p<.48F && (Math.abs(w-.17F)<.024F || Math.abs(w-.83F)<.024F)) result=METAL.sample(.72F);
        if(p>.60F && p<.70F) {
            float stitch=MaterialTextureComponentPainter.positiveModulo(x+(w-.5F)*4,2.6F);
            result=stitch<.42F?0x3C1522:WRAP.sample(.57F+shine*.15F);
        }
        if(crescent((p-.35F)/.057F,(w-.5F)/.27F)) result=METAL.sample(.81F);
        float cloud=.55F+(float)Math.sin(p*21)*.11F;
        if(p>.17F && p<.46F && Math.abs(w-cloud)<.018F) result=Palette.lerp(result,0xB44B55,.58F);
        return result;
    }
    static boolean crescent(float u,float v) {
        return u*u+v*v<1 && (u-.48F)*(u-.48F)+(v+.14F)*(v+.14F)>.79F;
    }
}
