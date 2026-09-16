package dev.bladetetra.challenge;

import dev.bladetetra.BladeTetra;
import dev.bladetetra.registry.ModItems;
import mods.flammpfeil.slashblade.entity.BladeStandEntity;
import mods.flammpfeil.slashblade.registry.SlashBladeItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashSet;
import java.util.Set;

/**
 * Keeps the Divine Domain ritual puppet on a real SlashBlade blade stand.
 * The stand is locked to ritual use: players may only remove the displayed
 * unsigned puppet with an empty main hand while the session is waiting.
 */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DivineDomainRitualStand {
    private static final String DIVINE_CHALLENGE = "blade_tetra_divine_challenge";
    private static final String DIVINE_ORIGIN_X = "blade_tetra_divine_origin_x";
    private static final String DIVINE_ORIGIN_Z = "blade_tetra_divine_origin_z";
    private static final String RITUAL_STAND = "blade_tetra_divine_ritual_stand";
    private static final String RITUAL_SESSION = "blade_tetra_divine_ritual_session";

    @SubscribeEvent
    public static void serverTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.getServer().getTickCount() % 5 != 0) {
            return;
        }
        ServerLevel divine = event.getServer().getLevel(DivineDomainManager.DIVINE_REALM);
        if (divine == null) {
            return;
        }
        Set<Long> checkedSessions = new HashSet<>();
        for (ServerPlayer player : divine.players()) {
            if (!player.getPersistentData().contains(DIVINE_CHALLENGE)
                    || !player.getPersistentData().contains(DIVINE_ORIGIN_X)
                    || !player.getPersistentData().contains(DIVINE_ORIGIN_Z)) {
                continue;
            }
            long challengeId = player.getPersistentData().getLong(DIVINE_CHALLENGE);
            if (!checkedSessions.add(challengeId)) {
                continue;
            }
            int ox = player.getPersistentData().getInt(DIVINE_ORIGIN_X);
            int oz = player.getPersistentData().getInt(DIVINE_ORIGIN_Z);
            ensureStand(divine, ox, oz, challengeId);
        }
    }

    private static void ensureStand(ServerLevel level, int originX, int originZ, long challengeId) {
        BlockPos rack = DivineDomainArenaData.rack(originX, originZ);
        AABB search = new AABB(rack).inflate(1.5D, 2.0D, 1.5D);
        BladeStandEntity stand = level.getEntitiesOfClass(BladeStandEntity.class, search,
                candidate -> candidate.getPersistentData().getBoolean(RITUAL_STAND))
                .stream().findFirst().orElse(null);
        if (stand == null) {
            stand = BladeStandEntity.createInstanceFromPos(level, rack,
                    Direction.SOUTH, SlashBladeItems.BLADESTAND_2.get());
            stand.setInvulnerable(true);
            stand.getPersistentData().putBoolean(RITUAL_STAND, true);
            level.addFreshEntity(stand);
        }

        stand.getPersistentData().putLong(RITUAL_SESSION, challengeId);
        boolean waiting = DivineDomainManager.isWaitingForPuppet(challengeId);
        if (waiting && !stand.getItem().is(ModItems.KARMIC_PUPPET.get())) {
            stand.setItem(new ItemStack(ModItems.KARMIC_PUPPET.get()), false);
        } else if (!waiting && stand.getItem().is(ModItems.KARMIC_PUPPET.get())) {
            stand.setItem(ItemStack.EMPTY, false);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void protectStand(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getTarget() instanceof BladeStandEntity stand)
                || !stand.getPersistentData().getBoolean(RITUAL_STAND)) {
            return;
        }

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (event.getLevel().isClientSide || event.getHand() != InteractionHand.MAIN_HAND
                || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!stand.getItem().is(ModItems.KARMIC_PUPPET.get())) {
            return;
        }
        if (!player.getMainHandItem().isEmpty()) {
            player.displayClientMessage(Component.literal("空手取下木偶。")
                    .withStyle(ChatFormatting.GRAY), true);
            return;
        }
        if (!player.getPersistentData().contains(DIVINE_CHALLENGE)) {
            return;
        }
        long challengeId = player.getPersistentData().getLong(DIVINE_CHALLENGE);
        if (stand.getPersistentData().getLong(RITUAL_SESSION) != challengeId) {
            return;
        }
        if (DivineDomainManager.takePuppetFromStand(player)) {
            stand.setItem(ItemStack.EMPTY, false);
            stand.playSound(SoundEvents.ITEM_FRAME_REMOVE_ITEM, 1.0F, 1.0F);
        }
    }

    private DivineDomainRitualStand() {
    }
}
