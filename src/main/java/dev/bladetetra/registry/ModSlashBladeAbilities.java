package dev.bladetetra.registry;

import dev.bladetetra.BladeTetra;
import dev.bladetetra.combat.ModComboStates;
import dev.bladetetra.forging.LegacyFusion;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import mods.flammpfeil.slashblade.registry.specialeffects.SpecialEffect;
import mods.flammpfeil.slashblade.slasharts.SlashArts;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/** Native SlashBlade registrations used by the first ordered legacy fusions. */
public final class ModSlashBladeAbilities {
    public static final DeferredRegister<SlashArts> SLASH_ARTS =
            DeferredRegister.create(SlashArts.REGISTRY_KEY, BladeTetra.MOD_ID);
    public static final DeferredRegister<SpecialEffect> SPECIAL_EFFECTS =
            DeferredRegister.create(SpecialEffect.REGISTRY_KEY, BladeTetra.MOD_ID);

    public static final RegistryObject<SlashArts> TWIN_FOX_PIERCING =
            SLASH_ARTS.register("twin_fox_piercing", () -> new SlashArts(entity ->
                    isActive(entity.getMainHandItem(),
                            LegacyFusion.BLACK_SAYA_WHITE_HILT)
                            ? ComboStateRegistry.STANDBY.getId()
                            : ComboStateRegistry.NONE.getId())
                    .setComboStateJust(entity -> isActive(entity.getMainHandItem(),
                            LegacyFusion.BLACK_SAYA_WHITE_HILT)
                            ? ComboStateRegistry.STANDBY.getId()
                            : ComboStateRegistry.NONE.getId())
                    .setComboStateSuper(entity -> isActive(entity.getMainHandItem(),
                            LegacyFusion.BLACK_SAYA_WHITE_HILT)
                            ? ComboStateRegistry.STANDBY.getId()
                            : ComboStateRegistry.NONE.getId())
                    .setProudSoulCost(40));

    public static final RegistryObject<SlashArts> TWIN_PHASE_KIKOUKU =
            SLASH_ARTS.register("twin_phase_kikouku", () -> new SlashArts(entity ->
                    isActive(entity.getMainHandItem(),
                            LegacyFusion.YASHA_SAYA_KIKOUKU_HILT)
                            ? ModComboStates.TWIN_PHASE_DRAW.getId()
                            : ComboStateRegistry.NONE.getId())
                    .setComboStateJust(entity -> isActive(entity.getMainHandItem(),
                            LegacyFusion.YASHA_SAYA_KIKOUKU_HILT)
                            ? ModComboStates.TWIN_PHASE_DRAW.getId()
                            : ComboStateRegistry.NONE.getId())
                    .setComboStateSuper(entity -> isActive(entity.getMainHandItem(),
                            LegacyFusion.YASHA_SAYA_KIKOUKU_HILT)
                            ? ModComboStates.TWIN_PHASE_DRAW.getId()
                            : ComboStateRegistry.NONE.getId())
                    .setProudSoulCost(45));

    /** Structural SE: the registry says it cannot be extracted into an orb. */
    public static final RegistryObject<SpecialEffect> TWIN_FOX_REFLECTION =
            SPECIAL_EFFECTS.register("twin_fox_reflection",
                    () -> new SpecialEffect(0, false, false));

    private static boolean isActive(net.minecraft.world.item.ItemStack stack,
            LegacyFusion expected) {
        return LegacyFusion.active(stack) == expected;
    }

    private ModSlashBladeAbilities() {
    }
}
