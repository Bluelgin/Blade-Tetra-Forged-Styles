package dev.bladetetra.challenge;

import dev.bladetetra.BladeTetra;
import mods.flammpfeil.slashblade.entity.BladeStandEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Divine Domain ritual altar interaction and legacy stand migration.
 *
 * <p>The old method name {@link #ensureStand} is intentionally retained as a
 * branch/save compatibility hook. It no longer creates a SlashBlade stand:
 * persisted ritual stands are removed and the authored altar is restored.</p>
 */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DivineDomainRitualStand {
    private static final String DIVINE_CHALLENGE = "blade_tetra_divine_challenge";
    private static final String DIVINE_ORIGIN_X = "blade_tetra_divine_origin_x";
    private static final String DIVINE_ORIGIN_Z = "blade_tetra_divine_origin_z";
    private static final String LEGACY_RITUAL_STAND = "blade_tetra_divine_ritual_stand";

    /**
     * Migration hook used by the current manager before dimension transfer.
     * No SlashBlade BladeStandEntity is created here anymore.
     */
    static void ensureStand(ServerLevel level, int originX, int originZ, long challengeId) {
        BlockPos center = DivineDomainArenaData.altar(originX, originZ);

        // Remove stands created by earlier PR builds so old test worlds migrate
        // cleanly and never enter SlashBlade's buggy stand rendering path.
        AABB legacySearch = new AABB(center).inflate(3.0D, 3.0D, 3.0D);
        for (BladeStandEntity stand : level.getEntitiesOfClass(BladeStandEntity.class, legacySearch,
                candidate -> candidate.getPersistentData().getBoolean(LEGACY_RITUAL_STAND))) {
            stand.discard();
        }

        buildAltar(level, center);
    }

    private static void buildAltar(ServerLevel level, BlockPos center) {
        // Low octagonal blackstone dais beneath the intact torii.
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (Math.abs(dx) == 2 && Math.abs(dz) == 2) {
                    continue;
                }
                BlockPos floor = center.offset(dx, -1, dz);
                level.setBlock(floor,
                        (Math.abs(dx) + Math.abs(dz) >= 3)
                                ? Blocks.RED_NETHER_BRICKS.defaultBlockState()
                                : Blocks.POLISHED_BLACKSTONE.defaultBlockState(),
                        2 | 16);
            }
        }

        // The central reliquary is the clickable altar. The amethyst crystal is
        // only a visual stand-in for the dormant karmic mirror before pickup.
        level.setBlock(center, Blocks.LODESTONE.defaultBlockState(), 2 | 16);
        level.setBlock(center.above(), Blocks.AMETHYST_CLUSTER.defaultBlockState(), 2 | 16);
        for (BlockPos pos : new BlockPos[]{
                center.offset(2, 0, 0), center.offset(-2, 0, 0),
                center.offset(0, 0, 2), center.offset(0, 0, -2)}) {
            level.setBlock(pos, Blocks.CRYING_OBSIDIAN.defaultBlockState(), 2 | 16);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void takeOffering(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide
                || event.getHand() != InteractionHand.MAIN_HAND
                || !(event.getEntity() instanceof ServerPlayer player)
                || !player.level().dimension().equals(DivineDomainManager.DIVINE_REALM)
                || !player.getPersistentData().contains(DIVINE_CHALLENGE)
                || !player.getPersistentData().contains(DIVINE_ORIGIN_X)
                || !player.getPersistentData().contains(DIVINE_ORIGIN_Z)) {
            return;
        }

        int ox = player.getPersistentData().getInt(DIVINE_ORIGIN_X);
        int oz = player.getPersistentData().getInt(DIVINE_ORIGIN_Z);
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
            // Pickup has a visible world-state change: the altar remains, while
            // the dormant crystal representing the unclaimed mirror disappears.
            player.level().setBlock(altar.above(), Blocks.AIR.defaultBlockState(), 2 | 16);
        }
    }

    private DivineDomainRitualStand() {
    }
}
