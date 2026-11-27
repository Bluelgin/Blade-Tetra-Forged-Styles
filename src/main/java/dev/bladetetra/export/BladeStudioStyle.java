package dev.bladetetra.export;

import net.minecraft.client.gui.GuiGraphics;

/** Small shared pixel primitives; input and narration remain vanilla widgets. */
final class BladeStudioStyle {
    static final int INK = 0xff1c1916;
    static final int BRONZE = 0xff806345;
    static final int TEXT = 0xffe6d9c0;

    static void panel(GuiGraphics g, int x, int y, int right, int bottom) {
        g.fill(x, y, right, bottom, INK);
        g.fill(x, y, right, y + 1, BRONZE);
        g.fill(x, bottom - 1, right, bottom, 0xff3c3026);
        g.fill(x, y + 1, x + 1, bottom - 1, 0xff493a2c);
        g.fill(right - 1, y + 1, right, bottom - 1, 0xff493a2c);
    }
    private BladeStudioStyle() {}
}
