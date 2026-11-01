package dev.bladetetra.client;

import com.mojang.blaze3d.platform.NativeImage;
import dev.bladetetra.easteregg.SoulLegacyState;
import dev.bladetetra.visual.MaterialAppearance;
import net.minecraft.world.item.ItemStack;
import static dev.bladetetra.client.MaterialTextureManager.*;
import static dev.bladetetra.client.MaterialTextureStyleEngine.*;

/** Authored finishes share existing atlas coordinates and bounded mesh/texture caches. */
final class AwakenedWeaponArt {
    enum Theme { NONE, AKATSUKI, SENBONZAKURA, KYOUKA, RAIKIRI }
    static Theme fromStack(ItemStack stack) {
        var soul=SoulLegacyState.active(stack);
        return SoulLegacyState.isActive(stack,soul)?fromName(soul.name()):Theme.NONE;
    }
    static Theme fromName(String name) {
        return switch(name) {
            case "AKATSUKI" -> Theme.AKATSUKI;
            case "SENBONZAKURA" -> Theme.SENBONZAKURA;
            case "KYOUKA" -> Theme.KYOUKA;
            case "RAIKIRI" -> Theme.RAIKIRI;
            default -> Theme.NONE;
        };
    }
    static Palette metal(Theme t) {
        return switch(t) {
            case SENBONZAKURA -> new Palette(0x392A37,0xAE8F9A,0xF4DEE6);
            case KYOUKA -> new Palette(0x172D38,0x739EAD,0xD8F5F4);
            default -> new Palette(0x191E36,0x687992,0xD4DBF5);
        };
    }
    static Palette lacquer(Theme t) {
        return switch(t) {
            case SENBONZAKURA -> new Palette(0x170F1D,0x302035,0x775163);
            case KYOUKA -> new Palette(0x081C26,0x163A44,0x3A727D);
            default -> new Palette(0x0B1022,0x202B43,0x4D5B82);
        };
    }
    static int accent(Theme t) {
        return switch(t) {case SENBONZAKURA -> 0xFFC6DD;case KYOUKA -> 0xA6E9E8;default -> 0xB4AEFF;};
    }
    static void apply(NativeImage image,MaterialAppearance appearance,TextureLayout layout,boolean broken,Theme theme) {
        if(theme==Theme.NONE||theme==Theme.AKATSUKI) return;
        Palette metal=metal(theme),lacquer=lacquer(theme);
        MaterialStyle wrap=new MaterialStyle(theme==Theme.SENBONZAKURA
                ?new Palette(0x4E384C,0xBDADB5,0xF6E9EC):lacquer,SurfacePattern.POLISHED_METAL,0);
        MaterialStyle fittings=new MaterialStyle(metal,SurfacePattern.POLISHED_METAL,0);
        var gem=AttachmentFinishPainter.socketStyle(appearance.attachments());
        for(int y=0;y<image.getHeight();y++) for(int x=0;x<image.getWidth();x++) {
            int source=image.getPixelRGBA(x,y);if(alpha(source)==0) continue;
            float u=logicalCoordinate(x,image.getWidth()),v=logicalCoordinate(y,image.getHeight());
            int base=red(source)<<16|green(source)<<8|blue(source),color=base;
            var blade=bladeCoordinates(layout,x,y,image.getWidth(),image.getHeight(),u,v);
            if(blade.valid()) {
                float bx=blade.bladeX(),by=blade.bladeY();
                // Preserve material planes; the soul adds narrow inlays and an edge treatment.
                if(by<8.5) color=Palette.lerp(base,lacquer.shadow(),.26F);
                if(by>27) color=Palette.lerp(color,metal.highlight(),.35F);
                double p=(bx-1)/62.;
                double line=theme==Theme.KYOUKA?14.1+(p-.5)*.48+Math.sin(p*Math.PI)*.34:
                        theme==Theme.SENBONZAKURA?14.8-Math.sin(p*Math.PI)*.82+p*.28:
                        14.2+Math.sin(p*Math.PI*5)*1.15;
                if(p>.1&&p<.9&&Math.abs(by-line)<.33) color=accent(theme);
            } else if(inside(u,v,1,35,63,55)) {
                color=saya(theme,u,v);
            } else if(inside(u,v,1,59,47,81)) {
                color=MaterialTextureComponentPainter.applyTsukaProfile(wrap,fittings,.5F,u,v,x,y,
                        appearance.tsukaProfile(),dev.bladetetra.visual.TsukaWrapColor.dye(appearance.tsukaWrapColor()));
                color=ComponentDetailPainter.wrap(color,wrap,appearance.attachments().wrap(),u,v);
                color=AttachmentFinishPainter.socket(color,appearance.attachments(),gem,u,v);
            } else if(inside(u,v,52,58,76,82)) {
                color=ForgedGuardPainter.color(metal.sample(.48F),metal,u,v,appearance.tsubaProfile());
                double r=Math.hypot((u-64)/11.4,(v-70)/11.4);
                if(theme==Theme.KYOUKA) {
                    double boundary=ForgedGuardModel.outline(appearance.tsubaProfile(),Math.atan2(v-70,u-64),theme);
                    color=metal.sample(.65F);
                    if(boundary>0 && r/boundary>.87) color=metal.sample(.86F);
                    else if(r>.48 && r<.54) color=metal.sample(.39F);
                } else if(r>.82) color=metal.sample(.8F);
            } else if(inside(u,v,80,58,96,82)) {
                color=ComponentDetailPainter.fitting(metal.sample(.6F),metal,(u-80)/16,(v-58)/24);
            }
            if(color!=base) image.setPixelRGBA(x,y,abgr(alpha(source),Palette.lerp(base,color,broken?.45F:.9F)));
        }
    }
    static int saya(Theme theme,float u,float v) {
        Palette lacquer=lacquer(theme),metal=metal(theme);
        float p=(u-1)/62F,w=(v-35)/20F;
        float shine=(float)Math.exp(-Math.pow((w-.32)/.22,2));
        float grain=(float)(Math.sin(u*2.7+Math.sin(v*.7))*.016);
        int color=lacquer.sample(.22F+shine*.36F+grain);
        // Engraved metal throat and end cap, with dark seams and paired edge beads.
        if(p<.065F || p>.935F) {
            color=ComponentDetailPainter.fitting(metal.sample(.56F+shine*.22F),metal,p<.065F?p/.065F:(p-.935F)/.065F,w);
            if(Math.abs(p-.055F)<.009F || Math.abs(p-.946F)<.009F) return metal.shadow();
            if(w>.36F&&w<.64F) color=Palette.lerp(color,metal.highlight(),.18F);
            return color;
        }
        // A short, real-looking cord section: crossing threads around a dark underlay.
        if(p>.65F && p<.79F) {
            float thread=MaterialTextureComponentPainter.positiveModulo(u+(w-.5F)*6,3.2F);
            float cross=MaterialTextureComponentPainter.positiveModulo(u-(w-.5F)*6,3.2F);
            Palette silk=theme==Theme.SENBONZAKURA?new Palette(0x3A263A,0x8D6C83,0xD3AABF):
                    theme==Theme.KYOUKA?new Palette(0x10282C,0x42656A,0x8BAFAE):new Palette(0x141B31,0x465376,0x929ABB);
            color=silk.sample(thread<1.1F?.62F+shine*.18F:.25F);
            if(cross<.45F) color=silk.sample(.39F+shine*.22F);
            if(p<.665F||p>.775F) color=metal.sample(.50F);
            return color;
        }
        // Fine edge inlay occupies only the decorated forward panel, with bare lacquer elsewhere.
        if(p>.13F && p<.58F && (Math.abs(w-.16F)<.019F || Math.abs(w-.84F)<.019F))
            color=metal.sample(.47F);
        if(theme==Theme.SENBONZAKURA) {
            float branch=.55F+(float)Math.sin(p*12)*.13F;
            if(p>.17F&&p<.55F&&Math.abs(w-branch)<.022F) color=metal.sample(.40F);
            for(int i=0;i<3;i++) {
                double cx=.24+i*.12,cy=.51+Math.sin(cx*12)*.13;
                double dx=(p-cx)/.040,dy=(w-cy)/.19;
                double radius=Math.hypot(dx,dy),angle=Math.atan2(dy,dx);
                if(radius<.73+.22*Math.cos(angle*5)) color=radius<.24?0xD0B49A:
                        Palette.lerp(0xAD8099,0xF0CEDA,(float)Math.max(0,1-radius));
            }
            // A little silver petal near the end, balanced against the larger forward crest.
            if(Math.abs((p-.86)/.024)+Math.abs((w-.48)/.13)<1) color=metal.sample(.73F);
        } else if(theme==Theme.KYOUKA) {
            // Silver maki-e water fans and an inset mirror crest, confined to one lacquer panel.
            if(p>.16F && p<.57F) {
                double r=Math.hypot((p-.35)/.27,(w-.99)/.75);
                if((Math.abs(r-.62)<.026 || Math.abs(r-.89)<.022) && w>.28F)
                    color=metal.sample(.65F);
                double dx=(p-.35)/.043,dy=(w-.46)/.20;
                double mirror=Math.abs(dx)+Math.abs(dy);
                if(mirror<1) color=mirror>.72?metal.sample(.85F):lacquer.sample(.58F);
                if(mirror<.65 && Math.abs(dx+dy*.5)<.15) color=metal.sample(.69F);
            }
            if(p>.83F && p<.87F && Math.abs(w-.5F)<.27F) color=metal.sample(.48F);
        } else if(theme==Theme.RAIKIRI) {
            // Angular metal inlay and a thin branching thunder stroke, not a plain wavy stripe.
            if(p>.17F && p<.57F) {
                float q=(p-.17F)/.4F;
                float bolt=q<.36F?.3F+q*.8F:q<.57F?.59F-(q-.36F)*1.15F:.35F+(q-.57F)*.68F;
                if(Math.abs(w-bolt)<.035F) color=metal.sample(.78F);
                if(q>.35F&&q<.67F&&Math.abs(w-(.59F+(q-.35F)*.65F))<.022F) color=0xA7A6D6;
            }
            if(p>.83F && p<.9F && Math.abs(w-.5F)<.28F) {
                color=metal.sample(.43F);
                if(Math.abs(w-.5F)<.055F) color=0xA2A5CC;
            }
        }
        return color;
    }
    /** Portrait quad is square; native frame is a diamond. Keep art inside the inner opening. */
    static boolean insideBackplate(int x,int y,int width,int height) {
        return Math.abs((x+.5)/width-.5)+Math.abs((y+.5)/height-.5)<=.48;
    }
    private AwakenedWeaponArt() {}
}
