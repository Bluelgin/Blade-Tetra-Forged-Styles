package dev.bladetetra.challenge;

import dev.bladetetra.BladeTetra;
import dev.bladetetra.registry.ModEntities;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.FlintAndSteelItem;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingKnockBackEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.event.level.PistonEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ChallengeEvents {
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void preventMikageKnockback(LivingKnockBackEvent event) {
        if (event.getEntity() instanceof MikageEntity) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void breakBlock(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof net.minecraft.world.level.Level level
                && ChallengeManager.isProtectedRealm(level)
                && !event.getPlayer().isCreative()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void placeBlock(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel() instanceof net.minecraft.world.level.Level level
                && ChallengeManager.isProtectedRealm(level)
                && (!(event.getEntity() instanceof ServerPlayer player)
                        || !player.isCreative())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void fluidChange(BlockEvent.FluidPlaceBlockEvent event) {
        if (event.getLevel() instanceof net.minecraft.world.level.Level level
                && ChallengeManager.isProtectedRealm(level)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void piston(PistonEvent.Pre event) {
        if (event.getLevel() instanceof net.minecraft.world.level.Level level
                && ChallengeManager.isProtectedRealm(level)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void explosion(ExplosionEvent.Detonate event) {
        if (ChallengeManager.isProtectedRealm(event.getLevel())) {
            event.getAffectedBlocks().clear();
        }
    }

    @SubscribeEvent
    public static void useBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!ChallengeManager.isProtectedRealm(event.getLevel())
                || event.getEntity().isCreative()) {
            return;
        }
        if (event.getItemStack().getItem() instanceof BlockItem
                || event.getItemStack().getItem() instanceof BucketItem
                || event.getItemStack().getItem() instanceof FlintAndSteelItem) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void preventChallengeDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !ChallengeManager.isParticipant(player)) {
            return;
        }
        event.setCanceled(true);
        player.setHealth(1.0F);
        player.removeAllEffects();
        ChallengeManager.ejectDefeatedPlayer(player);
        player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                "message.blade_tetra.challenge.failed"));
    }

    private ChallengeEvents() {
    }

    @Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Attributes {
        @SubscribeEvent
        public static void register(net.minecraftforge.event.entity.EntityAttributeCreationEvent event) {
            event.put(ModEntities.MIKAGE.get(), MikageEntity.createAttributes().build());
            event.put(ModEntities.MIKAGE_PHANTOM_SWORD.get(),
                    MikagePhantomSwordEntity.createAttributes().build());
            event.put(ModEntities.MIKAGE_ECHO.get(),
                    MikageEchoEntity.createAttributes().build());
        }
    }
}
