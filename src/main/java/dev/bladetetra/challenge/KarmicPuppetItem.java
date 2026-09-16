package dev.bladetetra.challenge;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Fireproof ritual token that freezes the owner's SlashBlade kill total. */
public final class KarmicPuppetItem extends Item {
    static final String TAG_KILLS = "blade_tetra_divine_kills";
    static final String TAG_TIER = "blade_tetra_divine_tier";
    static final String TAG_SESSION = "blade_tetra_divine_session";
    static final String TAG_SNAPSHOT = "blade_tetra_divine_snapshot";

    public KarmicPuppetItem() {
        super(new Properties().stacksTo(1).fireResistant());
    }

    static ItemStack snapshot(long kills, DivineDomainTier tier, long sessionId) {
        ItemStack stack = new ItemStack(dev.bladetetra.registry.ModItems.KARMIC_PUPPET.get());
        var tag = stack.getOrCreateTag();
        tag.putLong(TAG_KILLS, Math.max(0L, kills));
        tag.putString(TAG_TIER, tier.name());
        tag.putLong(TAG_SESSION, sessionId);
        tag.putBoolean(TAG_SNAPSHOT, true);
        return stack;
    }

    static boolean hasSnapshot(ItemStack stack) {
        return stack.is(dev.bladetetra.registry.ModItems.KARMIC_PUPPET.get())
                && stack.getOrCreateTag().getBoolean(TAG_SNAPSHOT);
    }

    @Override
    public Component getName(ItemStack stack) {
        if (hasSnapshot(stack)) {
            DivineDomainTier tier;
            try {
                tier = DivineDomainTier.valueOf(stack.getOrCreateTag().getString(TAG_TIER));
            } catch (IllegalArgumentException ignored) {
                tier = DivineDomainTier.ECHO;
            }
            return Component.literal("木偶 · " + tier.displayName());
        }
        return Component.literal("无铭木偶");
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
            List<Component> tooltip, TooltipFlag flag) {
        if (hasSnapshot(stack)) {
            tooltip.add(Component.literal("刀下亡魂：" + stack.getOrCreateTag().getLong(TAG_KILLS))
                    .withStyle(ChatFormatting.DARK_RED));
            tooltip.add(Component.literal("你的刀记得它斩过的每一个人。")
                    .withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.literal("尚未承载杀业。")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
