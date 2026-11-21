package dev.bladetetra.combat;

import dev.bladetetra.forging.LegacyImprintKind;
import dev.bladetetra.forging.NamedLegacyParts;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProgrammaticFusionPlanTest {
    @Test
    void sourceOrderAndExplicitIdsSurvivePlanning() {
        var saya = source("test_saya", "uninstalled_addon:some_art", List.of());
        var guard = source("test_guard", "slashblade:drive_vertical", List.of());
        var forward = ProgrammaticFusionPlan.from(new NamedLegacyParts(saya, guard, guard));
        var reverse = ProgrammaticFusionPlan.from(new NamedLegacyParts(guard, saya, saya));

        assertEquals("test_saya->test_guard", forward.key());
        assertEquals(saya.slashArt(), forward.releaseAbility());
        assertEquals(guard.slashArt(), forward.responseAbility());
        assertEquals(forward.responseAbility(), reverse.releaseAbility());
        assertEquals(forward.releaseAbility(), reverse.responseAbility());
    }

    @Test
    void effectsAndBladeNamesNeverSupplyAnArtId() {
        // Both the weapon name and its SE deliberately collide with real SA IDs.
        var noArt = source("circle_slash", null, List.of(new ResourceLocation("slashblade", "piercing")));
        var guard = source("guard", "slashblade:drive_vertical", List.of());
        var plan = ProgrammaticFusionPlan.from(new NamedLegacyParts(noArt, guard, guard));
        assertNull(plan.releaseAbility());
        assertEquals(guard.slashArt(), plan.responseAbility());
    }

    @Test
    void sameSourceAndAuthoredPairKeepTheirOwnPath() {
        var source = source("same_source", "slashblade:circle_slash", List.of());
        assertNull(ProgrammaticFusionPlan.from(new NamedLegacyParts(source, source, source)));
        assertNull(ProgrammaticFusionPlan.from(new NamedLegacyParts(
                LegacyImprintKind.BLACK_FOX, LegacyImprintKind.WHITE_FOX, LegacyImprintKind.WHITE_FOX)));
    }

    @Test
    void sourceFramesAreScopedToExactArtsAndStates() {
        var spiral = new ResourceLocation("slashblade_addon", "spiral_edge");
        var soul = new ResourceLocation("recasting", "soul_sever");
        assertEquals(7, NativeFusionLifecycle.requiredFrame(spiral, spiral));
        assertEquals(16, NativeFusionLifecycle.requiredFrame(soul, soul));
        assertEquals(0, NativeFusionLifecycle.requiredFrame(soul, new ResourceLocation("slashblade", "void_slash_sheath")));
        assertEquals(0, NativeFusionLifecycle.requiredFrame(
                new ResourceLocation("unknown", "spiral_edge"), spiral));
    }

    private static LegacyImprintKind source(String id, String art, List<ResourceLocation> effects) {
        var base = LegacyImprintKind.WOODEN_PUPPET;
        return new LegacyImprintKind(id, new ResourceLocation("slashblade", id), base.model(),
                base.texture(), base.material(), base.defaultProfile(), base.baseAttack(),
                base.maxDamage(), art == null ? null : new ResourceLocation(art), effects);
    }
}
