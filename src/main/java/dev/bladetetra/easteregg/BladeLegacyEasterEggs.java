package dev.bladetetra.easteregg;

import dev.bladetetra.BladeTetra;
import dev.bladetetra.combat.BladeStyle;
import dev.bladetetra.combat.ComponentEffectResolver;
import dev.bladetetra.forging.ForgingImprovements;
import dev.bladetetra.combat.ConductiveBladeHandler;
import dev.bladetetra.combat.ModComboStates;
import dev.bladetetra.combat.StyleResolver;
import dev.bladetetra.config.GameplayConfig;
import dev.bladetetra.item.ModularSlashBladeItem;
import dev.bladetetra.visual.MaterialAppearance;
import dev.bladetetra.visual.SayaPresetSkin;
import mods.flammpfeil.slashblade.event.SlashBladeEvent;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.EntityStruckByLightningEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/** Long-form secrets earned by keeping and rebuilding one modular blade. */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BladeLegacyEasterEggs {
    public static final String TAG_BAIREN_UNLOCKED = "blade_tetra_bairen_unlocked";
    public static final String TAG_BANSHO_UNLOCKED = "blade_tetra_bansho_unlocked";
    public static final String TAG_RAIKIRI_UNLOCKED = "blade_tetra_raikiri_unlocked";
    public static final String TAG_SHOSHIN_UNLOCKED = "blade_tetra_shoshin_unlocked";

    private static final String TAG_WAS_BROKEN = "blade_tetra_legacy_was_broken";
    private static final String TAG_BREAK_COUNT = "blade_tetra_legacy_break_count";
    private static final String TAG_RESTORE_COUNT = "blade_tetra_legacy_restore_count";
    private static final String TAG_LAST_BLADE_MATERIAL =
            "blade_tetra_legacy_last_blade_material";
    private static final String TAG_MATERIAL_CHANGES =
            "blade_tetra_legacy_material_changes";
    private static final String TAG_MASTERY_MASK = "blade_tetra_style_mastery_mask";
    private static final String TAG_RAIKIRI_CHARGED_AT =
            "blade_tetra_raikiri_charged_at";
    private static final String TAG_RAIKIRI_LAST_SA =
            "blade_tetra_raikiri_last_sa";
    private static final String TAG_SHOSHIN_ORIGIN =
            "blade_tetra_shoshin_iron_katana_origin";
    private static final String TAG_SHOSHIN_KILLS =
            "blade_tetra_shoshin_qualified_kills";

    private static final int BAIREN_RESTORES = 3;
    private static final int BAIREN_HONES = 4;
    private static final int SHOSHIN_KILLS = 50;
    private static final int RAIKIRI_CHARGE_WINDOW = 1200;
    private static final int RAIKIRI_SA_KILL_WINDOW = 100;
    private static final int MASTERY_IAIDO = 1;
    private static final int MASTERY_RENGEKI = 2;
    private static final int MASTERY_DANGAKU = 4;

    private static final ResourceLocation BAIREN_ADVANCEMENT = id("bairen");
    private static final ResourceLocation BANSHO_ADVANCEMENT = id("bansho");
    private static final ResourceLocation RAIKIRI_ADVANCEMENT = id("raikiri");
    private static final ResourceLocation SHOSHIN_ADVANCEMENT = id("shoshin");

    public static boolean isBairenUnlocked(ItemStack stack) {
        return hasFlag(stack, TAG_BAIREN_UNLOCKED);
    }

    public static boolean isBanshoUnlocked(ItemStack stack) {
        return hasFlag(stack, TAG_BANSHO_UNLOCKED);
    }

    public static boolean isRaikiriUnlocked(ItemStack stack) {
        return hasFlag(stack, TAG_RAIKIRI_UNLOCKED);
    }

    public static boolean isShoshinUnlocked(ItemStack stack) {
        return hasFlag(stack, TAG_SHOSHIN_UNLOCKED);
    }

    public static void markIronKatanaOrigin(ItemStack stack) {
        if (stack.getItem() instanceof ModularSlashBladeItem) {
            stack.getOrCreateTag().putBoolean(TAG_SHOSHIN_ORIGIN, true);
            stack.getOrCreateTag().putInt(TAG_SHOSHIN_KILLS, 0);
        }
    }

    public static boolean prepareShoshinForDebug(ItemStack stack) {
        if (!isShoshinCandidate(stack)) return false;
        CompoundTag tag = stack.getOrCreateTag();
        tag.putBoolean(TAG_SHOSHIN_ORIGIN, true);
        tag.putInt(TAG_SHOSHIN_KILLS, SHOSHIN_KILLS - 1);
        return true;
    }

    public static boolean prepareBairenForDebug(ItemStack stack) {
        if (!(stack.getItem() instanceof ModularSlashBladeItem)) return false;
        CompoundTag tag = stack.getOrCreateTag();
        tag.putInt(TAG_RESTORE_COUNT, BAIREN_RESTORES);
        tag.putInt(TAG_MATERIAL_CHANGES, 1);
        tag.putInt("honing_count", BAIREN_HONES);
        return true;
    }

    public static boolean prepareBanshoForDebug(ItemStack stack) {
        if (!(stack.getItem() instanceof ModularSlashBladeItem)
                || StyleResolver.resolve(stack) != BladeStyle.IAIDO) {
            return false;
        }
        stack.getOrCreateTag().putInt(
                TAG_MASTERY_MASK,
                MASTERY_RENGEKI | MASTERY_DANGAKU);
        return true;
    }

    public static boolean prepareRaikiriForDebug(ItemStack stack, long now) {
        if (!isRaikiriCandidate(stack)) return false;
        CompoundTag tag = stack.getOrCreateTag();
        tag.putLong(TAG_RAIKIRI_CHARGED_AT, now);
        tag.remove(TAG_RAIKIRI_LAST_SA);
        return true;
    }

    public static boolean unlockForDebug(ItemStack stack, String legacy) {
        if (!(stack.getItem() instanceof ModularSlashBladeItem)) return false;
        String flag = switch (legacy) {
            case "shoshin" -> TAG_SHOSHIN_UNLOCKED;
            case "bairen" -> TAG_BAIREN_UNLOCKED;
            case "bansho" -> TAG_BANSHO_UNLOCKED;
            case "raikiri" -> TAG_RAIKIRI_UNLOCKED;
            default -> null;
        };
        if (flag == null) return false;
        stack.getOrCreateTag().putBoolean(flag, true);
        return true;
    }

    public static boolean resetForDebug(ItemStack stack, String legacy) {
        CompoundTag tag = stack.getTag();
        if (tag == null) return false;
        switch (legacy) {
            case "shoshin" -> {
                tag.remove(TAG_SHOSHIN_UNLOCKED);
                tag.remove(TAG_SHOSHIN_ORIGIN);
                tag.remove(TAG_SHOSHIN_KILLS);
            }
            case "bairen" -> {
                tag.remove(TAG_BAIREN_UNLOCKED);
                tag.remove(TAG_WAS_BROKEN);
                tag.remove(TAG_BREAK_COUNT);
                tag.remove(TAG_RESTORE_COUNT);
                tag.remove(TAG_LAST_BLADE_MATERIAL);
                tag.remove(TAG_MATERIAL_CHANGES);
            }
            case "bansho" -> {
                tag.remove(TAG_BANSHO_UNLOCKED);
                tag.remove(TAG_MASTERY_MASK);
            }
            case "raikiri" -> {
                tag.remove(TAG_RAIKIRI_UNLOCKED);
                tag.remove(TAG_RAIKIRI_CHARGED_AT);
                tag.remove(TAG_RAIKIRI_LAST_SA);
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    /** Called from the item tick so state survives workbench module swaps. */
    public static void trackInventoryState(
            ItemStack stack,
            Level level,
            Entity holder) {
        if (level.isClientSide()
                || !GameplayConfig.ENABLE_EASTER_EGG_UNLOCKS.get()
                || !(holder instanceof ServerPlayer)
                || !(stack.getItem() instanceof ModularSlashBladeItem)) {
            return;
        }

        CompoundTag tag = stack.getOrCreateTag();
        boolean broken = isBroken(stack);
        if (!tag.contains(TAG_WAS_BROKEN, Tag.TAG_BYTE)) {
            tag.putBoolean(TAG_WAS_BROKEN, broken);
        } else {
            boolean wasBroken = tag.getBoolean(TAG_WAS_BROKEN);
            if (broken != wasBroken) {
                if (broken) {
                    tag.putInt(TAG_BREAK_COUNT, tag.getInt(TAG_BREAK_COUNT) + 1);
                } else if (tag.getInt(TAG_BREAK_COUNT)
                        > tag.getInt(TAG_RESTORE_COUNT)) {
                    tag.putInt(
                            TAG_RESTORE_COUNT,
                            tag.getInt(TAG_RESTORE_COUNT) + 1);
                }
                tag.putBoolean(TAG_WAS_BROKEN, broken);
            }
        }

        String material = MaterialAppearance.fromStack(stack).blade();
        if (!tag.contains(TAG_LAST_BLADE_MATERIAL, Tag.TAG_STRING)) {
            tag.putString(TAG_LAST_BLADE_MATERIAL, material);
        } else if (!material.equals(tag.getString(TAG_LAST_BLADE_MATERIAL))) {
            tag.putString(TAG_LAST_BLADE_MATERIAL, material);
            tag.putInt(
                    TAG_MATERIAL_CHANGES,
                    tag.getInt(TAG_MATERIAL_CHANGES) + 1);
        }
    }

    public static void appendTooltip(ItemStack stack, List<Component> tooltip) {
        if (isBanshoUnlocked(stack)) {
            tooltip.add(Component.translatable("tooltip.blade_tetra.bansho.unlocked")
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
        }

        if (isBairenUnlocked(stack)) {
            tooltip.add(Component.translatable("tooltip.blade_tetra.bairen.unlocked")
                    .withStyle(ChatFormatting.GOLD));
        }

        if (SoulLegacyState.isActive(stack, SoulLegacyState.Legacy.RAIKIRI)) {
            tooltip.add(Component.translatable("tooltip.blade_tetra.raikiri.unlocked")
                    .withStyle(ChatFormatting.AQUA));
        } else if (isRaikiriUnlocked(stack)) {
            tooltip.add(Component.translatable("tooltip.blade_tetra.raikiri.dormant")
                    .withStyle(ChatFormatting.DARK_AQUA));
        }

        if (isShoshinUnlocked(stack)) {
            tooltip.add(Component.translatable("tooltip.blade_tetra.shoshin.unlocked")
                    .withStyle(ChatFormatting.YELLOW));
        }
    }

    @SubscribeEvent
    public static void onBladeHit(SlashBladeEvent.HitEvent event) {
        if (!GameplayConfig.ENABLE_EASTER_EGG_UNLOCKS.get()) return;
        ItemStack blade = event.getBlade();
        if (event.getUser().level().isClientSide()
                || !(event.getUser() instanceof ServerPlayer player)
                || !(blade.getItem() instanceof ModularSlashBladeItem)
                || isBanshoUnlocked(blade)) {
            return;
        }

        ResourceLocation combo = event.getSlashBladeState().getComboSeq();
        BladeStyle style = StyleResolver.resolve(blade);
        int bit = 0;
        if (style == BladeStyle.IAIDO && ModComboStates.isIaidoDraw(combo)) {
            bit = MASTERY_IAIDO;
        } else if (style == BladeStyle.RENGEKI
                && ComboStateRegistry.COMBO_B7.getId().equals(combo)) {
            bit = MASTERY_RENGEKI;
        } else if (style == BladeStyle.DANGAKU
                && ModComboStates.isDangakuCleave(combo)) {
            bit = MASTERY_DANGAKU;
        }
        if (bit == 0) return;

        CompoundTag tag = blade.getOrCreateTag();
        int mask = tag.getInt(TAG_MASTERY_MASK) | bit;
        tag.putInt(TAG_MASTERY_MASK, mask);
        if ((mask & 7) == 7) {
            unlock(player, blade, TAG_BANSHO_UNLOCKED, BANSHO_ADVANCEMENT,
                    "message.blade_tetra.bansho.unlocked", ChatFormatting.LIGHT_PURPLE,
                    ParticleTypes.ENCHANT, SoundEvents.ENCHANTMENT_TABLE_USE, 0.86F);
        }
    }

    @SubscribeEvent
    public static void onStruckByLightning(EntityStruckByLightningEvent event) {
        if (!GameplayConfig.ENABLE_EASTER_EGG_UNLOCKS.get()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ItemStack blade = player.getMainHandItem();
        if (!isRaikiriCandidate(blade) || isRaikiriUnlocked(blade) || isBroken(blade)) {
            return;
        }
        blade.getOrCreateTag().putLong(
                TAG_RAIKIRI_CHARGED_AT,
                player.level().getGameTime());
        player.displayClientMessage(
                Component.translatable("message.blade_tetra.raikiri.charged")
                        .withStyle(ChatFormatting.AQUA),
                false);
    }

    @SubscribeEvent
    public static void onSlashArt(SlashBladeEvent.PerformSlashArtEvent event) {
        if (!GameplayConfig.ENABLE_EASTER_EGG_UNLOCKS.get()) return;
        ItemStack blade = event.getEntityLiving().getMainHandItem();
        if (event.getEntityLiving().level().isClientSide()
                || event.getType() != SlashArts.ArtsType.Success
                || !isRaikiriCandidate(blade)
                || isRaikiriUnlocked(blade)) {
            return;
        }
        CompoundTag tag = blade.getOrCreateTag();
        long chargedAt = tag.getLong(TAG_RAIKIRI_CHARGED_AT);
        long now = event.getEntityLiving().level().getGameTime();
        if (chargedAt > 0L && now - chargedAt <= RAIKIRI_CHARGE_WINDOW) {
            tag.putLong(TAG_RAIKIRI_LAST_SA, now);
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!GameplayConfig.ENABLE_EASTER_EGG_UNLOCKS.get()) return;
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) return;
        ItemStack blade = player.getMainHandItem();
        if (!(blade.getItem() instanceof ModularSlashBladeItem) || isBroken(blade)) return;

        tryUnlockBairen(player, blade);
        tryUnlockRaikiri(player, blade);
        tryUnlockShoshin(player, blade);
    }

    private static void tryUnlockBairen(ServerPlayer player, ItemStack blade) {
        if (isBairenUnlocked(blade)) return;
        CompoundTag tag = blade.getOrCreateTag();
        if (tag.getInt(TAG_RESTORE_COUNT) < BAIREN_RESTORES
                || tag.getInt(TAG_MATERIAL_CHANGES) < 1
                || tag.getInt("honing_count") < BAIREN_HONES) {
            return;
        }
        unlock(player, blade, TAG_BAIREN_UNLOCKED, BAIREN_ADVANCEMENT,
                "message.blade_tetra.bairen.unlocked", ChatFormatting.GOLD,
                ParticleTypes.END_ROD, SoundEvents.ANVIL_USE, 1.08F);
    }

    private static void tryUnlockRaikiri(ServerPlayer player, ItemStack blade) {
        if (!isRaikiriCandidate(blade) || isRaikiriUnlocked(blade)) return;
        CompoundTag tag = blade.getOrCreateTag();
        long now = player.level().getGameTime();
        long chargedAt = tag.getLong(TAG_RAIKIRI_CHARGED_AT);
        long lastSa = tag.getLong(TAG_RAIKIRI_LAST_SA);
        if (!player.serverLevel().isThundering()
                || chargedAt <= 0L
                || lastSa <= 0L
                || now - chargedAt > RAIKIRI_CHARGE_WINDOW
                || now - lastSa > RAIKIRI_SA_KILL_WINDOW) {
            return;
        }
        unlock(player, blade, TAG_RAIKIRI_UNLOCKED, RAIKIRI_ADVANCEMENT,
                "message.blade_tetra.raikiri.unlocked", ChatFormatting.AQUA,
                ParticleTypes.ELECTRIC_SPARK, SoundEvents.LIGHTNING_BOLT_THUNDER, 1.28F);
        tag.remove(TAG_RAIKIRI_CHARGED_AT);
        tag.remove(TAG_RAIKIRI_LAST_SA);
    }

    private static void tryUnlockShoshin(ServerPlayer player, ItemStack blade) {
        if (!isShoshinCandidate(blade)
                || isShoshinUnlocked(blade)) {
            return;
        }
        CompoundTag tag = blade.getOrCreateTag();
        // Exact starter builds from older versions are adopted on first use,
        // while newly crafted iron katana already carry the origin marker.
        tag.putBoolean(TAG_SHOSHIN_ORIGIN, true);
        int qualifiedKills = Math.min(
                SHOSHIN_KILLS,
                tag.getInt(TAG_SHOSHIN_KILLS) + 1);
        tag.putInt(TAG_SHOSHIN_KILLS, qualifiedKills);
        if (qualifiedKills < SHOSHIN_KILLS) return;
        unlock(player, blade, TAG_SHOSHIN_UNLOCKED, SHOSHIN_ADVANCEMENT,
                "message.blade_tetra.shoshin.unlocked", ChatFormatting.YELLOW,
                ParticleTypes.COMPOSTER, SoundEvents.PLAYER_LEVELUP, 1.18F);
    }

    private static boolean isRaikiriCandidate(ItemStack stack) {
        return stack.getItem() instanceof ModularSlashBladeItem
                && SayaPresetSkin.fromStack(stack)
                        == SayaPresetSkin.PURPLE_LIGHTNING
                && ConductiveBladeHandler.conductivityScore(stack) > 0
                && SoulLegacyState.hasSingleInscription(
                        stack, SoulLegacyState.Legacy.RAIKIRI);
    }

    private static boolean isShoshinCandidate(ItemStack stack) {
        if (!(stack.getItem() instanceof ModularSlashBladeItem)) return false;
        CompoundTag tag = stack.getTag();
        if (tag == null) return false;
        MaterialAppearance appearance = MaterialAppearance.fromStack(stack);
        return ComponentEffectResolver.hasModule(
                        stack,
                        ModularSlashBladeItem.BLADE_SLOT,
                        ModularSlashBladeItem.BLADE_MODULE)
                && ComponentEffectResolver.hasModule(
                        stack,
                        ModularSlashBladeItem.TSUKA_SLOT,
                        ModularSlashBladeItem.TSUKA_MODULE)
                && ComponentEffectResolver.hasModule(
                        stack,
                        ModularSlashBladeItem.TSUBA_SLOT,
                        ModularSlashBladeItem.TSUBA_MODULE)
                && ComponentEffectResolver.hasModule(
                        stack,
                        ModularSlashBladeItem.SAYA_SLOT,
                        ModularSlashBladeItem.SAYA_MODULE)
                && ComponentEffectResolver.hasModule(
                        stack,
                        ModularSlashBladeItem.HABAKI_SLOT,
                        ModularSlashBladeItem.HABAKI_MODULE)
                && ComponentEffectResolver.hasModule(
                        stack,
                        ModularSlashBladeItem.KASHIRA_SLOT,
                        ModularSlashBladeItem.KASHIRA_MODULE)
                && "iron".equals(appearance.blade())
                && "stick".equals(appearance.tsuka())
                && "iron".equals(appearance.tsuba())
                && "oak".equals(appearance.saya())
                && "iron".equals(appearance.habaki())
                && "iron".equals(appearance.kashira())
                && !hasSpecializedForging(stack)
                && !tag.contains(ModularSlashBladeItem.INSCRIPTION_SLOT, Tag.TAG_STRING)
                && !tag.contains(ModularSlashBladeItem.FULLER_SLOT, Tag.TAG_STRING);
    }

    private static boolean hasSpecializedForging(ItemStack stack) {
        return ForgingImprovements.has(
                        stack,
                        ModularSlashBladeItem.BLADE_SLOT,
                        ForgingImprovements.KOBUSE)
                || ForgingImprovements.has(
                        stack,
                        ModularSlashBladeItem.BLADE_SLOT,
                        ForgingImprovements.SANMAI)
                || ForgingImprovements.has(
                        stack,
                        ModularSlashBladeItem.BLADE_SLOT,
                        ForgingImprovements.SHIHOZUME)
                || ForgingImprovements.has(
                        stack,
                        ModularSlashBladeItem.BLADE_SLOT,
                        ForgingImprovements.HAMAGURI)
                || ForgingImprovements.has(
                        stack,
                        ModularSlashBladeItem.BLADE_SLOT,
                        ForgingImprovements.HIRA)
                || ForgingImprovements.has(
                        stack,
                        ModularSlashBladeItem.BLADE_SLOT,
                        ForgingImprovements.USUBA)
                || ForgingImprovements.has(
                        stack,
                        ModularSlashBladeItem.TSUKA_SLOT,
                        ForgingImprovements.NAKAGO_FIT)
                || ForgingImprovements.has(
                        stack,
                        ModularSlashBladeItem.TSUKA_SLOT,
                        ForgingImprovements.RIGID_ASSEMBLY);
    }

    private static void unlock(
            ServerPlayer player,
            ItemStack blade,
            String flag,
            ResourceLocation advancement,
            String message,
            ChatFormatting color,
            net.minecraft.core.particles.ParticleOptions particle,
            net.minecraft.sounds.SoundEvent sound,
            float pitch) {
        blade.getOrCreateTag().putBoolean(flag, true);
        ServerLevel level = player.serverLevel();
        level.sendParticles(particle,
                player.getX(), player.getY() + 1.0D, player.getZ(),
                42, 0.75D, 0.65D, 0.75D, 0.035D);
        level.playSound(null, player.blockPosition(), sound, SoundSource.PLAYERS,
                0.9F, pitch);
        player.displayClientMessage(
                Component.translatable(message).withStyle(color), false);
        award(player, advancement);
    }

    private static void award(ServerPlayer player, ResourceLocation id) {
        var advancement = player.server.getAdvancements().getAdvancement(id);
        if (advancement == null) return;
        var progress = player.getAdvancements().getOrStartProgress(advancement);
        for (String criterion : progress.getRemainingCriteria()) {
            player.getAdvancements().award(advancement, criterion);
        }
    }

    private static boolean hasFlag(ItemStack stack, String key) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.getBoolean(key);
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

    private static boolean containsAny(String value, String... needles) {
        for (String needle : needles) {
            if (value.contains(needle)) return true;
        }
        return false;
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(BladeTetra.MOD_ID, path);
    }

    private BladeLegacyEasterEggs() {
    }
}
