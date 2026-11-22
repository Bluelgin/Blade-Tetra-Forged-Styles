package dev.bladetetra.forging;

import dev.bladetetra.config.GameplayConfig;
import dev.bladetetra.item.ModularSlashBladeItem;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import se.mickelus.tetra.data.DataManager;
import se.mickelus.tetra.effect.ItemEffect;
import se.mickelus.tetra.items.modular.IModularItem;
import se.mickelus.tetra.module.data.EffectData;
import se.mickelus.tetra.module.data.MaterialData;
import se.mickelus.tetra.module.schematic.OutcomeDefinition;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static dev.bladetetra.forging.ForgingImprovements.HIRA;
import static dev.bladetetra.forging.ForgingImprovements.KOBUSE;
import static dev.bladetetra.forging.ForgingImprovements.NORMALIZED_CONSTRUCTION;
import static dev.bladetetra.forging.ForgingImprovements.SANMAI;
import static dev.bladetetra.forging.ForgingImprovements.SHIHOZUME;
import static dev.bladetetra.forging.ForgingImprovements.USUBA;
import static dev.bladetetra.forging.ForgingImprovements.has;

public final class TreatiseForging {
    private static final String CORE_MATERIAL = "blade_tetra_forging_core";
    private static final String CORE_FEATURE = "blade_tetra:forging_core/";
    private static final List<String> DEFAULT_EFFECTS = List.of("stun", "bleeding", "armorPenetration", "stabilizing");

    private TreatiseForging() {}

    public static double damageFraction(ItemStack stack) {
        if (stack.getTag() == null) return 0;
        String slot = ModularSlashBladeItem.BLADE_SLOT;
        double edge = has(stack, slot, USUBA) ? .10 : has(stack, slot, HIRA) ? .06 : 0;
        double construction = has(stack, slot, SANMAI) ? .12 : has(stack, slot, SHIHOZUME) ? .08 : 0;
        double multiplier = GameplayConfig.SPEC.isLoaded() ? GameplayConfig.FORGING_DAMAGE_MULTIPLIER.get() : 1;
        return (edge + construction) * multiplier;
    }

    public static void applyOutcome(ItemStack stack, String slot, OutcomeDefinition outcome) {
        if (!(stack.getItem() instanceof ModularSlashBladeItem)) return;

        if (outcome.improvements != null && outcome.improvements.keySet().stream().anyMatch(key ->
                key.startsWith("blade_tetra/edge/") || key.startsWith("blade_tetra/construction/")
                        || key.startsWith("blade_tetra/assembly/"))) {
            // The direct Holosphere preview applies an outcome without assembling it.
            IModularItem.updateIdentifier(stack);
        }
        if (!ModularSlashBladeItem.BLADE_SLOT.equals(slot)) return;

        if (outcome.moduleKey != null || outcome.improvements != null
                && outcome.improvements.containsKey(NORMALIZED_CONSTRUCTION)) clearCore(stack);

        if (outcome.improvements == null || outcome.material == null
                || ForgingImprovements.CONSTRUCTIONS.stream().noneMatch(outcome.improvements::containsKey)) return;

        // Tetra preserves the material predicate when it expands a metal outcome with its own cost.
        // This identifies the chosen outcome even after consumption and in the Holosphere.
        var predicate = outcome.material.getPredicate();
        var id = DataManager.instance.materialData.getData().entrySet().stream()
                .filter(entry -> entry.getValue().material != null
                        && entry.getValue().material.getPredicate() == predicate)
                .map(Map.Entry::getKey)
                .sorted()
                .findFirst();
        clearCore(stack);
        id.ifPresent(key -> stack.getOrCreateTag().putString(CORE_MATERIAL, key.toString()));
    }

    public static boolean isCompositeSchematic(String key) {
        String path = key.startsWith("tetra:") ? key.substring(6) : key;
        return switch (path) {
            case "slashblade/forging/kobuse", "slashblade/forging/sanmai", "slashblade/forging/shihozume" -> true;
            default -> false;
        };
    }

    public static void clearCore(ItemStack stack) {
        if (stack.getTag() != null) stack.getTag().remove(CORE_MATERIAL);
    }

    public static EffectData mergeEffects(ItemStack stack, EffectData base) {
        CoreEffect core = coreEffect(stack);
        if (core == null || !core.strongerThan(base)) return base;

        EffectData result = new EffectData();
        result.levelMap.putAll(base.levelMap);
        result.efficiencyMap.putAll(base.efficiencyMap);
        result.levelMap.put(core.effect(), core.level());
        result.efficiencyMap.put(core.effect(), core.efficiency());
        return result;
    }

