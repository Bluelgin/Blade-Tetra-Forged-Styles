package dev.bladetetra.easteregg;

import dev.bladetetra.BladeTetra;
import dev.bladetetra.combat.BladeTechniqueHandler;
import dev.bladetetra.combat.ModComboStates;
import dev.bladetetra.config.GameplayConfig;
import dev.bladetetra.item.ModularSlashBladeItem;
import dev.bladetetra.network.AwakenedBladeVfxPacket;
import dev.bladetetra.network.AwakenedMirrorEchoPacket;
import dev.bladetetra.network.AwakenedBladeStatePacket;
import dev.bladetetra.network.ModNetwork;
import mods.flammpfeil.slashblade.entity.IShootable;
import mods.flammpfeil.slashblade.entity.EntitySlashEffect;
import mods.flammpfeil.slashblade.util.AttackManager;
import mods.flammpfeil.slashblade.util.KnockBacks;
import mods.flammpfeil.slashblade.event.SlashBladeEvent;
import mods.flammpfeil.slashblade.slasharts.SlashArts;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ambient.AmbientCreature;
import net.minecraft.world.entity.animal.AbstractGolem;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * V2 awakened combat. Validated real damage builds the four distinct resources;
 * an actual successful native Slash Art spends them. No client-supplied hit or
 * position can cause damage. Single-soul and first-inscribed dual-soul blades
 * choose one primary ability; old save/unlock tags remain authoritative.
 *
 * All transient state is server-side, bounded, and keyed by player + blade UUID.
 * Secondary hits never recursively award marks or trigger other soul abilities.
 */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class AwakenedBladeCombat {
    private static final String BLADE_ID = "blade_tetra_awakened_v2_id";
    private static final String SAKURA_DISPERSED = "blade_tetra_awakened_v2_sakura_dispersed";
    private static final int EXPIRE_TICKS = 160;
    private static final String VISUAL_CUT = "blade_tetra_v2_mirror_visual";
    private static final int CAUSAL_LIMIT = 64;
    private static final int STATE_LIMIT = 512;
    private static final Map<Key, State> STATES = new HashMap<>();
    private static final List<PendingReplay> REPLAYS = new ArrayList<>();
    private static final List<PendingCausal> CAUSAL = new ArrayList<>();
    private static final List<PendingPetal> PETALS = new ArrayList<>();
    private static final List<PendingSakuraWave> SAKURA_WAVES = new ArrayList<>();
    private static int ticks;
    private static int nextMirrorCutId = 1;

    /** Guards only legacy combat callbacks; never guards ritual or unlock code. */
    public static boolean replacesLegacy() {
        return GameplayConfig.ENABLE_AWAKENED_V2.get();
    }

    /** Called exclusively after the corresponding server-side ritual succeeds. */
    public static void awakening(ServerPlayer player, SoulLegacyState.Legacy soul) {
        if (!replacesLegacy() || soul == SoulLegacyState.Legacy.NONE) return;
        Vec3 pos = player.position().add(0, 1.1D, 0);
        visual(player.serverLevel(), soul, AwakenedBladeVfxPacket.AWAKEN,
                pos, pos, 0);
    }

    /** Visual-only native slash effects cannot hurt or stagger anyone. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void cancelReplayVisualDamage(LivingAttackEvent event) {
        Entity direct = event.getSource().getDirectEntity();
        if (direct instanceof EntitySlashEffect
                && direct.getPersistentData().getBoolean(VISUAL_CUT)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRealDamage(LivingDamageEvent event) {
        if (!replacesLegacy() || !(event.getEntity().level() instanceof ServerLevel level)
                || !Float.isFinite(event.getAmount()) || event.getAmount() <= 0.0F
                || BladeTechniqueHandler.isTechniqueDamage(event.getSource())
                || SoulLegacyDamageGuard.isSecondary(event.getSource())) return;
        ServerPlayer player = attacker(event);
        if (player == null || !canAffect(player, event.getEntity())) return;
        ItemStack blade = player.getMainHandItem();
        SoulLegacyState.Legacy soul = SoulLegacyState.active(blade);
        if (!SoulLegacyState.isActive(blade, soul)) return;
        State state = state(player, blade);
        long now = level.getGameTime();
        LivingEntity target = event.getEntity();
        if (!target.getUUID().equals(state.targetId) || now - state.lastHit > EXPIRE_TICKS) {
            state.targetId = target.getUUID();
            state.scarDirections = 0;
            state.scarMagnitude = 0;
            state.mirror.clear();
            state.sakuraCharge = 0;
            state.raikiriCharge = 0;
        }
        state.dimension = level.dimension();
        state.lastHit = now;
        state.lastDamage = Math.max(0.0F, Math.min(32.0F, event.getAmount()));
        Vec3 center = target.getBoundingBox().getCenter();
        switch (soul) {
            case AKATSUKI -> {
                // Relative angle forces the player to reposition rather than spam one slash.
                int direction = AwakenedCombatMath.scarSector(
                        player.getX() - target.getX(), player.getZ() - target.getZ());
                int bit = 1 << direction;
                if ((state.scarDirections & bit) == 0) {
                    state.scarDirections |= bit;
                    state.scarMagnitude = Math.min(32.0F,
                            state.scarMagnitude + state.lastDamage * 0.32F);
                    visual(level, soul, AwakenedBladeVfxPacket.MARK,
                            center, center, direction);
                }
            }
            case KYOUKA -> {
                // Record validated real hits, not raw client inputs or phantom entities.
                ResourceLocation combo = blade.getCapability(ModularSlashBladeItem.BLADESTATE)
                        .map(value -> value.getComboSeq()).orElse(null);
                // Capture a genuine native combo and pose at a server-confirmed hit.
                if (combo != null) {
                    int cutId = nextMirrorCutId++;
                    state.mirror.add(new RecordedCut(now, state.lastDamage,
                            player.getYRot(), player.getXRot(), combo,
                            player.position(), center, cutId));
                    sendMirrorPose(level, player, cutId, AwakenedMirrorEchoPacket.CAPTURE,
                            player.position(), player.getYRot());
                    if (state.mirror.size() > 3) state.mirror.remove(0);
                }
                visual(level, soul, AwakenedBladeVfxPacket.MARK, center, center,
                        Math.round(player.getYRot()));
            }
            case SENBONZAKURA -> {
                if (blade.getOrCreateTag().getBoolean(SAKURA_DISPERSED)) {
                    state.sakuraCharge = Math.min(6, state.sakuraCharge + 1);
                    int formation = AwakenedCombatMath.sakuraFormation(
                            !player.onGround(), player.isSprinting());
                    visual(level, soul, AwakenedBladeVfxPacket.MARK,
                            center, center, formation);
                    // An independent countercut every three verified hits,
                    // without spawning extra AI entities or a second scan.
                    if (state.sakuraCharge % 3 == 0 && now >= state.petalUntil
                            && PETALS.size() < 64) {
                        float petal = Math.min(3.0F, state.lastDamage * 0.18F);
                        PETALS.add(new PendingPetal(level.dimension(), player.getUUID(),
                                id(blade), target.getUUID(), now + 3L, petal));
                        state.petalUntil = now + 10;
                    }
                }
            }
            case RAIKIRI -> {
                boolean flashFollowup = state.stepChain > 0
                        && !state.stepConfirmed
                        && now >= state.lastStep && now - state.lastStep <= 22;
                if (flashFollowup) {
                    state.stepConfirmed = true;
                    state.raikiriCharge = Math.min(4,
                            state.raikiriCharge + Math.min(2, state.stepChain));
                    visual(level, soul, AwakenedBladeVfxPacket.MARK,
                            center, center, state.stepChain);
                } else {
                    state.raikiriCharge = Math.min(4, state.raikiriCharge + 1);
                }
            }
            default -> {
            }
        }
        syncHud(player, soul, blade, state, false);
    }

    @SubscribeEvent
    public static void onSlashArt(SlashBladeEvent.PerformSlashArtEvent event) {
        if (!replacesLegacy() || event.getType() != SlashArts.ArtsType.Success
                || !(event.getEntityLiving() instanceof ServerPlayer player)) return;
        ItemStack blade = player.getMainHandItem();
        SoulLegacyState.Legacy soul = SoulLegacyState.active(blade);
        if (!SoulLegacyState.isActive(blade, soul)) return;
        ServerLevel level = player.serverLevel();
        State state = state(player, blade);
        long now = level.getGameTime();
        switch (soul) {
            case AKATSUKI -> releaseAkatsuki(level, player, blade, state, now);
            case KYOUKA -> releaseKyouka(level, player, blade, state, now);
            case SENBONZAKURA -> releaseSenbonzakura(level, player, blade, state, now);
            case RAIKIRI -> releaseRaikiri(level, player, blade, state, now);
            default -> {
            }
        }
        syncHud(player, soul, blade, state, false);
    }

    private static void releaseAkatsuki(ServerLevel level, ServerPlayer player,
            ItemStack blade, State state, long now) {
        if (now < state.cooldownUntil || now - state.lastHit > EXPIRE_TICKS) return;
        int marks = Integer.bitCount(state.scarDirections);
        if (!AwakenedCombatMath.hasCausalCross(state.scarDirections)
                || CAUSAL.size() >= CAUSAL_LIMIT) return;
        LivingEntity target = target(level, player, state, 24.0D);
        if (target == null) return;
        // The causal hit is delayed; swapping blades, losing sight or escaping
        // the marked area breaks fate instead of granting guaranteed bonus damage.
        float damage = Math.min(30.0F,
                AwakenedCombatMath.causalCut(state.lastDamage, marks)
                        + state.scarMagnitude * 0.25F);
        if (damage <= 0 || !AwakenedRankCost.trySpend(player, blade,
                AwakenedRankCost.Cast.AKATSUKI)) return;
        Vec3 center = target.getBoundingBox().getCenter();
        CAUSAL.add(new PendingCausal(level.dimension(), player.getUUID(),
                id(blade), target.getUUID(), center, now + 33L, damage));
        state.cooldownUntil = now + 100L;
        int directions = state.scarDirections;
        state.scarDirections = 0;
        state.scarMagnitude = 0;
        visual(level, SoulLegacyState.Legacy.AKATSUKI,
                AwakenedBladeVfxPacket.RELEASE, center, center, directions);
        releaseAkatsukiFusion(level, player, blade, target, center, now, damage);
    }

    /** Three genuinely different follow-ups share the *same* one Rank debit. */
    private static void releaseAkatsukiFusion(ServerLevel level, ServerPlayer player,
            ItemStack blade, LivingEntity target, Vec3 anchor, long now, float budget) {
        SoulResonance.Resonance fusion = SoulResonance.resolve(blade);
        if (!SoulResonance.isFormed(blade, fusion)) return;
        switch (fusion) {
            case MIRROR_MOON -> {
                if (REPLAYS.size() >= 96) return;
                ResourceLocation combo = blade.getCapability(ModularSlashBladeItem.BLADESTATE)
                        .map(b -> b.getComboSeq()).orElse(null);
                if (combo == null) return;
                // A second cut is reflected from the original causal crossing,
                // and misses if the target escapes the recorded location.
                REPLAYS.add(new PendingReplay(level.dimension(), player.getUUID(),
                        id(blade), target.getUUID(), now + 39L,
                        Math.min(5.0F, budget * 0.15F), player.getYRot(),
                        player.getXRot(), combo, player.position(), anchor, -1));
                visual(level, SoulLegacyState.Legacy.KYOUKA,
                        AwakenedBladeVfxPacket.RELEASE, anchor, anchor, 1);
            }
            case FUNERAL_BLOSSOM -> {
                if (SAKURA_WAVES.size() >= 94) return;
                List<LivingEntity> bystanders = level.getEntitiesOfClass(
                                LivingEntity.class, new AABB(anchor, anchor).inflate(4.0D),
                                entity -> entity != target && canAffect(player, entity))
                        .stream().sorted(Comparator.comparingDouble(
                                entity -> entity.distanceToSqr(anchor))).limit(2).toList();
                int i = 0;
                for (LivingEntity victim : bystanders) {
                    SAKURA_WAVES.add(new PendingSakuraWave(level.dimension(),
                            player.getUUID(), id(blade), victim.getUUID(),
                            victim.getBoundingBox().getCenter(), now + 36L + i++ * 3L,
                            Math.min(3.0F, budget * 0.10F),
                            SoulResonance.Resonance.FUNERAL_BLOSSOM));
                }
                if (!bystanders.isEmpty()) {
                    visual(level, SoulLegacyState.Legacy.SENBONZAKURA,
                            AwakenedBladeVfxPacket.RELEASE, anchor, anchor, 0);
                }
            }
            case CRIMSON_THUNDER -> {
                // A safe positioning lunge for the player, without another hit.
                Vec3 from = player.position();
                Vec3 to = anchor.subtract(from).multiply(1, 0, 1);
                if (to.lengthSqr() > 4.0D && to.lengthSqr() < 64.0D) {
                    Vec3 moved = collisionCheckedStep(level, player, from,
                            to.normalize(), Math.min(3.5D, to.length() - 1.3D));
                    if (moved.distanceToSqr(from) > 0.24D) {
                        player.connection.teleport(moved.x, moved.y, moved.z,
                                player.getYRot(), player.getXRot());
                        visual(level, SoulLegacyState.Legacy.RAIKIRI,
                                AwakenedBladeVfxPacket.STEP,
                                from.add(0, 1, 0), moved.add(0, 1, 0), 1);
                    }
                }
            }
            default -> { }
        }
    }

    private static void releaseKyouka(ServerLevel level, ServerPlayer player,
            ItemStack blade, State state, long now) {
        if (now < state.cooldownUntil || now - state.lastHit > EXPIRE_TICKS
                || state.mirror.size() < 2 || REPLAYS.size() >= 96) return;
        LivingEntity target = target(level, player, state, 24.0D);
        if (target == null) return;
        // Reserve a single cast payment, not one debit per mirrored image.
        if (!AwakenedRankCost.trySpend(player, blade,
                AwakenedRankCost.Cast.KYOUKA)) return;
        int number = 0;
        for (RecordedCut cut : state.mirror) {
            // At most three staggered mirror strikes, each within its own budget.
            float damage = AwakenedCombatMath.mirrorCut(cut.damage);
            if (damage > 0) REPLAYS.add(new PendingReplay(level.dimension(),
                    player.getUUID(), id(blade), target.getUUID(),
                    now + 24L + number * 5L, damage, cut.yaw, cut.pitch,
                    cut.combo, cut.origin, cut.targetAnchor, cut.cutId));
            number++;
        }
        releaseKyoukaFusion(level, player, blade, target, state.mirror, now);
        state.mirror.clear();
        state.cooldownUntil = now + 90L;
        Vec3 center = target.getBoundingBox().getCenter();
        visual(level, SoulLegacyState.Legacy.KYOUKA,
                AwakenedBladeVfxPacket.RELEASE, center, center, number);
    }

    private static void releaseKyoukaFusion(ServerLevel level, ServerPlayer player,
            ItemStack blade, LivingEntity target, List<RecordedCut> records, long now) {
        SoulResonance.Resonance fusion = SoulResonance.resolve(blade);
        if (!SoulResonance.isFormed(blade, fusion)) return;
        switch (fusion) {
            case MIRROR_BLOSSOM -> {
                // Petals materialize where original mirrored attacks landed.
                // Every attack preserves its own spatial escape window.
                int limit = Math.min(2, Math.min(records.size(), 96 - SAKURA_WAVES.size()));
                for (int i = 0; i < limit; i++) {
                    RecordedCut cut = records.get(i);
                    SAKURA_WAVES.add(new PendingSakuraWave(level.dimension(),
                            player.getUUID(), id(blade), target.getUUID(),
                            cut.targetAnchor, now + 31L + i * 5L,
                            Math.min(3.0F, cut.damage * 0.13F),
                            SoulResonance.Resonance.MIRROR_BLOSSOM));
                }
                if (limit > 0) {
                    Vec3 center = target.getBoundingBox().getCenter();
                    visual(level, SoulLegacyState.Legacy.SENBONZAKURA,
                            AwakenedBladeVfxPacket.RELEASE, center, center, 2);
                }
            }
            case MIRROR_THUNDER -> {
                // Mirror cuts stay at the old position while the swordsman
                // safely side-steps; the mirror cannot drag a foe behind walls.
                Vec3 look = player.getLookAngle().multiply(1, 0, 1);
                if (look.lengthSqr() < 0.01D) return;
                Vec3 side = new Vec3(-look.z, 0, look.x).normalize();
                Vec3 from = player.position();
                Vec3 moved = collisionCheckedStep(level, player, from, side, 2.0D);
                if (moved.distanceToSqr(from) > 0.24D) {
                    player.connection.teleport(moved.x, moved.y, moved.z,
                            player.getYRot(), player.getXRot());
                    visual(level, SoulLegacyState.Legacy.RAIKIRI,
                            AwakenedBladeVfxPacket.STEP,
                            from.add(0, 1, 0), moved.add(0, 1, 0), 1);
                }
            }
            default -> { }
        }
    }

    private static void releaseSenbonzakura(ServerLevel level, ServerPlayer player,
            ItemStack blade, State state, long now) {
        CompoundTag tag = blade.getOrCreateTag();
        if (player.isShiftKeyDown()) {
            if (now < state.stanceUntil) return;
            boolean dispersed = !tag.getBoolean(SAKURA_DISPERSED);
            tag.putBoolean(SAKURA_DISPERSED, dispersed);
            state.stanceUntil = now + 12L;
            Vec3 pos = player.position().add(0, 1, 0);
            visual(level, SoulLegacyState.Legacy.SENBONZAKURA,
                    AwakenedBladeVfxPacket.STANCE, pos, pos, dispersed ? 1 : 0);
            return;
        }
        if (!tag.getBoolean(SAKURA_DISPERSED) || state.sakuraCharge < 3
                || now < state.cooldownUntil || now - state.lastHit > EXPIRE_TICKS) return;
        LivingEntity primary = target(level, player, state, 20.0D);
        if (primary == null) return;
        int formation = AwakenedCombatMath.sakuraFormation(
                !player.onGround(), player.isSprinting());
        Vec3 center = formation == 0
                ? player.getBoundingBox().getCenter() : primary.getBoundingBox().getCenter();
        Vec3 forward = player.getLookAngle().multiply(1, 0, 1).normalize();
        List<LivingEntity> enemies = level.getEntitiesOfClass(LivingEntity.class,
                        new AABB(center, center).inflate(formation == 1 ? 7.0D : 4.5D),
                        entity -> canAffect(player, entity)
                                && (formation != 1 || (entity.getBoundingBox().getCenter()
                                        .subtract(player.position()).multiply(1, 0, 1)
                                        .normalize().dot(forward) >= 0.55D)))
                .stream().sorted(Comparator.comparingDouble(primary::distanceToSqr))
                .limit(5).toList();
        if (enemies.isEmpty()) return;
        // One bounded damage budget, shared by all decorative sword blades.
        float budget = AwakenedCombatMath.petalBudget(
                state.lastDamage, state.sakuraCharge);
        if (budget <= 0 || SAKURA_WAVES.size() + enemies.size() > 96
                || !AwakenedRankCost.trySpend(player, blade,
                        AwakenedRankCost.Cast.SAKURA_ARRAY)) return;
        float each = budget / enemies.size();
        int step = 0;
        for (LivingEntity enemy : enemies) {
            SAKURA_WAVES.add(new PendingSakuraWave(level.dimension(), player.getUUID(),
                    id(blade), enemy.getUUID(), enemy.getBoundingBox().getCenter(),
                    now + 28L + (step++ * 3L), each,
                    SoulResonance.isFormed(blade, SoulResonance.Resonance.THUNDER_BLOSSOM)
                            ? SoulResonance.Resonance.THUNDER_BLOSSOM
                            : SoulResonance.Resonance.NONE));
        }
        state.sakuraCharge = 0;
        state.cooldownUntil = now + 110L;
        Vec3 targetCenter = primary.getBoundingBox().getCenter();
        Vec3 origin = formation == 2 ? targetCenter : player.position();
        visual(level, SoulLegacyState.Legacy.SENBONZAKURA,
                AwakenedBladeVfxPacket.RELEASE, origin, targetCenter, formation);
    }

    private static void releaseRaikiri(ServerLevel level, ServerPlayer player,
            ItemStack blade, State state, long now) {
        if (player.isShiftKeyDown()) {
            if (now < state.stepUntil) return;
            Vec3 from = player.position();
            Vec3 look = player.getLookAngle();
            Vec3 horizontal = new Vec3(look.x, 0.0D, look.z);
            if (horizontal.lengthSqr() < 0.0001D) return;
            Vec3 direction = horizontal.normalize();
            Vec3 moved = collisionCheckedStep(level, player, from, direction, 3.5D);
            if (moved.distanceToSqr(from) < 0.24D) return;
            // One Rank payment opens a full hit-confirmed three-step sequence.
            // Charging again after every step would immediately demote a B
            // player below B and make the follow-up mechanically impossible.
            boolean continuing = AwakenedCombatMath.isThunderFollowup(
                    state.stepChain, state.stepConfirmed, state.lastStep, now);
            if (!continuing && !AwakenedRankCost.trySpend(player, blade,
                    AwakenedRankCost.Cast.RAIKIRI_STEP)) return;
            player.connection.teleport(moved.x, moved.y, moved.z,
                    player.getYRot(), player.getXRot());
            state.stepChain = AwakenedCombatMath.nextThunderChain(
                    state.stepChain, state.stepConfirmed, state.lastStep, now);
            state.stepConfirmed = false;
            state.lastStep = now;
            state.stepUntil = now + (state.stepChain > 1 ? 6L : 10L);
            visual(level, SoulLegacyState.Legacy.RAIKIRI,
                    AwakenedBladeVfxPacket.STEP,
                    from.add(0, 1, 0), moved.add(0, 1, 0), state.stepChain);
            return;
        }
        if (state.raikiriCharge < 2 || now < state.cooldownUntil
                || now - state.lastHit > EXPIRE_TICKS) return;
        LivingEntity primary = target(level, player, state, 8.0D);
        if (primary == null || !player.hasLineOfSight(primary)) return;
        float damage = AwakenedCombatMath.thunderFinisher(
                state.lastDamage, state.raikiriCharge);
        if (damage <= 0 || !AwakenedRankCost.trySpend(player, blade,
                AwakenedRankCost.Cast.RAIKIRI_FINISH)) return;
        Vec3 from = player.position();
        Vec3 delta = primary.position().subtract(from).multiply(1, 0, 1);
        Vec3 moved = from;
        if (delta.lengthSqr() > 1.44D) {
            double travel = Math.min(3.5D, delta.length() - 1.15D);
            moved = collisionCheckedStep(level, player, from, delta.normalize(), travel);
        }
        // The thrust is real movement, not a fabricated visual homing attack.
        if (moved.distanceToSqr(from) > 0.24D) {
            player.connection.teleport(moved.x, moved.y, moved.z,
                    player.getYRot(), player.getXRot());
        }
        if (!secondaryHit(level, player, primary, damage)) return;
        int chain = state.stepChain;
        state.raikiriCharge = 0;
        state.stepChain = 0;
        state.stepConfirmed = false;
        state.cooldownUntil = now + 75L;
        Vec3 trailEnd = moved.distanceToSqr(from) > 0.24D
                ? moved.add(0, 1, 0) : primary.getBoundingBox().getCenter();
        visual(level, SoulLegacyState.Legacy.RAIKIRI,
                AwakenedBladeVfxPacket.RELEASE,
                from.add(0, 1, 0), trailEnd, chain);
    }

    /** Identical continuous AABB collision checks for manual steps and finishers. */
    private static Vec3 collisionCheckedStep(ServerLevel level, ServerPlayer player,
            Vec3 from, Vec3 horizontalDirection, double maxTravel) {
        if (!Double.isFinite(maxTravel) || maxTravel <= 0
                || horizontalDirection.lengthSqr() < 0.0001D) return from;
        Vec3 direction = horizontalDirection.normalize();
        Vec3 moved = from;
        int checks = Math.min(7, (int) Math.ceil(Math.min(3.5D, maxTravel) * 2.0D));
        for (int step = 1; step <= checks; step++) {
            double distance = Math.min(maxTravel, step * 0.5D);
            Vec3 next = from.add(direction.scale(distance));
            if (!level.noCollision(player, player.getBoundingBox()
                    .move(next.subtract(from)))) break;
            moved = next;
        }
        return moved;
    }

    /**
     * An occasional authoritative heartbeat keeps the owner's HUD visible
     * after login, dimension changes and weapon swaps without scanning entities.
     * It also clears gauges once their real-hit window expires.
     */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !replacesLegacy()
                || !(event.player instanceof ServerPlayer player)) return;
        ItemStack blade = player.getMainHandItem();
        SoulLegacyState.Legacy soul = SoulLegacyState.active(blade);
        if (!SoulLegacyState.isActive(blade, soul)) return;
        if (soul == SoulLegacyState.Legacy.SENBONZAKURA
                && blade.getOrCreateTag().getBoolean(SAKURA_DISPERSED)
                && player.tickCount % 20 == 0) {
            // One low-rate, bounded ambient event per dispersed player/second.
            // Renders orbiting pieces, never creates real projectile entities.
            Vec3 center = player.position().add(0, 1.0D, 0);
            visual(player.serverLevel(), soul, AwakenedBladeVfxPacket.MARK,
                    center, center, 9);
        }
        if (player.tickCount % 80 != 0) return;
        State state = state(player, blade);
        long now = player.level().getGameTime();
        if (now - state.lastHit > EXPIRE_TICKS) {
            state.targetId = null;
            state.lastDamage = 0;
            state.scarDirections = 0;
            state.scarMagnitude = 0;
            state.mirror.clear();
            state.sakuraCharge = 0;
            state.raikiriCharge = 0;
            state.stepChain = 0;
            state.stepConfirmed = false;
        }
        syncHud(player, soul, blade, state, true);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!replacesLegacy()) {
            REPLAYS.clear();
            CAUSAL.clear();
            PETALS.clear();
            SAKURA_WAVES.clear();
            STATES.clear();
            return;
        }
        MinecraftServer server = event.getServer();
        Iterator<PendingSakuraWave> waves = SAKURA_WAVES.iterator();
        while (waves.hasNext()) {
            PendingSakuraWave pending = waves.next();
            ServerLevel level = server.getLevel(pending.dimension);
            if (level == null) { waves.remove(); continue; }
            if (level.getGameTime() < pending.when) continue;
            waves.remove();
            ServerPlayer player = server.getPlayerList().getPlayer(pending.player);
            if (player == null || player.level() != level
                    || !pending.blade.equals(readId(player.getMainHandItem()))
                    || !SoulLegacyState.isActive(player.getMainHandItem(),
                            pending.fusion == SoulResonance.Resonance.FUNERAL_BLOSSOM
                                    ? SoulLegacyState.Legacy.AKATSUKI
                                    : pending.fusion == SoulResonance.Resonance.MIRROR_BLOSSOM
                                            ? SoulLegacyState.Legacy.KYOUKA
                                            : SoulLegacyState.Legacy.SENBONZAKURA)
                    || (pending.fusion == SoulResonance.Resonance.NONE
                            ? !player.getMainHandItem().getOrCreateTag()
                                    .getBoolean(SAKURA_DISPERSED)
                            : !SoulResonance.isFormed(player.getMainHandItem(), pending.fusion)))
                continue;
            Entity found = level.getEntity(pending.target);
            if (!(found instanceof LivingEntity target) || !canAffect(player, target)
                    || target.getBoundingBox().getCenter().distanceToSqr(pending.anchor) > 25D
                    || player.distanceToSqr(target) > 576D || !player.hasLineOfSight(target))
                continue;
            if (secondaryHit(level, player, target, pending.damage)) {
                Vec3 center = target.getBoundingBox().getCenter();
                visual(level, SoulLegacyState.Legacy.SENBONZAKURA,
                        AwakenedBladeVfxPacket.MARK, center, center, 2);
                if (pending.fusion == SoulResonance.Resonance.THUNDER_BLOSSOM) {
                    // The sixth dual technique traps enemies in a brief static field.
                    target.addEffect(new MobEffectInstance(
                            MobEffects.MOVEMENT_SLOWDOWN, 35, 1));
                    visual(level, SoulLegacyState.Legacy.RAIKIRI,
                            AwakenedBladeVfxPacket.MARK, center, center, 0);
                }
            }
        }
        Iterator<PendingPetal> petals = PETALS.iterator();
        while (petals.hasNext()) {
            PendingPetal pending = petals.next();
            ServerLevel level = server.getLevel(pending.dimension);
            if (level == null) { petals.remove(); continue; }
            if (level.getGameTime() < pending.when) continue;
            petals.remove();
            ServerPlayer player = server.getPlayerList().getPlayer(pending.player);
            if (player == null || player.level() != level
                    || !pending.blade.equals(readId(player.getMainHandItem()))
                    || !SoulLegacyState.isActive(player.getMainHandItem(),
                            SoulLegacyState.Legacy.SENBONZAKURA)
                    || !player.getMainHandItem().getOrCreateTag().getBoolean(SAKURA_DISPERSED))
                continue;
            Entity found = level.getEntity(pending.target);
            if (!(found instanceof LivingEntity target)
                    || !canAffect(player, target) || player.distanceToSqr(target) > 400D
                    || !player.hasLineOfSight(target)) continue;
            if (secondaryHit(level, player, target, pending.damage)) {
                Vec3 center = target.getBoundingBox().getCenter();
                visual(level, SoulLegacyState.Legacy.SENBONZAKURA,
                        AwakenedBladeVfxPacket.MARK, center, center, 0);
            }
        }
        Iterator<PendingCausal> causes = CAUSAL.iterator();
        while (causes.hasNext()) {
            PendingCausal pending = causes.next();
            ServerLevel level = server.getLevel(pending.dimension);
            if (level == null) { causes.remove(); continue; }
            if (level.getGameTime() < pending.when) continue;
            causes.remove();
            ServerPlayer player = server.getPlayerList().getPlayer(pending.player);
            if (player == null || player.level() != level
                    || !pending.blade.equals(readId(player.getMainHandItem()))
                    || !SoulLegacyState.isActive(player.getMainHandItem(),
                            SoulLegacyState.Legacy.AKATSUKI)) continue;
            Entity found = level.getEntity(pending.target);
            if (!(found instanceof LivingEntity target)
                    || target.getBoundingBox().getCenter().distanceToSqr(pending.anchor) > 25.0D
                    || !canAffect(player, target) || player.distanceToSqr(target) > 576.0D
                    || !player.hasLineOfSight(target)) continue;
            secondaryHit(level, player, target, pending.damage);
        }
        Iterator<PendingReplay> it = REPLAYS.iterator();
        while (it.hasNext()) {
            PendingReplay cut = it.next();
            ServerLevel level = server.getLevel(cut.dimension);
            if (level == null) {
                it.remove();
                continue;
            }
            if (level.getGameTime() < cut.when) continue;
            it.remove();
            ServerPlayer player = server.getPlayerList().getPlayer(cut.player);
            if (player == null || player.level() != level
                    || !cut.blade.equals(readId(player.getMainHandItem()))
                    || !SoulLegacyState.isActive(player.getMainHandItem(),
                            SoulLegacyState.Legacy.KYOUKA)) continue;
            Entity found = level.getEntity(cut.target);
            if (!(found instanceof LivingEntity target)
                    || !canAffect(player, target) || player.distanceToSqr(target) > 576D)
                continue;
            // Recorded-space attacks do not magically follow a fleeing target.
            if (!player.hasLineOfSight(target) || target.getBoundingBox().getCenter()
                    .distanceToSqr(cut.targetAnchor) > 9.0D) continue;
            if (cut.cutId >= 0) {
                sendMirrorPose(level, player, cut.cutId, AwakenedMirrorEchoPacket.PLAY,
                        cut.origin, cut.yaw);
            }
            spawnMirrorSlash(player, target, cut);
            if (secondaryHit(level, player, target, cut.damage)) {
                Vec3 center = target.getBoundingBox().getCenter();
                visual(level, SoulLegacyState.Legacy.KYOUKA,
                        AwakenedBladeVfxPacket.MARK, cut.origin.add(0, 1, 0),
                        cut.targetAnchor, Math.round(cut.yaw));
            }
        }
        if (++ticks % 100 == 0) {
            STATES.entrySet().removeIf(entry -> {
                ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey().player);
                if (player == null || !(player.level() instanceof ServerLevel level))
                    return true;
                return entry.getValue().dimension != level.dimension()
                        || level.getGameTime() - entry.getValue().lastHit > EXPIRE_TICKS * 2L;
            });
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        REPLAYS.removeIf(entry -> entry.dimension.equals(level.dimension()));
        CAUSAL.removeIf(entry -> entry.dimension.equals(level.dimension()));
        PETALS.removeIf(entry -> entry.dimension.equals(level.dimension()));
        SAKURA_WAVES.removeIf(entry -> entry.dimension.equals(level.dimension()));
        STATES.entrySet().removeIf(entry -> level.dimension().equals(entry.getValue().dimension));
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        REPLAYS.clear();
        CAUSAL.clear();
        PETALS.clear();
        SAKURA_WAVES.clear();
        STATES.clear();
        ticks = 0;
    }

    private static void spawnMirrorSlash(ServerPlayer attacker, LivingEntity target,
            PendingReplay replay) {
        // SlashBlade's native slash effect replays the recorded combo plane.
        // Zero damage plus the attack-event guard prevents phantom extra hits.
        float roll = ModComboStates.isIaidoDraw(replay.combo) ? -30.0F
                : ModComboStates.isIaidoFinish(replay.combo) ? 45.0F
                : replay.combo.getPath().contains("aerial") ? 90.0F
                : 14.0F;
        EntitySlashEffect slash = AttackManager.doSlash(attacker, replay.yaw,
                true, false, 0.0D);
        if (slash == null) return;
        slash.setDamage(0.0D);
        slash.setMute(true);
        slash.setIndirect(true);
        slash.setNoClip(true);
        slash.setCycleHit(false);
        slash.setKnockBack(KnockBacks.cancel);
        slash.setColor(0xB7E8FF);
        slash.setLifetime(8);
        slash.setBaseSize(Math.max(1.1F, Math.min(3.0F,
                target.getBbHeight() * 0.98F)));
        slash.setRotationRoll(roll);
        slash.setYRot(replay.yaw);
        slash.setPos(replay.targetAnchor.x, replay.targetAnchor.y,
                replay.targetAnchor.z);
        slash.getPersistentData().putBoolean(VISUAL_CUT, true);
    }

    private static ServerPlayer attacker(LivingDamageEvent event) {
        Entity direct = event.getSource().getDirectEntity();
        Entity source = event.getSource().getEntity();
        if (!(source instanceof ServerPlayer player)) return null;
        if (direct == player) return player;
        if (direct instanceof IShootable sword && sword.getShooter() == player) return player;
        return null;
    }

    private static boolean secondaryHit(ServerLevel level, ServerPlayer player,
            LivingEntity target, float amount) {
        if (!Float.isFinite(amount) || amount <= 0 || !canAffect(player, target))
            return false;
        // SlashBlade replays are authored hits, not a second instance of the
        // original combo damage: bypass the normal 10-tick mob hurt grace only
        // within this bounded, non-player, already-validated secondary strike.
        target.invulnerableTime = 0;
        return dev.bladetetra.combat.InheritedCombatDamage.apply(() ->
                SoulLegacyDamageGuard.apply(() -> target.hurt(
                        level.damageSources().playerAttack(player), amount)));
    }

    private static boolean canAffect(ServerPlayer player, LivingEntity target) {
        if (player == target || !target.isAlive() || target instanceof Player
                || player.isAlliedTo(target) || target.isAlliedTo(player)
                || target instanceof TamableAnimal || target instanceof AbstractVillager
                || target instanceof AbstractGolem || target instanceof Animal
                || target instanceof WaterAnimal || target instanceof AmbientCreature)
            return false;
        if (target instanceof Enemy) return true;
        return target instanceof Mob mob && (mob.getTarget() == player
                || mob.getLastHurtByMob() == player);
    }

    private static LivingEntity target(ServerLevel level, ServerPlayer player,
            State state, double reach) {
        if (state.targetId == null || state.dimension != level.dimension()) return null;
        Entity entity = level.getEntity(state.targetId);
        if (!(entity instanceof LivingEntity living)
                || !canAffect(player, living) || player.distanceToSqr(living) > reach * reach)
            return null;
        return living;
    }

    private static UUID readId(ItemStack blade) {
        CompoundTag tag = blade.getTag();
        return tag != null && tag.hasUUID(BLADE_ID) ? tag.getUUID(BLADE_ID) : null;
    }

    private static UUID id(ItemStack blade) {
        CompoundTag tag = blade.getOrCreateTag();
        if (!tag.hasUUID(BLADE_ID)) tag.putUUID(BLADE_ID, UUID.randomUUID());
        return tag.getUUID(BLADE_ID);
    }

    private static State state(ServerPlayer player, ItemStack blade) {
        Key key = new Key(player.getUUID(), id(blade));
        State found = STATES.get(key);
        if (found != null) return found;
        if (STATES.size() >= STATE_LIMIT) {
            // Evict oldest transient entry, not any weapon NBT or unlock.
            STATES.entrySet().stream().min(Comparator.comparingLong(
                    e -> e.getValue().lastHit)).ifPresent(e -> STATES.remove(e.getKey()));
        }
        State created = new State();
        created.dimension = player.serverLevel().dimension();
        STATES.put(key, created);
        return created;
    }

    private static void syncHud(ServerPlayer player, SoulLegacyState.Legacy soul,
            ItemStack blade, State state, boolean force) {
        int meter = switch (soul) {
            case AKATSUKI -> Integer.bitCount(state.scarDirections);
            case KYOUKA -> state.mirror.size();
            case SENBONZAKURA -> state.sakuraCharge;
            case RAIKIRI -> state.raikiriCharge;
            default -> 0;
        };
        int maximum = switch (soul) {
            case AKATSUKI, SENBONZAKURA -> 6;
            case KYOUKA -> 3;
            case RAIKIRI -> 4;
            default -> 1;
        };
        boolean dispersed = soul == SoulLegacyState.Legacy.SENBONZAKURA
                && blade.getOrCreateTag().getBoolean(SAKURA_DISPERSED);
        AwakenedRankCost.Cast cast = switch (soul) {
            case AKATSUKI -> AwakenedRankCost.Cast.AKATSUKI;
            case KYOUKA -> AwakenedRankCost.Cast.KYOUKA;
            case SENBONZAKURA -> dispersed
                    ? AwakenedRankCost.Cast.SAKURA_ARRAY : null;
            case RAIKIRI -> player.isShiftKeyDown()
                    ? AwakenedRankCost.Cast.RAIKIRI_STEP
                    : AwakenedRankCost.Cast.RAIKIRI_FINISH;
            default -> null;
        };
        boolean charge = GameplayConfig.ENABLE_AWAKENED_RANK_COSTS.get() && cast != null;
        int minimum = charge ? AwakenedRankCost.requiredLevel(blade, cast) : 0;
        int price = charge ? AwakenedRankCost.cost(blade, cast) : 0;
        if (!force && state.lastSyncedMeter == meter
                && state.lastSyncedStance == dispersed
                && state.lastSyncedMinimum == minimum
                && state.lastSyncedCost == price) return;
        state.lastSyncedMeter = meter;
        state.lastSyncedStance = dispersed;
        state.lastSyncedMinimum = minimum;
        state.lastSyncedCost = price;
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new AwakenedBladeStatePacket(id(blade), soul.ordinal(), meter,
                        maximum, dispersed, minimum, price));
    }

    private static void sendMirrorPose(ServerLevel level, ServerPlayer attacker,
            int cutId, int action, Vec3 pos, float yaw) {
        AwakenedMirrorEchoPacket packet = new AwakenedMirrorEchoPacket(
                action, attacker.getId(), cutId, pos.x, pos.y, pos.z, yaw);
        // The capture only goes to players actually near its original location.
        // Replay has its own authoritative server decision and location.
        for (ServerPlayer viewer : level.players()) {
            if (viewer.distanceToSqr(pos) < 48.0D * 48.0D) {
                ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> viewer), packet);
            }
        }
    }

    private static void visual(ServerLevel level, SoulLegacyState.Legacy soul,
            int type, Vec3 pos, Vec3 end, int variant) {
        AwakenedBladeVfxPacket packet = new AwakenedBladeVfxPacket(
                soul.ordinal(), type, pos.x, pos.y, pos.z,
                end.x, end.y, end.z, variant);
        for (ServerPlayer viewer : level.players()) {
            if (viewer.distanceToSqr(pos) <= 64.0D * 64.0D) {
                ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> viewer), packet);
            }
        }
    }

    private record Key(UUID player, UUID blade) { }
    private record RecordedCut(long tick, float damage, float yaw, float pitch,
            ResourceLocation combo, Vec3 origin, Vec3 targetAnchor, int cutId) { }
    private record PendingReplay(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension,
            UUID player, UUID blade, UUID target, long when, float damage, float yaw,
            float pitch, ResourceLocation combo, Vec3 origin, Vec3 targetAnchor,
            int cutId) { }
    private record PendingCausal(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension,
            UUID player, UUID blade, UUID target, Vec3 anchor, long when, float damage) { }
    private record PendingPetal(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension,
            UUID player, UUID blade, UUID target, long when, float damage) { }
    private record PendingSakuraWave(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension,
            UUID player, UUID blade, UUID target, Vec3 anchor, long when, float damage,
            SoulResonance.Resonance fusion) { }

    private static final class State {
        net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension;
        UUID targetId;
        float lastDamage;
        int scarDirections;
        float scarMagnitude;
        int sakuraCharge;
        long petalUntil;
        int raikiriCharge;
        long lastHit;
        long cooldownUntil;
        long stepUntil;
        long lastStep;
        int stepChain;
        boolean stepConfirmed;
        long stanceUntil;
        int lastSyncedMeter = -1;
        int lastSyncedMinimum = -1;
        int lastSyncedCost = -1;
        boolean lastSyncedStance;
        final List<RecordedCut> mirror = new ArrayList<>(3);
    }

    private AwakenedBladeCombat() { }
}
