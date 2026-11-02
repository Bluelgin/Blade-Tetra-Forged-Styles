package dev.bladetetra.forging;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;

/** Retired research protocol. Keep old packet entry points harmless and loadable. */
public final class LegacyImprinting {
    public static InteractionResult tryBegin(UseOnContext context) { return InteractionResult.PASS; }
    public static void beginFromPacket(ServerPlayer player, InteractionHand hand, BlockPos pos) {}
    public static void finish(ServerPlayer player, int accuracy, LegacyCalibrationProfile profile) {
        player.getPersistentData().remove("blade_tetra_legacy_imprint");
    }
    private LegacyImprinting() {}
}