    public static float bleedingChance(ItemStack stack, EffectData base, float nativeChance) {
        CoreEffect core = coreEffect(stack);
        if (core == null || core.effect() != ItemEffect.bleeding || !core.strongerThan(base)) return nativeChance;
        return nativeChance * core.fixedChanceMultiplier();
    }

    public static void appendCoreTooltip(ItemStack stack, EffectData base, List<Component> tooltip) {
        CoreEffect core = coreEffect(stack);
        if (core == null) return;
        Component name = Component.translatableWithFallback("tetra.stats." + core.effect().getKey(),
                core.effect().getKey());
        if (!core.strongerThan(base)) {
            tooltip.add(Component.translatable("tooltip.blade_tetra.forging_core.overlap", name)
                    .withStyle(ChatFormatting.GRAY));
            return;
        }
        MutableComponent text = core.effect() == ItemEffect.bleeding
                ? Component.translatable("tooltip.blade_tetra.forging_core.chance", name,
                        format(30 * core.fixedChanceMultiplier()))
                : core.effect() == ItemEffect.stun
                        ? Component.translatable("tooltip.blade_tetra.forging_core.chance", name, format(core.level()))
                        : core.scale() == Scale.EFFICIENCY
                                ? Component.translatable("tooltip.blade_tetra.forging_core.efficiency",
                                        name, format(core.efficiency()))
                                : Component.translatable("tooltip.blade_tetra.forging_core", name, format(core.level()));
        tooltip.add(text.withStyle(ChatFormatting.GRAY));
    }

    private static String format(float value) {
        return BigDecimal.valueOf(Math.round(value * 100) / 100d).stripTrailingZeros().toPlainString();
    }

    @Nullable
    private static CoreEffect coreEffect(ItemStack stack) {
        var tag = stack.getTag();
        if (tag == null || !tag.contains(CORE_MATERIAL)) return null;
        ResourceLocation id = ResourceLocation.tryParse(tag.getString(CORE_MATERIAL));
        if (id == null) return null;
        MaterialData material = DataManager.instance.materialData.getData().get(id);
        if (material == null || material.effects == null) return null;

        String slot = ModularSlashBladeItem.BLADE_SLOT;
        float strength = has(stack, slot, KOBUSE) ? .50f
                : has(stack, slot, SANMAI) ? .75f : has(stack, slot, SHIHOZUME) ? 1 : 0;
        if (strength == 0) return null;

        if (material.features != null) {
            for (String feature : material.features) {
                if (!feature.startsWith(CORE_FEATURE)) continue;
                String rule = feature.substring(CORE_FEATURE.length());
                if (rule.startsWith("level/")) return resolve(material, rule.substring(6), Scale.LEVEL, strength);
                if (rule.startsWith("efficiency/")) return resolve(material, rule.substring(11), Scale.EFFICIENCY, strength);
                // A material can opt out explicitly with the "none" feature.
                return null;
            }
        }
        for (String key : DEFAULT_EFFECTS) {
            CoreEffect resolved = resolve(material, key, key.equals("bleeding") ? Scale.FIXED_CHANCE : Scale.LEVEL,
                    strength);
            if (resolved != null) return resolved;
        }
        return null;
    }

    @Nullable
    private static CoreEffect resolve(MaterialData material, String key, Scale scale, float strength) {
        ItemEffect effect = ItemEffect.get(key);
        int level = material.effects.getLevel(effect);
        float efficiency = material.effects.getEfficiency(effect);
        if (level <= 0 && efficiency <= 0) return null;

        // Bleeding's level is its damage, and its native proc chance is a constant.
        if (effect == ItemEffect.bleeding) scale = Scale.FIXED_CHANCE;
        return new CoreEffect(effect, scale == Scale.LEVEL ? Math.round(level * strength) : level,
                scale == Scale.EFFICIENCY ? efficiency * strength : efficiency,
                scale == Scale.FIXED_CHANCE ? strength : 1, scale);
    }

    private enum Scale { LEVEL, EFFICIENCY, FIXED_CHANCE }

    private record CoreEffect(ItemEffect effect, float level, float efficiency, float fixedChanceMultiplier,
            Scale scale) {
        boolean strongerThan(EffectData base) {
            int currentLevel = base.getLevel(effect);
            float currentEfficiency = base.getEfficiency(effect);
            if (scale == Scale.FIXED_CHANCE && currentLevel > 0 && fixedChanceMultiplier < 1) return false;
            if (scale == Scale.EFFICIENCY) {
                return efficiency > currentEfficiency || efficiency == currentEfficiency && level > currentLevel;
            }
            return level > currentLevel || level == currentLevel && efficiency > currentEfficiency;
        }
    }
}
