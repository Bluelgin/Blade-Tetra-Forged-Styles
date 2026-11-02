package dev.bladetetra.forging;

import dev.bladetetra.combat.LegacyFusionHandler;
import dev.bladetetra.combat.ProgrammaticFusionAttunement;
import dev.bladetetra.combat.ProgrammaticFusionPlan;
import dev.bladetetra.item.ModularSlashBladeItem;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.ToolAction;
import se.mickelus.tetra.items.modular.IModularItem;
import se.mickelus.tetra.module.data.GlyphData;
import se.mickelus.tetra.module.schematic.BaseSchematic;
import se.mickelus.tetra.module.schematic.CraftingContext;
import se.mickelus.tetra.module.schematic.SchematicType;
import java.util.Map;

/** Native zero-material workbench operation; the installed fittings pay the cost. */
public final class ProgrammaticFusionSchematic extends BaseSchematic {
    public static final String KEY = "slashblade/fuse_slash_art";
    private static final String TEXT = "blade_tetra.schematic.fuse_slash_art.";
    @Override public String getKey() { return KEY; }
    @Override public String getName() { return Component.translatable(TEXT + "name").getString(); }
    @Override public String[] getSources() { return new String[]{"blade_tetra"}; }
    @Override public String getDescription(ItemStack stack) {
        var parts = NamedLegacyParts.fromStack(stack);
        if (parts.saya() == null || parts.tsuba() == null) return "";
        return Component.translatable(TEXT + "description",
                Component.translatable(parts.saya().translationKey()),
                Component.translatable(parts.tsuba().translationKey())).getString();
    }
    @Override public int getNumMaterialSlots() { return 0; }
    @Override public int getRequiredQuantity(ItemStack stack, int index, ItemStack material) { return 0; }
    @Override public boolean isRelevant(ItemStack stack) {
        return stack != null && stack.getItem() instanceof ModularSlashBladeItem
                && LegacyFusion.active(stack) == null
                && ProgrammaticFusionPlan.candidate(stack) != null
                && ProgrammaticFusionPlan.from(stack) == null;
    }
    @Override public boolean matchesRequirements(CraftingContext context) {
        return context != null && isRelevant(context.targetStack);
    }
    @Override public boolean isApplicableForSlot(String slot, ItemStack stack) {
        return ModularSlashBladeItem.BLADE_SLOT.equals(slot) && isRelevant(stack);
    }
    @Override public boolean acceptsMaterial(ItemStack stack, String slot, int index, ItemStack material) { return false; }
    @Override public boolean isMaterialsValid(ItemStack stack, String slot, ItemStack[] materials) {
        return isApplicableForSlot(slot, stack);
    }
    @Override public boolean isIntegrityViolation(Player player, ItemStack stack, ItemStack[] materials, String slot) { return false; }
    @Override public Map<ToolAction, Integer> getRequiredToolLevels(ItemStack stack, ItemStack[] materials) { return Map.of(); }
    @Override public SchematicType getType() { return SchematicType.other; }
    @Override public GlyphData getGlyph() { return new GlyphData(144, 16); }
    @Override public ItemStack applyUpgrade(ItemStack stack, ItemStack[] materials,
            boolean consumeResources, String slot, Player player) {
        ItemStack result = stack.copy();
        if (!isMaterialsValid(stack, slot, materials) || !ProgrammaticFusionAttunement.confirm(result)) return result;
        result.getCapability(ItemSlashBlade.BLADESTATE).ifPresent(state -> LegacyFusionHandler.sync(result, state));
        IModularItem.updateIdentifier(result);
        return result;
    }
}
