package dev.bladetetra.easteregg;

import dev.bladetetra.BladeTetra;
import dev.bladetetra.combat.BladeStyle;
import dev.bladetetra.combat.StyleResolver;
import dev.bladetetra.config.GameplayConfig;
import dev.bladetetra.item.ModularSlashBladeItem;
import dev.bladetetra.visual.SayaPresetSkin;
import mods.flammpfeil.slashblade.SlashBlade;
import mods.flammpfeil.slashblade.entity.EntityAbstractSummonedSword;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/** Cherry-grove Rengeki ritual and petal-sword legacy. */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SenbonzakuraAwakening {
    public static final String TAG_UNLOCKED =
            "blade_tetra_senbonzakura_unlocked";
    public static final String SAKURA_VARIANT =
            "awakened_soul_inscription/sakura_crystal";

    private static final String TAG_RITUAL_HITS =
            "blade_tetra_senbonzakura_ritual_hits";
    private static final String TAG_LAST_RITUAL_HIT =
            "blade_tetra_senbonzakura_last_ritual_hit";
    private static final String TAG_RITUAL_READY_UNTIL =
            "blade_tetra_senbonzakura_ready_until";
    private static final String TAG_DEBUG_LAST_HIT_UNTIL =
            "blade_tetra_senbonzakura_debug_last_hit_until";
    private static final String TAG_LAST_SA =
            "blade_tetra_senbonzakura_last_sa";
    private static final String TAG_PETAL_HITS =
            "blade_tetra_senbonzakura_petal_hits";
    private static final String TAG_PETAL_MARKS =
            "blade_tetra_senbonzakura_petal_marks";
    private static final String TAG_SA_WINDOW =
            "blade_tetra_senbonzakura_sa_window";
    private static final String TAG_COOLDOWN_UNTIL =
            "blade_tetra_senbonzakura_cooldown_until";

    private static final int REQUIRED_RITUAL_HITS = 16;
    private static final int RITUAL_CHAIN_WINDOW = 40;
    private static final int RITUAL_READY_WINDOW = 1200;
    private static final int SA_KILL_WINDOW = 100;
    private static final int HITS_PER_MARK = 4;
    private static final int MAX_MARKS = 3;
    private static final int SA_HIT_WINDOW = 30;
    private static final int COOLDOWN_TICKS = 160;
    private static final double SWORD_DAMAGE = 1.5D;
    private static final int SWORD_COLOR = 0xFFB7D5;
    private static final ResourceLocation ADVANCEMENT = id("senbonzakura");

    public static boolean isUnlocked(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.getBoolean(TAG_UNLOCKED);
    }

    public static boolean hasSakuraInscription(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null
                || !ModularSlashBladeItem.AWAKENED_SOUL_INSCRIPTION_MODULE.equals(
                tag.getString(ModularSlashBladeItem.INSCRIPTION_SLOT))) {
            return false;
        }
        String variantKey = ModularSlashBladeItem.AWAKENED_SOUL_INSCRIPTION_MODULE
                + "_material";
        return tag.contains(variantKey, Tag.TAG_STRING)
                && SAKURA_VARIANT.equals(tag.getString(variantKey));
    }

    public static boolean isCandidate(ItemStack stack) {
        return stack.getItem() instanceof ModularSlashBladeItem
                && StyleResolver.resolve(stack) == BladeStyle.RENGEKI
                && SayaPresetSkin.fromStack(stack) == SayaPresetSkin.SAKURA
                && hasSakuraInscription(stack);
    }

    public static boolean isActive(ItemStack stack) {
        return isUnlocked(stack) && isCandidate(stack) && !isBroken(stack);
    }

    public static int petalMarks(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? 0
                : Math.max(0, Math.min(MAX_MARKS, tag.getInt(TAG_PETAL_MARKS)));
    }

    public static void appendTooltip(ItemStack stack, List<Component> tooltip) {
        if (isActive(stack)) {
            tooltip.add(Component.translatable(
                            "tooltip.blade_tetra.senbonzakura.awakened")
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
            tooltip.add(Component.translatable(
                            "tooltip.blade_tetra.senbonzakura.marks",
                            petalMarks(stack), MAX_MARKS)
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
        } else if (isUnlocked(stack)) {
            tooltip.add(Component.translatable(
                            "tooltip.blade_tetra.senbonzakura.dormant")
                    .withStyle(ChatFormatting.DARK_PURPLE));
        }
    }

    /** Keeps the uninterrupted ritual honest when the candidate leaves the main hand. */
    public static void trackInventoryState(
            ItemStack stack,
            net.minecraft.world.level.Level level,
            Entity holder,
            boolean selected) {
        if (level.isClientSide()
                || selected
                || !(holder instanceof ServerPlayer)
                || !isCandidate(stack)
                || isUnlocked(stack)) {
            return;
        }
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(TAG_RITUAL_HITS, Tag.TAG_INT)) {
            return;
        }
        if (tag.getLong(TAG_DEBUG_LAST_HIT_UNTIL) < level.getGameTime()) {
            clearRitualProgress(stack);
        }
    }

    @SubscribeEvent
    public static void onBladeHit(SlashBladeEvent.HitEvent event) {
        if (!GameplayConfig.ENABLE_EASTER_EGG_UNLOCKS.get()
                || event.getUser().level().isClientSide()
                || !(event.getUser() instanceof ServerPlayer player)) {
            return;
        }
        ItemStack blade = event.getBlade();
        if (!(blade.getItem() instanceof ModularSlashBladeItem)) {
            return;
        }
        long now = player.level().getGameTime();

        if (!isUnlocked(blade) && isCandidate(blade) && !isBroken(blade)) {
            advanceRitual(player, blade, now);
        }

        if (!isActive(blade)) {
            return;
        }
        CompoundTag tag = blade.getOrCreateTag();
        if (StyleResolver.resolve(blade) == BladeStyle.RENGEKI) {
            int hits = tag.getInt(TAG_PETAL_HITS) + 1;
            if (hits >= HITS_PER_MARK) {
                hits = 0;
                int marks = Math.min(MAX_MARKS, petalMarks(blade) + 1);
                if (marks != petalMarks(blade)) {
                    tag.putInt(TAG_PETAL_MARKS, marks);
                    player.displayClientMessage(Component.translatable(
                                    "message.blade_tetra.senbonzakura.mark",
                                    marks, MAX_MARKS)
                            .withStyle(ChatFormatting.LIGHT_PURPLE), true);
                }
            }
            tag.putInt(TAG_PETAL_HITS, hits);
        }

        if (tag.getLong(TAG_SA_WINDOW) >= now
                && tag.getLong(TAG_COOLDOWN_UNTIL) <= now
                && petalMarks(blade) > 0) {
            releasePetalSwords(
                    player,
                    event.getTarget(),
                    blade,
                    petalMarks(blade));
        }
    }

    @SubscribeEvent
    public static void onSlashArt(SlashBladeEvent.PerformSlashArtEvent event) {
        if (!GameplayConfig.ENABLE_EASTER_EGG_UNLOCKS.get()
                || event.getEntityLiving().level().isClientSide()
                || event.getType() != SlashArts.ArtsType.Success
                || !(event.getEntityLiving() instanceof ServerPlayer player)) {
            return;
        }
        ItemStack blade = player.getMainHandItem();
        long now = player.level().getGameTime();
        CompoundTag tag = blade.getOrCreateTag();

        if (!isUnlocked(blade)
                && isCandidate(blade)
                && tag.getLong(TAG_RITUAL_READY_UNTIL) >= now
                && isRitualEnvironment(player)) {
            tag.putLong(TAG_LAST_SA, now);
        }

        if (isActive(blade)
                && petalMarks(blade) > 0
                && tag.getLong(TAG_COOLDOWN_UNTIL) <= now) {
            tag.putLong(TAG_SA_WINDOW, now + SA_HIT_WINDOW);
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!GameplayConfig.ENABLE_EASTER_EGG_UNLOCKS.get()
                || !(event.getSource().getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ItemStack blade = player.getMainHandItem();
        if (!isCandidate(blade) || isUnlocked(blade) || isBroken(blade)) {
            return;
        }
        CompoundTag tag = blade.getOrCreateTag();
        long now = player.level().getGameTime();
        long lastSa = tag.getLong(TAG_LAST_SA);
        if (tag.getLong(TAG_RITUAL_READY_UNTIL) < now
                || lastSa <= 0L
                || now - lastSa > SA_KILL_WINDOW
                || !isRitualEnvironment(player)) {
            return;
        }
        awaken(player, blade);
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ItemStack blade = player.getMainHandItem();
        if (isCandidate(blade) && !isUnlocked(blade)) {
            clearRitualProgress(blade);
            player.displayClientMessage(Component.translatable(
                            "message.blade_tetra.senbonzakura.ritual_broken")
                    .withStyle(ChatFormatting.GRAY), true);
        }
    }

    private static void advanceRitual(
            ServerPlayer player,
            ItemStack blade,
            long now) {
        CompoundTag tag = blade.getOrCreateTag();
        long lastHit = tag.getLong(TAG_LAST_RITUAL_HIT);
        boolean debugWindow = tag.getLong(TAG_DEBUG_LAST_HIT_UNTIL) >= now;
        int hits = debugWindow
                || lastHit > 0L && now - lastHit <= RITUAL_CHAIN_WINDOW
                ? tag.getInt(TAG_RITUAL_HITS) + 1
                : 1;
        tag.remove(TAG_DEBUG_LAST_HIT_UNTIL);
        hits = Math.min(REQUIRED_RITUAL_HITS, hits);
        tag.putInt(TAG_RITUAL_HITS, hits);
        tag.putLong(TAG_LAST_RITUAL_HIT, now);
        if (hits == REQUIRED_RITUAL_HITS
                && tag.getLong(TAG_RITUAL_READY_UNTIL) < now) {
            tag.putLong(TAG_RITUAL_READY_UNTIL, now + RITUAL_READY_WINDOW);
            player.displayClientMessage(Component.translatable(
                            "message.blade_tetra.senbonzakura.ritual_ready")
                    .withStyle(ChatFormatting.LIGHT_PURPLE), false);
            player.serverLevel().sendParticles(
                    ParticleTypes.CHERRY_LEAVES,
                    player.getX(), player.getY() + 1.0D, player.getZ(),
                    18, 0.55D, 0.65D, 0.55D, 0.02D);
        }
    }

    private static void awaken(ServerPlayer player, ItemStack blade) {
        blade.getOrCreateTag().putBoolean(TAG_UNLOCKED, true);
        clearRitualProgress(blade);
        blade.getOrCreateTag().putInt(TAG_PETAL_HITS, 0);
        blade.getOrCreateTag().putInt(TAG_PETAL_MARKS, 0);
        ServerLevel level = player.serverLevel();
        level.sendParticles(
                ParticleTypes.CHERRY_LEAVES,
                player.getX(), player.getY() + 1.1D, player.getZ(),
                72, 1.15D, 0.9D, 1.15D, 0.035D);
        level.sendParticles(
                ParticleTypes.END_ROD,
                player.getX(), player.getY() + 1.0D, player.getZ(),
                24, 0.75D, 0.75D, 0.75D, 0.025D);
        level.playSound(
                null,
                player.blockPosition(),
                SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.PLAYERS,
                1.0F,
                1.35F);
        player.displayClientMessage(Component.translatable(
                        "message.blade_tetra.senbonzakura.awakened")
                .withStyle(ChatFormatting.LIGHT_PURPLE), false);
        award(player, ADVANCEMENT);
    }

    private static void releasePetalSwords(
            ServerPlayer player,
            LivingEntity target,
            ItemStack blade,
            int marks) {
        CompoundTag tag = blade.getOrCreateTag();
        long now = player.level().getGameTime();
        tag.putInt(TAG_PETAL_MARKS, 0);
        tag.putInt(TAG_PETAL_HITS, 0);
        tag.remove(TAG_SA_WINDOW);
        tag.putLong(TAG_COOLDOWN_UNTIL, now + COOLDOWN_TICKS);

        ServerLevel level = player.serverLevel();
        int count = marks * 2;
        Vec3 center = target.getBoundingBox().getCenter();
        for (int index = 0; index < count; index++) {
            double angle = Math.PI * 2.0D * index / count;
            Vec3 start = center.add(
                    Math.cos(angle) * 2.1D,
                    3.6D + index * 0.18D,
                    Math.sin(angle) * 2.1D);
            Vec3 direction = center.subtract(start).normalize();
            EntityAbstractSummonedSword sword =
                    new EntityAbstractSummonedSword(
                            SlashBlade.RegistryEvents.SummonedSword,
                            level);
            sword.setPos(start.x, start.y, start.z);
            sword.setDamage(SWORD_DAMAGE);
            sword.setOwner(player);
            sword.setShooter(player);
            sword.setColor(SWORD_COLOR);
            sword.setRoll(index * (360.0F / count));
            sword.setDelay(index * 2);
            sword.shoot(
                    direction.x,
                    direction.y,
                    direction.z,
                    2.35F,
                    0.0F);
            level.addFreshEntity(sword);
        }
        level.sendParticles(
                ParticleTypes.CHERRY_LEAVES,
                center.x, center.y, center.z,
                14 + count * 2, 0.8D, 0.9D, 0.8D, 0.035D);
        level.playSound(
                null,
                target.blockPosition(),
                SoundEvents.AMETHYST_BLOCK_RESONATE,
                SoundSource.PLAYERS,
                0.75F,
                1.55F);
    }

    private static boolean isRitualEnvironment(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        long dayTime = Math.floorMod(level.getDayTime(), 24000L);
        return level.getBiome(player.blockPosition()).is(Biomes.CHERRY_GROVE)
                && dayTime <= 1500L;
    }

    public static boolean prepareForDebug(
            ItemStack blade,
            long now) {
        if (!isCandidate(blade)) {
            return false;
        }
        CompoundTag tag = blade.getOrCreateTag();
        tag.putInt(TAG_RITUAL_HITS, REQUIRED_RITUAL_HITS - 1);
        tag.putLong(TAG_LAST_RITUAL_HIT, now);
        tag.putLong(TAG_DEBUG_LAST_HIT_UNTIL, now + RITUAL_READY_WINDOW);
        tag.remove(TAG_RITUAL_READY_UNTIL);
        tag.remove(TAG_LAST_SA);
        return true;
    }

    public static boolean unlockForDebug(ItemStack blade) {
        if (!isCandidate(blade)) {
            return false;
        }
        blade.getOrCreateTag().putBoolean(TAG_UNLOCKED, true);
        blade.getOrCreateTag().putInt(TAG_PETAL_MARKS, MAX_MARKS);
        clearRitualProgress(blade);
        return true;
    }

    public static void resetForDebug(ItemStack blade) {
        CompoundTag tag = blade.getTag();
        if (tag == null) {
            return;
        }
        tag.remove(TAG_UNLOCKED);
        tag.remove(TAG_PETAL_HITS);
        tag.remove(TAG_PETAL_MARKS);
        tag.remove(TAG_SA_WINDOW);
        tag.remove(TAG_COOLDOWN_UNTIL);
        clearRitualProgress(blade);
    }

    private static void clearRitualProgress(ItemStack blade) {
        CompoundTag tag = blade.getTag();
        if (tag == null) {
            return;
        }
        tag.remove(TAG_RITUAL_HITS);
        tag.remove(TAG_LAST_RITUAL_HIT);
        tag.remove(TAG_RITUAL_READY_UNTIL);
        tag.remove(TAG_DEBUG_LAST_HIT_UNTIL);
        tag.remove(TAG_LAST_SA);
    }

    private static boolean isBroken(ItemStack stack) {
        return stack.getCapability(ModularSlashBladeItem.BLADESTATE)
                .map(state -> state.isBroken())
                .orElse(false);
    }

    private static void award(ServerPlayer player, ResourceLocation id) {
        var advancement = player.server.getAdvancements().getAdvancement(id);
        if (advancement == null) {
            return;
        }
        var progress = player.getAdvancements().getOrStartProgress(advancement);
        for (String criterion : progress.getRemainingCriteria()) {
            player.getAdvancements().award(advancement, criterion);
        }
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(BladeTetra.MOD_ID, path);
    }

    private SenbonzakuraAwakening() {
    }
}
