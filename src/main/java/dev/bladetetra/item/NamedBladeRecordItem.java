package dev.bladetetra.item;

import dev.bladetetra.client.SmithingJournalClient;
import dev.bladetetra.forging.LegacyFusionGuide;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.WrittenBookItem;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Compact handbook for named-blade imprinting, orthodox inheritance and authored
 * cross-blade legacy fusions. Fusion recipes are read from the same built-in
 * catalog that drives gameplay so the handbook cannot silently drift from code.
 */
public final class NamedBladeRecordItem extends WrittenBookItem {
    private static final int FUSIONS_PER_PAGE = 2;

    public NamedBladeRecordItem() {
        super(new Properties().stacksTo(1));
    }

    @Override
    public ItemStack getDefaultInstance() {
        ItemStack stack = super.getDefaultInstance();
        prepareBook(stack);
        return stack;
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable("item.blade_tetra.named_blade_record");
    }

    @Override
    public InteractionResultHolder<ItemStack> use(
            Level level,
            Player player,
            InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        prepareBook(stack);
        if (level.isClientSide) {
            DistExecutor.unsafeRunWhenOn(
                    Dist.CLIENT,
                    () -> () -> SmithingJournalClient.open(stack));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            @Nullable Level level,
            List<Component> tooltip,
            TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.blade_tetra.named_blade_record")
                .withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("tooltip.blade_tetra.named_blade_record.use")
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    private static void prepareBook(ItemStack stack) {
        CompoundTag tag = stack.getOrCreateTag();
        ListTag pages = new ListTag();
        pages.add(page(Component.translatable("lore.blade_tetra.named_blade_record.cover")));
        pages.add(page(Component.translatable("lore.blade_tetra.named_blade_record.imprinting")));
        pages.add(page(Component.translatable("lore.blade_tetra.named_blade_record.orthodox")));
        pages.add(page(Component.translatable("lore.blade_tetra.named_blade_record.fusion_intro")));

        List<LegacyFusionGuide.Entry> fusions = LegacyFusionGuide.entries();
        for (int start = 0; start < fusions.size(); start += FUSIONS_PER_PAGE) {
            int end = Math.min(start + FUSIONS_PER_PAGE, fusions.size());
            pages.add(page(fusionPage(fusions.subList(start, end))));
        }

        tag.putString("title", "Named Blade Record");
        tag.putString("author", "佚名刀匠");
        tag.putInt("generation", 0);
        tag.putBoolean("resolved", true);
        tag.put("pages", pages);
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
}
