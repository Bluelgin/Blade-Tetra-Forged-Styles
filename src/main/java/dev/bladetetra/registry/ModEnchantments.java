package dev.bladetetra.registry;

import dev.bladetetra.BladeTetra;
import dev.bladetetra.enchantment.SoulContractEnchantment;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEnchantments {
    public static final DeferredRegister<Enchantment> ENCHANTMENTS =
            DeferredRegister.create(ForgeRegistries.ENCHANTMENTS, BladeTetra.MOD_ID);

    public static final RegistryObject<Enchantment> SOUL_CONTRACT =
            ENCHANTMENTS.register("soul_contract", SoulContractEnchantment::new);

    private ModEnchantments() {
    }
}
