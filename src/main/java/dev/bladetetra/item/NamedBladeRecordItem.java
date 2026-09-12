package dev.bladetetra.item;

import dev.bladetetra.client.SmithingJournalClient;
import dev.bladetetra.forging.LegacyFusionGuide;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
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

    static void prepareBook(ItemStack stack) {
        CompoundTag tag = stack.getOrCreateTag();
        tag.putString("title", "Named Blade Record");
        tag.putString("author", "佚名刀匠");
        tag.putInt("generation", 0);
        tag.putBoolean("resolved", true);
        tag.put("pages", NamedBladeRecordPages.build(LegacyFusionGuide.entries()));
    }
}
