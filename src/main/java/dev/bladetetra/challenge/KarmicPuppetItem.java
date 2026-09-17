package dev.bladetetra.challenge;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Fireproof ritual mirror that freezes the owner's total SlashBlade kill count.
 *
 * <p>The registry id is intentionally kept for save compatibility, but the
 * player-facing identity is now the Divine Domain karmic mirror instead of a
 * puppet. CustomModelData follows the frozen tier so the inventory icon can
 * change without client-side runtime state.</p>
 */
public final class KarmicPuppetItem extends Item {
    static final String TAG_KILLS = "blade_tetra_divine_kills";
    static final String TAG_TIER = "blade_tetra_divine_tier";
    static final String TAG_SESSION = "blade_tetra_divine_session";
    static final String TAG_SNAPSHOT = "blade_tetra_divine_snapshot";
    private static final String CUSTOM_MODEL_DATA = "CustomModelData";

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
        tag.putInt(CUSTOM_MODEL_DATA, modelData(tier));
        return stack;
    }

    static boolean hasSnapshot(ItemStack stack) {
        return stack.is(dev.bladetetra.registry.ModItems.KARMIC_PUPPET.get())
                && stack.getOrCreateTag().getBoolean(TAG_SNAPSHOT);
    }

    private static int modelData(DivineDomainTier tier) {
        return switch (tier) {
            case ECHO -> 1;
            case GRUDGE -> 2;
            case HUNDRED_GHOSTS -> 3;
            case ASURA -> 4;
            case AVICI -> 5;
        };
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
            return Component.literal("业镜 · " + tier.displayName());
        }
        return Component.literal("无铭业镜");
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
            List<Component> tooltip, TooltipFlag flag) {
        if (hasSnapshot(stack)) {
            tooltip.add(Component.literal("映照杀业：" + stack.getOrCreateTag().getLong(TAG_KILLS))
                    .withStyle(ChatFormatting.DARK_RED));
            tooltip.add(Component.literal("取镜的一刻，刀下亡魂已被定格。")
                    .withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.literal("投入祭火，以此杀业叩问神域。")
                    .withStyle(ChatFormatting.DARK_GRAY));
        } else {
            tooltip.add(Component.literal("尚未映照持刀者的杀业。")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
