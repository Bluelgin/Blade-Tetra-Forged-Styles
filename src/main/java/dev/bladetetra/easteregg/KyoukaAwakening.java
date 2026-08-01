package dev.bladetetra.easteregg;

import dev.bladetetra.BladeTetra;
import dev.bladetetra.combat.BladeStyle;
import dev.bladetetra.combat.ModComboStates;
import dev.bladetetra.combat.StyleResolver;
import dev.bladetetra.config.GameplayConfig;
import dev.bladetetra.item.ModularSlashBladeItem;
import dev.bladetetra.visual.MaterialAppearance;
import dev.bladetetra.visual.SayaPresetSkin;
import mods.flammpfeil.slashblade.event.SlashBladeEvent;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/** Rain-soaked Iaido ritual and delayed reflected cut for Kyouka Suigetsu. */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class KyoukaAwakening {
    public static final String TAG_UNLOCKED = "blade_tetra_kyouka_unlocked";

    private static final String TAG_STREAK = "blade_tetra_kyouka_streak";
    private static final String TAG_LAST_HIT = "blade_tetra_kyouka_last_hit";
    private static final String TAG_LAST_TARGET = "blade_tetra_kyouka_last_target";
    private static final String TAG_MIRROR_COOLDOWN = "blade_tetra_kyouka_mirror_cooldown";
    private static final String PROUDSOUL_VARIANT =
            "awakened_soul_inscription/proudsoul_sphere";
    private static final int REQUIRED_DRAWS = 3;
    private static final int STREAK_WINDOW = 200;
    private static final int KILL_WINDOW = 8;
    private static final ResourceLocation ADVANCEMENT =
            ResourceLocation.fromNamespaceAndPath(BladeTetra.MOD_ID, "kyouka");

    private static final List<PendingMirrorSlash> PENDING = new ArrayList<>();

    public static boolean isUnlocked(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.getBoolean(TAG_UNLOCKED);
    }

    /** The ordinary proudsoul sphere becomes Kyouka's host after the ritual. */
    public static boolean hasMirrorInscription(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null
                || !ModularSlashBladeItem.AWAKENED_SOUL_INSCRIPTION_MODULE.equals(
                tag.getString(ModularSlashBladeItem.INSCRIPTION_SLOT))) {
            return false;
        }
        String variantKey = ModularSlashBladeItem.AWAKENED_SOUL_INSCRIPTION_MODULE
                + "_material";
        return tag.contains(variantKey, Tag.TAG_STRING)
                && PROUDSOUL_VARIANT.equals(tag.getString(variantKey));
    }

    public static boolean isActive(ItemStack stack) {
        return isUnlocked(stack) && hasMirrorInscription(stack) && !isBroken(stack);
    }

    public static boolean prepareForDebug(ItemStack stack, long now) {
        if (!isAwakeningCandidate(stack)) return false;
        CompoundTag tag = stack.getOrCreateTag();
        tag.putInt(TAG_STREAK, REQUIRED_DRAWS - 1);
        tag.putLong(TAG_LAST_HIT, now);
        tag.remove(TAG_LAST_TARGET);
        return true;
    }

    public static boolean unlockForDebug(ItemStack stack) {
        if (!hasMirrorInscription(stack)) return false;
        stack.getOrCreateTag().putBoolean(TAG_UNLOCKED, true);
        clearRitualProgress(stack);
        return true;
    }

    public static boolean resetForDebug(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null) return false;
        tag.remove(TAG_UNLOCKED);
        tag.remove(TAG_MIRROR_COOLDOWN);
        clearRitualProgress(stack);
        return true;
    }

    public static void appendTooltip(ItemStack stack, List<Component> tooltip) {
        if (isActive(stack)) {
            tooltip.add(Component.translatable("tooltip.blade_tetra.kyouka.awakened")
                    .withStyle(ChatFormatting.AQUA));
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
        if (!isAwakeningCandidate(blade)
                || isUnlocked(blade)
                || !ModComboStates.isIaidoDraw(
                event.getSlashBladeState().getComboSeq())
                || !isRitualEnvironment(player)) {
            return;
        }

        CompoundTag tag = blade.getOrCreateTag();
        long now = player.level().getGameTime();
        int streak = now - tag.getLong(TAG_LAST_HIT) <= STREAK_WINDOW
                ? tag.getInt(TAG_STREAK) : 0;
        tag.putInt(TAG_STREAK, Math.min(REQUIRED_DRAWS, streak + 1));
        tag.putLong(TAG_LAST_HIT, now);
        tag.putUUID(TAG_LAST_TARGET, event.getTarget().getUUID());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingHurt(LivingHurtEvent event) {
        if (event.getEntity() instanceof ServerPlayer wounded) {
            ItemStack blade = wounded.getMainHandItem();
            if (isAwakeningCandidate(blade) && !isUnlocked(blade)) {
                clearRitualProgress(blade);
            }
        }

        if (!(event.getSource().getEntity() instanceof ServerPlayer attacker)
                || event.getSource().getDirectEntity() != attacker
                || attacker.level().isClientSide()
                || event.getAmount() <= 0.0F) {
            return;
        }
        ItemStack blade = attacker.getMainHandItem();
        if (!isActive(blade)
                || StyleResolver.resolve(blade) != BladeStyle.IAIDO) {
            return;
        }
        ResourceLocation combo = blade.getCapability(ModularSlashBladeItem.BLADESTATE)
                .map(state -> state.getComboSeq()).orElse(null);
        if (!ModComboStates.isIaidoDraw(combo)
                && !ModComboStates.isIaidoFinish(combo)) {
            return;
        }

        long now = attacker.level().getGameTime();
        CompoundTag tag = blade.getOrCreateTag();
        if (tag.getLong(TAG_MIRROR_COOLDOWN) > now) return;
        tag.putLong(TAG_MIRROR_COOLDOWN,
                now + GameplayConfig.MIRROR_SLASH_COOLDOWN_TICKS.get());

        float damage = Math.min(
                GameplayConfig.MIRROR_SLASH_DAMAGE_CAP.get().floatValue(),
                event.getAmount()
                        * GameplayConfig.MIRROR_SLASH_DAMAGE_RATIO.get().floatValue());
        PENDING.add(new PendingMirrorSlash(
                attacker.serverLevel().dimension().location(),
                attacker.getUUID(),
                event.getEntity().getUUID(),
                now + GameplayConfig.MIRROR_SLASH_DELAY_TICKS.get(),
                damage));
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!GameplayConfig.ENABLE_EASTER_EGG_UNLOCKS.get()
                || !(event.getSource().getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ItemStack blade = player.getMainHandItem();
        if (!isAwakeningCandidate(blade) || isUnlocked(blade)
                || !isRitualEnvironment(player)) {
            return;
        }
        CompoundTag tag = blade.getOrCreateTag();
        long now = player.level().getGameTime();
        if (tag.getInt(TAG_STREAK) < REQUIRED_DRAWS
                || now - tag.getLong(TAG_LAST_HIT) > KILL_WINDOW
                || !tag.hasUUID(TAG_LAST_TARGET)
                || !event.getEntity().getUUID().equals(tag.getUUID(TAG_LAST_TARGET))) {
            return;
        }
        awaken(player, blade);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || PENDING.isEmpty()) return;
        MinecraftServer server = event.getServer();
        Iterator<PendingMirrorSlash> iterator = PENDING.iterator();
        while (iterator.hasNext()) {
            PendingMirrorSlash pending = iterator.next();
            ServerLevel level = server.getLevel(net.minecraft.resources.ResourceKey.create(
                    net.minecraft.core.registries.Registries.DIMENSION,
                    pending.dimension()));
            if (level == null) {
                iterator.remove();
                continue;
            }
            if (level.getGameTime() < pending.dueTick()) continue;
            iterator.remove();
            executeMirrorSlash(level, pending);
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        ResourceLocation dimension = level.dimension().location();
        PENDING.removeIf(pending -> pending.dimension().equals(dimension));
    }

    private static void executeMirrorSlash(
            ServerLevel level,
            PendingMirrorSlash pending) {
        ServerPlayer attacker = level.getServer().getPlayerList()
                .getPlayer(pending.attacker());
        if (attacker == null || attacker.level() != level
                || !isActive(attacker.getMainHandItem())) {
            return;
        }
        LivingEntity target = level.getEntity(pending.target()) instanceof LivingEntity living
                ? living : null;
        if (target == null || !target.isAlive()) {
            target = findReflectedTarget(level, attacker);
        }
        if (target == null || attacker.distanceToSqr(target) > 256.0D) return;

        target.invulnerableTime = 0;
        target.hurt(level.damageSources().magic(), pending.damage());
        double y = target.getY() + target.getBbHeight() * 0.55D;
        level.sendParticles(ParticleTypes.END_ROD,
                target.getX(), y, target.getZ(),
                9, target.getBbWidth() * 0.45D, 0.18D,
                target.getBbWidth() * 0.45D, 0.035D);
        level.sendParticles(ParticleTypes.SPLASH,
                target.getX(), target.getY() + 0.08D, target.getZ(),
                12, 0.65D, 0.02D, 0.65D, 0.02D);
        level.playSound(null, target.blockPosition(),
                SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS,
                0.65F, 1.45F);
    }

    private static LivingEntity findReflectedTarget(
            ServerLevel level,
            ServerPlayer attacker) {
        return level.getEntitiesOfClass(
                        LivingEntity.class,
                        new AABB(attacker.blockPosition()).inflate(6.0D),
                        entity -> entity instanceof Enemy
                                && entity.isAlive()
                                && entity != attacker
                                && !attacker.isAlliedTo(entity))
                .stream()
                .min(Comparator.comparingDouble(attacker::distanceToSqr))
                .orElse(null);
    }

    private static boolean isAwakeningCandidate(ItemStack stack) {
        if (!(stack.getItem() instanceof ModularSlashBladeItem)
                || StyleResolver.resolve(stack) != BladeStyle.IAIDO
                || SayaPresetSkin.fromStack(stack) != SayaPresetSkin.KYOUKA
                || !hasMirrorInscription(stack)
                || isBroken(stack)) {
            return false;
        }
        String material = MaterialAppearance.fromStack(stack).blade();
        return containsAny(material,
                "amethyst", "quartz", "glass", "crystal", "diamond",
                "opal", "sapphire", "prism", "mirror");
    }

    private static boolean isRitualEnvironment(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        // The player's feet are deliberately inside water during this ritual,
        // so checking weather or sky visibility at their feet is unreliable.
        // Kyouka needs an open night reflected by water, not rain or one exact
        // moon phase. Check just above eye level for a clear sky instead.
        net.minecraft.core.BlockPos skyCheck = net.minecraft.core.BlockPos.containing(
                player.getX(), player.getEyeY(), player.getZ()).above();
        return level.isNight()
                && level.canSeeSky(skyCheck)
                && player.isInWater();
    }

    private static void awaken(ServerPlayer player, ItemStack blade) {
        blade.getOrCreateTag().putBoolean(TAG_UNLOCKED, true);
        clearRitualProgress(blade);
        ServerLevel level = player.serverLevel();
        level.sendParticles(ParticleTypes.END_ROD,
                player.getX(), player.getY() + 1.0D, player.getZ(),
                48, 1.0D, 0.65D, 1.0D, 0.018D);
        level.sendParticles(ParticleTypes.SPLASH,
                player.getX(), player.getY() + 0.1D, player.getZ(),
                36, 1.25D, 0.05D, 1.25D, 0.025D);
        level.playSound(null, player.blockPosition(),
                SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS,
                1.0F, 1.15F);
        player.displayClientMessage(
                Component.translatable("message.blade_tetra.kyouka.awakened")
                        .withStyle(ChatFormatting.AQUA), false);

        var advancement = player.server.getAdvancements().getAdvancement(ADVANCEMENT);
        if (advancement != null) {
            var progress = player.getAdvancements().getOrStartProgress(advancement);
            for (String criterion : progress.getRemainingCriteria()) {
                player.getAdvancements().award(advancement, criterion);
            }
        }
    }

    private static void clearRitualProgress(ItemStack blade) {
        CompoundTag tag = blade.getOrCreateTag();
        tag.remove(TAG_STREAK);
        tag.remove(TAG_LAST_HIT);
        tag.remove(TAG_LAST_TARGET);
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

    private record PendingMirrorSlash(
            ResourceLocation dimension,
            UUID attacker,
            UUID target,
            long dueTick,
            float damage) {
    }

    private KyoukaAwakening() {
    }
}
