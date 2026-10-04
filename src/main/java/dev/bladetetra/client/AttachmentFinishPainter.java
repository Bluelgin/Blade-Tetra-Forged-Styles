package dev.bladetetra.client;

import dev.bladetetra.compat.attachments.SwordAttachmentSchematics;
import dev.bladetetra.visual.BladeAttachmentAppearance;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import se.mickelus.tetra.data.DataManager;

import static dev.bladetetra.client.MaterialTextureManager.*;
import static dev.bladetetra.client.MaterialTextureStyleEngine.*;

/** Visual-only finishes. Gameplay values and ingredient definitions stay owned by Tetra/providers. */
final class AttachmentFinishPainter {
    static MaterialStyle wrapStyle(BladeAttachmentAppearance attachments, MaterialStyle fallback) {
        if (attachments.wrap().isBlank()) return fallback;
        MaterialStyle nativeStyle = tetraMaterialStyle(attachments.wrapMaterial());
        return nativeStyle == null ? styleFor(attachments.wrapMaterial()) : nativeStyle;
    }

    static MaterialStyle socketStyle(BladeAttachmentAppearance attachments) {
        return styleFor(attachments.socketMaterial());
    }

    static int socket(int base, BladeAttachmentAppearance attachments, MaterialStyle gem, float x, float y) {
        if (attachments.socket().isBlank()) return base;
        // Keep one small inset at the kashira end, not gemstones across the grip.
        // Native tsuka UVs run from the guard (x=1) to the pommel (x=47).
        float distance = Math.abs((x - 43.8F) / 3.0F) + Math.abs((y - 70.0F) / 6.3F);
        if (distance > 1.0F) return base;
        if (distance > .88F) return Palette.scale(base, .40F);
        if (distance > .73F) return Palette.lerp(base, 0xD9DDE0, x < 43.8F ? .55F : .22F);
        float tone = clamp01(.28F + (74.3F - y) / 8.6F * .55F);
        int color = gem.palette().sample(tone);
        if (x < 43.8F && y < 70) color = Palette.lerp(color, gem.palette().highlight(), .62F);
        if (x > 43.8F && y > 70) color = Palette.lerp(color, gem.palette().shadow(), .42F);
        if (Math.abs(x - 43.3F) < .45F && Math.abs(y - 68.5F) < .8F) {
            color = Palette.lerp(gem.palette().highlight(), 0xFFFFFF, .55F);
        }
        return color;
    }

    static int coating(int base, int coatingColor, float bladeX, float bladeY) {
        if (coatingColor < 0) return base;
        // A translucent sheen keeps planes/hamon visible and leaves the cutting edge bright.
        if (bladeY > 27.5F) return Palette.lerp(base, coatingColor, .07F);
        float strength = .13F;
        float sheen = 8.5F + (float)Math.sin((bladeX-1)/62 * Math.PI) * 1.2F;
        if (Math.abs(bladeY - sheen) < .65F) strength = .44F;
        if (Math.abs(bladeY - sheen - 1.2F) < .35F) strength = .28F;
        int color = Palette.lerp(base, coatingColor, strength);
        return Math.abs(bladeY-sheen) < .30F
                ? Palette.lerp(color, 0xFFFFFF, .12F) : color;
    }

    static int coatingColor(BladeAttachmentAppearance attachments) {
        if (attachments.coatings().isEmpty() || DataManager.instance == null) return -1;
        // Prefer the provider's actual ingredient sprite, not a hard-coded mod-id palette.
        var entries = DataManager.instance.schematicData.getData().entrySet().stream()
                .sorted(java.util.Map.Entry.comparingByKey()).toList();
        for (var entry : entries) {
            var recipe = entry.getValue();
            if (recipe.outcomes == null) continue;
            for (var outcome : recipe.outcomes) {
                if (outcome.improvements == null || outcome.material == null
                        || outcome.improvements.keySet().stream().noneMatch(attachments.coatings()::contains)) continue;
                for (ItemStack ingredient : outcome.material.getApplicableItemStacks()) {
                    if (ingredient.isEmpty()) continue;
                    var sprite = Minecraft.getInstance().getItemRenderer()
                            .getModel(ingredient, null, null, 0).getParticleIcon();
                    if (sprite.contents().name().getPath().equals("missingno")) continue;
                    int sumR = 0, sumG = 0, sumB = 0, count = 0;
                    for (int y = 0; y < sprite.contents().height(); y++) {
                        for (int x = 0; x < sprite.contents().width(); x++) {
                            int pixel = sprite.getPixelRGBA(0, x, y);
                            if (alpha(pixel) < 128) continue;
                            sumR += red(pixel); sumG += green(pixel); sumB += blue(pixel); count++;
                        }
                    }
                    if (count > 0) return (sumR / count << 16) | (sumG / count << 8) | sumB / count;
                }
            }
        }
        return -1; // No trustworthy visual hint: retain original metal instead of inventing a color.
    }

    private AttachmentFinishPainter() {}
}
