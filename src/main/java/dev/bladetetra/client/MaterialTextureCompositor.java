package dev.bladetetra.client;

import com.mojang.blaze3d.platform.NativeImage;
import dev.bladetetra.forging.FoxLegacyParts;
import dev.bladetetra.visual.MaterialAppearance;
import dev.bladetetra.visual.TsukaWrapColor;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.DyeColor;
import java.util.List;
import static dev.bladetetra.client.MaterialTextureManager.*;
import static dev.bladetetra.client.MaterialTextureStyleEngine.*;
import static dev.bladetetra.client.MaterialTextureComponentPainter.*;

/** Component composition only; atlas/cache ownership stays in MaterialTextureManager. */
final class MaterialTextureCompositor {
    private MaterialTextureCompositor() {}

    static void recolor(
            NativeImage image,
            MaterialAppearance appearance,
            ResourceManager resourceManager,
            TextureLayout textureLayout) {
        boolean completePotato = appearance.physicalComponentsMatch("potato");
        int potatoYellow = 0xD9AD4F;
        MaterialStyle blade = componentStyle(appearance.blade(), potatoYellow);
        MaterialStyle tsuka = componentStyle(
                appearance.tsuka(), completePotato ? potatoYellow : 0x956033);
        MaterialStyle tsuba = componentStyle(
                appearance.tsuba(), completePotato ? potatoYellow : 0xB17A31);
        MaterialStyle saya = componentStyle(appearance.saya(), 0x9D6635);
        MaterialStyle habaki = componentStyle(
                appearance.habaki(), completePotato ? potatoYellow : 0x704222);
        MaterialStyle kashira = componentStyle(
                appearance.kashira(), completePotato ? potatoYellow : 0x744525);
        MaterialStyle fuller = styleFor(appearance.fuller());
        FoxLegacyParts fox = appearance.foxLegacy();
        blade = foxStyle(blade, fox.blade(), true);
        saya = foxStyle(saya, fox.saya(), false);
        tsuba = foxStyle(tsuba, fox.tsuba(), false);
        tsuka = foxStyle(tsuka, fox.tsuka(), false);
        tsuka = AttachmentFinishPainter.wrapStyle(appearance.attachments(), tsuka);
        MaterialStyle socket = AttachmentFinishPainter.socketStyle(appearance.attachments());
        if (!appearance.attachments().socket().isBlank()) kashira = styleFor("iron");
        int coatingColor = AttachmentFinishPainter.coatingColor(appearance.attachments());
        DyeColor tsukaWrapColor = TsukaWrapColor.dye(
                appearance.tsukaWrapColor());
        List<BannerLayer> bannerLayers = loadBannerLayers(
                resourceManager,
                appearance.sayaSkin());
        NativeImage presetSaya = loadPresetSaya(
                resourceManager,
                appearance.sayaPreset());

        try {
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    float atlasX = logicalCoordinate(x, image.getWidth());
                    float atlasY = logicalCoordinate(y, image.getHeight());
                    int source = image.getPixelRGBA(x, y);
                    int alpha = alpha(source);
                    if (alpha == 0) {
                        continue;
                    }

                    int red = red(source);
                    int green = green(source);
                    int blue = blue(source);
                    int luminance = (red * 30 + green * 59 + blue * 11) / 100;
                    MaterialStyle style = null;
                    float tone = 0.5F;

                    BladeCoordinateMap.Coordinates bladeCoordinates =
                            bladeCoordinates(
                                    textureLayout,
                                    x,
                                    y,
                                    image.getWidth(),
                                    image.getHeight(),
                                    atlasX,
                                    atlasY);
                    if (bladeCoordinates.valid()) {
                        float bladeX = bladeCoordinates.bladeX();
                        float bladeY = bladeCoordinates.bladeY();
                        tone = normalize(luminance, 60, 235);
                        tone = cleanBladeTone(
                                luminance, bladeX, bladeY);
                        int bladeColor = blade.sampleBlade(
                                tone, bladeX, bladeY);
                        int structuredColor = applyMasterBladePlanes(
                                bladeColor,
                                blade.palette(),
                                bladeX,
                                bladeY);
                        structuredColor = applyBladeFormComposition(
                                structuredColor,
                                blade.palette(),
                                appearance.bladeFormProfile(),
                                bladeX,
                                bladeY);
                        structuredColor = applyForgingProfile(
                                structuredColor,
                                blade.palette(),
                                appearance.forgingProfile(),
                                bladeX,
                                bladeY);
                        int finishedColor = applyFuller(
                                structuredColor,
                                blade,
                                fuller,
                                appearance.fullerProfile(),
                                bladeX,
                                bladeY);
                        finishedColor = applyEdgeFinish(
                                finishedColor,
                                blade.palette(),
                                appearance.edgeFinishProfile(),
                                bladeX,
                                bladeY);
                        finishedColor = AttachmentFinishPainter.coating(
                                finishedColor, coatingColor, bladeX, bladeY);
                        finishedColor = applyFoxAccent(
                                finishedColor, fox.blade(), bladeX, bladeY, 0);
                        image.setPixelRGBA(x, y, abgr(alpha, finishedColor));
                        continue;
                    } else if (inside(atlasX, atlasY, 1, 35, 63, 55)) {
                        boolean fittingBand = red > 90 && green > 70;
                        if (completePotato && !fittingBand) {
                            image.setPixelRGBA(x, y, abgr(0, 0));
                            continue;
                        }
                        style = fittingBand ? habaki : saya;
                        tone = fittingBand
                                ? normalize(luminance, 85, 190)
                                : normalize(luminance, 20, 75);
                        int sayaColor = fittingBand
                                ? habaki.sample(tone, x, y)
                                : sampleSayaMaterial(
                                        saya,
                                        tone,
                                        atlasX,
                                        atlasY,
                                        appearance.sayaProfile());
                        if (!fittingBand && presetSaya != null) {
                            sayaColor = applyPresetSaya(
                                    sayaColor,
                                    tone,
                                    atlasX,
                                    atlasY,
                                    presetSaya);
                        } else if (!fittingBand
                                && appearance.sayaSkin().present()) {
                            sayaColor = applySayaSkin(
                                    sayaColor,
                                    tone,
                                    atlasX,
                                    atlasY,
                                    appearance.sayaSkin(),
                                    bannerLayers);
                        }
                        if (!fittingBand) {
                            sayaColor = applySayaLacquer(
                                    sayaColor,
                                    saya.palette(),
                                    appearance.sayaProfile(),
                                    atlasX,
                                    atlasY);
                            sayaColor = applyFoxAccent(
                                    sayaColor, fox.saya(), atlasX, atlasY, 1);
                        }
                        image.setPixelRGBA(
                                x,
                                y,
                                abgr(alpha, sayaColor));
                        continue;
                    } else if (inside(atlasX, atlasY, 1, 59, 47, 81)) {
                        tone = normalize(luminance, 15, 190);
                        int tsukaColor = completePotato
                                ? tsuka.sample(tone, x, y)
                                : applyTsukaProfile(
                                        tsuka,
                                        kashira,
                                        tone,
                                        atlasX,
                                        atlasY,
                                        x,
                                        y,
                                        appearance.tsukaProfile(),
                                        tsukaWrapColor);
                        tsukaColor = applyFoxAccent(
                                tsukaColor, fox.tsuka(), atlasX, atlasY, 2);
                        tsukaColor = AttachmentFinishPainter.socket(
                                tsukaColor, appearance.attachments(), socket, atlasX, atlasY);
                        image.setPixelRGBA(
                                x,
                                y,
                                abgr(alpha, tsukaColor));
                        continue;
                    } else if (inside(atlasX, atlasY, 52, 58, 76, 82)) {
                        if (completePotato) {
                            image.setPixelRGBA(x, y, abgr(0, 0));
                            continue;
                        }
                        tone = normalize(luminance, 18, 105);
                        TsubaPixel tsubaPixel = applyTsubaProfile(
                                tsuba.sample(tone, x, y),
                                alpha,
                                atlasX,
                                atlasY,
                                appearance.tsubaProfile(),
                                tsuba.palette());
                        int foxTsubaColor = applyFoxAccent(
                                tsubaPixel.color(), fox.tsuba(), atlasX, atlasY, 3);
                        image.setPixelRGBA(
                                x,
                                y,
                                abgr(tsubaPixel.alpha(), foxTsubaColor));
                        continue;
                    } else if (inside(atlasX, atlasY, 80, 58, 96, 82)) {
                        style = habaki;
                        tone = normalize(luminance, 80, 190);
                    }

                    if (style != null) {
                        image.setPixelRGBA(
                                x,
                                y,
                                abgr(alpha, style.sample(tone, x, y)));
                    }
                }
            }
        } finally {
            bannerLayers.forEach(BannerLayer::close);
            if (presetSaya != null) {
                presetSaya.close();
            }
        }
    }

}

