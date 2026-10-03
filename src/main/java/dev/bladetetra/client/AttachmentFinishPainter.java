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
        float distance = Math.abs((x - 2.0F) / 1.2F) + Math.abs((y - 70.0F) / 3.2F);
        if (distance > 1.0F) return base;
        if (distance > .82F) return Palette.scale(base, .48F);
        float tone = clamp01(.3F + (73.2F - y) / 6.4F * .55F);
        int color = gem.palette().sample(tone);
        if (x < 2.0F && y < 69.4F) color = gem.palette().highlight();
        return color;
    }

    static int coating(int base, int coatingColor, float bladeX, float bladeY) {
        if (coatingColor < 0) return base;
        // A translucent sheen keeps planes/hamon visible and leaves the cutting edge bright.
        float strength = bladeY > 27.5F ? .07F : .21F;
        if (Math.abs(bladeY - (8.0F + bladeX * .07F)) < .65F) strength += .10F;
        return Palette.lerp(base, coatingColor, strength);
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
