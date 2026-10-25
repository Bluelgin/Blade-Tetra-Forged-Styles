package dev.bladetetra.easteregg;

import dev.bladetetra.BladeTetra;
import dev.bladetetra.combat.BladeTechniqueHandler;
import dev.bladetetra.item.ModularSlashBladeItem;
import dev.bladetetra.network.AwakenedBladeVfxPacket;
import dev.bladetetra.network.ModNetwork;
import mods.flammpfeil.slashblade.entity.IShootable;
import mods.flammpfeil.slashblade.event.SlashBladeEvent;
import mods.flammpfeil.slashblade.slasharts.SlashArts;
import net.minecraft.nbt.CompoundTag;
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
    private static final int STATE_LIMIT = 512;
    private static final Map<Key, State> STATES = new HashMap<>();
    private static final List<PendingReplay> REPLAYS = new ArrayList<>();
    private static int ticks;

    /** Guards only legacy combat callbacks; never guards ritual or unlock code. */
    public static boolean replacesLegacy() {
        return true;
    }

    /** Called exclusively after the corresponding server-side ritual succeeds. */
    public static void awakening(ServerPlayer player, SoulLegacyState.Legacy soul) {
        if (soul == SoulLegacyState.Legacy.NONE) return;
        Vec3 pos = player.position().add(0, 1.1D, 0);
        visual(player.serverLevel(), soul, AwakenedBladeVfxPacket.AWAKEN,
                pos, pos, 0);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRealDamage(LivingDamageEvent event) {
        if (!(event.getEntity().level() instanceof ServerLevel level)
                || event.getAmount() <= 0.0F
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
                double angle = Math.atan2(player.getZ() - target.getZ(),
                        player.getX() - target.getX());
                int direction = Math.floorMod((int) Math.floor(
                        (angle + Math.PI) * 3.0D / Math.PI), 6);
                int bit = 1 << direction;
                if ((state.scarDirections & bit) == 0) {
                    state.scarDirections |= bit;
                    visual(level, soul, AwakenedBladeVfxPacket.MARK,
                            center, center, direction);
                }
            }
            case KYOUKA -> {
                // Record validated real hits, not raw client inputs or phantom entities.
                state.mirror.add(new RecordedCut(now, state.lastDamage, player.getYRot()));
                if (state.mirror.size() > 3) state.mirror.remove(0);
                visual(level, soul, AwakenedBladeVfxPacket.MARK, center, center, 0);
            }
            case SENBONZAKURA -> {
                state.sakuraCharge = Math.min(6, state.sakuraCharge + 1);
                if (blade.getOrCreateTag().getBoolean(SAKURA_DISPERSED)) {
                    visual(level, soul, AwakenedBladeVfxPacket.MARK,
                            center, center, state.sakuraCharge);
                }
            }
            case RAIKIRI -> {
                state.raikiriCharge = Math.min(4, state.raikiriCharge + 1);
                if (state.raikiriCharge == 4) {
                    visual(level, soul, AwakenedBladeVfxPacket.MARK,
                            center, center, state.raikiriCharge);
                }
            }
            default -> {
            }
        }
    }

    @SubscribeEvent
    public static void onSlashArt(SlashBladeEvent.PerformSlashArtEvent event) {
        if (event.getType() != SlashArts.ArtsType.Success
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
    }

    private static void releaseAkatsuki(ServerLevel level, ServerPlayer player,
            ItemStack blade, State state, long now) {
        if (now < state.cooldownUntil || now - state.lastHit > EXPIRE_TICKS) return;
        int marks = Integer.bitCount(state.scarDirections);
        if (marks < 3) return;
        LivingEntity target = target(level, player, state, 24.0D);
        if (target == null) return;
        float damage = Math.min(30.0F, state.lastDamage
                * (0.6F + marks * 0.26F));
        if (!secondaryHit(level, player, target, damage)) return;
        state.cooldownUntil = now + 100L;
        state.scarDirections = 0;
        Vec3 center = target.getBoundingBox().getCenter();
        visual(level, SoulLegacyState.Legacy.AKATSUKI,
                AwakenedBladeVfxPacket.RELEASE, center, center, marks);
    }

    private static void releaseKyouka(ServerLevel level, ServerPlayer player,
            ItemStack blade, State state, long now) {
        if (now < state.cooldownUntil || now - state.lastHit > EXPIRE_TICKS
                || state.mirror.size() < 2 || REPLAYS.size() >= 96) return;
        LivingEntity target = target(level, player, state, 24.0D);
        if (target == null) return;
        int number = 0;
        for (RecordedCut cut : state.mirror) {
            // At most three staggered mirror strikes, each within its own budget.
            float damage = Math.min(12.0F, cut.damage * 0.32F);
            if (damage > 0) REPLAYS.add(new PendingReplay(level.dimension(),
                    player.getUUID(), id(blade), target.getUUID(),
                    now + 4L + number * 5L, damage, cut.yaw));
            number++;
        }
        state.mirror.clear();
        state.cooldownUntil = now + 90L;
        Vec3 center = target.getBoundingBox().getCenter();
        visual(level, SoulLegacyState.Legacy.KYOUKA,
                AwakenedBladeVfxPacket.RELEASE, center, center, number);
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
        List<LivingEntity> enemies = level.getEntitiesOfClass(LivingEntity.class,
                        new AABB(primary.blockPosition()).inflate(5.0D),
                        entity -> canAffect(player, entity))
                .stream().sorted(Comparator.comparingDouble(primary::distanceToSqr))
                .limit(5).toList();
        if (enemies.isEmpty()) return;
        // One bounded damage budget, shared by all decorative sword blades.
        float budget = Math.min(26.0F, state.lastDamage
                * (0.35F + 0.23F * state.sakuraCharge));
        float each = budget / enemies.size();
        for (LivingEntity enemy : enemies) secondaryHit(level, player, enemy, each);
        state.sakuraCharge = 0;
        state.cooldownUntil = now + 110L;
        Vec3 center = primary.getBoundingBox().getCenter();
        visual(level, SoulLegacyState.Legacy.SENBONZAKURA,
                AwakenedBladeVfxPacket.RELEASE, center, center, enemies.size());
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
            Vec3 moved = from;
            // Check every sub-step against the actual player AABB; no wall phasing.
            for (int step = 1; step <= 7; step++) {
                Vec3 next = from.add(direction.scale(step * 0.5D));
                if (!level.noCollision(player, player.getBoundingBox()
                        .move(next.subtract(from)))) break;
                moved = next;
            }
            if (moved.distanceToSqr(from) < 0.24D) return;
            player.connection.teleport(moved.x, moved.y, moved.z,
                    player.getYRot(), player.getXRot());
            state.stepUntil = now + 14L;
            state.raikiriCharge = Math.min(4, state.raikiriCharge + 1);
            visual(level, SoulLegacyState.Legacy.RAIKIRI,
                    AwakenedBladeVfxPacket.STEP,
                    from.add(0, 1, 0), moved.add(0, 1, 0), 0);
            return;
        }
        if (state.raikiriCharge < 2 || now < state.cooldownUntil
                || now - state.lastHit > EXPIRE_TICKS) return;
        LivingEntity primary = target(level, player, state, 20.0D);
        if (primary == null) return;
        float damage = Math.min(20.0F,
                state.lastDamage * (0.35F + state.raikiriCharge * 0.18F));
        if (!secondaryHit(level, player, primary, damage)) return;
        state.raikiriCharge = 0;
        state.cooldownUntil = now + 75L;
        visual(level, SoulLegacyState.Legacy.RAIKIRI,
                AwakenedBladeVfxPacket.RELEASE,
                player.position().add(0, 1, 0), primary.getBoundingBox().getCenter(), 0);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = event.getServer();
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
            if (secondaryHit(level, player, target, cut.damage)) {
                Vec3 center = target.getBoundingBox().getCenter();
                visual(level, SoulLegacyState.Legacy.KYOUKA,
                        AwakenedBladeVfxPacket.MARK, center, center, 0);
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
        STATES.entrySet().removeIf(entry -> level.dimension().equals(entry.getValue().dimension));
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        REPLAYS.clear();
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
        if (amount <= 0 || !canAffect(player, target)) return false;
        return SoulLegacyDamageGuard.apply(() -> target.hurt(
                level.damageSources().playerAttack(player), amount));
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
    private record RecordedCut(long tick, float damage, float yaw) { }
    private record PendingReplay(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension,
            UUID player, UUID blade, UUID target, long when, float damage, float yaw) { }

    private static final class State {
        net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension;
        UUID targetId;
        float lastDamage;
        int scarDirections;
        int sakuraCharge;
        int raikiriCharge;
        long lastHit;
        long cooldownUntil;
        long stepUntil;
        long stanceUntil;
        final List<RecordedCut> mirror = new ArrayList<>(3);
    }

    private AwakenedBladeCombat() { }
}
