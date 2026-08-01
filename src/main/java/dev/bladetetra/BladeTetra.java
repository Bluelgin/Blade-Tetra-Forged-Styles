package dev.bladetetra;

import dev.bladetetra.combat.ModComboStates;
import dev.bladetetra.compat.AutoMaterialConfig;
import dev.bladetetra.config.ClientVisualConfig;
import dev.bladetetra.config.GameplayConfig;
import dev.bladetetra.registry.ModEnchantments;
import dev.bladetetra.registry.ModItems;
import dev.bladetetra.registry.ModLootModifiers;
import dev.bladetetra.registry.ModRecipes;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(BladeTetra.MOD_ID)
public final class BladeTetra {
    public static final String MOD_ID = "blade_tetra";

    public BladeTetra() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModLoadingContext.get().registerConfig(
                ModConfig.Type.COMMON,
                AutoMaterialConfig.SPEC);
        ModLoadingContext.get().registerConfig(
                ModConfig.Type.SERVER,
                GameplayConfig.SPEC,
                "blade-tetra-server.toml");
        ModLoadingContext.get().registerConfig(
                ModConfig.Type.CLIENT,
                ClientVisualConfig.SPEC,
                "blade-tetra-client.toml");
        ModItems.ITEMS.register(modBus);
        ModItems.CREATIVE_TABS.register(modBus);
        ModLootModifiers.CODECS.register(modBus);
        ModEnchantments.ENCHANTMENTS.register(modBus);
        ModRecipes.SERIALIZERS.register(modBus);
        ModComboStates.COMBOS.register(modBus);
    }
}
