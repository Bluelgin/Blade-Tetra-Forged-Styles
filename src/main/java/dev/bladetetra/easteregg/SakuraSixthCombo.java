package dev.bladetetra.easteregg;

import dev.bladetetra.combat.ModComboStates;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import mods.flammpfeil.slashblade.registry.combo.ComboState;
import net.minecraftforge.registries.RegistryObject;

/** Legacy mid-combo save ID only. New ordinary attacks use upstream B6/B7;
 * new infinite dance uses SakuraDanceCombos. Never routes back to B1.
 */
public final class SakuraSixthCombo {
    public static final int INPUT_TAIL_MS = 500;
    public static final RegistryObject<ComboState> SIXTH = ModComboStates.COMBOS.register(
            "awakened_sakura_b6", SakuraSixthCombo::create);
    private static boolean compatible;
    public static void register() {}
    public static boolean compatible() { return compatible; }
    private static ComboState create() {
        var original = ComboStateRegistry.COMBO_B6.get();
        var builder = ComboState.Builder.newInstance()
                .startAndEnd(original.getStartFrame(), original.getEndFrame())
                .motionLoc(original.getMotionLoc()).speed(original.getSpeed())
                .priority(original.getPriority()).timeout(original.timeout + INPUT_TAIL_MS)
                .next(original::getNext) // legacy saved node recovers into ordinary B7
                .nextOfTimeout(original::getNextOfTimeout)
                .addHoldAction(original::holdAction)
                .addHitEffect(original::hitEffect)
                .clickAction(original::clickAction)
                .releaseAction(original::releaseAction);
        try {
            // Builder only exposes addTickAction, and its default already invokes
            // ArrowReflector. Adding original::tickAction would invoke reflection
            // twice. One registration-time assignment keeps the ENTIRE native
            // callback exactly once, with no per-tick reflection or global mutation.
            var tick = ComboState.Builder.class.getDeclaredField("tickAction");
            if (tick.getType() != java.util.function.Consumer.class)
                throw new NoSuchFieldException("unexpected ComboState.Builder.tickAction type");
            tick.setAccessible(true);
            tick.set(builder, (java.util.function.Consumer<net.minecraft.world.entity.LivingEntity>) original::tickAction);
            compatible = true;
        } catch (ReflectiveOperationException | RuntimeException unsupported) {
            compatible = false;
            com.mojang.logging.LogUtils.getLogger().warn(
                    "Sakura B6 adapter unavailable; retaining native B6 without accelerated re-entry", unsupported);
        }
        return builder.build();
    }
    private SakuraSixthCombo() {}
}
