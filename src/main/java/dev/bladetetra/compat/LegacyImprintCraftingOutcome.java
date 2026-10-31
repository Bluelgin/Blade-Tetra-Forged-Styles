package dev.bladetetra.compat;

import dev.bladetetra.forging.LegacyImprintKind;
import dev.bladetetra.forging.NamedLegacyCatalog;
import dev.bladetetra.forging.NamedLegacyImprintStorage;
import dev.bladetetra.item.NamedBladeIngotItem;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.ToolAction;
import se.mickelus.tetra.craftingeffect.outcome.CraftingEffectOutcome;
import se.mickelus.tetra.module.schematic.UpgradeSchematic;

import java.util.Map;

/** Writes/removes Blade Tetra's soft source id after Tetra modifies a fitting slot. */
public final class LegacyImprintCraftingOutcome implements CraftingEffectOutcome {
    /**
     * Tetra 6.10 and older crafting-effect ABI.
     *
     * <p>Keep this exact descriptor for older Tetra versions. Neither overload
     * uses {@code @Override}: that keeps this source compilable against both the
     * old interface and the 6.12+ interface, where only one descriptor exists at
     * compile time.</p>
     */
    public boolean apply(ResourceLocation[] unlockedEffects, ItemStack upgradedStack, String slot,
            boolean isReplacing, Player player, ItemStack[] preMaterials,
            Map<ToolAction, Integer> tools, Level world, UpgradeSchematic schematic,
            BlockPos pos, BlockState blockState, boolean consumeResources,
            ItemStack[] postMaterials) {
        return applyImprint(upgradedStack, slot, schematic, preMaterials);
    }

    /**
     * Tetra 6.12+ crafting-effect ABI.
     *
     * <p>6.12 appended a severity argument to CraftingEffectOutcome#apply.
     * Keeping both public descriptors lets the same implementation work when
     * compiled or loaded against either side of that API boundary. Severity is
     * irrelevant to the legacy-imprint bookkeeping, so both entry points share
     * the same implementation.</p>
     */
    public boolean apply(ResourceLocation[] unlockedEffects, ItemStack upgradedStack, String slot,
            boolean isReplacing, Player player, ItemStack[] preMaterials,
            Map<ToolAction, Integer> tools, Level world, UpgradeSchematic schematic,
            BlockPos pos, BlockState blockState, boolean consumeResources,
            ItemStack[] postMaterials, float severity) {
        return applyImprint(upgradedStack, slot, schematic, preMaterials);
    }

    private static boolean applyImprint(
            ItemStack upgradedStack,
            String slot,
            UpgradeSchematic schematic,
            ItemStack[] preMaterials) {
        String key = schematic == null ? null : schematic.getKey();
        boolean changed = NamedLegacyImprintStorage.applyCraftResult(upgradedStack, slot, key);
        String dynamicPart = NamedLegacyImprintStorage.partForSlot(slot);
        if (dynamicPart != null && key != null
                && (key.equals("slashblade/legacy_ingot_" + dynamicPart)
                    || key.equals("tetra:slashblade/legacy_ingot_" + dynamicPart))) {
            // Only the consumed source ingot may determine the installed identity.
            // No source is ever inferred from a tooltip, translated name or client geometry.
            String ingotSource = null;
            if (preMaterials != null) for (ItemStack ingredient : preMaterials) {
                if (ingotSource == null) ingotSource = NamedBladeIngotItem.sourceId(ingredient);
            }
            if (ingotSource != null) {
                // Missing addon sources remain soft references, like historical imprints.
                changed |= NamedLegacyImprintStorage.putSource(
                        upgradedStack.getOrCreateTag(), dynamicPart, ingotSource);
                try {
                    LegacyImprintKind kind = NamedLegacyCatalog.get(ingotSource);
                    changed |= NamedLegacyImprintStorage.putSnapshot(upgradedStack, dynamicPart, kind);
                } catch (RuntimeException | LinkageError ignored) { /* fallback retains soft ref */ }
            }
            return changed;
        }
        String part = NamedLegacyImprintStorage.partForSlot(slot);
        String source = NamedLegacyImprintStorage.sourceFromSchematic(key, part);
        if (part == null || source == null
                || !source.equals(NamedLegacyImprintStorage.sourceId(upgradedStack, part))) {
            return changed;
        }

        // Freeze the pure named-blade semantics at the moment the imitation is
        // created. Runtime ItemStack queries can then avoid third-party discovery.
        // Snapshotting is optional hardening: a broken addon catalog must never
        // turn an otherwise successful Tetra craft into a failure.
        try {
            LegacyImprintKind kind = NamedLegacyCatalog.get(source);
            return kind != null
                    ? NamedLegacyImprintStorage.putSnapshot(upgradedStack, part, kind) || changed
                    : changed;
        } catch (RuntimeException | LinkageError failure) {
            return changed;
        }
    }
}
