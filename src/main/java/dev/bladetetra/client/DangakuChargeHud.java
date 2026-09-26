package dev.bladetetra.client;

import dev.bladetetra.BladeTetra;
import dev.bladetetra.combat.BladeStyle;
import dev.bladetetra.combat.DangakuChargeMath;
import dev.bladetetra.combat.StyleResolver;
import dev.bladetetra.item.ModularSlashBladeItem;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Lightweight local HUD; combat authority stays entirely on the server. */
@Mod.EventBusSubscriber(
        modid = BladeTetra.MOD_ID,
        value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DangakuChargeHud {
    private static final int BAR_WIDTH = 118;
    private static final int BAR_HEIGHT = 7;

    @SubscribeEvent
    public static void render(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null
                || minecraft.options.hideGui
                || minecraft.screen != null
                || !isCharging(minecraft.player.getMainHandItem())) {
            return;
        }

        long heldTicks = minecraft.player.getTicksUsingItem();
        double charge = DangakuChargeMath.chargeForHeldTicks(heldTicks);
        var graphics = event.getGuiGraphics();
        int x = (graphics.guiWidth() - BAR_WIDTH) / 2;
        int y = graphics.guiHeight() - 66;
        int fill = (int) Math.round(BAR_WIDTH * charge);

        graphics.fill(x - 1, y - 1, x + BAR_WIDTH + 1, y + BAR_HEIGHT + 1, 0xB0000000);
        graphics.fill(x, y, x + BAR_WIDTH, y + BAR_HEIGHT, 0x90505050);
        int fillColor = DangakuChargeMath.isOvercharged(heldTicks)
                ? 0xFFD08A6C
                : 0xFFE7D39B;
        graphics.fill(x, y, x + fill, y + BAR_HEIGHT, fillColor);

        String phase = DangakuChargeMath.isPeakWindow(heldTicks)
                ? "  ◆"
                : DangakuChargeMath.isOvercharged(heldTicks) ? "  ↓" : "";
        Component label = Component.literal(
                Component.translatable("style.blade_tetra.dangaku").getString()
                        + "  " + Math.round(charge * 100.0D) + "%" + phase);
        graphics.drawCenteredString(
                minecraft.font,
                label,
                graphics.guiWidth() / 2,
                y - 11,
                0xF2E8D5);
    }

    private static boolean isCharging(ItemStack blade) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!minecraft.player.isUsingItem()
                || minecraft.player.getUsedItemHand() != InteractionHand.MAIN_HAND
                || minecraft.player.getUseItem() != blade
                || !(blade.getItem() instanceof ModularSlashBladeItem)
                || StyleResolver.resolve(blade) != BladeStyle.DANGAKU) {
            return false;
        }

        ResourceLocation combo = blade.getCapability(ModularSlashBladeItem.BLADESTATE)
                .map(state -> state.resolvCurrentComboState(minecraft.player))
                .orElse(ComboStateRegistry.NONE.getId());
        return ComboStateRegistry.NONE.getId().equals(combo);
    }

    private DangakuChargeHud() {
    }
}
