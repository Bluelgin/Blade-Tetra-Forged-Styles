package dev.bladetetra;

import dev.bladetetra.combat.ModComboStates;
import dev.bladetetra.compat.LegacyFusionRequirement;
import dev.bladetetra.compat.LegacyImprintCraftingCondition;
import dev.bladetetra.compat.LegacyImprintCraftingOutcome;
import dev.bladetetra.compat.SoulFusionRequirement;
import dev.bladetetra.compat.fusion.ProgrammaticFusionCompat;
import dev.bladetetra.config.ClientVisualConfig;
import dev.bladetetra.config.GameplayConfig;
import dev.bladetetra.network.ModNetwork;
import dev.bladetetra.registry.ModEnchantments;
import dev.bladetetra.registry.ModEntities;
import dev.bladetetra.registry.ModItems;
import dev.bladetetra.registry.ModLootModifiers;
import dev.bladetetra.registry.ModRecipes;
import dev.bladetetra.registry.ModSlashBladeAbilities;
import dev.bladetetra.registry.ModSounds;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import se.mickelus.tetra.craftingeffect.CraftingEffectRegistry;

@Mod(BladeTetra.MOD_ID)
public final class BladeTetra {
    public static final String MOD_ID = "blade_tetra";

    public BladeTetra() {
        SoulFusionRequirement.register();
        dev.bladetetra.compat.LegacyPatternRequirement.register();
        LegacyFusionRequirement.register();

        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        modBus.addListener(BladeTetra::commonSetup);
        ModLoadingContext.get().registerConfig(
                ModConfig.Type.SERVER,
                GameplayConfig.SPEC,
                "blade-tetra-server.toml");
        ModLoadingContext.get().registerConfig(
                ModConfig.Type.CLIENT,
                ClientVisualConfig.SPEC,
                "blade-tetra-client.toml");
        ModItems.ITEMS.register(modBus);
        ModEntities.ENTITIES.register(modBus);
        ModItems.CREATIVE_TABS.register(modBus);
        ModLootModifiers.CODECS.register(modBus);
        ModEnchantments.ENCHANTMENTS.register(modBus);
        ModRecipes.SERIALIZERS.register(modBus);
        ModSounds.SOUND_EVENTS.register(modBus);
        ModComboStates.COMBOS.register(modBus);
        ModSlashBladeAbilities.SLASH_ARTS.register(modBus);
        ModSlashBladeAbilities.SPECIAL_EFFECTS.register(modBus);
        ModNetwork.register();
    }

    private static void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            CraftingEffectRegistry.registerConditionType(
                    "blade_tetra:legacy_imprint_slot", LegacyImprintCraftingCondition.class);
            CraftingEffectRegistry.registerEffectType(
                    "blade_tetra:apply_legacy_imprint_identity", LegacyImprintCraftingOutcome.class);
            ProgrammaticFusionCompat.registerLoadedAddons();
        });
    }
}
