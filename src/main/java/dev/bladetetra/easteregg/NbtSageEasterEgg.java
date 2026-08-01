package dev.bladetetra.easteregg;

import dev.bladetetra.BladeTetra;
import dev.bladetetra.item.ModularSlashBladeItem;
import dev.bladetetra.config.GameplayConfig;
import dev.bladetetra.lore.SmithingLore;
import dev.bladetetra.visual.MaterialAppearance;
import dev.bladetetra.visual.SayaBannerSkin;
import dev.bladetetra.visual.SayaPresetSkin;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
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

import java.util.List;

/** A bounded, milestone-based meta secret; raw NBT size never contributes. */
public final class NbtSageEasterEgg {
    public static final String TAG_UNLOCKED = "blade_tetra_nbt_sage_unlocked";

    private static final String TAG_MODULE_MASK = "blade_tetra_nbt_sage_modules";
    private static final String TAG_MATERIALS = "blade_tetra_nbt_sage_materials";
    private static final String TAG_USED_SAYA_SKIN = "blade_tetra_nbt_sage_saya_skin";
    private static final String TAG_USED_INSCRIPTION =
            "blade_tetra_nbt_sage_inscription";
    private static final int REQUIRED_MATERIALS = 3;
    private static final int REQUIRED_HONING = 5;
    private static final int ALL_MODULES = 0xff;
    private static final List<String> MODULE_SLOTS = List.of(
            ModularSlashBladeItem.BLADE_SLOT,
            ModularSlashBladeItem.TSUKA_SLOT,
            ModularSlashBladeItem.TSUBA_SLOT,
            ModularSlashBladeItem.SAYA_SLOT,
            ModularSlashBladeItem.HABAKI_SLOT,
            ModularSlashBladeItem.KASHIRA_SLOT,
            ModularSlashBladeItem.FULLER_SLOT,
            ModularSlashBladeItem.INSCRIPTION_SLOT);
    private static final ResourceLocation ADVANCEMENT =
            ResourceLocation.fromNamespaceAndPath(BladeTetra.MOD_ID, "nbt_sage");

    public static boolean isUnlocked(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.getBoolean(TAG_UNLOCKED);
    }

    public static void trackInventoryState(
            ItemStack stack,
            Level level,
            Entity holder) {
        if (level.isClientSide
                || !GameplayConfig.ENABLE_EASTER_EGG_UNLOCKS.get()
                || !(holder instanceof ServerPlayer player)
                || !(stack.getItem() instanceof ModularSlashBladeItem)) {
            return;
        }

        CompoundTag tag = stack.getOrCreateTag();
        recordModules(tag);
        recordMaterial(tag, MaterialAppearance.fromStack(stack).blade());
        if (SayaPresetSkin.fromStack(stack).present()
                || SayaBannerSkin.fromStack(stack).present()) {
            tag.putBoolean(TAG_USED_SAYA_SKIN, true);
        }
        if (tag.contains(ModularSlashBladeItem.INSCRIPTION_SLOT, Tag.TAG_STRING)) {
            tag.putBoolean(TAG_USED_INSCRIPTION, true);
        }

        if (!isUnlocked(stack) && qualifies(stack, player)) {
            unlock(stack, player);
        }
    }

