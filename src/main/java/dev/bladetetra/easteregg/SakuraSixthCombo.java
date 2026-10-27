package dev.bladetetra.easteregg;

import dev.bladetetra.combat.ModComboStates;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import mods.flammpfeil.slashblade.registry.combo.ComboState;
import net.minecraftforge.registries.RegistryObject;

/** Same native B6 animation and callbacks, with a two-tick input tail.
 * Upstream permits early next-input at tick 4 but authors hits through tick 6;
 * its 333ms timeout otherwise expires before a safe tick-7 re-entry. Only the
 * dispersed Sakura B5 route uses this node. No global native registry mutation.
 */
public final class SakuraSixthCombo {
    public static final RegistryObject<ComboState> SIXTH = ModComboStates.COMBOS.register(
            "awakened_sakura_b6", SakuraSixthCombo::create);
    public static void register() {}
    private static ComboState create() {
        var original = ComboStateRegistry.COMBO_B6.get();
        return ComboState.Builder.newInstance()
                .startAndEnd(original.getStartFrame(), original.getEndFrame())
                .motionLoc(original.getMotionLoc()).speed(original.getSpeed())
                .priority(original.getPriority()).timeout(original.timeout + 100)
                .next(user -> SIXTH.getId()) // NextComboEvent supplies the authorized B1 route
                .nextOfTimeout(original::getNextOfTimeout)
                .addTickAction(original::tickAction)
                .addHoldAction(original::holdAction)
                .addHitEffect(original::hitEffect)
                .clickAction(original::clickAction)
                .releaseAction(original::releaseAction).build();
    }
    private SakuraSixthCombo() {}
}
