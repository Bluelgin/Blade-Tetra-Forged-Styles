package dev.bladetetra.client;

import dev.bladetetra.BladeTetra;
import dev.bladetetra.item.AwakenedSoulFragmentItem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.List;

/** Lore on hover; Shift translates the entire soul into a practical guide. */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID, value = Dist.CLIENT)
public final class AwakenedSoulGuideTooltip {
    static final String PREFIX = "tooltip.blade_tetra.soul_fragment.";
    static boolean expanded() { return Screen.hasShiftDown() || SoulGuideReviewClient.expanded(); }

    @SubscribeEvent public static void tooltip(ItemTooltipEvent event) {
        if (!(event.getItemStack().getItem() instanceof AwakenedSoulFragmentItem item)) return;
        var lines = event.getToolTip();
        if (!expanded()) {
            entries(lines, item.soul(), "lore");
            lines.add(Component.empty());
            lines.add(text("read").withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        var o = Minecraft.getInstance().options;
        heading(lines, "heading.controls");
        lines.add(text("controls.upper", key(o.keyShift), key(o.keyDown), key(o.keyUse)));
        lines.add(text("controls.cleave", key(o.keyShift), key(o.keyDown), key(o.keyUse)));
        lines.add(text("controls.sa", key(o.keyUse)));
        if (!item.soul().equals("senbonzakura"))
            lines.add(text("controls.orbit", nativeKey("key.slashblade.summon_blade")));
        if (item.soul().equals("raikiri"))
            lines.add(text("controls.dash", key(o.keyShift), key(o.keyUp), key(o.keyUse)));
        if (item.soul().equals("kyouka")) lines.add(text("controls.move", nativeKey("key.slashblade.special_move")));
        heading(lines, "heading.skills");entries(lines, item.soul(), "skills");
        lines.add(text(item.soul() + ".coda"));
        heading(lines, "heading.details");entries(lines, item.soul(), "details");
        heading(lines, "heading.link");
        lines.add(text("controls.fusion", key(SoulSwitchInput.SWITCH)));
        if (item.soul().equals("kyouka") || item.soul().equals("senbonzakura")) lines.add(text("controls.custom"));
        lines.add(text(item.soul() + ".style"));lines.add(text(item.soul() + ".summary.3"));
        lines.add(text("defaults").withStyle(ChatFormatting.DARK_GRAY));
    }
    private static void heading(List<Component> lines, String key) {
        lines.add(Component.empty());lines.add(text(key).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
    }
    private static void entries(List<Component> lines, String soul, String section) {
        for (int i = 1; i <= 32 && I18n.exists(PREFIX + soul + "." + section + "." + i); i++) {
            var value = text(soul + "." + section + "." + i);
            if (section.equals("lore")) value.withStyle(ChatFormatting.ITALIC);
            lines.add(value);
        }
    }
    private static MutableComponent text(String key, Object... args) {
        return SoulGuideText.decorate(key, Component.translatable(PREFIX + key, args));
    }
    private static Component key(KeyMapping mapping) { return mapping.getTranslatedKeyMessage(); }
    private static Component nativeKey(String name) {
        for (var mapping : Minecraft.getInstance().options.keyMappings)
            if (mapping.getName().equals(name)) return key(mapping);
        return Component.translatable(name);
    }
    private AwakenedSoulGuideTooltip() {}
}
