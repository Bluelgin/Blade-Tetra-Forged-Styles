package dev.bladetetra.combat;

import dev.bladetetra.BladeTetra;
import dev.bladetetra.forging.LegacyFusion;
import dev.bladetetra.forging.LegacyImprintKind;
import dev.bladetetra.forging.NamedLegacyParts;
import dev.bladetetra.easteregg.SoulLegacyDamageGuard;
import dev.bladetetra.network.BladeTechniqueVfxPacket;
import dev.bladetetra.network.ModNetwork;
import dev.bladetetra.registry.ModSlashBladeAbilities;
import mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState;
import mods.flammpfeil.slashblade.entity.EntitySlashEffect;
import mods.flammpfeil.slashblade.event.SlashBladeEvent;
import mods.flammpfeil.slashblade.slasharts.SlashArts;
import mods.flammpfeil.slashblade.util.AttackManager;
import mods.flammpfeil.slashblade.util.KnockBacks;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/** Runtime behaviour and lifecycle reconciliation for ordered named-blade fusions. */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LegacyFusionHandler {
    private static final String LAST_ACTIVE = "blade_tetra_legacy_fusion_active";
    private static final String PREVIOUS_SLASH_ART =
            "blade_tetra_legacy_fusion_previous_slash_art";
    private static final String ABILITY_OWNER = "blade_tetra_named_ability_owner";
    private static final String APPLIED_SLASH_ART = "blade_tetra_named_applied_slash_art";
    private static final String OWNED_SPECIAL_EFFECTS = "blade_tetra_named_owned_effects";
    private static final String PURSUIT_TARGET = "blade_tetra_twin_fox_pursuit_target";
    private static final String PURSUIT_DIRECTION_X = "blade_tetra_twin_fox_pursuit_direction_x";
    private static final String PURSUIT_DIRECTION_Z = "blade_tetra_twin_fox_pursuit_direction_z";
    private static final String LEGACY_PURSUIT_COMBO = "blade_tetra_twin_fox_pursuit_combo";
    private static final String PURSUIT_EXPIRES = "blade_tetra_twin_fox_pursuit_expires";
    private static final String PURSUIT_COOLDOWN = "blade_tetra_twin_fox_pursuit_cooldown";
    private static final int HUNT_DURATION_TICKS = 20;
    private static final int PURSUIT_MARK_TICKS = 60;
    private static final int PURSUIT_DELAY_TICKS = 5;
    private static final int PURSUIT_COOLDOWN_TICKS = 30;
    private static final List<PendingHunt> PENDING_HUNTS = new ArrayList<>();
    private static final List<PendingPursuit> PENDING_PURSUITS = new ArrayList<>();
    private static final List<PendingTwinPhase> PENDING_TWIN_PHASES = new ArrayList<>();
    private static final List<PendingTwinPhaseSlash> PENDING_TWIN_PHASE_SLASHES =
            new ArrayList<>();

    /**
     * Called from the modular item's existing derived-state synchronization.
     * Reconciles transient abilities with the currently assembled fittings.
     * Old 1.5.2 test stacks are sanitized here as well: that build appended the
     * reflection effect on every refresh and did not restore the displaced SA.
     */
    public static void sync(ItemStack blade, ISlashBladeState state) {
        LegacyFusion active = LegacyFusion.active(blade);
        CompoundTag tag = blade.getOrCreateTag();
        String previous = tag.getString(LAST_ACTIVE);
        String current = active == null ? "" : active.id();

        ResourceLocation pursuit = ModSlashBladeAbilities.TWIN_FOX_REFLECTION.getId();
        reconcileSpecialEffect(state, pursuit,
                active == LegacyFusion.WHITE_SAYA_BLACK_HILT);
        if (active != LegacyFusion.WHITE_SAYA_BLACK_HILT) {
            clearPursuit(tag);
        }

        ResourceLocation piercing = ModSlashBladeAbilities.TWIN_FOX_PIERCING.getId();
        LegacyImprintKind orthodox = active == null
                ? NamedLegacyParts.fromStack(blade).completeSet() : null;
        if (orthodox != null && !orthodox.supportsOrthodoxInheritance()) orthodox = null;

        String owner = active != null
                ? "fusion:" + active.id()
                : orthodox == null ? "" : "orthodox:" + orthodox.id();
        ResourceLocation inheritedSlashArt = fusionSlashArt(active);
        if (inheritedSlashArt == null && orthodox != null) {
            inheritedSlashArt = orthodox.slashArt();
        }
        List<ResourceLocation> inheritedEffects = orthodox == null
                ? List.of() : orthodox.specialEffects();

        String previousOwner = tag.getString(ABILITY_OWNER);
        // Migrate the first fusion build without losing the SA it displaced.
        if (previousOwner.isEmpty()
                && LegacyFusion.BLACK_SAYA_WHITE_HILT.id().equals(previous)
                && piercing.equals(state.getSlashArtsKey())) {
            previousOwner = "fusion:" + LegacyFusion.BLACK_SAYA_WHITE_HILT.id();
            tag.putString(ABILITY_OWNER, previousOwner);
            tag.putString(APPLIED_SLASH_ART, piercing.toString());
        }
        if (!owner.equals(previousOwner)) {
            restoreStructuralSlashArt(tag, state);
            removeOwnedSpecialEffects(tag, state);
            applyStructuralSlashArt(tag, state, inheritedSlashArt);
            addOwnedSpecialEffects(tag, state, inheritedEffects);
            if (owner.isEmpty()) tag.remove(ABILITY_OWNER);
            else tag.putString(ABILITY_OWNER, owner);
        } else if (!owner.isEmpty()) {
            addOwnedSpecialEffects(tag, state, inheritedEffects);
        }

        if (current.isEmpty()) {
            tag.remove(LAST_ACTIVE);
        } else if (!current.equals(previous)) {
            tag.putString(LAST_ACTIVE, current);
        }
    }

    private static ResourceLocation fusionSlashArt(LegacyFusion fusion) {
        if (fusion == LegacyFusion.BLACK_SAYA_WHITE_HILT) {
            return ModSlashBladeAbilities.TWIN_FOX_PIERCING.getId();
        }
        if (fusion == LegacyFusion.YASHA_SAYA_KIKOUKU_HILT) {
            return ModSlashBladeAbilities.TWIN_PHASE_KIKOUKU.getId();
        }
        return null;
    }

    private static void reconcileSpecialEffect(ISlashBladeState state,
            ResourceLocation effect, boolean shouldExist) {
        long count = state.getSpecialEffects().stream()
                .filter(effect::equals)
                .count();
        if ((!shouldExist && count > 0L) || (shouldExist && count != 1L)) {
            state.getSpecialEffects().removeIf(effect::equals);
            if (shouldExist) {
                state.addSpecialEffect(effect);
            }
        }
    }

    private static void applyStructuralSlashArt(CompoundTag tag,
            ISlashBladeState state, ResourceLocation slashArt) {
        if (slashArt == null || slashArt.equals(state.getSlashArtsKey())) return;
        tag.putString(PREVIOUS_SLASH_ART, state.getSlashArtsKey().toString());
        tag.putString(APPLIED_SLASH_ART, slashArt.toString());
        state.setSlashArtsKey(slashArt);
    }

    private static void restoreStructuralSlashArt(CompoundTag tag,
            ISlashBladeState state) {
        ResourceLocation applied = ResourceLocation.tryParse(
                tag.getString(APPLIED_SLASH_ART));
        if (applied != null && applied.equals(state.getSlashArtsKey())) {
            ResourceLocation restored = ResourceLocation.tryParse(
                    tag.getString(PREVIOUS_SLASH_ART));
            state.setSlashArtsKey(restored == null
                    ? mods.flammpfeil.slashblade.registry.SlashArtsRegistry.NONE.getId()
                    : restored);
        }
        tag.remove(PREVIOUS_SLASH_ART);
        tag.remove(APPLIED_SLASH_ART);
    }

    private static void addOwnedSpecialEffects(CompoundTag tag,
            ISlashBladeState state, List<ResourceLocation> desired) {
        List<String> owned = new ArrayList<>();
        String stored = tag.getString(OWNED_SPECIAL_EFFECTS);
        if (!stored.isEmpty()) owned.addAll(List.of(stored.split(",")));
        for (ResourceLocation effect : desired) {
            if (!state.getSpecialEffects().contains(effect)) {
                state.addSpecialEffect(effect);
                if (!owned.contains(effect.toString())) owned.add(effect.toString());
            }
        }
        if (owned.isEmpty()) tag.remove(OWNED_SPECIAL_EFFECTS);
        else tag.putString(OWNED_SPECIAL_EFFECTS, String.join(",", owned));
    }

    private static void removeOwnedSpecialEffects(CompoundTag tag,
            ISlashBladeState state) {
        String stored = tag.getString(OWNED_SPECIAL_EFFECTS);
        if (!stored.isEmpty()) {
            for (String value : stored.split(",")) {
                ResourceLocation effect = ResourceLocation.tryParse(value);
                if (effect != null) state.getSpecialEffects().removeIf(effect::equals);
            }
        }
        tag.remove(OWNED_SPECIAL_EFFECTS);
    }

    @SubscribeEvent
    public static void onSlashArt(SlashBladeEvent.PerformSlashArtEvent event) {
        LivingEntity user = event.getEntityLiving();
        if (!(user instanceof ServerPlayer player)
                || event.getType() == SlashArts.ArtsType.Fail) {
            return;
        }
        ItemStack blade = player.getMainHandItem();
        if (ModSlashBladeAbilities.TWIN_PHASE_KIKOUKU.getId().equals(
                event.getSlashBladeState().getSlashArtsKey())) {
            beginTwinPhase(player, blade, event.getSlashBladeState());
            return;
        }
        if (!ModSlashBladeAbilities.TWIN_FOX_PIERCING.getId().equals(
                event.getSlashBladeState().getSlashArtsKey())) {
            return;
        }
        if (LegacyFusion.active(blade) != LegacyFusion.BLACK_SAYA_WHITE_HILT) {
            return;
        }
        ServerLevel level = player.serverLevel();
        LivingEntity target = acquireHuntTarget(player, event.getSlashBladeState());
        Vec3 start = player.getEyePosition().add(player.getLookAngle().scale(0.9D));
        Vec3 aim = target == null
                ? player.pick(18.0D, 0.0F, false).getLocation()
                : target.getBoundingBox().getCenter();
        PendingHunt hunt = new PendingHunt(level.dimension(), player.getUUID(),
                target == null ? null : target.getUUID(), start, aim,
                level.getGameTime(), player.getYRot());
        PENDING_HUNTS.add(hunt);
        sendHuntVfx(level, player, target, start, aim,
                BladeTechniqueVfxPacket.TWIN_FOX_MOONHUNT,
                HUNT_DURATION_TICKS, 1.0F);
        foxDust(level, start, false, 26, 0.48D);
        level.playSound(null, player.blockPosition(), SoundEvents.FIRECHARGE_USE,
                SoundSource.PLAYERS, 0.48F, 1.65F);
    }

    private static void beginTwinPhase(ServerPlayer player, ItemStack blade,
            ISlashBladeState state) {
        if (LegacyFusion.active(blade) != LegacyFusion.YASHA_SAYA_KIKOUKU_HILT) {
            return;
        }
        ServerLevel level = player.serverLevel();
        List<LivingEntity> nearby = level.getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(6.5D),
                target -> canAffect(player, target));
        boolean crowd = nearby.size() >= 3;
        LivingEntity target = crowd ? null : acquireHuntTarget(player, state);
        if (!crowd && target == null && !nearby.isEmpty()) {
            target = nearby.stream().min(java.util.Comparator.comparingDouble(
                    player::distanceToSqr)).orElse(null);
        }
        Vec3 center = player.position().add(0.0D, 0.85D, 0.0D);
        Vec3 end = crowd ? crowdCenter(nearby)
                : target == null ? center : target.getBoundingBox().getCenter();
        int type = crowd ? BladeTechniqueVfxPacket.TWIN_PHASE_KIKOUKU
                : BladeTechniqueVfxPacket.TWIN_PHASE_YASHA;
        PENDING_TWIN_PHASES.add(new PendingTwinPhase(level.dimension(),
                player.getUUID(), target == null ? null : target.getUUID(),
                level.getGameTime() + 9L, crowd, end));
        scheduleTwinPhaseSlashes(level, player, end, crowd,
                target == null ? null : target.getUUID());
        sendTwinPhaseVfx(level, player, target, center, end, type, 18,
                crowd ? 1.08F : 1.0F);
        level.playSound(null, player.blockPosition(),
                crowd ? SoundEvents.RESPAWN_ANCHOR_CHARGE
                        : SoundEvents.AMETHYST_BLOCK_RESONATE,
                SoundSource.PLAYERS, crowd ? 0.72F : 0.58F,
                crowd ? 0.72F : 1.55F);
    }

    @SubscribeEvent
    public static void onBladeHit(SlashBladeEvent.HitEvent event) {
        LivingEntity target = event.getTarget();
        if (!(event.getUser() instanceof ServerPlayer player)
                || !canAffect(player, target)) {
            return;
        }
        ItemStack blade = event.getBlade();
        if (LegacyFusion.active(blade) != LegacyFusion.WHITE_SAYA_BLACK_HILT
                || !event.getSlashBladeState().hasSpecialEffect(
                ModSlashBladeAbilities.TWIN_FOX_REFLECTION.getId())) {
            return;
        }
        long now = player.level().getGameTime();
        CompoundTag tag = blade.getOrCreateTag();
        if (tag.getLong(PURSUIT_COOLDOWN) > now) {
            return;
        }
        String targetId = target.getUUID().toString();
        Vec3 direction = pursuitDirection(player, target);
        boolean matchingMark = targetId.equals(tag.getString(PURSUIT_TARGET))
                && tag.getLong(PURSUIT_EXPIRES) >= now;
        Vec3 markedDirection = new Vec3(tag.getDouble(PURSUIT_DIRECTION_X), 0.0D,
                tag.getDouble(PURSUIT_DIRECTION_Z));
        float targetSpan = Math.max(target.getBbWidth(), target.getBbHeight());
        if (matchingMark && formsPincer(markedDirection, direction, targetSpan)) {
            clearPursuit(tag);
            tag.putLong(PURSUIT_COOLDOWN, now + PURSUIT_COOLDOWN_TICKS);
            PENDING_PURSUITS.add(new PendingPursuit(player.serverLevel().dimension(),
                    player.getUUID(), target.getUUID(), now + PURSUIT_DELAY_TICKS,
                    pursuitDamage(player), markedDirection, direction));
            sendPursuitVfx(player.serverLevel(), player, target, markedDirection,
                    BladeTechniqueVfxPacket.TWIN_FOX_PURSUIT_CROSS, 12, 1.0F);
            foxDust(player.serverLevel(), target.getBoundingBox().getCenter(),
                    false, 16, 0.30D);
        } else if (!matchingMark) {
            tag.putString(PURSUIT_TARGET, targetId);
            tag.putDouble(PURSUIT_DIRECTION_X, direction.x);
            tag.putDouble(PURSUIT_DIRECTION_Z, direction.z);
            tag.putLong(PURSUIT_EXPIRES, now + PURSUIT_MARK_TICKS);
            sendPursuitVfx(player.serverLevel(), player, target, direction,
                    BladeTechniqueVfxPacket.TWIN_FOX_PURSUIT_MARK,
                    PURSUIT_MARK_TICKS, 0.72F);
            foxDust(player.serverLevel(), target.getBoundingBox().getCenter(),
                    true, 12, 0.24D);
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        tickHunts(event);
        tickPursuits(event);
        tickTwinPhaseSlashes(event);
        tickTwinPhases(event);
    }

    private static void tickTwinPhaseSlashes(TickEvent.ServerTickEvent event) {
        Iterator<PendingTwinPhaseSlash> iterator = PENDING_TWIN_PHASE_SLASHES.iterator();
        while (iterator.hasNext()) {
            PendingTwinPhaseSlash pending = iterator.next();
            ServerLevel level = event.getServer().getLevel(pending.dimension());
            if (level == null) {
                iterator.remove();
                continue;
            }
            if (level.getGameTime() < pending.dueTick()) continue;
            iterator.remove();
            ServerPlayer player = event.getServer().getPlayerList()
                    .getPlayer(pending.playerId());
            if (player == null || player.level() != level
                    || LegacyFusion.active(player.getMainHandItem())
                    != LegacyFusion.YASHA_SAYA_KIKOUKU_HILT) {
                continue;
            }
            spawnTwinPhaseVisualSlash(player, pending.position(), pending.yaw(),
                    pending.roll(), pending.color(), pending.size(), pending.lifetime());
            applyTwinPhaseSlashDamage(level, player, pending);
        }
    }

    private static void tickTwinPhases(TickEvent.ServerTickEvent event) {
        Iterator<PendingTwinPhase> iterator = PENDING_TWIN_PHASES.iterator();
        while (iterator.hasNext()) {
            PendingTwinPhase pending = iterator.next();
            ServerLevel level = event.getServer().getLevel(pending.dimension());
            if (level == null) {
                iterator.remove();
                continue;
            }
            if (level.getGameTime() < pending.dueTick()) continue;
            iterator.remove();
            ServerPlayer player = event.getServer().getPlayerList()
                    .getPlayer(pending.playerId());
            if (player == null || player.level() != level
                    || LegacyFusion.active(player.getMainHandItem())
                    != LegacyFusion.YASHA_SAYA_KIKOUKU_HILT) {
                continue;
            }
            double attack = Math.max(1.0D,
                    player.getAttributeValue(Attributes.ATTACK_DAMAGE));
            if (pending.crowd()) {
                int hits = 0;
                for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class,
                        AABB.ofSize(pending.impactCenter(), 13.5D, 8.0D, 13.5D),
                        candidate -> canAffect(player, candidate))) {
                    if (hits++ >= 8) break;
                    float damage = (float) Math.min(14.0D, attack * 0.72D);
                    SoulLegacyDamageGuard.apply(() -> target.hurt(
                            level.damageSources().playerAttack(player), damage));
                }
                level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                        pending.impactCenter().x, pending.impactCenter().y + 0.55D,
                        pending.impactCenter().z,
                        30, 2.1D, 0.7D, 2.1D, 0.025D);
                level.playSound(null, BlockPos.containing(pending.impactCenter()),
                        SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS,
                        1.05F, 0.62F);
            } else if (pending.targetId() != null) {
                Entity entity = level.getEntity(pending.targetId());
                if (entity instanceof LivingEntity target
                        && canAffect(player, target)
                        && player.distanceToSqr(target) <= 24.0D * 24.0D) {
                    float damage = (float) Math.min(30.0D, attack * 1.45D);
                    SoulLegacyDamageGuard.apply(() -> target.hurt(
                            level.damageSources().playerAttack(player), damage));
                    level.sendParticles(ParticleTypes.CHERRY_LEAVES,
                            target.getX(), target.getY() + target.getBbHeight() * 0.55D,
                            target.getZ(), 24, 0.65D, 0.75D, 0.65D, 0.018D);
                    level.playSound(null, target.blockPosition(),
                            SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS,
                            0.95F, 1.18F);
                }
            }
        }
    }

    private static void tickHunts(TickEvent.ServerTickEvent event) {
        Iterator<PendingHunt> iterator = PENDING_HUNTS.iterator();
        while (iterator.hasNext()) {
            PendingHunt pending = iterator.next();
            ServerLevel level = event.getServer().getLevel(pending.dimension);
            if (level == null) {
                iterator.remove();
                continue;
            }
            ServerPlayer player = event.getServer().getPlayerList()
                    .getPlayer(pending.playerId);
            if (player == null || player.level() != level
                    || LegacyFusion.active(player.getMainHandItem())
                    != LegacyFusion.BLACK_SAYA_WHITE_HILT) {
                iterator.remove();
                continue;
            }
            long elapsed = level.getGameTime() - pending.startTick;
            double progress = Math.min(1.0D,
                    Math.max(0.0D, elapsed / (double) HUNT_DURATION_TICKS));
            Vec3 endpoint = huntEndpoint(level, pending);
            Vec3 white = huntPath(pending.start, endpoint, progress, 1.0D);
            Vec3 black = huntPath(pending.start, endpoint, progress, -1.0D);
            if (elapsed > 0L && elapsed < HUNT_DURATION_TICKS) {
                double previousProgress = Math.max(0.0D,
                        (elapsed - 1.0D) / HUNT_DURATION_TICKS);
                Vec3 previousWhite = huntPath(pending.start, endpoint,
                        previousProgress, 1.0D);
                Vec3 previousBlack = huntPath(pending.start, endpoint,
                        previousProgress, -1.0D);
                if (!pending.whiteBlocked && pathBlocked(level, player,
                        previousWhite, white)) pending.whiteBlocked = true;
                if (!pending.blackBlocked && pathBlocked(level, player,
                        previousBlack, black)) pending.blackBlocked = true;
                LivingEntity victim = huntVictim(level, player, pending, endpoint);
                if (elapsed >= 7L && victim != null) {
                    double reach = 0.82D + victim.getBbWidth() * 0.55D;
                    Vec3 center = victim.getBoundingBox().getCenter();
                    if (!pending.whiteBlocked && !pending.whiteHit
                            && white.distanceTo(center) <= reach) {
                        pending.whiteHit = true;
                        foxDust(level, white, true, 16, 0.28D);
                    }
                    if (!pending.blackBlocked && !pending.blackHit
                            && black.distanceTo(center) <= reach) {
                        pending.blackHit = true;
                        foxDust(level, black, false, 16, 0.28D);
                    }
                }
                if ((elapsed & 3L) == 0L) {
                    foxTrail(level, white, true);
                    foxTrail(level, black, false);
                }
            }
            if (elapsed >= HUNT_DURATION_TICKS) {
                iterator.remove();
                LivingEntity victim = huntVictim(level, player, pending, endpoint);
                if (victim != null) {
                    Vec3 center = victim.getBoundingBox().getCenter();
                    double reach = 1.0D + victim.getBbWidth() * 0.55D;
                    pending.whiteHit |= !pending.whiteBlocked
                            && white.distanceTo(center) <= reach;
                    pending.blackHit |= !pending.blackBlocked
                            && black.distanceTo(center) <= reach;
                }
                spawnFusionSlash(player, white, pending.yaw + 96.0F,
                        0xF3E8E2, 0.0D, 1.58F);
                spawnFusionSlash(player, black, pending.yaw - 96.0F,
                        0x371A30, 0.0D, 1.62F);
                int hitCount = (pending.whiteHit ? 1 : 0) + (pending.blackHit ? 1 : 0);
                double damage = hitCount * fusionDamage(player, 0.30D, 8.0D);
                if (hitCount == 2) {
                    damage += fusionDamage(player, 0.75D, 20.0D);
                }
                if (damage > 0.0D) {
                    Vec3 impact = victim == null
                            ? endpoint : victim.getBoundingBox().getCenter();
                    spawnFusionSlash(player, impact.add(0.0D, 0.12D, 0.0D),
                            pending.yaw, 0xA62846, Math.min(36.0D, damage),
                            hitCount == 2 ? 2.15F : 1.45F);
                    if (hitCount == 2 && victim != null
                            && victim.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) < 0.8D) {
                        Vec3 pull = endpoint.subtract(victim.position())
                                .multiply(1.0D, 0.0D, 1.0D);
                        if (pull.lengthSqr() > 0.01D) {
                            victim.push(pull.normalize().x * 0.18D, 0.04D,
                                    pull.normalize().z * 0.18D);
                        }
                    }
                    sendHuntVfx(level, player, victim, pending.start, impact,
                            BladeTechniqueVfxPacket.TWIN_FOX_MOONHUNT_IMPACT,
                            12, hitCount == 2 ? 1.25F : 0.72F);
                }
                foxDust(level, endpoint, true, hitCount == 2 ? 54 : 28, 0.62D);
                level.sendParticles(ParticleTypes.SWEEP_ATTACK,
                        endpoint.x, endpoint.y, endpoint.z,
                        hitCount == 2 ? 4 : 2, 0.28D, 0.24D, 0.28D, 0.0D);
                level.playSound(null, BlockPos.containing(endpoint),
                        SoundEvents.AMETHYST_BLOCK_CHIME,
                        SoundSource.PLAYERS, hitCount == 2 ? 1.05F : 0.68F, 1.45F);
                level.playSound(null, BlockPos.containing(endpoint),
                        SoundEvents.PLAYER_ATTACK_SWEEP,
                        SoundSource.PLAYERS, hitCount == 2 ? 1.1F : 0.72F, 0.72F);
                if (hitCount == 2) {
                    level.playSound(null, BlockPos.containing(endpoint),
                            SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 0.58F, 1.65F);
                }
            }
        }
    }

    private static void tickPursuits(TickEvent.ServerTickEvent event) {
        Iterator<PendingPursuit> iterator = PENDING_PURSUITS.iterator();
        while (iterator.hasNext()) {
            PendingPursuit pending = iterator.next();
            ServerLevel level = event.getServer().getLevel(pending.dimension());
            if (level == null) {
                iterator.remove();
                continue;
            }
            if (level.getGameTime() < pending.dueTick()) {
                continue;
            }
            iterator.remove();
            ServerPlayer player = event.getServer().getPlayerList()
                    .getPlayer(pending.playerId());
            Entity entity = level.getEntity(pending.targetId());
            if (player == null || player.level() != level
                    || !(entity instanceof LivingEntity target)
                    || !canAffect(player, target)
                    || player.distanceToSqr(target) > 32.0D * 32.0D
                    || LegacyFusion.active(player.getMainHandItem())
                    != LegacyFusion.WHITE_SAYA_BLACK_HILT) {
                continue;
            }
            Vec3 center = target.getBoundingBox().getCenter();
            float whiteYaw = directionYaw(pending.markedDirection());
            float blackYaw = directionYaw(pending.triggerDirection());
            spawnFusionSlash(player, center, blackYaw + 90.0F,
                    0x371A30, pending.damage(), 1.38F);
            spawnFusionSlash(player, center.add(0.0D, 0.10D, 0.0D),
                    whiteYaw - 90.0F, 0xF3E8E2, 0.0D, 1.08F);
            foxDust(level, center, false, 26, 0.42D);
            level.playSound(null, target.blockPosition(),
                    SoundEvents.PLAYER_ATTACK_SWEEP,
                    SoundSource.PLAYERS, 0.72F, 1.30F);
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            PENDING_HUNTS.removeIf(pending -> pending.dimension.equals(level.dimension()));
            PENDING_PURSUITS.removeIf(pending -> pending.dimension().equals(level.dimension()));
            PENDING_TWIN_PHASES.removeIf(pending -> pending.dimension().equals(level.dimension()));
            PENDING_TWIN_PHASE_SLASHES.removeIf(
                    pending -> pending.dimension().equals(level.dimension()));
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        PENDING_HUNTS.clear();
        PENDING_PURSUITS.clear();
        PENDING_TWIN_PHASES.clear();
        PENDING_TWIN_PHASE_SLASHES.clear();
    }

    private static void spawnFusionSlash(LivingEntity user, Vec3 position,
            float yaw, int color, double damage, float size) {
        EntitySlashEffect slash = AttackManager.doSlash(user, yaw, true, false, damage);
        if (slash == null) {
            return;
        }
        slash.setDamage(damage);
        slash.setIndirect(true);
        slash.setNoClip(true);
        slash.setCycleHit(false);
        slash.setKnockBack(KnockBacks.cancel);
        slash.setColor(color);
        slash.setBaseSize(size);
        slash.setLifetime(12);
        slash.setPos(position.x, position.y, position.z);
        slash.getPersistentData().putBoolean(BladeTechniqueHandler.TECHNIQUE_ENTITY, true);
        if (damage <= 0.0D) {
            slash.getPersistentData().putBoolean("blade_tetra_technique_visual", true);
            slash.setMute(true);
        }
    }

    private static void scheduleTwinPhaseSlashes(ServerLevel level,
            ServerPlayer player, Vec3 center, boolean crowd, UUID targetId) {
        long now = level.getGameTime();
        double attack = Math.max(1.0D,
                player.getAttributeValue(Attributes.ATTACK_DAMAGE));
        if (!crowd) {
            for (int index = 0; index < 5; index++) {
                PENDING_TWIN_PHASE_SLASHES.add(new PendingTwinPhaseSlash(
                        level.dimension(), player.getUUID(), targetId, center,
                        now + 1L + index * 2L, player.getYRot() + index * 7.0F,
                        -56.0F + index * 28.0F,
                        index == 2 ? 0xFFF5E9 : 0xF06A9B,
                        1.20F + index * 0.10F, 9,
                        twinPhaseSlashDamage(attack, false, false), false));
            }
            return;
        }
        for (int index = 0; index < 4; index++) {
            float angle = player.getYRot() + index * 90.0F;
            double radians = Math.toRadians(angle);
            Vec3 position = center.add(Math.sin(radians) * 1.15D,
                    0.62D + (index & 1) * 0.22D,
                    Math.cos(radians) * 1.15D);
            PENDING_TWIN_PHASE_SLASHES.add(new PendingTwinPhaseSlash(
                    level.dimension(), player.getUUID(), null, position,
                    now + 2L + index, angle + 180.0F,
                    index % 2 == 0 ? 24.0F : -24.0F,
                    index == 1 ? 0xF4DAEA : 0x7C184A,
                    1.75F, 11,
                    twinPhaseSlashDamage(attack, true, false), true));
        }
        for (int index = 0; index < 3; index++) {
            PENDING_TWIN_PHASE_SLASHES.add(new PendingTwinPhaseSlash(
                    level.dimension(), player.getUUID(), null,
                    center.add(0.0D, 0.72D + index * 0.26D, 0.0D),
                    now + 7L + index, player.getYRot() + index * 60.0F,
                    -48.0F + index * 48.0F,
                    index == 1 ? 0xFFF4FA : 0xB72B68,
                    2.15F + index * 0.16F, 12,
                    twinPhaseSlashDamage(attack, true, true), true));
        }
    }

    static float twinPhaseSlashDamage(double attack, boolean crowd,
            boolean finisher) {
        double scale = crowd ? (finisher ? 0.16D : 0.13D) : 0.22D;
        double cap = crowd ? (finisher ? 4.0D : 3.5D) : 5.0D;
        return (float) Math.max(0.5D, Math.min(cap, attack * scale));
    }

    private static void applyTwinPhaseSlashDamage(ServerLevel level,
            ServerPlayer player, PendingTwinPhaseSlash pending) {
        if (pending.damage() <= 0.0F) return;
        if (!pending.crowd()) {
            Entity entity = pending.targetId() == null
                    ? null : level.getEntity(pending.targetId());
            if (entity instanceof LivingEntity target && target.isAlive()
                    && canAffect(player, target)
                    && target.getBoundingBox().getCenter()
                    .distanceToSqr(pending.position()) <= 24.0D * 24.0D) {
                hurtTwinPhaseTarget(level, player, target, pending.damage());
            }
            return;
        }

        int hits = 0;
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class,
                AABB.ofSize(pending.position(), 8.0D, 6.0D, 8.0D),
                candidate -> canAffect(player, candidate))) {
            if (hits++ >= 8) break;
            hurtTwinPhaseTarget(level, player, target, pending.damage());
        }
    }

    private static void hurtTwinPhaseTarget(ServerLevel level, ServerPlayer player,
            LivingEntity target, float damage) {
        // Each visible cut is a real hit. Clearing only the vanilla hurt window
        // lets the rapid cadence land while still respecting cancelled/immune damage.
        target.invulnerableTime = 0;
        SoulLegacyDamageGuard.apply(() -> target.hurt(
                level.damageSources().playerAttack(player), damage));
    }

    private static void spawnTwinPhaseVisualSlash(LivingEntity user, Vec3 position,
            float yaw, float roll, int color, float size, int lifetime) {
        EntitySlashEffect slash = AttackManager.doSlash(user, yaw,
                true, false, 0.0D);
        if (slash == null) return;
        slash.setDamage(0.0D);
        slash.setMute(true);
        slash.setIndirect(true);
        slash.setNoClip(true);
        slash.setCycleHit(false);
        slash.setKnockBack(KnockBacks.cancel);
        slash.setColor(color);
        slash.setBaseSize(size);
        slash.setLifetime(lifetime);
        slash.setRotationRoll(roll);
        slash.setPos(position.x, position.y, position.z);
        slash.setYRot(yaw);
        slash.getPersistentData().putBoolean(
                BladeTechniqueHandler.TECHNIQUE_ENTITY, true);
        slash.getPersistentData().putBoolean(
                "blade_tetra_technique_visual", true);
    }

    private static void foxDust(ServerLevel level, Vec3 center, boolean whiteFirst,
            int count, double spread) {
        DustParticleOptions white = new DustParticleOptions(
                new Vector3f(0.94F, 0.87F, 0.84F), 0.82F);
        DustParticleOptions dark = new DustParticleOptions(
                new Vector3f(0.24F, 0.045F, 0.12F), 0.90F);
        level.sendParticles(whiteFirst ? white : dark,
                center.x, center.y, center.z, count,
                spread, spread * 0.72D, spread, 0.025D);
        level.sendParticles(whiteFirst ? dark : white,
                center.x, center.y, center.z, Math.max(6, count / 2),
                spread * 0.72D, spread * 0.52D, spread * 0.72D, 0.018D);
    }

    private static LivingEntity acquireHuntTarget(ServerPlayer player,
            ISlashBladeState state) {
        Entity locked = state.getTargetEntity(player.level());
        if (locked instanceof LivingEntity living
                && player.distanceToSqr(living) <= 24.0D * 24.0D
                && player.hasLineOfSight(living)
                && canAffect(player, living)) {
            return living;
        }
        Vec3 look = player.getLookAngle().normalize();
        AABB search = player.getBoundingBox()
                .expandTowards(look.scale(24.0D)).inflate(4.0D);
        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;
        for (LivingEntity candidate : player.level().getEntitiesOfClass(
                LivingEntity.class, search,
                target -> canAffect(player, target)
                        && player.hasLineOfSight(target))) {
            Vec3 delta = candidate.getBoundingBox().getCenter()
                    .subtract(player.getEyePosition());
            double distance = delta.length();
            if (distance <= 0.001D || distance > 24.0D) {
                continue;
            }
            double alignment = look.dot(delta.scale(1.0D / distance));
            if (alignment < 0.82D) {
                continue;
            }
            double score = distance * (2.0D - alignment);
            if (score < bestScore) {
                best = candidate;
                bestScore = score;
            }
        }
        return best;
    }

    private static Vec3 huntEndpoint(ServerLevel level, PendingHunt pending) {
        if (pending.targetId == null) {
            return pending.aim;
        }
        Entity entity = level.getEntity(pending.targetId);
        if (!(entity instanceof LivingEntity target) || !target.isAlive()) {
            return pending.aim;
        }
        return pending.aim.lerp(target.getBoundingBox().getCenter(), 0.35D);
    }

    private static Vec3 huntPath(Vec3 start, Vec3 end,
            double progress, double direction) {
        Vec3 delta = end.subtract(start);
        Vec3 side = new Vec3(-delta.z, 0.0D, delta.x);
        if (side.lengthSqr() < 0.0001D) {
            side = new Vec3(1.0D, 0.0D, 0.0D);
        } else {
            side = side.normalize();
        }
        double arc = Math.min(6.0D, Math.max(2.2D, delta.length() * 0.34D));
        Vec3 control = start.add(end).scale(0.5D)
                .add(side.scale(arc * direction))
                .add(0.0D, 1.15D, 0.0D);
        double inverse = 1.0D - progress;
        Vec3 curve = start.scale(inverse * inverse)
                .add(control.scale(2.0D * inverse * progress))
                .add(end.scale(progress * progress));
        // The two foxes cross on opposite sides of the target instead of
        // collapsing into a single indistinguishable hit point.
        return curve.add(side.scale(direction * 0.58D * progress))
                .add(0.0D, direction > 0.0D ? 0.22D * progress : -0.08D * progress,
                        0.0D);
    }

    private static LivingEntity huntVictim(ServerLevel level, ServerPlayer player,
            PendingHunt pending, Vec3 endpoint) {
        if (pending.targetId != null) {
            Entity entity = level.getEntity(pending.targetId);
            if (entity instanceof LivingEntity living && canAffect(player, living)
                    && living.distanceToSqr(endpoint) <= 4.5D * 4.5D) {
                return living;
            }
        }
        return level.getEntitiesOfClass(LivingEntity.class,
                        new AABB(endpoint, endpoint).inflate(1.75D),
                        target -> canAffect(player, target))
                .stream().min(java.util.Comparator.comparingDouble(
                        target -> target.distanceToSqr(endpoint))).orElse(null);
    }

    private static boolean pathBlocked(ServerLevel level, LivingEntity owner,
            Vec3 from, Vec3 to) {
        return level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, owner)).getType() != HitResult.Type.MISS;
    }

    private static void sendHuntVfx(ServerLevel level, ServerPlayer player,
            LivingEntity target, Vec3 start, Vec3 end, int type,
            int duration, float intensity) {
        BladeTechniqueVfxPacket packet = new BladeTechniqueVfxPacket(type,
                start.x, start.y, start.z, end.x, end.y, end.z,
                player.getYRot(), intensity, player.getId(),
                target == null ? -1 : target.getId(), duration, level.random.nextInt());
        for (ServerPlayer viewer : level.players()) {
            if (viewer == player || viewer.distanceToSqr(end) <= 72.0D * 72.0D) {
                ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> viewer), packet);
            }
        }
    }

    private static void foxTrail(ServerLevel level, Vec3 position,
            boolean white) {
        DustParticleOptions dust = white
                ? new DustParticleOptions(new Vector3f(0.94F, 0.87F, 0.84F), 0.72F)
                : new DustParticleOptions(new Vector3f(0.24F, 0.045F, 0.12F), 0.78F);
        level.sendParticles(dust, position.x, position.y, position.z,
                7, 0.13D, 0.13D, 0.13D, 0.008D);
    }

    private static void clearPursuit(CompoundTag tag) {
        tag.remove(PURSUIT_TARGET);
        tag.remove(PURSUIT_DIRECTION_X);
        tag.remove(PURSUIT_DIRECTION_Z);
        tag.remove(LEGACY_PURSUIT_COMBO);
        tag.remove(PURSUIT_EXPIRES);
    }

    private static Vec3 pursuitDirection(LivingEntity player, LivingEntity target) {
        Vec3 direction = player.position().subtract(target.position())
                .multiply(1.0D, 0.0D, 1.0D);
        if (direction.lengthSqr() < 0.0001D) {
            direction = Vec3.directionFromRotation(0.0F, player.getYRot())
                    .multiply(-1.0D, 0.0D, -1.0D);
        }
        return direction.normalize();
    }

    static boolean formsPincer(Vec3 first, Vec3 second, float targetWidth) {
        if (first.lengthSqr() < 0.5D || second.lengthSqr() < 0.5D) return false;
        double maximumDot = targetWidth >= 2.5F
                ? Math.cos(Math.toRadians(80.0D))
                : Math.cos(Math.toRadians(100.0D));
        return first.normalize().dot(second.normalize()) <= maximumDot;
    }

    private static double pursuitDamage(LivingEntity player) {
        return Math.max(0.5D, player.getAttributeValue(Attributes.ATTACK_DAMAGE) * 0.55D);
    }

    private static float directionYaw(Vec3 direction) {
        return (float) Math.toDegrees(Math.atan2(-direction.x, direction.z));
    }

    private static void sendPursuitVfx(ServerLevel level, ServerPlayer player,
            LivingEntity target, Vec3 direction, int type, int duration, float intensity) {
        Vec3 center = target.getBoundingBox().getCenter();
        double radius = Math.max(0.85D, target.getBbWidth() * 0.5D + 0.55D);
        Vec3 anchor = center.add(direction.scale(radius));
        BladeTechniqueVfxPacket packet = new BladeTechniqueVfxPacket(type,
                anchor.x, anchor.y, anchor.z, center.x, center.y, center.z,
                player.getYRot(), intensity, -1, target.getId(), duration,
                level.random.nextInt());
        for (ServerPlayer viewer : level.players()) {
            if (viewer == player || viewer.distanceToSqr(center) <= 72.0D * 72.0D) {
                ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> viewer), packet);
            }
        }
    }

    private static void sendTwinPhaseVfx(ServerLevel level, ServerPlayer player,
            LivingEntity target, Vec3 start, Vec3 end, int type,
            int duration, float intensity) {
        BladeTechniqueVfxPacket packet = new BladeTechniqueVfxPacket(type,
                start.x, start.y, start.z, end.x, end.y, end.z,
                player.getYRot(), intensity, player.getId(),
                target == null ? -1 : target.getId(), duration,
                level.random.nextInt());
        for (ServerPlayer viewer : level.players()) {
            if (viewer == player || viewer.distanceToSqr(start) <= 72.0D * 72.0D) {
                ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> viewer), packet);
            }
        }
    }

    private static double fusionDamage(LivingEntity player, double ratio, double cap) {
        return Math.max(0.5D, Math.min(cap,
                player.getAttributeValue(Attributes.ATTACK_DAMAGE) * ratio));
    }

    private static Vec3 crowdCenter(List<LivingEntity> targets) {
        Vec3 sum = Vec3.ZERO;
        for (LivingEntity target : targets) {
            sum = sum.add(target.position());
        }
        return targets.isEmpty() ? Vec3.ZERO
                : sum.scale(1.0D / targets.size()).add(0.0D, 0.08D, 0.0D);
    }

    private static boolean canAffect(LivingEntity owner, LivingEntity target) {
        if (target == owner || !target.isAlive() || owner.isAlliedTo(target)) {
            return false;
        }
        if (target instanceof TamableAnimal tame && tame.isOwnedBy(owner)) {
            return false;
        }
        return !(owner instanceof Player player && target instanceof Player other)
                || player.canHarmPlayer(other);
    }

    private static final class PendingHunt {
        final ResourceKey<Level> dimension;
        final UUID playerId;
        final UUID targetId;
        final Vec3 start;
        final Vec3 aim;
        final long startTick;
        final float yaw;
        boolean whiteHit;
        boolean blackHit;
        boolean whiteBlocked;
        boolean blackBlocked;

        PendingHunt(ResourceKey<Level> dimension, UUID playerId, UUID targetId,
                Vec3 start, Vec3 aim, long startTick, float yaw) {
            this.dimension = dimension;
            this.playerId = playerId;
            this.targetId = targetId;
            this.start = start;
            this.aim = aim;
            this.startTick = startTick;
            this.yaw = yaw;
        }
    }

    private record PendingPursuit(ResourceKey<Level> dimension, UUID playerId,
            UUID targetId, long dueTick, double damage, Vec3 markedDirection,
            Vec3 triggerDirection) {
    }

    private record PendingTwinPhase(ResourceKey<Level> dimension, UUID playerId,
            UUID targetId, long dueTick, boolean crowd, Vec3 impactCenter) {
    }

    private record PendingTwinPhaseSlash(ResourceKey<Level> dimension,
            UUID playerId, UUID targetId, Vec3 position, long dueTick, float yaw,
            float roll, int color, float size, int lifetime, float damage,
            boolean crowd) {
    }

    private LegacyFusionHandler() {
    }
}
