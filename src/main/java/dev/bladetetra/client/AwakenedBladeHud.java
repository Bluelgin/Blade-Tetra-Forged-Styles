package dev.bladetetra.client;

import dev.bladetetra.BladeTetra;
import dev.bladetetra.easteregg.SoulLegacyState;
import dev.bladetetra.network.AwakenedBladeStatePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** A small, owner-only HUD. Resource values are never inferred from animations. */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class AwakenedBladeHud {
    private static final String BLADE_ID = "blade_tetra_awakened_v2_id";
    private static final ResourceLocation[] ATLASES = {
            ResourceLocation.fromNamespaceAndPath(BladeTetra.MOD_ID,
                    "textures/effect/awakened/akatsuki/atlas.png"),
            ResourceLocation.fromNamespaceAndPath(BladeTetra.MOD_ID,
                    "textures/effect/awakened/kyouka/atlas.png"),
            ResourceLocation.fromNamespaceAndPath(BladeTetra.MOD_ID,
                    "textures/effect/awakened/senbonzakura/atlas.png"),
            ResourceLocation.fromNamespaceAndPath(BladeTetra.MOD_ID,
                    "textures/effect/awakened/raikiri/atlas.png")
    };
    private static AwakenedBladeStatePacket active;
    private static ClientLevel level;
    private static long lastMessage;

    public static void accept(AwakenedBladeStatePacket packet) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || packet.soul() < 1 || packet.soul() > 4
                || packet.maximum() < 1 || packet.maximum() > 7
                || packet.meter() < 0 || packet.meter() > packet.maximum()
                || packet.minimumRank() < 0 || packet.minimumRank() > 7
                || packet.rankCost() < 0 || packet.rankCost() > 1800) return;
        level = mc.level;
        active = packet;
        lastMessage = mc.level.getGameTime();
    }

    @SubscribeEvent
    public static void render(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.level != level
                || active == null || mc.options.hideGui || mc.screen != null
                || mc.level.getGameTime() - lastMessage > 160) return;
        ItemStack blade = mc.player.getMainHandItem();
        CompoundTag tag = blade.getTag();
        if (tag == null || !tag.hasUUID(BLADE_ID)
                || !active.blade().equals(tag.getUUID(BLADE_ID))
                || SoulLegacyState.active(blade).ordinal() != active.soul()) return;
        var graphics = event.getGuiGraphics();
        int soul = active.soul();
        int x = graphics.guiWidth() / 2 + 101;
        int y = graphics.guiHeight() - 55;
        int texW = soul == 1 ? 1024 : 512;
        int u = switch (soul) {
            case 1, 3 -> 0;
            case 2 -> 128;
            default -> 256;
        };
        int v = soul <= 2 ? 384 : 256;
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(0.1875F, 0.1875F, 1.0F);
        graphics.blit(ATLASES[soul - 1], 0, 0, u, v, 128, 128, texW, 512);
        graphics.pose().popPose();
        String suffix = switch (soul) {
            case 1 -> "akatsuki";
            case 2 -> "kyouka";
            case 3 -> "senbonzakura";
            default -> "raikiri";
        };
        Component label = Component.translatable("hud.blade_tetra.awakened." + suffix);
        String number = soul == 3 ? switch (active.meter()) {
            case 4 -> "A · Flow";
            case 5 -> "S · Flow";
            case 6 -> "SS · Flow";
            case 7 -> "SSS · Flow";
            default -> "—";
        } : active.meter() + "/" + active.maximum();
        graphics.drawString(mc.font, label, x + 27, y + 1, 0xF0E8F5);
        graphics.drawString(mc.font, number, x + 27, y + 13, 0xDAD6EB);
        if (soul == 3 && active.dispersed()) {
            graphics.drawString(mc.font,
                    Component.translatable("hud.blade_tetra.awakened.dispersed"),
                    x + 27, y + 24, 0xE8ACC8);
        }
        if (active.minimumRank() > 0 && active.rankCost() > 0) {
            String grade = switch (active.minimumRank()) {
                case 5 -> "S";
                case 4 -> "A";
                case 3 -> "B";
                default -> "?";
            };
            graphics.drawString(mc.font,
                    Component.translatable("hud.blade_tetra.awakened.rank_cost",
                            grade, active.rankCost()),
                    x + 27, y + (soul == 3 && active.dispersed() ? 35 : 24),
                    0xEACB7C);
        }
    }

    private AwakenedBladeHud() { }
}
