package dev.bladetetra.client;

import dev.bladetetra.visual.BladeAttachmentAppearance;
import dev.bladetetra.visual.MaterialAppearance;
import org.junit.jupiter.api.Test;
import java.util.List;
import static dev.bladetetra.client.MaterialTextureStyleEngine.*;
import static org.junit.jupiter.api.Assertions.*;

class ComponentDetailPainterTest {
    private static final Palette PALETTE = new Palette(0x26313C, 0x758391, 0xD3E2ED);
    private static final MaterialStyle STYLE = new MaterialStyle(PALETTE, SurfacePattern.POLISHED_METAL, 0);
    private static final int BASE = 0x758391;

    @Test void absentAttachmentsDoNotChangePixels() {
        var empty = new BladeAttachmentAppearance("", "", List.of());
        assertEquals(BASE, ComponentDetailPainter.wrap(BASE, STYLE, "", 14, 70));
        assertEquals(BASE, AttachmentFinishPainter.socket(BASE, empty, STYLE, 3, 70));
        assertEquals(BASE, AttachmentFinishPainter.coating(BASE, -1, 20, 15));
    }
    @Test void wrapNeverCoversMetalEndCollars() {
        for (String key : List.of("hilt/wrap/leather", "hilt/wrap/wool", "hilt/wrap/provider_unknown")) {
            assertEquals(BASE, ComponentDetailPainter.wrap(BASE, STYLE, key, 2, 70));
            assertEquals(BASE, ComponentDetailPainter.wrap(BASE, STYLE, key, 46, 70));
        }
    }
    @Test void leatherAndClothAreVisiblyDifferentOnSameBase() {
        int differences = 0;
        for (int x=5;x<43;x++) for (int y=60;y<80;y++) {
            if (ComponentDetailPainter.wrap(BASE, STYLE, "hilt/wrap/wool", x, y)
                    != ComponentDetailPainter.wrap(BASE, STYLE, "hilt/wrap/leather", x, y)) differences++;
        }
        assertTrue(differences > 200);
    }
    @Test void socketHasDistinctRimFacetsAndStaysAtPommel() {
        var gem = new BladeAttachmentAppearance("", "sword_socket/diamond", List.of());
        assertEquals(BASE, AttachmentFinishPainter.socket(BASE, gem, STYLE, 18, 70));
        assertEquals(BASE, AttachmentFinishPainter.socket(BASE, gem, STYLE, 3, 70));
        assertNotEquals(AttachmentFinishPainter.socket(BASE, gem, STYLE, 43.3F, 69),
                AttachmentFinishPainter.socket(BASE, gem, STYLE, 44.3F, 71));
        assertNotEquals(BASE, AttachmentFinishPainter.socket(BASE, gem, STYLE, 43.8F, 70));
    }
    @Test void coatingKeepsEdgeMostlyUnchangedAndHasLocalSheen() {
        assertEquals(Palette.lerp(BASE, 0xB63040, .07F), AttachmentFinishPainter.coating(BASE, 0xB63040, 20, 30));
        assertNotEquals(AttachmentFinishPainter.coating(BASE, 0xB63040, 20, 9.5F),
                AttachmentFinishPainter.coating(BASE, 0xB63040, 20, 18));
    }
    @Test void threeDragonPatternsAreNotJustSharedScales() {
        int fireIce=0, iceLightning=0;
        for (int x=10;x<55;x++) for (float y=12;y<19;y+=.25F) {
            int fire=SurfacePattern.DRAGON_FIRE.decorateBlade(BASE, PALETTE, x, y, 0);
            int ice=SurfacePattern.DRAGON_ICE.decorateBlade(BASE, PALETTE, x, y, 0);
            int lightning=SurfacePattern.DRAGON_LIGHTNING.decorateBlade(BASE, PALETTE, x, y, 0);
            if(fire!=ice) fireIce++;
            if(ice!=lightning) iceLightning++;
        }
        assertTrue(fireIce>100 && iceLightning>100);
    }
    @Test void furnitureHasDarkBevelAndBrightLipWithoutChangingPaletteFamily() {
        assertNotEquals(ComponentDetailPainter.fitting(BASE, PALETTE, .5F, .02F),
                ComponentDetailPainter.fitting(BASE, PALETTE, .5F, .12F));
        assertNotEquals(BASE, ComponentDetailPainter.saya(BASE, PALETTE, 58, 37.4F, MaterialAppearance.SayaProfile.QUICKDRAW));
    }
}
