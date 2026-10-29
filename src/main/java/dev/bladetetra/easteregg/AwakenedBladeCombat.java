package dev.bladetetra.easteregg;

import dev.bladetetra.BladeTetra;
import dev.bladetetra.combat.BladeTechniqueHandler;
import dev.bladetetra.combat.ModComboStates;
import dev.bladetetra.config.GameplayConfig;
import dev.bladetetra.item.ModularSlashBladeItem;
import dev.bladetetra.network.AwakenedBladeVfxPacket;
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
 * V2 awakened combat for Kyouka, Senbonzakura and Raikiri.
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
    private static final int STATE_LIMIT = 512;
    private static final Map<Key, State> STATES = new HashMap<>();
    private static int ticks;

    /** Guards only legacy combat callbacks; never guards ritual or unlock code. */
    public static boolean replacesLegacy() {
        return GameplayConfig.ENABLE_AWAKENED_V2.get();
    }

    /** Akatsuki retains the original Final Moon and Contract Blade combat hooks. */
    public static boolean isV2CombatSoul(SoulLegacyState.Legacy soul) {
        return soul != null && soul != SoulLegacyState.Legacy.NONE
                && soul != SoulLegacyState.Legacy.AKATSUKI;
    }

    /** Called exclusively after the corresponding server-side ritual succeeds. */
    public static void awakening(ServerPlayer player, SoulLegacyState.Legacy soul) {
        AwakeningPresentationService.success(player, soul);
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
        if (!SoulLegacyState.isActive(blade, soul) || !isV2CombatSoul(soul)
                || soul == SoulLegacyState.Legacy.KYOUKA) return;
        State state = state(player, blade);
        long now = level.getGameTime();
        LivingEntity target = event.getEntity();
        if (!target.getUUID().equals(state.targetId) || now - state.lastHit > EXPIRE_TICKS) {
            state.targetId = target.getUUID();
            state.raikiriCharge = 0;
            // Step confirmation must survive the first hit on a new target:
            // before the dash lands, targetId may legitimately be null. Reset
            // the chain on timeout/failed input instead, never here.
        }
        state.dimension = level.dimension();
        state.lastHit = now;
        state.lastDamage = Math.max(0.0F, Math.min(32.0F, event.getAmount()));
        Vec3 center = target.getBoundingBox().getCenter();
        switch (soul) {
            case KYOUKA -> { /* Three Mirrors observes committed native actions separately. */ }
            case SENBONZAKURA -> {
                // Native sword hits authorize only a cosmetic swarm volley.
                // No independent attack entity or secondary damage is spawned.
                if (SakuraEndlessFlow.isBurning(player, blade)
                        && SakuraEndlessFlow.shouldShowVolley(player, blade, now)) {
                    visual(level, soul, AwakenedBladeVfxPacket.MARK,
                            player.position().add(0, 1.0D, 0), center,
                            10 + SakuraEndlessFlow.initialRank(player, blade));
                }
            }
            case RAIKIRI -> {
                // Only slash effects created by our upgraded native rapid
                // combo authorize another free Thunder Step. This remains
                // valid even when the attack entity hits AFTER the ComboState
                // changed, and rejects unrelated SA and ordinary melee hits.
                Entity direct = event.getSource().getDirectEntity();
                boolean thunderHit = direct instanceof EntitySlashEffect slash
                        && slash.getPersistentData().getBoolean(RaikiriRapidCombo.THUNDER_HIT);
                boolean flashFollowup = thunderHit && state.stepChain > 0
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
        if (!SoulLegacyState.isActive(blade, soul) || !isV2CombatSoul(soul)
                || soul == SoulLegacyState.Legacy.KYOUKA) return;
        ServerLevel level = player.serverLevel();
        State state = state(player, blade);
        long now = level.getGameTime();
        switch (soul) {
            case KYOUKA -> { /* Native Spiral Swords owns activation; SA remains native. */ }
            case SENBONZAKURA -> releaseSenbonzakura(level, player, blade, state, now);
            case RAIKIRI -> releaseRaikiri(level, player, blade, state, now);
            default -> {
            }
        }
        syncHud(player, soul, blade, state, false);
    }

    private static void releaseSenbonzakura(ServerLevel level, ServerPlayer player,
            ItemStack blade, State state, long now) {
        // Remove standalone ring/fan/rain SA. Shift+SA only switches forms.
        if (!player.isShiftKeyDown() || now < state.stanceUntil
                || dev.bladetetra.combat.StyleResolver.resolve(blade)
                        != dev.bladetetra.combat.BladeStyle.RENGEKI) return;
        CompoundTag tag = blade.getOrCreateTag();
        boolean dispersed = !tag.getBoolean(SAKURA_DISPERSED);
        tag.putBoolean(SAKURA_DISPERSED, dispersed);
        state.stanceUntil = now + 12L;
        Vec3 pos = player.position().add(0, 1, 0);
        visual(level, SoulLegacyState.Legacy.SENBONZAKURA,
                AwakenedBladeVfxPacket.STANCE, pos, pos, dispersed ? 1 : 0);
    }

    /**
     * One Shift+W native Rapid Slash activation, never a Slash Art fallback.
     * Execute once per accepted NextComboEvent before entering the cloned
     * native animation node. Native rapid slash alone owns melee hit effects.
     */
    public static boolean beginRaikiriRapid(ServerPlayer player, ItemStack blade) {
        if (!replacesLegacy() || blade != player.getMainHandItem()
                || SoulLegacyState.active(blade) != SoulLegacyState.Legacy.RAIKIRI
                || !SoulLegacyState.isActive(blade, SoulLegacyState.Legacy.RAIKIRI))
            return false;
        ServerLevel level = player.serverLevel();
        State state = state(player, blade);
        long now = level.getGameTime();
        if (now < state.stepUntil) return false;
        Vec3 from = player.position();
        Vec3 look = player.getLookAngle();
        Vec3 horizontal = new Vec3(look.x, 0.0D, look.z);
        if (horizontal.lengthSqr() < 0.0001D) return false;
        Vec3 moved = collisionCheckedStep(level, player, from,
                horizontal.normalize(), 3.5D);
        if (moved.distanceToSqr(from) < 0.24D) return false;
        boolean continuing = AwakenedCombatMath.isThunderFollowup(
                state.stepChain, state.stepConfirmed, state.lastStep, now);
        if (!continuing && !AwakenedRankCost.trySpend(player, blade,
                AwakenedRankCost.Cast.RAIKIRI_STEP)) return false;
        state.stepChain = AwakenedCombatMath.nextThunderChain(
                state.stepChain, state.stepConfirmed, state.lastStep, now);
        state.stepConfirmed = false;
        state.lastStep = now;
        state.stepUntil = now + (state.stepChain > 1 ? 6L : 10L);
        player.connection.teleport(moved.x, moved.y, moved.z,
                player.getYRot(), player.getXRot());
        visual(level, SoulLegacyState.Legacy.RAIKIRI,
                AwakenedBladeVfxPacket.STEP,
                from.add(0, 1, 0), moved.add(0, 1, 0), state.stepChain);
        syncHud(player, SoulLegacyState.Legacy.RAIKIRI, blade, state, false);
        return true;
    }

    private static void releaseRaikiri(ServerLevel level, ServerPlayer player,
            ItemStack blade, State state, long now) {
        if (state.raikiriCharge < 2 || now < state.cooldownUntil
                || now - state.lastHit > EXPIRE_TICKS) return;
        LivingEntity primary = target(level, player, state, 8.0D);
        if (primary == null || !player.hasLineOfSight(primary)) return;
        float damage = AwakenedCombatMath.thunderFinisher(
                state.lastDamage, state.raikiriCharge);
        if (damage <= 0) return;
        Vec3 from = player.position();
        Vec3 delta = primary.position().subtract(from).multiply(1, 0, 1);
        Vec3 moved = from;
        if (delta.lengthSqr() > 1.44D) {
            double travel = Math.min(3.5D, delta.length() - 1.15D);
            moved = collisionCheckedStep(level, player, from, delta.normalize(), travel);
        }
        // A visible enemy 8 blocks away is targetable, but a real melee
        // thrust must REACH it after the collision-checked lunge. No
        // wall-blocked remote hit and no Rank debit on an unreachable cast.
        if (!AwakenedCombatMath.thunderFinisherCanReach(
                moved, primary.getBoundingBox())) return;
        if (!AwakenedRankCost.trySpend(player, blade,
                AwakenedRankCost.Cast.RAIKIRI_FINISH)) return;
        // Apply damage from the REAL post-lunge position, so protection mods
        // that validate attack distance see the correct server coordinates.
        // If damage is rejected, roll back the movement and exactly our Rank
        // charge: the player cannot farm free teleports from protected mobs.
        boolean relocated = moved.distanceToSqr(from) > 0.24D;
        if (relocated) {
            player.connection.teleport(moved.x, moved.y, moved.z,
                    player.getYRot(), player.getXRot());
        }
        if (!secondaryHit(level, player, primary, damage)) {
            if (relocated) {
                player.connection.teleport(from.x, from.y, from.z,
                        player.getYRot(), player.getXRot());
            }
            AwakenedRankCost.refund(player, blade, AwakenedRankCost.Cast.RAIKIRI_FINISH);
            return;
        }
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
        if (!SoulLegacyState.isActive(blade, soul) || !isV2CombatSoul(soul)
                || soul == SoulLegacyState.Legacy.KYOUKA) return;
        if (player.tickCount % ((soul == SoulLegacyState.Legacy.RAIKIRI
                || soul == SoulLegacyState.Legacy.SENBONZAKURA) ? 10 : 80) != 0)
            return;
        State state = state(player, blade);
        long now = player.level().getGameTime();
        if (now - state.lastHit > EXPIRE_TICKS) {
            state.targetId = null;
            state.lastDamage = 0;
            state.raikiriCharge = 0;
        }
        // A first lightning step may begin after minutes without any hit.
        // Only its own step timestamp, never stale lastHit, expires the chain.
        if (soul == SoulLegacyState.Legacy.RAIKIRI
                && AwakenedCombatMath.thunderChainExpired(state.lastStep, now)) {
            state.stepChain = 0;
            state.stepConfirmed = false;
        }
        syncHud(player, soul, blade, state, player.tickCount % 80 == 0);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!replacesLegacy()) {
            STATES.clear();
            return;
        }
        MinecraftServer server = event.getServer();
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
        STATES.entrySet().removeIf(entry -> level.dimension().equals(entry.getValue().dimension));
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        STATES.clear();
        ticks = 0;
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
        int previousInvulnerability = target.invulnerableTime;
        target.invulnerableTime = 0;
        boolean landed = dev.bladetetra.combat.InheritedCombatDamage.apply(() ->
                SoulLegacyDamageGuard.apply(() -> target.hurt(
                        level.damageSources().playerAttack(player), amount)));
        if (!landed) {
            // Failed/cancelled hits must not silently strip the victim's
            // ordinary hurt immunity from unrelated attacks.
            target.invulnerableTime = previousInvulnerability;
        }
        return landed;
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
            case KYOUKA -> 0;
            case SENBONZAKURA -> SakuraEndlessFlow.isBurning(player, blade)
                    ? SakuraEndlessFlow.initialRank(player, blade) : 0;
            case RAIKIRI -> state.raikiriCharge;
            default -> 0;
        };
        int maximum = switch (soul) {
            case SENBONZAKURA -> 7;
            case KYOUKA -> 3;
            case RAIKIRI -> 4;
            default -> 1;
        };
        boolean dispersed = soul == SoulLegacyState.Legacy.SENBONZAKURA
                && blade.getOrCreateTag().getBoolean(SAKURA_DISPERSED);
        AwakenedRankCost.Cast cast = switch (soul) {
            case KYOUKA -> null;
            case SENBONZAKURA -> null; // Rank burn owns its own native HUD.
            case RAIKIRI -> RaikiriRapidCombo.directionalIntent(player)
                    ? AwakenedRankCost.Cast.RAIKIRI_STEP
                    : AwakenedRankCost.Cast.RAIKIRI_FINISH;
            default -> null;
        };
        boolean charge = GameplayConfig.ENABLE_AWAKENED_RANK_COSTS.get() && cast != null
                && !(cast == AwakenedRankCost.Cast.RAIKIRI_STEP
                        && AwakenedCombatMath.isThunderFollowup(
                                state.stepChain, state.stepConfirmed, state.lastStep,
                                player.serverLevel().getGameTime()));
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
    private static final class State {
        net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension;
        UUID targetId;
        float lastDamage;
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
    }

    private AwakenedBladeCombat() { }
}
