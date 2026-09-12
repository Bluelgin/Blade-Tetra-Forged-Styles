package dev.bladetetra;

import dev.bladetetra.combat.ModComboStates;
import dev.bladetetra.config.ClientVisualConfig;
import dev.bladetetra.config.GameplayConfig;
import dev.bladetetra.registry.ModEnchantments;
import dev.bladetetra.registry.ModItems;
import dev.bladetetra.registry.ModEntities;
import dev.bladetetra.registry.ModLootModifiers;
import dev.bladetetra.registry.ModRecipes;
import dev.bladetetra.registry.ModSounds;
import dev.bladetetra.network.ModNetwork;
import dev.bladetetra.compat.SoulFusionRequirement;
import dev.bladetetra.compat.LegacyFusionRequirement;
import dev.bladetetra.registry.ModSlashBladeAbilities;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(BladeTetra.MOD_ID)
public final class BladeTetra {
    public static final String MOD_ID = "blade_tetra";

    public BladeTetra() {
        SoulFusionRequirement.register();
        dev.bladetetra.compat.LegacyPatternRequirement.register();
        LegacyFusionRequirement.register();
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
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
}
