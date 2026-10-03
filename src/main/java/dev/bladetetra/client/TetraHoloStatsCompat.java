package dev.bladetetra.client;

import com.mojang.logging.LogUtils;
import se.mickelus.tetra.gui.stats.bar.GuiStatBase;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/** Client-only bridge: Tetra 6.10.0 moved HoloStatsGui to .craft.schematic, then moved it back. */
final class TetraHoloStatsCompat {
    private static final String[] GUI_CLASSES = {
            "se.mickelus.tetra.items.modular.impl.holo.gui.craft.HoloStatsGui",
            "se.mickelus.tetra.items.modular.impl.holo.gui.craft.schematic.HoloStatsGui"
    };
    private static final Method ADD_BAR = resolve();
    private static boolean failed;
    private TetraHoloStatsCompat() { }

    static boolean addBar(GuiStatBase bar) {
        if (ADD_BAR == null || failed) return false;
        try {
            ADD_BAR.invoke(null, bar);
            return true;
        } catch (IllegalAccessException | InvocationTargetException | LinkageError failure) {
            failed = true;
            LogUtils.getLogger().warn("Tetra holographic extension unavailable; keeping native stats", failure);
            return false;
        }
    }

    private static Method resolve() {
        for (String name : GUI_CLASSES) {
            try {
                return Class.forName(name, false, TetraHoloStatsCompat.class.getClassLoader())
                        .getMethod("addBar", GuiStatBase.class);
            } catch (ClassNotFoundException | NoSuchMethodException | LinkageError ignored) {
                // Try the alternate package, not a hard-coded version comparison.
            }
        }
        LogUtils.getLogger().warn("Tetra holographic addBar API unavailable; workbench SA stats remain enabled");
        return null;
    }
}
