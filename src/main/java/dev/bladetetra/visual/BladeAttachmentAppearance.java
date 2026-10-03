package dev.bladetetra.visual;

import dev.bladetetra.compat.attachments.SwordAttachmentSchematics;
import dev.bladetetra.item.ModularSlashBladeItem;
import net.minecraft.world.item.ItemStack;
import se.mickelus.tetra.module.ItemModuleMajor;

import java.util.Arrays;
import java.util.List;

/** Transient view of native module/improvement identities; does not store duplicate material NBT. */
public record BladeAttachmentAppearance(String wrap, String socket, List<String> coatings) {
    public static BladeAttachmentAppearance fromStack(ItemStack stack) {
        if (!(stack.getItem() instanceof ModularSlashBladeItem item)) return new BladeAttachmentAppearance("", "", List.of());
        String wrap = "";
        if (item.getModuleFromSlot(stack, ModularSlashBladeItem.TSUKA_SLOT) instanceof ItemModuleMajor tsuka) {
            wrap = Arrays.stream(tsuka.getImprovements(stack)).map(data -> data.key)
                    .filter(key -> key.startsWith("hilt/wrap/")).sorted().findFirst().orElse("");
        }
        String socket = "";
        var pommel = item.getModuleFromSlot(stack, ModularSlashBladeItem.KASHIRA_SLOT);
        if (pommel != null && SwordAttachmentSchematics.SOCKET_MODULE.equals(pommel.getKey())) {
            socket = pommel.getVariantData(stack).key;
        }
        List<String> coatings = List.of();
        if (item.getModuleFromSlot(stack, ModularSlashBladeItem.BLADE_SLOT) instanceof ItemModuleMajor blade) {
            coatings = Arrays.stream(blade.getImprovements(stack)).map(data -> data.key)
                    .filter(SwordAttachmentSchematics::isCoatingKey).sorted().toList();
        }
        return new BladeAttachmentAppearance(wrap, socket, coatings);
    }

    public String wrapMaterial() { return material(wrap); }
    public String socketMaterial() { return material(socket); }
    public String signature() { return wrap + "|" + socket + "|" + String.join(",", coatings); }

    private static String material(String variant) {
        return variant.substring(variant.lastIndexOf('/') + 1);
    }
}
