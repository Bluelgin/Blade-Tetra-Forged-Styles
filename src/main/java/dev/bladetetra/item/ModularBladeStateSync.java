package dev.bladetetra.item;

import dev.bladetetra.combat.ComponentEffectResolver;
import dev.bladetetra.combat.LegacyFusionHandler;
import dev.bladetetra.combat.ModComboStates;
import dev.bladetetra.combat.StyleResolver;
import dev.bladetetra.registry.ModEnchantments;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

import java.util.Map;
import java.util.Objects;

/** Synchronizes Tetra-derived construction into SlashBlade's runtime blade state. */
final class ModularBladeStateSync {
    private static final String SOUL_CONTRACT_PREVIOUS_DEFAULT_KEY =
            "blade_tetra_soul_contract_previous_default";
    private static final String TRANSLATION_KEY = "item.blade_tetra.modular_slashblade";
    private static final ResourceLocation FALLBACK_MODULAR_MODEL =
            Objects.requireNonNull(ResourceLocation.tryParse(
                    "blade_tetra:model/modular/wood.obj"));
    private static final ResourceLocation MODULAR_TEXTURE =
            Objects.requireNonNull(ResourceLocation.tryParse(
                    "blade_tetra:model/modular/standard.png"));

    static void sync(ItemStack stack, float moduleDamage, int moduleMaxDamage) {
        boolean soulInscription = hasBewitchingSoulInscription(stack);
        stack.getCapability(ItemSlashBlade.BLADESTATE).ifPresent(state -> {
            state.setNonEmpty();
            syncSoulContract(stack, state, soulInscription);

            ResourceLocation styleRoot = ModComboStates.getRoot(StyleResolver.resolve(stack));
            if (!styleRoot.equals(state.getComboRoot())) {
                state.setComboRoot(styleRoot);
                state.setComboSeq(ComboStateRegistry.NONE.getId());
            }

            if (state.getMaxDamage() != moduleMaxDamage) {
                int previousMaxDamage = state.getMaxDamage();
                int previousDamage = state.getDamage();
                float damageRatio = previousMaxDamage > 1
                        ? previousDamage / (float) (previousMaxDamage - 1)
                        : 0.0F;
                state.setMaxDamage(moduleMaxDamage);
                state.setDamage(Mth.clamp(
                        Math.round(damageRatio * (moduleMaxDamage - 1)),
                        0,
                        moduleMaxDamage - 1));
            }
            if (Float.compare(state.getBaseAttackModifier(), moduleDamage) != 0) {
                state.setBaseAttackModifier(moduleDamage);
            }
            if (!TRANSLATION_KEY.equals(state.getTranslationKey())) {
                state.setTranslationKey(TRANSLATION_KEY);
            }

            ResourceLocation model = resolveModularModel(stack);
            if (state.getModel().filter(model::equals).isEmpty()) {
                state.setModel(model);
            }
            if (state.getTexture().filter(MODULAR_TEXTURE::equals).isEmpty()) {
                state.setTexture(MODULAR_TEXTURE);
            }
            LegacyFusionHandler.sync(stack, state);
        });
    }

    private static boolean hasBewitchingSoulInscription(ItemStack stack) {
        return ComponentEffectResolver.hasModule(
                        stack, ModularSlashBladeItem.INSCRIPTION_SLOT,
                        ModularSlashBladeItem.SOUL_INSCRIPTION_MODULE)
                || ComponentEffectResolver.hasModule(
                        stack, ModularSlashBladeItem.INSCRIPTION_SLOT,
                        ModularSlashBladeItem.AWAKENED_SOUL_INSCRIPTION_MODULE);
    }

    private static void syncSoulContract(
            ItemStack stack,
            mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState state,
            boolean awakened) {
        if (!ModEnchantments.SOUL_CONTRACT.isPresent()) {
            return;
        }

        Enchantment soulContract = ModEnchantments.SOUL_CONTRACT.get();
        Map<Enchantment, Integer> enchantments = EnchantmentHelper.getEnchantments(stack);
        boolean hasSoulContract = enchantments.containsKey(soulContract);

        if (awakened) {
            if (!hasSoulContract) {
                if (state.isDefaultBewitched()) {
                    stack.getOrCreateTag().putBoolean(
                            SOUL_CONTRACT_PREVIOUS_DEFAULT_KEY, true);
                } else {
                    stack.getOrCreateTag().remove(SOUL_CONTRACT_PREVIOUS_DEFAULT_KEY);
                }
                enchantments.put(soulContract, 1);
                EnchantmentHelper.setEnchantments(enchantments, stack);
            }
            if (!state.isDefaultBewitched()) {
                state.setDefaultBewitched(true);
            }
            return;
        }

        if (hasSoulContract) {
            enchantments.remove(soulContract);
            EnchantmentHelper.setEnchantments(enchantments, stack);
            CompoundTag tag = stack.getOrCreateTag();
            state.setDefaultBewitched(tag.getBoolean(SOUL_CONTRACT_PREVIOUS_DEFAULT_KEY));
            tag.remove(SOUL_CONTRACT_PREVIOUS_DEFAULT_KEY);
        }
    }

    private static ResourceLocation resolveModularModel(ItemStack stack) {
        String blade = switch (StyleResolver.resolve(stack)) {
            case RENGEKI -> "wakizashi";
            case DANGAKU -> "nodachi";
            case IAIDO -> "katana";
            case STANDARD -> "orthodox";
        };
        String saya = ComponentEffectResolver.hasModule(
                stack, ModularSlashBladeItem.SAYA_SLOT,
                ModularSlashBladeItem.QUICKDRAW_SAYA_MODULE) ? "quickdraw"
                : ComponentEffectResolver.hasModule(
                        stack, ModularSlashBladeItem.SAYA_SLOT,
                        ModularSlashBladeItem.SPIRIT_SAYA_MODULE) ? "spirit" : "basic";
        String tsuba = ComponentEffectResolver.hasModule(
                stack, ModularSlashBladeItem.TSUBA_SLOT,
                ModularSlashBladeItem.LIGHT_TSUBA_MODULE) ? "light"
                : ComponentEffectResolver.hasModule(
                        stack, ModularSlashBladeItem.TSUBA_SLOT,
                        ModularSlashBladeItem.GUARD_TSUBA_MODULE) ? "guard" : "simple";
        String tsuka = ComponentEffectResolver.hasModule(
                stack, ModularSlashBladeItem.TSUKA_SLOT,
                ModularSlashBladeItem.SWIFT_TSUKA_MODULE) ? "swift"
                : ComponentEffectResolver.hasModule(
                        stack, ModularSlashBladeItem.TSUKA_SLOT,
                        ModularSlashBladeItem.STABLE_TSUKA_MODULE) ? "stable" : "wrapped";

        ResourceLocation model = ResourceLocation.tryParse(
                "blade_tetra:model/modular/alpha9/"
                        + blade + "_" + saya + "_" + tsuba + "_" + tsuka + ".obj");
        return model != null ? model : FALLBACK_MODULAR_MODEL;
    }

    private ModularBladeStateSync() {
    }
}
