package dev.bladetetra.enchantment;

import dev.bladetetra.item.ModularSlashBladeItem;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;

/**
 * A zero-stat marker enchantment used by proudsoul inscriptions.
 *
 * <p>SlashBlade: Resharped requires a blade to be enchanted before its
 * default-bewitched flag can make it a bewitched blade. Keeping that marker in
 * a dedicated enchantment lets the inscription be removed without touching any
 * enchantments applied by the player.</p>
 */
public final class SoulContractEnchantment extends Enchantment {
    public SoulContractEnchantment() {
        super(
                Rarity.VERY_RARE,
                EnchantmentCategory.BREAKABLE,
                new EquipmentSlot[] { EquipmentSlot.MAINHAND });
    }

    @Override
    public int getMaxLevel() {
        return 1;
    }

    @Override
    public boolean canEnchant(ItemStack stack) {
        return stack.getItem() instanceof ModularSlashBladeItem;
    }

    @Override
    public boolean isDiscoverable() {
        return false;
    }

    @Override
    public boolean isTradeable() {
        return false;
    }

    @Override
    public boolean isAllowedOnBooks() {
        return false;
    }
}
