package dev.bladetetra.export;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import mods.flammpfeil.slashblade.client.renderer.CarryType;
import mods.flammpfeil.slashblade.item.SwordType;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import mods.flammpfeil.slashblade.registry.SlashArtsRegistry;
import mods.flammpfeil.slashblade.registry.SpecialEffectsRegistry;
import mods.flammpfeil.slashblade.registry.slashblade.EnchantmentDefinition;
import mods.flammpfeil.slashblade.registry.slashblade.PropertiesDefinition;
import mods.flammpfeil.slashblade.registry.slashblade.RenderDefinition;
import mods.flammpfeil.slashblade.registry.slashblade.SlashBladeDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Encodes the portion represented directly by Resharped's named-blade registry. */
record NativeBladeExport(float attack, int durability, ResourceLocation combo, ResourceLocation slashArt,
                         List<ResourceLocation> effects, List<BladeExportSnapshot.EnchantmentValue> enchantments,
                         ResourceLocation model, ResourceLocation texture, int color,
                         boolean colorInverse, CarryType carryType,
                         boolean bewitched, boolean sealed, boolean unbreakable) {
    NativeBladeExport {
        if (!Float.isFinite(attack) || durability < 1) {
            throw new IllegalArgumentException("Invalid blade attack or durability");
        }
        effects = List.copyOf(effects);
        enchantments = List.copyOf(enchantments);
    }

    JsonObject encode(ResourceLocation blade, Set<String> dependencies, Set<ResourceLocation> bakedAssets, ExportBattleProfile battle) {
        validateAppearance(blade, dependencies, bakedAssets, model);
        validateAppearance(blade, dependencies, bakedAssets, texture);
        boolean portableCombo = battle != null && battle.supports(combo);
        if (!portableCombo) requireProvider(combo, dependencies);
        requireProvider(slashArt, dependencies);
        if ((!portableCombo && !ComboStateRegistry.REGISTRY.get().containsKey(combo))
                || !SlashArtsRegistry.REGISTRY.get().containsKey(slashArt)) {
            throw new IllegalArgumentException("Unregistered combo or slash art: " + combo + ", " + slashArt);
        }
        for (ResourceLocation effect : effects) {
            requireProvider(effect, dependencies);
            if (!SpecialEffectsRegistry.REGISTRY.get().containsKey(effect)) {
                throw new IllegalArgumentException("Unregistered special effect: " + effect);
            }
        }
        for (var enchantment : enchantments) {
            requireProvider(enchantment.id(), dependencies);
            if (!ForgeRegistries.ENCHANTMENTS.containsKey(enchantment.id()) || enchantment.level() < 1) {
                throw new IllegalArgumentException("Unavailable enchantment: " + enchantment.id());
            }
        }

        var types = new ArrayList<SwordType>(2);
        if (bewitched) types.add(SwordType.BEWITCHED);
        if (sealed) types.add(SwordType.SEALED);
        var properties = PropertiesDefinition.Builder.newInstance().baseAttackModifier(attack)
                .maxDamage(durability).rootComboState(combo).slashArtsType(slashArt)
                .defaultSwordType(types).setUnbreakable(unbreakable);
        effects.forEach(properties::addSpecialEffect);
        var render = RenderDefinition.Builder.newInstance().modelName(model).textureName(texture)
                .effectColor(color).effectColorInverse(colorInverse).standbyRenderType(carryType).build();
        var definition = new SlashBladeDefinition(blade, render, properties.build(),
                enchantments.stream().map(entry -> new EnchantmentDefinition(entry.id(), entry.level())).toList());
        return SlashBladeDefinition.CODEC.encodeStart(JsonOps.INSTANCE, definition)
                .getOrThrow(false, error -> { throw new IllegalArgumentException(error); }).getAsJsonObject();
    }

    private static void validateAppearance(ResourceLocation blade, Set<String> dependencies,
            Set<ResourceLocation> bakedAssets, ResourceLocation asset) {
        if (asset.getNamespace().equals(blade.getNamespace())) {
            if (!bakedAssets.contains(asset)) {
                throw new IllegalArgumentException("Missing generated appearance resource: " + asset);
            }
        } else {
            requireProvider(asset, dependencies);
        }
    }

    private static void requireProvider(ResourceLocation id, Set<String> dependencies) {
        if (id == null || !dependencies.contains(id.getNamespace())) {
            throw new IllegalArgumentException("No portable provider for: " + id);
        }
    }
}
