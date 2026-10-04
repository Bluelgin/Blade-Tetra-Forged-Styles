package dev.bladetetra.combat;

import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import net.minecraft.resources.ResourceLocation;

import static dev.bladetetra.combat.StyleBranchRules.*;

/** Return native nodes, including their hold, jump and landing callbacks. */
final class StyleCommonCommands {
    static ResourceLocation resolve(Intent intent, boolean grounded, boolean rightClick) {
        return switch (commonMove(intent, grounded, rightClick)) {
            case RAPID -> ComboStateRegistry.RAPID_SLASH.getId();
            case UPPER -> ComboStateRegistry.UPPERSLASH.getId();
            case CLEAVE -> ComboStateRegistry.AERIAL_CLEAVE.getId();
            case NONE -> null;
        };
    }
    private StyleCommonCommands() {}
}
