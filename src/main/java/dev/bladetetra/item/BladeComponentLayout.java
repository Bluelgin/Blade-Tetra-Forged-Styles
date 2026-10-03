package dev.bladetetra.item;

import se.mickelus.tetra.gui.GuiModuleOffsets;
import static dev.bladetetra.item.ModularSlashBladeItem.*;

/** Physical slot layout only. Additional finishes live on these slots, never create parallel stat slots. */
final class BladeComponentLayout {
    static final String[] MAJOR = {BLADE_SLOT, TSUKA_SLOT};
    static final String[] MINOR = {SAYA_SLOT, TSUBA_SLOT, HABAKI_SLOT, KASHIRA_SLOT, FULLER_SLOT, INSCRIPTION_SLOT};
    static final String[] REQUIRED = {BLADE_SLOT, TSUKA_SLOT, SAYA_SLOT};
    static final GuiModuleOffsets MAJOR_OFFSETS = new GuiModuleOffsets(22, -2, 22, 18);
    static final GuiModuleOffsets MINOR_OFFSETS = new GuiModuleOffsets(
            -21, -25, -21, -12, -21, 1, -21, 14, -21, 27, -21, 40);

    private BladeComponentLayout() {}
}
