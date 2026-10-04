package dev.bladetetra.forging;

import dev.bladetetra.combat.LegacyFusionHandler;
import dev.bladetetra.item.ModularSlashBladeItem;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.ToolAction;
import se.mickelus.tetra.items.modular.IModularItem;
import se.mickelus.tetra.module.data.GlyphData;
import se.mickelus.tetra.module.schematic.BaseSchematic;
import se.mickelus.tetra.module.schematic.CraftingContext;
import se.mickelus.tetra.module.schematic.SchematicType;

import java.util.Map;

/** Reversible injected imprint, not a material upgrade or removal of fitting inheritance. */
public final class DeadThoughtRemovalSchematic extends BaseSchematic {
    // Tetra's custom schematic registry adds its own namespace to this path.
    public static final String KEY = "slashblade/remove_dead_thought";
    private static final String TEXT = "blade_tetra.schematic.remove_dead_thought.";

    @Override public String getKey() { return KEY; }
    @Override public String getName() { return Component.translatable(TEXT + "name").getString(); }
    @Override public String[] getSources() { return new String[]{"blade_tetra"}; }
    @Override public String getDescription(ItemStack stack) {
        String description = Component.translatable(TEXT + "description").getString();
        // Be conservative: matching fittings may also be attuned later.
        if (LegacyFusion.installed(stack) == LegacyFusion.NIHILUL_SAYA_CRIMSON_CHERRY_HILT) {
            description += "\n" + Component.translatable(TEXT + "fitting_warning").getString();
        }
        return description;
    }
    @Override public int getNumMaterialSlots() { return 1; }
    @Override public String getSlotName(ItemStack stack, int slot) {
        return Component.translatable(TEXT + "material").getString();
    }
    @Override public ItemStack[] getSlotPlaceholders(ItemStack stack, int slot) {
        return slot == 0 ? new ItemStack[]{new ItemStack(Items.AMETHYST_SHARD)} : new ItemStack[0];
    }
    @Override public int getRequiredQuantity(ItemStack stack, int slot, ItemStack material) {
        return slot == 0 ? 1 : 0;
    }
    @Override public boolean isRelevant(ItemStack stack) {
        return stack != null && stack.getItem() instanceof ModularSlashBladeItem
                && DeadThoughtDivineLegacy.isBound(stack);
    }
    @Override public boolean matchesRequirements(CraftingContext context) {
        return context != null && isRelevant(context.targetStack);
    }
    @Override public boolean isApplicableForSlot(String slot, ItemStack stack) {
        return ModularSlashBladeItem.BLADE_SLOT.equals(slot) && isRelevant(stack);
    }
    @Override public boolean acceptsMaterial(ItemStack stack, String slot, int index, ItemStack material) {
        return index == 0 && isApplicableForSlot(slot, stack)
                && material != null && material.is(Items.AMETHYST_SHARD);
    }
    @Override public boolean isMaterialsValid(ItemStack stack, String slot, ItemStack[] materials) {
        return materials != null && materials.length == 1
                && acceptsMaterial(stack, slot, 0, materials[0]) && materials[0].getCount() >= 1;
    }
    @Override public boolean isIntegrityViolation(Player player, ItemStack stack, ItemStack[] materials, String slot) {
        return false; // No module/integrity change; permit removal from old over-budget blades too.
    }
    @Override public Map<ToolAction, Integer> getRequiredToolLevels(ItemStack stack, ItemStack[] materials) {
        return Map.of();
    }
    @Override public SchematicType getType() { return SchematicType.other; }
    @Override public GlyphData getGlyph() { return new GlyphData(144, 16); }

    @Override public ItemStack applyUpgrade(ItemStack stack, ItemStack[] materials,
            boolean consumeResources, String slot, Player player) {
        ItemStack result = stack.copy();
        if (!isMaterialsValid(stack, slot, materials)) return result;
        // Previews change only the copy and never consume materials or player progress.
        if (!DeadThoughtDivineLegacy.unbind(result)) return result;
        result.getCapability(ItemSlashBlade.BLADESTATE).ifPresent(state -> LegacyFusionHandler.sync(result, state));
        IModularItem.updateIdentifier(result);
        if (consumeResources && (player == null || !player.getAbilities().instabuild)) materials[0].shrink(1);
        return result;
    }
}
