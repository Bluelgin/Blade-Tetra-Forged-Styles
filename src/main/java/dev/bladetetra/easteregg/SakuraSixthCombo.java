package dev.bladetetra.easteregg;

import dev.bladetetra.combat.ModComboStates;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import mods.flammpfeil.slashblade.registry.combo.ComboState;
import net.minecraftforge.registries.RegistryObject;

/** Saved 2.x transient B6 ID. The server safely recovers it to NONE on the next tick. */
public final class SakuraSixthCombo {
    public static final RegistryObject<ComboState> SIXTH=ModComboStates.COMBOS.register(
            "awakened_sakura_b6",()-> {
                var original=ComboStateRegistry.COMBO_B6.get();
                return ComboState.Builder.newInstance().startAndEnd(original.getStartFrame(),original.getEndFrame())
                        .motionLoc(original.getMotionLoc()).speed(original.getSpeed())
                        .next(user->ComboStateRegistry.NONE.getId()).nextOfTimeout(user->ComboStateRegistry.NONE.getId()).build();
            });
    public static void register() {}
    private SakuraSixthCombo() {}
}
