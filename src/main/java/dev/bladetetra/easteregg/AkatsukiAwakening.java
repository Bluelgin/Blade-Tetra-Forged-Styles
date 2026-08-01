package dev.bladetetra.easteregg;

import dev.bladetetra.BladeTetra;
import dev.bladetetra.compat.ContractBladeCompat;
import dev.bladetetra.config.GameplayConfig;
import dev.bladetetra.item.ModularSlashBladeItem;
import dev.bladetetra.visual.SayaPresetSkin;
import mods.flammpfeil.slashblade.event.SlashBladeEvent;
import mods.flammpfeil.slashblade.slasharts.SlashArts;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/** Hidden full-moon awakening ritual for the Akatsuki easter-egg blade. */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class AkatsukiAwakening {
    public static final String TAG_UNLOCKED = "blade_tetra_akatsuki_unlocked";
    private static final String TAG_LAST_SA = "blade_tetra_akatsuki_last_sa";
    private static final String BLOOD_VARIANT =
            "awakened_soul_inscription/blood_crystal";
    private static final int REQUIRED_KILLS = 50;
    private static final int SA_KILL_WINDOW = 100;
    private static final ResourceLocation ADVANCEMENT =
            ResourceLocation.fromNamespaceAndPath(BladeTetra.MOD_ID, "akatsuki");

    public static boolean isUnlocked(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.getBoolean(TAG_UNLOCKED);
    }

    public static boolean isCandidate(ItemStack stack) {
        if (!(stack.getItem() instanceof ModularSlashBladeItem)
                || SayaPresetSkin.fromStack(stack) != SayaPresetSkin.AKATSUKI) {
            return false;
        }
        CompoundTag tag = stack.getTag();
        if (tag == null
                || !ModularSlashBladeItem.AWAKENED_SOUL_INSCRIPTION_MODULE.equals(
                tag.getString(ModularSlashBladeItem.INSCRIPTION_SLOT))) {
            return false;
        }
        String variantKey = ModularSlashBladeItem.AWAKENED_SOUL_INSCRIPTION_MODULE
                + "_material";
        return tag.contains(variantKey, Tag.TAG_STRING)
                && BLOOD_VARIANT.equals(tag.getString(variantKey));
    }

    public static boolean isActive(ItemStack stack) {
        return isUnlocked(stack) && isCandidate(stack) && !isBroken(stack);
    }

    public static boolean prepareForDebug(ItemStack stack) {
        if (!isCandidate(stack)) return false;
        stack.getCapability(ModularSlashBladeItem.BLADESTATE)
                .ifPresent(state -> state.setKillCount(REQUIRED_KILLS - 1));
        stack.getOrCreateTag().remove(TAG_LAST_SA);
        return true;
    }

    public static boolean unlockForDebug(ItemStack stack) {
        if (!isCandidate(stack)) return false;
        stack.getOrCreateTag().putBoolean(TAG_UNLOCKED, true);
        stack.getOrCreateTag().remove(TAG_LAST_SA);
        return true;
    }

    public static boolean resetForDebug(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null) return false;
        tag.remove(TAG_UNLOCKED);
        tag.remove(TAG_LAST_SA);
        return true;
    }

    public static void appendTooltip(ItemStack stack, List<Component> tooltip) {
        if (isActive(stack)) {
            tooltip.add(Component.translatable("tooltip.blade_tetra.akatsuki.awakened")
                    .withStyle(ChatFormatting.DARK_RED));
            if (ContractBladeCompat.hasAkatsuki(stack)) {
                tooltip.add(Component.translatable(
                                "tooltip.blade_tetra.akatsuki.spirit_bound")
                        .withStyle(ChatFormatting.RED));
            }
        }
    }

    @SubscribeEvent
    public static void onSlashArt(SlashBladeEvent.PerformSlashArtEvent event) {
        if (!GameplayConfig.ENABLE_EASTER_EGG_UNLOCKS.get()) return;
        ItemStack blade = event.getEntityLiving().getMainHandItem();
        if (event.getEntityLiving().level().isClientSide()
                || event.getType() != SlashArts.ArtsType.Success
                || !isCandidate(blade) || isUnlocked(blade)) {
            return;
        }
        blade.getOrCreateTag().putLong(
                TAG_LAST_SA, event.getEntityLiving().level().getGameTime());
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!GameplayConfig.ENABLE_EASTER_EGG_UNLOCKS.get()) return;
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) return;
        ItemStack blade = player.getMainHandItem();
        if (!isCandidate(blade) || isUnlocked(blade) || isBroken(blade)) return;
        ServerLevel level = player.serverLevel();
        long lastSa = blade.getOrCreateTag().getLong(TAG_LAST_SA);
        long elapsed = level.getGameTime() - lastSa;
        if (lastSa <= 0L || elapsed < 0L || elapsed > SA_KILL_WINDOW
                || killCount(blade) < REQUIRED_KILLS - 1
                || !level.isNight() || level.getMoonPhase() != 0
                || !level.canSeeSky(player.blockPosition())) {
            return;
        }
        awaken(player, blade, level);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide()
                ) return;
        ItemStack blade = event.player.getMainHandItem();
        if (!isUnlocked(blade)) return;
        boolean active = isActive(blade);
        ContractBladeCompat.syncAkatsuki(event.player, blade, active);
    }

    private static void awaken(ServerPlayer player, ItemStack blade, ServerLevel level) {
        blade.getOrCreateTag().putBoolean(TAG_UNLOCKED, true);
        blade.getOrCreateTag().remove(TAG_LAST_SA);
        ContractBladeCompat.syncAkatsuki(player, blade, true);

        level.sendParticles(ParticleTypes.CRIMSON_SPORE,
                player.getX(), player.getY() + 1.0D, player.getZ(),
                90, 1.3D, 0.8D, 1.3D, 0.025D);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL,
                player.getX(), player.getY() + 1.0D, player.getZ(),
                36, 0.8D, 0.65D, 0.8D, 0.04D);
        level.playSound(null, player.blockPosition(),
                SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.PLAYERS,
                1.0F, 0.72F);
        player.displayClientMessage(
                Component.translatable("message.blade_tetra.akatsuki.awakened")
                        .withStyle(ChatFormatting.DARK_RED), false);

        var advancement = player.server.getAdvancements().getAdvancement(ADVANCEMENT);
        if (advancement != null) {
            var progress = player.getAdvancements().getOrStartProgress(advancement);
            for (String criterion : progress.getRemainingCriteria()) {
                player.getAdvancements().award(advancement, criterion);
            }
        }
    }

    private static int killCount(ItemStack stack) {
        return stack.getCapability(ModularSlashBladeItem.BLADESTATE)
                .map(state -> Math.max(0, state.getKillCount()))
                .orElse(0);
    }

    private static boolean isBroken(ItemStack stack) {
        return stack.getCapability(ModularSlashBladeItem.BLADESTATE)
                .map(state -> state.isBroken()).orElse(false);
    }

    private AkatsukiAwakening() {
    }
}
