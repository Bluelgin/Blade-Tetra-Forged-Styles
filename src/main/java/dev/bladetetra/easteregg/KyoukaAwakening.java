package dev.bladetetra.easteregg;

import dev.bladetetra.BladeTetra;
import dev.bladetetra.combat.BladeStyle;
import dev.bladetetra.combat.ModComboStates;
import dev.bladetetra.combat.BladeTechniqueHandler;
import dev.bladetetra.combat.RaikiriChainHandler;
import dev.bladetetra.combat.StyleResolver;
import dev.bladetetra.config.GameplayConfig;
import dev.bladetetra.item.ModularSlashBladeItem;
import dev.bladetetra.network.KyoukaVfxPacket;
import dev.bladetetra.network.ModNetwork;
import dev.bladetetra.visual.MaterialAppearance;
import dev.bladetetra.visual.SayaPresetSkin;
import mods.flammpfeil.slashblade.entity.EntitySlashEffect;
import mods.flammpfeil.slashblade.entity.IShootable;
import mods.flammpfeil.slashblade.event.SlashBladeEvent;
import mods.flammpfeil.slashblade.slasharts.SlashArts;
import mods.flammpfeil.slashblade.util.AttackManager;
import mods.flammpfeil.slashblade.util.KnockBacks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Rain-soaked Iaido ritual and true/false cut rhythm for Kyouka Suigetsu. */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class KyoukaAwakening {
    public static final String TAG_UNLOCKED = "blade_tetra_kyouka_unlocked";

    private static final String TAG_STREAK = "blade_tetra_kyouka_streak";
    private static final String TAG_LAST_HIT = "blade_tetra_kyouka_last_hit";
    private static final String TAG_LAST_TARGET = "blade_tetra_kyouka_last_target";
    private static final String TAG_MIRROR_COOLDOWN = "blade_tetra_kyouka_mirror_cooldown";
    private static final String TAG_BREAK_COOLDOWN = "blade_tetra_kyouka_break_cooldown";
    private static final String TAG_REFLECTION_TARGET = "blade_tetra_kyouka_reflection_target";
    private static final String TAG_REFLECTION_DAMAGE = "blade_tetra_kyouka_reflection_damage";
    private static final String TAG_REFLECTION_EXPIRES = "blade_tetra_kyouka_reflection_expires";
    private static final String TAG_REFLECTION_DIMENSION = "blade_tetra_kyouka_reflection_dimension";
    private static final String TAG_REFLECTION_YAW = "blade_tetra_kyouka_reflection_yaw";
    private static final String TAG_BLADE_ID = "blade_tetra_kyouka_blade_id";
    private static final String SA_SERIAL = "blade_tetra_kyouka_sa_serial";
    private static final String SA_CONFIRMED_UNTIL = "blade_tetra_kyouka_sa_confirmed_until";
    private static final String SA_DIRECT_UNTIL = "blade_tetra_kyouka_sa_direct_until";
    private static final String SA_PROJECTILE_SERIAL = "blade_tetra_kyouka_sa_projectile_serial";
    private static final String VISUAL_SLASH = "blade_tetra_kyouka_visual_slash";
    private static final String PROUDSOUL_VARIANT =
            "awakened_soul_inscription/proudsoul_sphere";
    private static final int REQUIRED_DRAWS = 3;
    private static final int STREAK_WINDOW = 200;
    private static final int KILL_WINDOW = 8;
    private static final ResourceLocation ADVANCEMENT =
            ResourceLocation.fromNamespaceAndPath(BladeTetra.MOD_ID, "kyouka");
    private static final ResourceKey<DamageType> MIRROR_DAMAGE = ResourceKey.create(
            Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(BladeTetra.MOD_ID, "kyouka_mirror"));
    private static final int SA_PROJECTILE_WINDOW_TICKS = 30;

    private static final List<PendingMirrorSlash> PENDING = new ArrayList<>();
    private static final Map<UUID, PendingHit> PENDING_HITS = new HashMap<>();

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
        return SoulLegacyState.isActive(stack, SoulLegacyState.Legacy.KYOUKA);
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
        tag.remove(TAG_BREAK_COOLDOWN);
        clearReflection(stack);
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
    }

    @SubscribeEvent
    public static void onSlashArt(SlashBladeEvent.PerformSlashArtEvent event) {
        if (event.getType() != SlashArts.ArtsType.Success
                || !(event.getEntityLiving() instanceof ServerPlayer player)
                || !isActive(player.getMainHandItem())) return;

        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        int serial = player.getPersistentData().getInt(SA_SERIAL) + 1;
        player.getPersistentData().putInt(SA_SERIAL, serial);
        player.getPersistentData().putLong(SA_CONFIRMED_UNTIL,
                now + SA_PROJECTILE_WINDOW_TICKS);
        player.getPersistentData().putLong(SA_DIRECT_UNTIL, now + 2L);

        for (Entity entity : level.getEntities(player, player.getBoundingBox().inflate(64.0D),
                candidate -> candidate.tickCount <= 2
                        && candidate instanceof IShootable shootable
                        && shootable.getShooter() == player)) {
            entity.getPersistentData().putInt(SA_PROJECTILE_SERIAL, serial);
        }

        // Some Slash Arts apply their real hit immediately before posting this event.
        PendingHit pending = PENDING_HITS.get(player.getUUID());
        if (pending != null && pending.hitTick() == now) {
            PENDING_HITS.remove(player.getUUID());
            resolveHit(level, player, pending, true, now);
        }
    }

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !(event.getEntity() instanceof IShootable shootable)
                || !(shootable.getShooter() instanceof ServerPlayer player)
                || !isActive(player.getMainHandItem())) return;
        long now = level.getGameTime();
        if (player.getPersistentData().getLong(SA_CONFIRMED_UNTIL) >= now) {
            event.getEntity().getPersistentData().putInt(SA_PROJECTILE_SERIAL,
                    player.getPersistentData().getInt(SA_SERIAL));
        }
    }

    /** SlashBlade supplies the geometry; this copy is presentation-only. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void cancelVisualSlashDamage(LivingAttackEvent event) {
        Entity direct = event.getSource().getDirectEntity();
        if (direct instanceof EntitySlashEffect
                && direct.getPersistentData().getBoolean(VISUAL_SLASH)) {
            event.setCanceled(true);
        }
    }

    /** Reads the real post-mitigation hit and delays it one tick to distinguish SA orderings. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDamage(LivingDamageEvent event) {
        if (event.getAmount() <= 0.0F || event.getSource().is(MIRROR_DAMAGE)
                || BladeTechniqueHandler.isTechniqueDamage(event.getSource())
                || SoulLegacyDamageGuard.isSecondary(event.getSource())) return;
        ServerPlayer player = resolveAttacker(event);
        if (player == null || !isActive(player.getMainHandItem())) return;

        Entity direct = event.getSource().getDirectEntity();
        if (direct != player && !(direct instanceof IShootable)) return;
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        int serial = player.getPersistentData().getInt(SA_SERIAL);
        boolean markedProjectile = direct != null && serial != 0
                && direct.getPersistentData().getInt(SA_PROJECTILE_SERIAL) == serial;
        boolean confirmedSa = player.getPersistentData().getLong(SA_CONFIRMED_UNTIL) >= now
                && (markedProjectile || direct == player
                        && player.getPersistentData().getLong(SA_DIRECT_UNTIL) >= now);
        ItemStack blade = player.getMainHandItem();
        ResourceLocation combo = blade.getCapability(ModularSlashBladeItem.BLADESTATE)
                .map(state -> state.getComboSeq()).orElse(null);
        boolean preciseIaido = direct == player
                && (ModComboStates.isIaidoDraw(combo) || ModComboStates.isIaidoFinish(combo));
        PendingHit hit = new PendingHit(
                level.dimension().location(), player.getUUID(), event.getEntity().getUUID(),
                bladeId(blade), now, now + 1L, event.getAmount(), player.getYRot(),
                preciseIaido);
        if (confirmedSa) {
            PENDING_HITS.remove(player.getUUID());
            resolveHit(level, player, hit, true, now);
        } else {
            PENDING_HITS.put(player.getUUID(), hit);
        }
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
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = event.getServer();
        if (!PENDING_HITS.isEmpty()) {
            Iterator<Map.Entry<UUID, PendingHit>> hits = PENDING_HITS.entrySet().iterator();
            while (hits.hasNext()) {
                PendingHit pending = hits.next().getValue();
                ServerLevel level = findLevel(server, pending.dimension());
                if (level == null) {
                    hits.remove();
                    continue;
                }
                if (level.getGameTime() < pending.releaseTick()) continue;
                hits.remove();
                ServerPlayer player = server.getPlayerList().getPlayer(pending.attacker());
                if (player != null && player.level() == level && pending.preciseIaido()) {
                    resolveHit(level, player, pending, false, level.getGameTime());
                }
            }
        }

        Iterator<PendingMirrorSlash> iterator = PENDING.iterator();
        while (iterator.hasNext()) {
            PendingMirrorSlash pending = iterator.next();
            ServerLevel level = findLevel(server, pending.dimension());
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
        PENDING_HITS.entrySet().removeIf(entry -> entry.getValue().dimension().equals(dimension));
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        PENDING.clear();
        PENDING_HITS.clear();
    }

    private static void resolveHit(ServerLevel level, ServerPlayer player,
            PendingHit hit, boolean slashArt, long now) {
        ItemStack blade = player.getMainHandItem();
        if (!isActive(blade) || !bladeId(blade).equals(hit.bladeId())) return;
        LivingEntity target = level.getEntity(hit.target()) instanceof LivingEntity living
                ? living : null;
        if (target == null || !target.isAlive() || player.distanceToSqr(target) > 1024.0D) return;

        Reflection reflection = readReflection(blade, level, now);
        CompoundTag tag = blade.getOrCreateTag();
        if (slashArt) {
            if (reflection == null || !reflection.target().equals(target.getUUID())
                    || tag.getLong(TAG_BREAK_COOLDOWN) > now) return;
            clearReflection(blade);
            float totalDamage = KyoukaMirrorMath.breakDamage(
                    hit.damage(), reflection.damage(),
                    GameplayConfig.MIRROR_BREAK_SA_DAMAGE_RATIO.get(),
                    GameplayConfig.MIRROR_BREAK_RECORDED_DAMAGE_RATIO.get(),
                    GameplayConfig.MIRROR_BREAK_DAMAGE_CAP.get());
            if (totalDamage <= 0.0F) return;
            tag.putLong(TAG_BREAK_COOLDOWN,
                    now + GameplayConfig.MIRROR_BREAK_COOLDOWN_TICKS.get());
            long due = now + GameplayConfig.MIRROR_BREAK_DELAY_TICKS.get();
            float first = totalDamage * 0.5F;
            PENDING.add(new PendingMirrorSlash(hit.dimension(), player.getUUID(),
                    target.getUUID(), hit.bladeId(), due, first,
                    hit.yaw(), MirrorCut.BREAK_FIRST));
            PENDING.add(new PendingMirrorSlash(hit.dimension(), player.getUUID(),
                    target.getUUID(), hit.bladeId(), due + 3L, totalDamage - first,
                    hit.yaw() + 90.0F, MirrorCut.BREAK_SECOND));
            playBreakCharge(level, target);
            return;
        }

        if (!hit.preciseIaido()) return;
        if (reflection != null && reflection.target().equals(target.getUUID())
                && tag.getLong(TAG_MIRROR_COOLDOWN) <= now) {
            float damage = KyoukaMirrorMath.reflectedDamage(
                    reflection.damage(),
                    GameplayConfig.MIRROR_SLASH_DAMAGE_RATIO.get(),
                    GameplayConfig.MIRROR_SLASH_DAMAGE_CAP.get());
            if (damage > 0.0F) {
                tag.putLong(TAG_MIRROR_COOLDOWN,
                        now + GameplayConfig.MIRROR_SLASH_COOLDOWN_TICKS.get());
                PENDING.add(new PendingMirrorSlash(hit.dimension(), player.getUUID(),
                        target.getUUID(), hit.bladeId(),
                        now + GameplayConfig.MIRROR_SLASH_DELAY_TICKS.get(), damage,
                        reflection.yaw() + 180.0F, MirrorCut.REFLECTION));
            }
        }
        recordReflection(blade, level, target, hit.damage(), hit.yaw(), now);
    }

    private static void executeMirrorSlash(ServerLevel level, PendingMirrorSlash pending) {
        ServerPlayer attacker = level.getServer().getPlayerList().getPlayer(pending.attacker());
        if (attacker == null || attacker.level() != level
                || !isActive(attacker.getMainHandItem())
                || !bladeId(attacker.getMainHandItem()).equals(pending.bladeId())) return;
        LivingEntity target = level.getEntity(pending.target()) instanceof LivingEntity living
                ? living : null;
        if (target == null || !target.isAlive() || attacker.distanceToSqr(target) > 1024.0D) return;

        ItemStack blade = attacker.getMainHandItem();
        boolean mirrorBlossom = SoulResonance.isFormed(blade,
                SoulResonance.Resonance.MIRROR_BLOSSOM);
        boolean mirrorThunder = SoulResonance.isFormed(blade,
                SoulResonance.Resonance.MIRROR_THUNDER);
        float petalBudget = mirrorBlossom ? pending.damage() * 0.25F : 0.0F;
        float directDamage = pending.damage() - petalBudget;
        spawnNativeSlash(attacker, target, pending);
        target.invulnerableTime = 0;
        boolean applied = SoulLegacyDamageGuard.apply(() -> target.hurt(
                mirrorDamage(level, attacker), directDamage));
        if (!applied) return;
        if (petalBudget > 0.0F && target.isAlive()) {
            SenbonzakuraAwakening.releaseMirrorPetals(attacker, target, petalBudget);
        }
        if (mirrorThunder && target.isAlive()) {
            RaikiriChainHandler.resonanceDischarge(level, attacker, target,
                    pending.damage() * 0.18F,
                    pending.cut() == MirrorCut.REFLECTION ? 2 : 3,
                    pending.cut() != MirrorCut.REFLECTION);
        }
        double y = target.getY() + target.getBbHeight() * 0.55D;
        int endRods = pending.cut() == MirrorCut.REFLECTION ? 9 : 18;
        level.sendParticles(ParticleTypes.END_ROD, target.getX(), y, target.getZ(),
                endRods, target.getBbWidth() * 0.55D, 0.26D,
                target.getBbWidth() * 0.55D, 0.045D);
        level.sendParticles(pending.cut() == MirrorCut.REFLECTION
                        ? ParticleTypes.SPLASH : ParticleTypes.ENCHANTED_HIT,
                target.getX(), target.getY() + 0.08D, target.getZ(),
                pending.cut() == MirrorCut.REFLECTION ? 12 : 28,
                0.85D, 0.05D, 0.85D, 0.035D);
        level.playSound(null, target.blockPosition(),
                pending.cut() == MirrorCut.BREAK_SECOND
                        ? SoundEvents.GLASS_BREAK : SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.PLAYERS,
                pending.cut() == MirrorCut.REFLECTION ? 0.65F : 0.95F,
                pending.cut() == MirrorCut.BREAK_SECOND ? 1.15F : 1.45F);
        if (pending.cut() == MirrorCut.BREAK_SECOND) {
            sendVfx(level, attacker, target, KyoukaVfxPacket.BREAK_SHATTER,
                    18, pending.bladeId().hashCode());
        }
    }

    private static void spawnNativeSlash(ServerPlayer attacker, LivingEntity target,
            PendingMirrorSlash pending) {
        float angle = switch (pending.cut()) {
            case REFLECTION -> 180.0F;
            case BREAK_FIRST -> -42.0F;
            case BREAK_SECOND -> 42.0F;
        };
        EntitySlashEffect slash = AttackManager.doSlash(attacker, angle,
                true, false, 0.0D);
        if (slash == null) return;
        slash.getPersistentData().putBoolean(VISUAL_SLASH, true);
        slash.setDamage(0.0D);
        slash.setMute(true);
        slash.setIndirect(true);
        slash.setNoClip(true);
        slash.setCycleHit(false);
        slash.setKnockBack(KnockBacks.cancel);
        slash.setLifetime(pending.cut() == MirrorCut.REFLECTION ? 9 : 12);
        slash.setColor(pending.cut() == MirrorCut.REFLECTION ? 0xA9EEFF : 0xC8F6FF);
        float scale = Mth.clamp(Math.max(target.getBbWidth(), target.getBbHeight())
                * (pending.cut() == MirrorCut.REFLECTION ? 0.82F : 1.05F),
                pending.cut() == MirrorCut.REFLECTION ? 1.05F : 1.35F, 3.8F);
        slash.setBaseSize(scale);
        slash.setRotationRoll(pending.cut() == MirrorCut.BREAK_SECOND ? -24.0F : 24.0F);
        slash.setPos(target.getX(), target.getY() + target.getBbHeight() * 0.48D,
                target.getZ());
        slash.setYRot(pending.yaw());
    }

    private static void recordReflection(ItemStack blade, ServerLevel level,
            LivingEntity target, float damage, float yaw, long now) {
        CompoundTag tag = blade.getOrCreateTag();
        tag.putUUID(TAG_REFLECTION_TARGET, target.getUUID());
        tag.putFloat(TAG_REFLECTION_DAMAGE, Math.max(0.0F, damage));
        tag.putLong(TAG_REFLECTION_EXPIRES,
                now + GameplayConfig.MIRROR_REFLECTION_WINDOW_TICKS.get());
        tag.putString(TAG_REFLECTION_DIMENSION, level.dimension().location().toString());
        tag.putFloat(TAG_REFLECTION_YAW, yaw);

        sendVfx(level, null, target, KyoukaVfxPacket.REFLECTION_POOL,
                GameplayConfig.MIRROR_REFLECTION_WINDOW_TICKS.get(),
                target.getUUID().hashCode() ^ (int) now);
    }

    private static Reflection readReflection(ItemStack blade, ServerLevel level, long now) {
        CompoundTag tag = blade.getTag();
        if (tag == null || !tag.hasUUID(TAG_REFLECTION_TARGET)
                || tag.getLong(TAG_REFLECTION_EXPIRES) < now
                || !level.dimension().location().toString()
                        .equals(tag.getString(TAG_REFLECTION_DIMENSION))) {
            clearReflection(blade);
            return null;
        }
        return new Reflection(tag.getUUID(TAG_REFLECTION_TARGET),
                Math.max(0.0F, tag.getFloat(TAG_REFLECTION_DAMAGE)),
                tag.getLong(TAG_REFLECTION_EXPIRES),
                tag.getFloat(TAG_REFLECTION_YAW));
    }

    private static void clearReflection(ItemStack blade) {
        CompoundTag tag = blade.getTag();
        if (tag == null) return;
        tag.remove(TAG_REFLECTION_TARGET);
        tag.remove(TAG_REFLECTION_DAMAGE);
        tag.remove(TAG_REFLECTION_EXPIRES);
        tag.remove(TAG_REFLECTION_DIMENSION);
        tag.remove(TAG_REFLECTION_YAW);
    }

    private static void playBreakCharge(ServerLevel level, LivingEntity target) {
        level.sendParticles(ParticleTypes.END_ROD,
                target.getX(), target.getY() + 0.08D, target.getZ(),
                22, 0.95D, 0.04D, 0.95D, 0.015D);
        level.playSound(null, target.blockPosition(),
                SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS,
                0.85F, 0.78F);
        sendVfx(level, null, target, KyoukaVfxPacket.BREAK_CHARGE,
                GameplayConfig.MIRROR_BREAK_DELAY_TICKS.get() + 7,
                target.getUUID().hashCode() ^ (int) level.getGameTime());
    }

    public static void playFinalMoonReflection(ServerLevel level,
            ServerPlayer attacker, LivingEntity target) {
        sendVfx(level, attacker, target, KyoukaVfxPacket.BREAK_SHATTER,
                28, target.getUUID().hashCode() ^ (int) level.getGameTime());
        level.sendParticles(ParticleTypes.END_ROD,
                target.getX(), target.getY() + target.getBbHeight() * 0.5D,
                target.getZ(), 28, 0.9D, 0.55D, 0.9D, 0.025D);
        level.playSound(null, target.blockPosition(), SoundEvents.GLASS_BREAK,
                SoundSource.PLAYERS, 0.9F, 0.72F);
    }

    private static void sendVfx(ServerLevel level, ServerPlayer source,
            LivingEntity target, int type, int duration, int seed) {
        float scale = Mth.clamp(Math.max(target.getBbWidth(), target.getBbHeight()) / 1.8F,
                0.78F, 2.35F);
        KyoukaVfxPacket packet = new KyoukaVfxPacket(type,
                target.getX(), target.getY(), target.getZ(),
                source == null ? -1 : source.getId(), target.getId(),
                scale, duration, seed);
        for (ServerPlayer viewer : level.players()) {
            if (viewer.distanceToSqr(target) <= 128.0D * 128.0D) {
                ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> viewer), packet);
            }
        }
    }

    private static ServerPlayer resolveAttacker(LivingDamageEvent event) {
        Entity direct = event.getSource().getDirectEntity();
        if (direct instanceof IShootable shootable
                && shootable.getShooter() instanceof ServerPlayer player) return player;
        return event.getSource().getEntity() instanceof ServerPlayer player ? player : null;
    }

    private static UUID bladeId(ItemStack blade) {
        CompoundTag tag = blade.getOrCreateTag();
        if (!tag.hasUUID(TAG_BLADE_ID)) tag.putUUID(TAG_BLADE_ID, UUID.randomUUID());
        return tag.getUUID(TAG_BLADE_ID);
    }

    private static ServerLevel findLevel(MinecraftServer server, ResourceLocation dimension) {
        for (ServerLevel level : server.getAllLevels()) {
            if (level.dimension().location().equals(dimension)) return level;
        }
        return null;
    }

    private static DamageSource mirrorDamage(ServerLevel level, ServerPlayer attacker) {
        return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(MIRROR_DAMAGE), null, attacker);
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
            UUID bladeId,
            long dueTick,
            float damage,
            float yaw,
            MirrorCut cut) {
    }

    private record PendingHit(
            ResourceLocation dimension,
            UUID attacker,
            UUID target,
            UUID bladeId,
            long hitTick,
            long releaseTick,
            float damage,
            float yaw,
            boolean preciseIaido) {
    }

    private record Reflection(UUID target, float damage, long expires, float yaw) {
    }

    private enum MirrorCut {
        REFLECTION,
        BREAK_FIRST,
        BREAK_SECOND
    }

    private KyoukaAwakening() {
    }
}
