package dev.bladetetra.challenge;

import dev.bladetetra.BladeTetra;
import mods.flammpfeil.slashblade.entity.BladeStandEntity;
import mods.flammpfeil.slashblade.registry.SlashBladeItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashSet;
import java.util.Set;

/**
 * Keeps a real SlashBlade blade stand under the Divine Domain's central torii.
 * The karmic puppet remains the ritual pickup; the stand itself is scenery and
 * cannot be repurposed as storage while the challenge is active.
 */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DivineDomainRitualStand {
    private static final String DIVINE_ORIGIN_X = "blade_tetra_divine_origin_x";
    private static final String DIVINE_ORIGIN_Z = "blade_tetra_divine_origin_z";
    private static final String RITUAL_STAND = "blade_tetra_divine_ritual_stand";

    @SubscribeEvent
    public static void serverTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.getServer().getTickCount() % 20 != 0) {
            return;
        }
        ServerLevel divine = event.getServer().getLevel(DivineDomainManager.DIVINE_REALM);
        if (divine == null) {
            return;
        }
        Set<Long> checkedOrigins = new HashSet<>();
        for (ServerPlayer player : divine.players()) {
            if (!player.getPersistentData().contains(DIVINE_ORIGIN_X)
                    || !player.getPersistentData().contains(DIVINE_ORIGIN_Z)) {
                continue;
            }
            int ox = player.getPersistentData().getInt(DIVINE_ORIGIN_X);
            int oz = player.getPersistentData().getInt(DIVINE_ORIGIN_Z);
            long originKey = BlockPos.asLong(ox, 0, oz);
            if (checkedOrigins.add(originKey)) {
                ensureStand(divine, ox, oz);
            }
        }
    }

    private static void ensureStand(ServerLevel level, int originX, int originZ) {
        BlockPos rack = DivineDomainArenaData.rack(originX, originZ);
        AABB search = new AABB(rack).inflate(1.5D, 2.0D, 1.5D);
        boolean exists = !level.getEntitiesOfClass(BladeStandEntity.class, search,
                stand -> stand.getPersistentData().getBoolean(RITUAL_STAND)).isEmpty();
        if (exists) {
            return;
        }
        BladeStandEntity stand = BladeStandEntity.createInstanceFromPos(level, rack,
                Direction.SOUTH, SlashBladeItems.BLADESTAND_2.get());
        stand.setInvulnerable(true);
        stand.getPersistentData().putBoolean(RITUAL_STAND, true);
        level.addFreshEntity(stand);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void protectStand(PlayerInteractEvent.EntityInteract event) {
        if (event.getTarget() instanceof BladeStandEntity stand
                && stand.getPersistentData().getBoolean(RITUAL_STAND)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }

    private DivineDomainRitualStand() {
    }
}
