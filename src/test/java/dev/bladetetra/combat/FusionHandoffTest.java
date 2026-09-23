package dev.bladetetra.combat;

import org.junit.jupiter.api.Test;

class FusionHandoffTest {
    @Test void shortSignature() { FusionHandoffScenarios.shortSignature(); }
    @Test void delayedSignature() { FusionHandoffScenarios.delayedSignature(); }
    @Test void longRecovery() { FusionHandoffScenarios.longRecovery(); }
    @Test void multiStage() { FusionHandoffScenarios.multiStage(); }
    @Test void firstPollAfterProgression() { FusionHandoffScenarios.firstPollAfterProgression(); }
    @Test void interruption() { FusionHandoffScenarios.interruption(); }
    @Test void watchdog() { FusionHandoffScenarios.watchdog(); }
    @Test void dynamicNaturalCompletion() { FusionHandoffScenarios.dynamicNaturalCompletion(); }
    @Test void dynamicInterruptionAndWatchdog() {
        FusionHandoffScenarios.dynamicInterruptionAndWatchdog();
    }
    @Test void responseLifecycle() { FusionHandoffScenarios.responseLifecycle(); }
    @Test void orderedIdentity() { FusionHandoffScenarios.orderedIdentity(); }
    @Test void missingAndCancelled() { FusionHandoffScenarios.missingAndCancelled(); }
    @Test void nativeAudits() { FusionHandoffScenarios.nativeAudits(); }
}
