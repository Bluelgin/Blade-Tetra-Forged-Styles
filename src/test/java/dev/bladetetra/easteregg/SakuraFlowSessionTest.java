package dev.bladetetra.easteregg;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SakuraFlowSessionTest {
    @Test void gapKeepsEntryEfficiencyBelowAAndDoesNotResetChargeClock() {
        var flow = new SakuraFlowSession();
        flow.observe(100, 1, 100, 7, 5000);
        flow.observe(105, 0, 100, 3, 500);
        flow.observe(110, 0, 100, 3, 500);
        assertEquals(41, flow.charge(110, 500, 120, true), "grace still burns");
        assertEquals(0, flow.charge(110, 459, 120, true), "same tick is paid once");
        flow.observe(114, 1, 114, 3, 459);
        assertTrue(flow.valid(114));
        assertEquals(7, flow.startRank);
        assertEquals(120, flow.nextCharge, "re-entry cannot reset payment");
    }

    @Test void graceExpiresAndBelowACannotRearmByClicking() {
        var flow = new SakuraFlowSession();
        flow.observe(100, 6, 100, 4, 1000);
        flow.observe(114, 0, 100, 3, 1000);
        assertTrue(flow.valid(114));
        flow.observe(115, 0, 100, 3, 1000);
        assertFalse(flow.valid(115));
        flow.observe(116, 1, 116, 3, 1000);
        assertFalse(flow.valid(116));
        flow.observe(120, 1, 120, 4, 2000);
        assertTrue(flow.valid(120));
    }

    @Test void exhaustionDoesNotRearmOnTheSameOngoingAttack() {
        var flow = new SakuraFlowSession();
        flow.observe(0, 6, 0, 7, 41);
        flow.observe(10, 6, 0, 7, 41);
        assertEquals(41, flow.charge(10, 41, 120, true));
        assertFalse(flow.active);
        flow.observe(11, 6, 0, 7, 0);
        assertFalse(flow.active);
        flow.observe(12, 6, 0, 7, 100);
        assertFalse(flow.active);
    }

    @Test void insufficientBudgetAndDisabledCostsDoNotCreateNegativePayments() {
        var flow = new SakuraFlowSession();
        flow.observe(0, 1, 0, 4, 59);
        assertEquals(0, flow.charge(10, 59, 120, true));
        assertFalse(flow.active);
        flow.observe(20, 1, 20, 4, 60);
        assertEquals(0, flow.charge(30, 60, 120, false));
        assertTrue(flow.active);
    }

    @Test void oneBufferedPressIsBoundToOneActionAndExpires() {
        var input = new SakuraFlowInput();
        var b6 = new ResourceLocation("blade_tetra", "awakened_sakura_b6");
        input.queue(b6, 100, 104);
        assertTrue(input.pending(b6, 100, 107));
        assertFalse(input.pending(b6, 107, 107), "new action cannot inherit input");
        assertFalse(input.pending(new ResourceLocation("slashblade", "combo_b1"), 100, 107));
        assertFalse(input.pending(b6, 100, 119));
        input.clear();
        assertFalse(input.pending(b6, 100, 107));
    }

    @Test void equippingAnOldActionCannotStartANewFlow() {
        var flow = new SakuraFlowSession();
        flow.observe(120, 6, 100, 7, 5000);
        assertFalse(flow.active);
        flow.observe(121, 6, 100, 7, 5000);
        assertFalse(flow.active);
        flow.observe(122, 1, 122, 7, 5000);
        assertTrue(flow.active);
    }
}
