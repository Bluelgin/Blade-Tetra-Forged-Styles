package dev.bladetetra.visual;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DeadThoughtVisualMathTest {
    @Test void erosionIsUnboundedButVisualsAreNot() {
        assertEquals(0, DeadThoughtVisualMath.severity(.149999));
        assertEquals(1, DeadThoughtVisualMath.severity(.15));
        assertEquals(2, DeadThoughtVisualMath.severity(.4));
        assertEquals(3, DeadThoughtVisualMath.severity(.7));
        assertEquals(3, DeadThoughtVisualMath.severity(1.8));
        assertEquals(3, DeadThoughtVisualMath.severity(Double.MAX_VALUE));
        assertEquals(0, DeadThoughtVisualMath.severity(Double.NaN));
    }
    @Test void boxScalingStaysReadableForTinyAndHugeTargets() {
        assertEquals(.8F, DeadThoughtVisualMath.scale(.3,.4));
        assertEquals(2.2F, DeadThoughtVisualMath.scale(100,100));
        assertEquals(1, DeadThoughtVisualMath.scale(Double.NaN, 2));
        assertEquals(1.5F, DeadThoughtVisualMath.wheelRadius(.8F));
        assertEquals(3.5F, DeadThoughtVisualMath.wheelRadius(2.2F));
    }
    @Test void RiftOpensAndActuallyCloses() {
        assertEquals(0, DeadThoughtVisualMath.opening(-1));
        assertEquals(0, DeadThoughtVisualMath.opening(0));
        assertEquals(1, DeadThoughtVisualMath.opening(2));
        assertEquals(1, DeadThoughtVisualMath.opening(4));
        assertEquals(.5F, DeadThoughtVisualMath.opening(6));
        assertEquals(0, DeadThoughtVisualMath.opening(8));
        assertEquals(0, DeadThoughtVisualMath.opening(500));
    }
    @Test void collapseFragmentsBurstHoldAndReturn() {
        assertEquals(0F, DeadThoughtVisualMath.collapseFragmentDistance(0), 0.0001F);
        assertEquals(1F, DeadThoughtVisualMath.collapseFragmentDistance(4), 0.0001F);
        assertEquals(1F, DeadThoughtVisualMath.collapseFragmentDistance(8), 0.0001F);
        assertEquals(.5F, DeadThoughtVisualMath.collapseFragmentDistance(14), 0.0001F);
        assertEquals(0F, DeadThoughtVisualMath.collapseFragmentDistance(20), 0.0001F);
        assertEquals(1F, DeadThoughtVisualMath.collapseFade(20), 0.0001F);
        assertEquals(.5F, DeadThoughtVisualMath.collapseFade(24), 0.0001F);
        assertEquals(0F, DeadThoughtVisualMath.collapseFade(28), 0.0001F);
    }
}
