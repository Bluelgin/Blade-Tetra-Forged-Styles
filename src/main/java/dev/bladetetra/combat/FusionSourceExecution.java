package dev.bladetetra.combat;

import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** Selection is not execution: a valid selection must be committed and verified. */
final class FusionSourceExecution {
    static <C> boolean enter(Supplier<C> doArts, Predicate<C> audited,
            Consumer<C> updateCombo, Predicate<C> committed) {
        C combo = doArts.get();
        if (combo == null || !audited.test(combo)) return false;
        updateCombo.accept(combo);
        return committed.test(combo);
    }

    private FusionSourceExecution() { }
}
