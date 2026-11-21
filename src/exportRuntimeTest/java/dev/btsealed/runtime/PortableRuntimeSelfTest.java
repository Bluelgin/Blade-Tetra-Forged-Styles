package dev.btsealed.runtime;

/** Runs only inside the isolated receiving-server probe JAR, not in production. */
public final class PortableRuntimeSelfTest {
    public static void verifyPrecisionClock() {
        var clock = new PortableRankRetention.Clock();
        if (clock.advance(100, true) || clock.advance(101, true)) {
            throw new IllegalStateException("Precision clock credited Rank before two protected ticks");
        }
        if (!clock.advance(102, true)) {
            throw new IllegalStateException("Precision clock did not compensate the second protected tick");
        }
        if (clock.advance(103, true) || !clock.advance(104, true)) {
            throw new IllegalStateException("Precision clock missed its alternate-tick cadence");
        }
        // Losing continuity must not carry a partial credit into the next hold.
        if (clock.advance(107, false) || clock.advance(108, true)
                || clock.advance(109, true) || !clock.advance(110, true)) {
            throw new IllegalStateException("Precision clock leaked a partial credit across a gap");
        }
        // Switching between two separate exported mods cannot reuse a partial Rank tick.
        var tracking = new PortableRankRetention.Tracking();
        if (tracking.advance(200, "item.bt_export_custom.sealed_blade")
                || tracking.advance(201, "item.bt_export_custom_second.sealed_blade")
                || tracking.advance(202, "item.bt_export_custom_second.sealed_blade")
                || !tracking.advance(203, "item.bt_export_custom_second.sealed_blade")) {
            throw new IllegalStateException("Precision clock transferred a Rank credit between sealed blades");
        }
        if (tracking.advance(204, null)
                || tracking.advance(205, "item.bt_export_custom.sealed_blade")
                || tracking.advance(206, "item.bt_export_custom.sealed_blade")
                || !tracking.advance(207, "item.bt_export_custom.sealed_blade")) {
            throw new IllegalStateException("Precision clock retained state after re-equipping a sealed blade");
        }
        System.out.println("BLADE_EXPORT_PRECISION_CLOCK_PASS");
    }

    private PortableRuntimeSelfTest() {}
}
