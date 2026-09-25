package dev.bladetetra.registry;

import dev.bladetetra.BladeTetra;
import dev.bladetetra.combat.ForgedSlashArtPlan;
import dev.bladetetra.combat.ModComboStates;
import dev.bladetetra.combat.ProgrammaticFusionPlan;
import dev.bladetetra.forging.LegacyFusion;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import mods.flammpfeil.slashblade.registry.specialeffects.SpecialEffect;
import mods.flammpfeil.slashblade.slasharts.SlashArts;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/** Native SlashBlade registrations used by Blade Tetra structural abilities. */
public final class ModSlashBladeAbilities {
    public static final DeferredRegister<SlashArts> SLASH_ARTS =
            DeferredRegister.create(SlashArts.REGISTRY_KEY, BladeTetra.MOD_ID);
    public static final DeferredRegister<SpecialEffect> SPECIAL_EFFECTS =
            DeferredRegister.create(SpecialEffect.REGISTRY_KEY, BladeTetra.MOD_ID);

    /**
     * Single runtime SA for all non-authored mixed named-blade fittings. The ordered
     * pair is resolved from the item at cast time rather than registered per pair.
     */
    public static final RegistryObject<SlashArts> PROGRAMMATIC_FUSION =
            SLASH_ARTS.register("programmatic_fusion", () -> new SlashArts(entity ->
                    programmaticCombo(entity, ProgrammaticTrigger.NORMAL))
                    .setComboStateJust(entity -> programmaticCombo(
                            entity, ProgrammaticTrigger.JUST))
                    .setComboStateSuper(entity -> programmaticCombo(
                            entity, ProgrammaticTrigger.SUPER))
                    .setProudSoulCost(45));

    /** One structural registry entry for every four-part Tetra-authored Slash Art. */
    public static final RegistryObject<SlashArts> FORGED_SLASH_ART =
            SLASH_ARTS.register("forged_slash_art", () -> new SlashArts(
                    ModSlashBladeAbilities::forgedCombo)
                    .setComboStateJust(ModSlashBladeAbilities::forgedCombo)
                    .setComboStateSuper(ModSlashBladeAbilities::forgedCombo)
                    .setProudSoulCost(45));

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

    public static final RegistryObject<SlashArts> DOUWARI =
            SLASH_ARTS.register("douwari", () -> standbyArt(
                    LegacyFusion.MURAMASA_SAYA_DOUTANUKI_HILT, 45));

    public static final RegistryObject<SlashArts> VOID_SCATTERING =
            SLASH_ARTS.register("void_scattering", () -> standbyArt(
                    LegacyFusion.SANGE_SAYA_YAMATO_HILT, 70));

    public static final RegistryObject<SlashArts> TSUKUMO_CROSS =
            SLASH_ARTS.register("tsukumo_cross", () -> standbyArt(
                    LegacyFusion.AGITO_SAYA_TUKUMO_HILT, 45));

    public static final RegistryObject<SlashArts> WITHERED_DRIVE =
            SLASH_ARTS.register("withered_drive", () -> standbyArt(
                    LegacyFusion.TAGAYASAN_SAYA_KOSEKI_HILT, 45));

    public static final RegistryObject<SlashArts> PIERCING_VOID_MOON =
            SLASH_ARTS.register("piercing_void_moon", () -> piercingArt(
                    LegacyFusion.BLACK_SAYA_SANGE_HILT, 55));

    /** Sakura-End lineage retained by the Dead Thought story-convergence fusion. */
    public static final RegistryObject<SlashArts> BLOOD_CHERRY_FINAL_SCENE =
            SLASH_ARTS.register("blood_cherry_final_scene", () -> sakuraEndArt(
                    LegacyFusion.NIHILUL_SAYA_CRIMSON_CHERRY_HILT, 60));

    /** Structural SE: the registry says it cannot be extracted into an orb. */
    public static final RegistryObject<SpecialEffect> TWIN_FOX_REFLECTION =
            SPECIAL_EFFECTS.register("twin_fox_reflection",
                    () -> new SpecialEffect(0, false, false));

    /** Structural normal-combo SE for the sealed Agito/Orotiagito fitting pair. */
    public static final RegistryObject<SpecialEffect> SNAKE_MOLT =
            SPECIAL_EFFECTS.register("snake_molt",
                    () -> new SpecialEffect(0, false, false));

    /** Dead Thought's non-extractable maximum-life erosion rule. */
    public static final RegistryObject<SpecialEffect> LIFE_EROSION =
            SPECIAL_EFFECTS.register("life_erosion",
                    () -> new SpecialEffect(0, false, false));

    private static ResourceLocation forgedCombo(LivingEntity entity) {
        ForgedSlashArtPlan plan = ForgedSlashArtPlan.from(entity.getMainHandItem());
        // ForgedSlashArtHandler replaces this placeholder with the selected
        // native SlashBlade entry during PerformSlashArtEvent. A non-NONE
        // placeholder keeps the normal SlashArt release/cost path active.
        return plan == null ? ComboStateRegistry.NONE.getId()
                : ComboStateRegistry.STANDBY.getId();
    }

