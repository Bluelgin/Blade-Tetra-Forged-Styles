package dev.bladetetra.combat;

import dev.bladetetra.BladeTetra;
import dev.bladetetra.registry.ModSlashBladeAbilities;
import mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState;
import mods.flammpfeil.slashblade.event.SlashBladeEvent;
import mods.flammpfeil.slashblade.slasharts.SlashArts;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Event facade for named-blade fusion abilities and player-authored Slash Arts.
 * Ability reconciliation and combat behavior live in dedicated handlers so this
 * class remains a small routing layer as systems are added.
 */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LegacyFusionHandler {
    public static void sync(ItemStack blade, ISlashBladeState state) {
        LegacyFusionAbilitySync.sync(blade, state);
    }

    @SubscribeEvent
    public static void onSlashArt(SlashBladeEvent.PerformSlashArtEvent event) {
        if (!(event.getEntityLiving() instanceof ServerPlayer player)
                || event.getType() == null
                || event.getType() == SlashArts.ArtsType.Fail) {
            return;
        }
        ItemStack blade = player.getMainHandItem();
        var art = event.getSlashBladeState().getSlashArtsKey();
        if (ModSlashBladeAbilities.FORGED_SLASH_ART.getId().equals(art)) {
            ForgedSlashArtHandler.onSlashArt(event,
                    player, blade, event.getSlashBladeState());
            return;
        }
        if (ModSlashBladeAbilities.PROGRAMMATIC_FUSION.getId().equals(art)) {
            ProgrammaticFusionHandler.onSlashArt(event,
                    player, blade, event.getSlashBladeState());
            return;
        }
        if (ModSlashBladeAbilities.TSUKUMO_CROSS.getId().equals(art)) {
            TsukumoCrossNativeHandler.onSlashArt(player, blade);
            return;
        }
        if (ModSlashBladeAbilities.WITHERED_DRIVE.getId().equals(art)) {
            SignatureFusionBatchHandler.onWitheredDrive(
                    player, blade, event.getSlashBladeState());
            return;
        }
        if (ModSlashBladeAbilities.PIERCING_VOID_MOON.getId().equals(art)) {
            SignatureFusionBatchHandler.onPiercingVoidMoon(player, blade);
            return;
        }
        if (ModSlashBladeAbilities.VOID_SCATTERING.getId().equals(art)) {
            VoidScatteringFusionHandler.onSlashArt(
                    event, player, blade, event.getSlashBladeState());
            return;
        }
        if (ModSlashBladeAbilities.DOUWARI.getId().equals(art)) {
            DouwariFusionHandler.onSlashArt(
                    player, blade, event.getSlashBladeState());
            return;
        }
        if (ModSlashBladeAbilities.TWIN_PHASE_KIKOUKU.getId().equals(art)) {
            TwinPhaseFusionHandler.onSlashArt(
                    player, blade, event.getSlashBladeState());
            return;
        }
        TwinFoxFusionHandler.onSlashArt(
                player, blade, event.getSlashBladeState());
    }

    @SubscribeEvent
    public static void onBladeHit(SlashBladeEvent.HitEvent event) {
        TwinFoxFusionHandler.onBladeHit(event);
        RustReleaseFusionHandler.onBladeHit(event);
        SignatureFusionBatchHandler.onBladeHit(event);
    }

    @SubscribeEvent
    public static void onLivingAttack(LivingAttackEvent event) {
        VoidScatteringFusionHandler.onLivingAttack(event);
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        VoidScatteringFusionHandler.onLivingHurt(event);
        RustReleaseFusionHandler.onLivingHurt(event);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        ForgedSlashArtHandler.tick(event);
        ProgrammaticFusionHandler.tick(event);
        TwinFoxFusionHandler.tick(event);
        TwinPhaseFusionHandler.tick(event);
        DouwariFusionHandler.tick(event);
        VoidScatteringFusionHandler.tick(event);
        TsukumoCrossNativeHandler.tick(event);
        SignatureFusionBatchHandler.tick(event);
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            ForgedSlashArtHandler.onLevelUnload(level);
            ProgrammaticFusionHandler.onLevelUnload(level);
            TwinFoxFusionHandler.onLevelUnload(level);
            TwinPhaseFusionHandler.onLevelUnload(level);
            DouwariFusionHandler.onLevelUnload(level);
            VoidScatteringFusionHandler.onLevelUnload(level);
            TsukumoCrossNativeHandler.onLevelUnload(level);
            SignatureFusionBatchHandler.onLevelUnload(level);
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        ForgedSlashArtHandler.clear();
        ProgrammaticFusionHandler.clear();
        TwinFoxFusionHandler.clear();
        TwinPhaseFusionHandler.clear();
        RustReleaseFusionHandler.clear();
        DouwariFusionHandler.clear();
        VoidScatteringFusionHandler.clear();
        TsukumoCrossNativeHandler.clear();
        SignatureFusionBatchHandler.clear();
    }

    // Package-visible compatibility seams retained for existing focused tests.
    static boolean formsPincer(Vec3 first, Vec3 second, float targetWidth) {
        return TwinFoxFusionHandler.formsPincer(first, second, targetWidth);
    }

    static float twinPhaseSlashDamage(double attack, boolean crowd,
            boolean finisher) {
        return TwinPhaseFusionHandler.slashDamage(attack, crowd, finisher);
    }

    private LegacyFusionHandler() {
    }
}
