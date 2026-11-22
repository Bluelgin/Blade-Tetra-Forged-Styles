package dev.bladetetra.mixin;

import dev.bladetetra.forging.TreatiseForging;
import dev.bladetetra.item.ModularSlashBladeItem;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.ToolAction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import se.mickelus.tetra.blocks.workbench.gui.CraftButtonGui;
import se.mickelus.tetra.module.schematic.UpgradeSchematic;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Mixin(value = CraftButtonGui.class, remap = false)
abstract class ForgingCraftButtonMixin {
    @Shadow private List<Component> tooltip;

    @Inject(method = "update", at = @At("TAIL"))
    private void bladeTetra$previewCore(UpgradeSchematic schematic, Player player, ItemStack target,
            ItemStack preview, ItemStack[] materials, String slot, Map<ToolAction, Integer> tools, CallbackInfo ci) {
        if (schematic == null || !TreatiseForging.isCompositeSchematic(schematic.getKey())
                || !(preview.getItem() instanceof ModularSlashBladeItem blade)
                || !schematic.isMaterialsValid(target, slot, materials)) return;

        List<Component> lines = tooltip == null ? new ArrayList<>() : new ArrayList<>(tooltip);
        int previousSize = lines.size();
        blade.appendForgingCoreTooltip(preview, lines);
        if (lines.size() == previousSize) {
            lines.add(Component.translatable("tooltip.blade_tetra.forging_core.none").withStyle(ChatFormatting.GRAY));
        }
        tooltip = lines;
    }
}
