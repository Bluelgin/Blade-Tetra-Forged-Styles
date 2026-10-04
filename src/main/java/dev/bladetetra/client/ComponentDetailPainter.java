package dev.bladetetra.client;

import dev.bladetetra.visual.MaterialAppearance;
import static dev.bladetetra.client.MaterialTextureManager.*;
import static dev.bladetetra.client.MaterialTextureStyleEngine.*;

/** Small, material-derived construction details; no item stats or extra NBT. */
final class ComponentDetailPainter {
    private ComponentDetailPainter() {}

    static int fitting(int base, Palette palette, float length, float width) {
        float edge = Math.min(width, 1 - width);
        if (edge < .09F) return Palette.lerp(base, palette.shadow(), .48F);
        if (edge < .16F) return Palette.lerp(base, palette.highlight(), .40F);
        float collar = Math.min(length, 1 - length);
        if (collar < .09F) return Palette.lerp(base, palette.shadow(), .35F);
        if (collar < .17F) return Palette.lerp(base, palette.highlight(), .28F);
        if (Math.abs(width - .30F) < .055F) return Palette.lerp(base, palette.highlight(), .22F);
        return base;
    }

    static int saya(int base, Palette palette, float x, float y, MaterialAppearance.SayaProfile profile) {
        float length = clamp01((x - 1) / 62), width = clamp01((y - 35) / 20);
        if (profile == MaterialAppearance.SayaProfile.QUICKDRAW) {
            // Reinforced draw-mouth seam, distinct from a plain lacquer saya.
            if (length > .90F && length < .94F) return fitting(base, palette, (length-.90F)/.04F, width);
        } else if (profile == MaterialAppearance.SayaProfile.SPIRIT) {
            float diamond = Math.abs((length - .78F) / .032F) + Math.abs((width - .5F) / .22F);
            if (diamond > .72F && diamond < 1) return Palette.lerp(base, palette.highlight(), .48F);
        }
        return base;
    }

    static int wrap(int base, MaterialStyle material, String key, float x, float y) {
        if (key.isBlank()) return base;
        float end = Math.min(x - 1, 47 - x);
        if (end < 2.15F) return base; // Keep the actual metal fuchi/kashira visible.
        String normalized = key.toLowerCase(java.util.Locale.ROOT);
        float width = (y - 59) / 22;
        if (normalized.contains("leather") || normalized.contains("hide")) {
            // Broad overlapping leather straps and a paired stitched seam.
            float seam = MaterialTextureComponentPainter.positiveModulo(x + (width-.5F)*5, 7);
            if (seam < .60F) return Palette.scale(base, .62F);
            if (seam < 1.1F) return Palette.lerp(base, material.palette().highlight(), .28F);
            if ((width < .16F || width > .84F) && ((int)x % 4 == 0)) {
                return Palette.lerp(base, material.palette().highlight(), .48F);
            }
        } else if (normalized.contains("wool") || normalized.contains("cloth")
                || normalized.contains("string") || normalized.contains("silk")) {
            // Crossed woven fibres, not leather grain; deliberately low contrast.
            float weave = MaterialTextureComponentPainter.positiveModulo(x + y, 3);
            if (weave < .6F) return Palette.lerp(base, material.palette().highlight(), .16F);
            if (MaterialTextureComponentPainter.positiveModulo(x-y, 3) < .5F) return Palette.scale(base, .88F);
        } else {
            // Unknown provider wraps keep their palette and get a neutral bound seam.
            if (width < .07F || width > .93F) return Palette.scale(base, .76F);
        }
        return base;
    }
}
