package dev.bladetetra.challenge;

import dev.bladetetra.BladeTetra;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.FlintAndSteelItem;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.PistonEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Protection rules that mirror the boss realms without widening ChallengeManager. */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DivineDomainProtectionEvents {
    static final String FAILED_RETURN = "blade_tetra_divine_failed_return";
    private static final String DIVINE_CHALLENGE = "blade_tetra_divine_challenge";
    private static final String DIVINE_ORIGIN_X = "blade_tetra_divine_origin_x";
    private static final String DIVINE_ORIGIN_Z = "blade_tetra_divine_origin_z";

    @SubscribeEvent
    public static void fluidChange(BlockEvent.FluidPlaceBlockEvent event) {
        if (event.getLevel() instanceof ServerLevel level
                && level.dimension().equals(DivineDomainManager.DIVINE_REALM)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void piston(PistonEvent.Pre event) {
        if (event.getLevel() instanceof ServerLevel level
                && level.dimension().equals(DivineDomainManager.DIVINE_REALM)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void useBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!event.getLevel().dimension().equals(DivineDomainManager.DIVINE_REALM)
                || event.getEntity().isCreative()) {
            return;
        }
        if (event.getItemStack().getItem() instanceof BlockItem
                || event.getItemStack().getItem() instanceof BucketItem
                || event.getItemStack().getItem() instanceof FlintAndSteelItem) {
            event.setCanceled(true);
        }
    }

    /**
     * Divine Domain deliberately removes the ordinary visitor tag during the hop.
     * Restore it before ChallengeManager's logout hook runs so that its existing
     * reconnect grace can expire the parked visitor participant instead of leaking
     * that challenge/arena slot forever when somebody disconnects in the ritual.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void prepareVisitorReconnect(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !player.level().dimension().equals(DivineDomainManager.DIVINE_REALM)
                || !player.getPersistentData().contains(DIVINE_CHALLENGE)) {
            return;
        }
        player.getPersistentData().putLong(ChallengeManager.PLAYER_CHALLENGE,
                player.getPersistentData().getLong(DIVINE_CHALLENGE));
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void recoverAfterReconnect(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !player.level().dimension().equals(DivineDomainManager.DIVINE_REALM)
                || !player.getPersistentData().contains(DIVINE_CHALLENGE)) {
            return;
        }
        long challengeId = player.getPersistentData().getLong(DIVINE_CHALLENGE);
        int originX = player.getPersistentData().getInt(DIVINE_ORIGIN_X);
        int originZ = player.getPersistentData().getInt(DIVINE_ORIGIN_Z);
        returnAsFailed(player, challengeId, originX, originZ,
                "神域中的仪式已经中断。御影将你送回了界门。");
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void ritualDefeat(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !player.level().dimension().equals(DivineDomainManager.DIVINE_REALM)
                || !player.getPersistentData().contains(DIVINE_CHALLENGE)) {
            return;
        }
        DivineDomainManager.Session session = DivineDomainManager.activeSession(player);
        if (session != null && session.support != null
                && (session.support.protectedPlayer(player) || session.support.guard(player))) {
            if (player.getHealth() <= 0) {
                player.setHealth(1);
            }
            event.setCanceled(true);
            return;
        }
        event.setCanceled(true);
        player.setHealth(1.0F);
        player.removeAllEffects();
        long challengeId = player.getPersistentData().getLong(DIVINE_CHALLENGE);
        int originX = player.getPersistentData().getInt(DIVINE_ORIGIN_X);
        int originZ = player.getPersistentData().getInt(DIVINE_ORIGIN_Z);
        returnAsFailed(player, challengeId, originX, originZ,
                "仪式拒绝了你的死亡。御影将你送回了界门。");
    }

    private static void returnAsFailed(ServerPlayer player, long challengeId,
            int originX, int originZ, String message) {
        ServerLevel mirror = player.getServer().getLevel(ChallengeManager.MIRROR_REALM);
        if (mirror == null) {
            return;
        }
        // The dimension-change hook uses this one-shot marker so a defeat or an
        // interrupted session is never mistaken for a successful cleared return.
        player.getPersistentData().putBoolean(FAILED_RETURN, true);
        player.getPersistentData().putLong(ChallengeManager.PLAYER_CHALLENGE, challengeId);
        player.getPersistentData().remove(DIVINE_CHALLENGE);
        player.teleportTo(mirror, originX + 163.5D, 64.0D, originZ - 2.5D,
                -90.0F, 0.0F);
        player.sendSystemMessage(Component.literal(message));
    }

    private DivineDomainProtectionEvents() {
    }
}
