package dev.bladetetra.combat;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import static org.junit.jupiter.api.Assertions.*;

class ForgedSlashArtTooltipTest {
    @Test
    void IncompleteCoreDetailsNeverAdvertisePrimaryOrSecondaryDamage() {
        var lines = new ArrayList<Component>();
        ForgedSlashArtCore.appendDetailsTooltip("sa_core/diamond", 0.10F, lines, false);
        assertEquals(1, lines.size());
        assertEquals("tooltip.blade_tetra.forged.core", key(lines.get(0)));
    }

    @Test
    void CompleteAltDetailsContainCoreAndScalesButDoNotRepeatTheDefaultBonus() {
        var lines = new ArrayList<Component>();
        ForgedSlashArtCore.appendDetailsTooltip("sa_core/diamond", 0.10F, lines, true);
        assertEquals(2, lines.size());
        assertEquals("tooltip.blade_tetra.forged.core", key(lines.get(0)));
        var scales = (TranslatableContents) lines.get(1).getContents();
        assertEquals("tooltip.blade_tetra.forged.damage_scales", scales.getKey());
        assertArrayEquals(new Object[]{"93.5%", "71.5%"}, scales.getArgs());
    }

    @Test
    void EmptyOrbSummaryOnlyShowsComponentProgressAndNoAltDetails() {
        var lines = new ArrayList<Component>();
        ForgedSlashArtPlan.appendOrbTooltip(null, lines);
        assertEquals(1, lines.size());
        var progress = (TranslatableContents) lines.get(0).getContents();
        assertEquals("tooltip.blade_tetra.forged.incomplete", progress.getKey());
        assertArrayEquals(new Object[]{0, 3}, progress.getArgs());
        lines.clear();
        ForgedSlashArtPlan.appendOrbDetails(null, lines);
        assertTrue(lines.isEmpty());
    }

    private static String key(Component component) {
        return ((TranslatableContents) component.getContents()).getKey();
    }
}
