package dev.bladetetra.combat;

import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import mods.flammpfeil.slashblade.slasharts.SlashArts;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForgedSlashArtPlanTest {
    @Test
    void nativeFlowPreservesFailAndJustWhileNormalizingSuper() {
        assertEquals(SlashArts.ArtsType.Fail,
                ForgedNativeComboFlow.sourceType(SlashArts.ArtsType.Fail));
        assertEquals(SlashArts.ArtsType.Success,
                ForgedNativeComboFlow.sourceType(SlashArts.ArtsType.Success));
        assertEquals(SlashArts.ArtsType.Jackpot,
                ForgedNativeComboFlow.sourceType(SlashArts.ArtsType.Jackpot));
        assertEquals(SlashArts.ArtsType.Success,
                ForgedNativeComboFlow.sourceType(SlashArts.ArtsType.Super));
    }

    @Test
    void everyTechniqueAndModifierCombinationCompilesToNativeRoute() {
        for (ForgedSlashArtPlan.Technique primary
                : ForgedSlashArtPlan.Technique.values()) {
            for (ForgedSlashArtPlan.Technique secondary
                    : ForgedSlashArtPlan.Technique.values()) {
                for (ForgedSlashArtPlan.Modifier modifier
                        : ForgedSlashArtPlan.Modifier.values()) {
                    ForgedSlashArtPlan plan = ForgedSlashArtPlan.compose(
                            "sa_core/diamond", primary, secondary, modifier);
                    assertNotNull(plan);
                    assertEquals(primary, plan.primary());
                    assertEquals(secondary, plan.secondary());
                    assertEquals(modifier, plan.modifier());
                    assertTrue(modifier.spliceTailTicks() >= 0);
                }
            }
        }
    }

    @Test
    void orderedPrimaryAndSecondaryRemainDifferentRoutes() {
        ForgedSlashArtPlan forward = ForgedSlashArtPlan.compose(
                "sa_core/iron",
                ForgedSlashArtPlan.Technique.PIERCING,
                ForgedSlashArtPlan.Technique.CIRCLE_SLASH,
                ForgedSlashArtPlan.Modifier.BALANCED);
        ForgedSlashArtPlan reverse = ForgedSlashArtPlan.compose(
                "sa_core/iron",
                ForgedSlashArtPlan.Technique.CIRCLE_SLASH,
                ForgedSlashArtPlan.Technique.PIERCING,
                ForgedSlashArtPlan.Modifier.BALANCED);
        assertNotEquals(forward.key(), reverse.key());
        assertNotEquals(forward.primary(), reverse.primary());
        assertNotEquals(forward.secondary(), reverse.secondary());
    }

    @Test
    void modifiersOnlyControlRoutingCadence() {
        assertEquals(1,
                ForgedSlashArtPlan.Modifier.BALANCED.spliceTailTicks());
        assertEquals(0,
                ForgedSlashArtPlan.Modifier.CONDENSED.spliceTailTicks());
        assertEquals(0,
                ForgedSlashArtPlan.Modifier.HASTE.spliceTailTicks());
        assertEquals(2,
                ForgedSlashArtPlan.Modifier.SHATTER.spliceTailTicks());
        assertTrue(ForgedSlashArtPlan.Modifier.ECHO.repeatsSecondary());
        assertFalse(ForgedSlashArtPlan.Modifier.BALANCED.repeatsSecondary());
    }

    @Test
    void secondaryEntriesSkipSourceWindupsAndEnterSignatureStates() {
        assertEquals(ComboStateRegistry.JUDGEMENT_CUT_SLASH.getId(),
                ForgedNativeComboFlow.signatureEntry(
                        ForgedSlashArtPlan.Technique.JUDGEMENT_CUT,
                        SlashArts.ArtsType.Success, true));
        assertEquals(ComboStateRegistry.JUDGEMENT_CUT_SLASH_JUST.getId(),
                ForgedNativeComboFlow.signatureEntry(
                        ForgedSlashArtPlan.Technique.JUDGEMENT_CUT,
                        SlashArts.ArtsType.Jackpot, true));
        assertEquals(ComboStateRegistry.SAKURA_END_RIGHT.getId(),
                ForgedNativeComboFlow.signatureEntry(
                        ForgedSlashArtPlan.Technique.SAKURA_END,
                        SlashArts.ArtsType.Success, true));
        assertEquals(ComboStateRegistry.SAKURA_END_RIGHT_AIR.getId(),
                ForgedNativeComboFlow.signatureEntry(
                        ForgedSlashArtPlan.Technique.SAKURA_END,
                        SlashArts.ArtsType.Success, false));
        assertEquals(ComboStateRegistry.PIERCING_2.getId(),
                ForgedNativeComboFlow.signatureEntry(
                        ForgedSlashArtPlan.Technique.PIERCING,
                        SlashArts.ArtsType.Success, true));
        assertEquals(ComboStateRegistry.PIERCING_JUST.getId(),
                ForgedNativeComboFlow.signatureEntry(
                        ForgedSlashArtPlan.Technique.PIERCING,
                        SlashArts.ArtsType.Jackpot, true));
    }

    @Test
    void spliceTicksFollowNativeSignatureCallbacks() {
        assertEquals(1, ForgedNativeComboFlow.signatureCompleteTick(
                ForgedSlashArtPlan.Technique.JUDGEMENT_CUT,
                ComboStateRegistry.JUDGEMENT_CUT_SLASH.getId()));
        assertEquals(2, ForgedNativeComboFlow.signatureCompleteTick(
                ForgedSlashArtPlan.Technique.JUDGEMENT_CUT,
                ComboStateRegistry.JUDGEMENT_CUT_SLASH_JUST.getId()));
        assertEquals(1, ForgedNativeComboFlow.signatureCompleteTick(
                ForgedSlashArtPlan.Technique.SAKURA_END,
                ComboStateRegistry.SAKURA_END_RIGHT.getId()));
        assertEquals(17, ForgedNativeComboFlow.signatureCompleteTick(
                ForgedSlashArtPlan.Technique.VOID_SLASH,
                ComboStateRegistry.VOID_SLASH.getId()));
        assertEquals(8, ForgedNativeComboFlow.signatureCompleteTick(
                ForgedSlashArtPlan.Technique.CIRCLE_SLASH,
                ComboStateRegistry.CIRCLE_SLASH.getId()));
        assertEquals(4, ForgedNativeComboFlow.signatureCompleteTick(
                ForgedSlashArtPlan.Technique.DRIVE_VERTICAL,
                ComboStateRegistry.DRIVE_VERTICAL.getId()));
        assertEquals(4, ForgedNativeComboFlow.signatureCompleteTick(
                ForgedSlashArtPlan.Technique.WAVE_EDGE,
                ComboStateRegistry.WAVE_EDGE_VERTICAL.getId()));
        assertEquals(3, ForgedNativeComboFlow.signatureCompleteTick(
                ForgedSlashArtPlan.Technique.PIERCING,
                ComboStateRegistry.PIERCING_2.getId()));
    }

    @Test
    void sameTechniquePairCanRestartTheSameNativeSignature() {
        ForgedSlashArtPlan plan = ForgedSlashArtPlan.compose(
                "sa_core/diamond",
                ForgedSlashArtPlan.Technique.CIRCLE_SLASH,
                ForgedSlashArtPlan.Technique.CIRCLE_SLASH,
                ForgedSlashArtPlan.Modifier.BALANCED);
        assertEquals(plan.primary(), plan.secondary());
        assertEquals(ComboStateRegistry.CIRCLE_SLASH.getId(),
                ForgedNativeComboFlow.signatureEntry(
                        plan.secondary(), SlashArts.ArtsType.Success, true));
    }

    @Test
    void coreRemainsPersistenceIdentityWithoutOwningNativeDamage() {
        ForgedSlashArtPlan iron = ForgedSlashArtPlan.compose(
                "sa_core/iron",
                ForgedSlashArtPlan.Technique.JUDGEMENT_CUT,
                ForgedSlashArtPlan.Technique.VOID_SLASH,
                ForgedSlashArtPlan.Modifier.BALANCED);
        ForgedSlashArtPlan diamond = ForgedSlashArtPlan.compose(
                "sa_core/diamond",
                ForgedSlashArtPlan.Technique.JUDGEMENT_CUT,
                ForgedSlashArtPlan.Technique.VOID_SLASH,
                ForgedSlashArtPlan.Modifier.BALANCED);

        assertNotEquals(iron.key(), diamond.key());
        assertEquals(iron.primary(), diamond.primary());
        assertEquals(iron.secondary(), diamond.secondary());
        assertEquals(iron.modifier(), diamond.modifier());
    }

    @Test
    void techniqueNamesUseSlashBladesOwnTranslationKeys() {
        for (ForgedSlashArtPlan.Technique technique
                : ForgedSlashArtPlan.Technique.values()) {
            assertEquals("slash_art.slashblade." + technique.id(),
                    technique.translationKey());
        }
    }
}
