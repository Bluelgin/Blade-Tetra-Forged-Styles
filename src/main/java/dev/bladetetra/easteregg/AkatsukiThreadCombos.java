package dev.bladetetra.easteregg;

import dev.bladetetra.combat.ModComboStates;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import mods.flammpfeil.slashblade.registry.combo.ComboState;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.RegistryObject;

/** Native piercing motion only. Deliberately excludes its movement and attack consumers. */
public final class AkatsukiThreadCombos {
    public static final ResourceLocation THRUST_ID=new ResourceLocation("blade_tetra","akatsuki_thread_thrust");
    public static final RegistryObject<ComboState> THRUST=ModComboStates.COMBOS.register("akatsuki_thread_thrust",()->{
        var nativePose=ComboStateRegistry.PIERCING.get();
        return ComboState.Builder.newInstance().startAndEnd(nativePose.getStartFrame(),nativePose.getEndFrame())
                .motionLoc(nativePose.getMotionLoc()).speed(nativePose.getSpeed()).priority(0).timeout(120000)
                .next(user->THRUST_ID).nextOfTimeout(user->ComboStateRegistry.PIERCING_END.getId())
                .clickAction(AkatsukiBloodTendrils::committed).build();
    });
    public static void register() {}
    private AkatsukiThreadCombos() {}
}
