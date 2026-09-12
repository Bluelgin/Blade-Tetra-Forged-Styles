package dev.bladetetra.item;

import dev.bladetetra.forging.LegacyFusionGuide;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.List;

/** Builds the written-book payload without depending on the item registry. */
final class NamedBladeRecordPages {
    private static final int FUSIONS_PER_PAGE = 2;

    static ListTag build(List<LegacyFusionGuide.Entry> fusions) {
        ListTag pages = new ListTag();
        pages.add(page(Component.translatable("lore.blade_tetra.named_blade_record.cover")));
        pages.add(page(Component.translatable("lore.blade_tetra.named_blade_record.imprinting")));
        pages.add(page(Component.translatable("lore.blade_tetra.named_blade_record.orthodox")));
        pages.add(page(Component.translatable("lore.blade_tetra.named_blade_record.fusion_intro")));

        for (int start = 0; start < fusions.size(); start += FUSIONS_PER_PAGE) {
            int end = Math.min(start + FUSIONS_PER_PAGE, fusions.size());
            pages.add(page(fusionPage(fusions.subList(start, end))));
        }
        return pages;
    }

    private static Component fusionPage(List<LegacyFusionGuide.Entry> entries) {
        MutableComponent page = Component.translatable(
                        "lore.blade_tetra.named_blade_record.fusion_page")
                .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD);
        for (LegacyFusionGuide.Entry entry : entries) {
            page.append(Component.literal("\n\n"));
            page.append(Component.translatable(
                    "lore.blade_tetra.named_blade_record.fusion_entry",
                    entry.sayaName(), entry.hiltName(), entry.abilityName()));
        }
        return page;
    }

    private static StringTag page(Component component) {
        return StringTag.valueOf(Component.Serializer.toJson(component));
    }

    private NamedBladeRecordPages() {
    }
}
