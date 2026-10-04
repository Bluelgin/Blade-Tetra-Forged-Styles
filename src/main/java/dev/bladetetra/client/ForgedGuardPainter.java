package dev.bladetetra.client;

import dev.bladetetra.visual.MaterialAppearance.TsubaProfile;
import static dev.bladetetra.client.MaterialTextureStyleEngine.*;

/** Face finish shared by all material choices; openings belong to the actual mesh. */
final class ForgedGuardPainter {
    private ForgedGuardPainter() {}
    static void prepareTexture(com.mojang.blaze3d.platform.NativeImage image) {
        for(int y=0;y<image.getHeight();y++) for(int x=0;x<image.getWidth();x++) {
            float u=MaterialTextureManager.logicalCoordinate(x,image.getWidth());
            float v=MaterialTextureManager.logicalCoordinate(y,image.getHeight());
            // The old rectangular cutout/shading must not punch holes in the new mesh.
            if(MaterialTextureManager.inside(u,v,52,58,76,82)) image.setPixelRGBA(x,y,
                    MaterialTextureManager.abgr(255,0x808080));
        }
    }
    static int color(int base,Palette palette,float x,float y,TsubaProfile profile) {
        if(profile==TsubaProfile.NONE) return base;
        double u=(x-64)/11.4, v=(y-70)/11.4, angle=Math.atan2(v,u);
        double r=Math.hypot(u,v)/ForgedGuardModel.outline(profile,angle);
        int result=Palette.lerp(base,palette.shadow(),.16F);
        if(r>.88) result=Palette.lerp(result,palette.highlight(),.64F);
        else if(Math.abs(r-.79)<.025) result=Palette.lerp(result,palette.shadow(),.60F);
        else if(Math.abs(r-.73)<.022) result=Palette.lerp(result,palette.highlight(),.40F);
        if(r<.25) result=Palette.lerp(result,palette.shadow(),.55F);
        // Four short radial engraved ticks, rather than a noisy field of runes.
        double axis=Math.abs(Math.IEEEremainder(angle,Math.PI/2));
        if(r>.46 && r<.62 && axis<.05) result=Palette.lerp(result,palette.highlight(),.50F);
        float light=(float)Math.max(0,(-u-v)*.25);
        return Palette.lerp(result,palette.highlight(),light*.15F);
    }
}