    private static ResourceLocation programmaticCombo(LivingEntity entity,
            ProgrammaticTrigger trigger) {
        ProgrammaticFusionPlan plan = ProgrammaticFusionPlan.from(entity.getMainHandItem());
        if (plan == null) {
            return ComboStateRegistry.NONE.getId();
        }
        return switch (plan.release().entry()) {
            case JUDGEMENT_CUT -> switch (trigger) {
                case JUST -> ComboStateRegistry.JUDGEMENT_CUT_SLASH_JUST.getId();
                case SUPER -> ComboStateRegistry.JUDGEMENT_CUT_END.getId();
                case NORMAL -> entity.onGround()
                        ? ComboStateRegistry.JUDGEMENT_CUT.getId()
                        : ComboStateRegistry.JUDGEMENT_CUT_SLASH_AIR.getId();
            };
            case SAKURA_END -> entity.onGround()
                    ? ComboStateRegistry.SAKURA_END_LEFT.getId()
                    : ComboStateRegistry.SAKURA_END_LEFT_AIR.getId();
            case VOID_SLASH -> ComboStateRegistry.VOID_SLASH.getId();
            case CIRCLE_SLASH -> ComboStateRegistry.CIRCLE_SLASH.getId();
            case DRIVE_VERTICAL -> ComboStateRegistry.DRIVE_VERTICAL.getId();
            case DRIVE_HORIZONTAL -> ComboStateRegistry.DRIVE_HORIZONTAL.getId();
            case WAVE_EDGE -> ComboStateRegistry.WAVE_EDGE_VERTICAL.getId();
            case PIERCING -> trigger == ProgrammaticTrigger.NORMAL
                    ? ComboStateRegistry.PIERCING.getId()
                    : ComboStateRegistry.PIERCING_JUST.getId();
            case DIRECT -> ComboStateRegistry.STANDBY.getId();
        };
    }

    private static SlashArts standbyArt(LegacyFusion fusion, int proudSoulCost) {
        return new SlashArts(entity -> isActive(entity.getMainHandItem(), fusion)
                ? ComboStateRegistry.STANDBY.getId()
                : ComboStateRegistry.NONE.getId())
                .setComboStateJust(entity -> isActive(entity.getMainHandItem(), fusion)
                        ? ComboStateRegistry.STANDBY.getId()
                        : ComboStateRegistry.NONE.getId())
                .setComboStateSuper(entity -> isActive(entity.getMainHandItem(), fusion)
                        ? ComboStateRegistry.STANDBY.getId()
                        : ComboStateRegistry.NONE.getId())
                .setProudSoulCost(proudSoulCost);
    }

    private static SlashArts piercingArt(LegacyFusion fusion, int proudSoulCost) {
        return new SlashArts(entity -> isActive(entity.getMainHandItem(), fusion)
                ? ComboStateRegistry.PIERCING.getId()
                : ComboStateRegistry.NONE.getId())
                .setComboStateJust(entity -> isActive(entity.getMainHandItem(), fusion)
                        ? ComboStateRegistry.PIERCING_JUST.getId()
                        : ComboStateRegistry.NONE.getId())
                .setComboStateSuper(entity -> isActive(entity.getMainHandItem(), fusion)
                        ? ComboStateRegistry.PIERCING_JUST.getId()
                        : ComboStateRegistry.NONE.getId())
                .setProudSoulCost(proudSoulCost);
    }

    private static SlashArts sakuraEndArt(LegacyFusion fusion, int proudSoulCost) {
        return new SlashArts(entity -> isActive(entity.getMainHandItem(), fusion)
                ? entity.onGround()
                        ? ComboStateRegistry.SAKURA_END_LEFT.getId()
                        : ComboStateRegistry.SAKURA_END_LEFT_AIR.getId()
                : ComboStateRegistry.NONE.getId())
                .setComboStateJust(entity -> isActive(entity.getMainHandItem(), fusion)
                        ? entity.onGround()
                                ? ComboStateRegistry.SAKURA_END_LEFT.getId()
                                : ComboStateRegistry.SAKURA_END_LEFT_AIR.getId()
                        : ComboStateRegistry.NONE.getId())
                .setComboStateSuper(entity -> isActive(entity.getMainHandItem(), fusion)
                        ? entity.onGround()
                                ? ComboStateRegistry.SAKURA_END_LEFT.getId()
                                : ComboStateRegistry.SAKURA_END_LEFT_AIR.getId()
                        : ComboStateRegistry.NONE.getId())
                .setProudSoulCost(proudSoulCost);
    }

    private static boolean isActive(net.minecraft.world.item.ItemStack stack,
            LegacyFusion expected) {
        return LegacyFusion.active(stack) == expected;
    }

    private enum ProgrammaticTrigger {
        NORMAL,
        JUST,
        SUPER
    }

    private ModSlashBladeAbilities() {
    }
}
