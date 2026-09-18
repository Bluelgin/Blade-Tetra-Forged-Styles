package dev.bladetetra.challenge;

import dev.bladetetra.BladeTetra;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/** Centralized player-facing notice for the unfinished Divine Domain postgame route. */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DivineDomainWipNotice {
    private static final String ENTRY_LABEL = "进入神域 [开发中]";
    private static final String TITLE = "⚠ 神域残响 · 开发中";
    private static final String DETAIL = "当前流程可正常体验，但地图、战斗、奖励与演出仍可能调整。";

    public static String entryLabel() {
        return ENTRY_LABEL;
    }

    public static Component title() {
        return Component.literal(TITLE).withStyle(ChatFormatting.GOLD);
    }

    public static Component detail() {
        return Component.literal(DETAIL).withStyle(ChatFormatting.GRAY);
    }

    public static void appendTooltip(List<Component> tooltip) {
        tooltip.add(title());
        tooltip.add(detail());
    }

    @SubscribeEvent
    public static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && event.getTo().equals(DivineDomainManager.DIVINE_REALM)) {
            player.sendSystemMessage(title());
            player.sendSystemMessage(detail());
        }
    }

    private DivineDomainWipNotice() {
    }
}