    public static void appendTooltip(ItemStack stack, List<Component> tooltip) {
        if (isUnlocked(stack)) {
            tooltip.add(Component.translatable("tooltip.blade_tetra.nbt_sage.unlocked")
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
        }
    }

    public static boolean prepareForDebug(ItemStack stack) {
        if (!(stack.getItem() instanceof ModularSlashBladeItem)) return false;
        CompoundTag tag = stack.getOrCreateTag();
        tag.putInt(TAG_MODULE_MASK, ALL_MODULES);
        ListTag materials = new ListTag();
        materials.add(StringTag.valueOf("iron"));
        materials.add(StringTag.valueOf("amethyst"));
        materials.add(StringTag.valueOf("copper"));
        tag.put(TAG_MATERIALS, materials);
        tag.putBoolean(TAG_USED_SAYA_SKIN, true);
        tag.putBoolean(TAG_USED_INSCRIPTION, true);
        tag.putInt("honing_count", REQUIRED_HONING);
        tag.putBoolean(BladeLegacyEasterEggs.TAG_BAIREN_UNLOCKED, true);
        tag.putBoolean(BladeLegacyEasterEggs.TAG_BANSHO_UNLOCKED, true);
        return true;
    }

    public static boolean unlockForDebug(ItemStack stack) {
        if (!(stack.getItem() instanceof ModularSlashBladeItem)) return false;
        stack.getOrCreateTag().putBoolean(TAG_UNLOCKED, true);
        return true;
    }

    public static boolean resetForDebug(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null) return false;
        tag.remove(TAG_UNLOCKED);
        tag.remove(TAG_MODULE_MASK);
        tag.remove(TAG_MATERIALS);
        tag.remove(TAG_USED_SAYA_SKIN);
        tag.remove(TAG_USED_INSCRIPTION);
        return true;
    }

    private static boolean qualifies(ItemStack stack, ServerPlayer player) {
        CompoundTag tag = stack.getOrCreateTag();
        return (tag.getInt(TAG_MODULE_MASK) & ALL_MODULES) == ALL_MODULES
                && tag.getList(TAG_MATERIALS, Tag.TAG_STRING).size()
                        >= REQUIRED_MATERIALS
                && tag.getBoolean(TAG_USED_SAYA_SKIN)
                && tag.getBoolean(TAG_USED_INSCRIPTION)
                && tag.getInt("honing_count") >= REQUIRED_HONING
                && BladeLegacyEasterEggs.isBairenUnlocked(stack)
                && BladeLegacyEasterEggs.isBanshoUnlocked(stack)
                && Integer.bitCount(SmithingLore.completionMask(player)) >= 3;
    }

    private static void recordModules(CompoundTag tag) {
        int mask = tag.getInt(TAG_MODULE_MASK);
        for (int i = 0; i < MODULE_SLOTS.size(); i++) {
            if (tag.contains(MODULE_SLOTS.get(i), Tag.TAG_STRING)) {
                mask |= 1 << i;
            }
        }
        tag.putInt(TAG_MODULE_MASK, mask);
    }

    private static void recordMaterial(CompoundTag tag, String material) {
        if (material == null || material.isBlank()) return;
        ListTag materials = tag.getList(TAG_MATERIALS, Tag.TAG_STRING);
        for (int i = 0; i < materials.size(); i++) {
            if (material.equals(materials.getString(i))) return;
        }
        // The criterion stops at three distinct materials, so the history can
        // never become an unbounded NBT list.
        if (materials.size() < REQUIRED_MATERIALS) {
            materials.add(StringTag.valueOf(material));
            tag.put(TAG_MATERIALS, materials);
        }
    }

    private static void unlock(ItemStack stack, ServerPlayer player) {
        stack.getOrCreateTag().putBoolean(TAG_UNLOCKED, true);
        ServerLevel level = player.serverLevel();
        level.sendParticles(ParticleTypes.ENCHANT,
                player.getX(), player.getY() + 1.0D, player.getZ(),
                32, 0.7D, 0.65D, 0.7D, 0.08D);
        level.sendParticles(ParticleTypes.END_ROD,
                player.getX(), player.getY() + 1.0D, player.getZ(),
                8, 0.45D, 0.5D, 0.45D, 0.02D);
        level.playSound(null, player.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE,
                SoundSource.PLAYERS, 0.85F, 0.72F);
        player.displayClientMessage(Component.translatable(
                        "message.blade_tetra.nbt_sage.unlocked")
                .withStyle(ChatFormatting.LIGHT_PURPLE), false);
        var advancement = player.server.getAdvancements().getAdvancement(ADVANCEMENT);
        if (advancement == null) return;
        var progress = player.getAdvancements().getOrStartProgress(advancement);
        for (String criterion : progress.getRemainingCriteria()) {
            player.getAdvancements().award(advancement, criterion);
        }
    }

    private NbtSageEasterEgg() {
    }
}
