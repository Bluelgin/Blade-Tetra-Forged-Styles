package dev.bladetetra.challenge;

import dev.bladetetra.BladeTetra;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Handles the Divine Domain ritual altar.
 *
 * <p>The altar is intentionally independent from SlashBlade's BladeStandEntity.
 * Interacting with the altar while a session is waiting hands the player the
 * session-bound karmic mirror and freezes their total SlashBlade kill count at
 * that exact moment.</p>
 */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DivineDomainRitualStand {
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void takeOffering(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide
                || event.getHand() != InteractionHand.MAIN_HAND
                || !(event.getEntity() instanceof ServerPlayer player)
                || !player.level().dimension().equals(DivineDomainManager.DIVINE_REALM)
                || !player.getPersistentData().contains("blade_tetra_divine_challenge")) {
            return;
        }

        int ox = player.getPersistentData().getInt("blade_tetra_divine_origin_x");
        int oz = player.getPersistentData().getInt("blade_tetra_divine_origin_z");
        BlockPos altar = DivineDomainArenaData.altar(ox, oz);
        BlockPos clicked = event.getPos();
        if (!clicked.equals(altar) && !clicked.equals(altar.above())) {
            return;
        }

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (!player.getMainHandItem().isEmpty()) {
            player.displayClientMessage(Component.literal("空手触碰祭坛中央的业镜。")
                    .withStyle(ChatFormatting.GRAY), true);
            return;
        }

        if (DivineDomainManager.takeOfferingFromAltar(player)) {
            player.level().playSound(null, altar, SoundEvents.AMETHYST_BLOCK_CHIME,
                    SoundSource.PLAYERS, 0.9F, 0.65F);
        }
    }

    private DivineDomainRitualStand() {
    }
}
