package dev.bladetetra.challenge;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import dev.bladetetra.BladeTetra;
import dev.bladetetra.challenge.ChallengeManager.GateMode;
import dev.bladetetra.challenge.mikage.*;
import dev.bladetetra.registry.*;
import mods.flammpfeil.slashblade.ability.ArrowReflector;
import mods.flammpfeil.slashblade.slasharts.*;
import mods.flammpfeil.slashblade.util.AttackManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/** Opt-in isolated Forge/Mixin diagnostic. Never runs in a normal player world. */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID)
public final class MikageNativeSmokeTest {
    private static final long SESSION = Long.MIN_VALUE + 28;
    private static int checks;
    @SuppressWarnings("unchecked")
    @SubscribeEvent public static void started(ServerStartedEvent event) {
        if (!Boolean.getBoolean("blade_tetra.mikageNativeSmoke")) return;
        Map<Long, ChallengeSession> sessions = null;
        ServerPlayer player = null, outsider = null; MikageEntity boss = null;
        var level = event.getServer().getLevel(ChallengeManager.MIRROR_REALM);
        try {
            check(level != null, "mirror realm available");
            var field = ChallengeManager.class.getDeclaredField("CHALLENGES"); field.setAccessible(true);
            sessions = (Map<Long, ChallengeSession>) field.get(null);
            var session = new ChallengeSession(SESSION, GateMode.NORMAL, 0); sessions.put(SESSION, session);
            level.getChunk(11, -1);
            for (int x = 172; x <= 188; x++) for (int z = -20; z <= 0; z++)
                level.setBlock(new BlockPos(x, 64, z), Blocks.STONE.defaultBlockState(), 3);
            player = testPlayer(level, new GameProfile(UUID.fromString("12800000-0000-0000-0000-000000000001"), "MikageSubject"));
            outsider = testPlayer(level, new GameProfile(UUID.fromString("12800000-0000-0000-0000-000000000002"), "MikageOutsider"));
            level.addNewPlayer(player); level.addNewPlayer(outsider);
            session.participants.add(player.getUUID()); player.getPersistentData().putLong(ChallengeManager.PLAYER_CHALLENGE, SESSION);
            player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            outsider.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            player.setItemSlot(EquipmentSlot.MAINHAND, ModItems.MODULAR_SLASHBLADE.get().createDefaultStack());
            boss = new MikageEntity(ModEntities.MIKAGE.get(), level); boss.setNoAi(true);
            boss.configureForParty(List.of(player), false, SESSION); boss.setPos(180, 65, -10); boss.setYRot(0);
            check(level.addFreshEntity(boss), "boss spawned");
            reset(player, outsider);
            var target = player.getUUID();
            var rule = new MikageNativeCombat.Rule(target, true, false, (p, source) -> true, (p, source) -> { });
            try (Owned owned = new Owned()) {
                var actor = boss;
                MikageNativeCombat.run(boss, owned.scope, rule, () -> AttackManager.doSlash(actor, 25, false, false, .52));
                tick(owned.scope, 4);
                check(player.getHealth() < 20, "native AttackHelper melee really hurts participant");
                check(outsider.getHealth() == 20, "native melee excludes outsider inside native box");
            }
            reset(player, outsider); player.invulnerableTime = 40;
            try (Owned owned = new Owned()) {
                var actor = boss;
                MikageNativeCombat.run(boss, owned.scope, rule, () -> AttackManager.doSlash(actor, 25, false, false, .52));
                tick(owned.scope, 4);
                check(player.getHealth() == 20 && player.invulnerableTime == 40, "native forceHit cannot erase existing immunity");
            }
            reset(player, outsider);
            try (Owned owned = new Owned()) {
                var actor = boss;
                MikageNativeCombat.run(boss, owned.scope, rule, () -> Drive.doSlash(actor, 0, 30, Vec3.ZERO, false, .62, 1.65F));
                tick(owned.scope, 5);
                check(player.getHealth() < 20, "native Drive ray and hurt work with a mob owner");
                check(outsider.getHealth() == 20, "Drive ray skips outsider");
            }
            reset(player, outsider);
            try (Owned owned = new Owned()) {
                var actor = boss; var aim = player.position().add(0, .8, 0);
                MikageNativeCombat.run(boss, owned.scope, rule, () -> MikageNativeAttacks.release(actor, owned.scope,
                        MikageMove.VOLLEY, aim, new Vec3(0, 0, 1), 0));
                tick(owned.scope, 5);
                check(player.getHealth() < 20, "summoned sword native collision and hurt work");
                check(outsider.getHealth() == 20, "summoned sword skips outsider");
            }
            reset(player, outsider);
            try (Owned owned = new Owned()) {
                var actor = boss; var aim = player.position().add(0, .8, 0);
                MikageNativeCombat.run(boss, owned.scope, rule, () -> MikageNativeAttacks.release(actor, owned.scope,
                        MikageMove.CUT, aim, new Vec3(0, 0, 1), 0));
                tick(owned.scope, 4);
                check(player.getHealth() < 20, "native Judgement Cut area really hurts at its warned position");
                check(outsider.getHealth() == 20, "native cut excludes outsider");
            }
            reset(player, outsider);
            try (Owned owned = new Owned()) {
                var sword = new MikageGateSwordEntity(ModEntities.MIKAGE_GATE_SWORD.get(), level);
                sword.configureAttack(boss, owned.scope, false); sword.setPos(player.position().add(0, .8, -1));
                sword.setDamage(3); sword.shoot(0, 0, 1, 1, 0);
                MikageNativeCombat.track(sword, boss, owned.scope, rule); check(level.addFreshEntity(sword), "pretracked barrage-style sword can spawn");
                Vec3 before = sword.getDeltaMovement(); ArrowReflector.doReflect(sword, player);
                check(sword.getDeltaMovement().distanceToSqr(before) > .1, "native reflection changed velocity");
                check(sword.getRayTrace(sword.position(), sword.position().add(0, 0, 3)) == null, "reflected sword cannot collide with players");
                tick(owned.scope, 3); check(player.getHealth() == 20, "reflection never produces a second player hit");
            }
            reset(player, outsider);
            try (Owned owned = new Owned()) {
                var actor = boss;
                boss.duel().swing(player, true);
                var parry = new MikageNativeCombat.Rule(target, true, true,
                        (p, source) -> !actor.duel().parryPursuit(p, actor.getEyePosition()), (p, source) -> { });
                MikageNativeCombat.run(boss, owned.scope, parry, () -> AttackManager.doSlash(actor, 25, false, false, .52));
                tick(owned.scope, 4);
                check(boss.duel().protects(player), "fresh sword input grants parry protection");
                check(player.getHealth() == 20, "parried native melee cannot hurt again on a later entity tick");
            }
            reset(player, outsider);
            try (Owned owned = new Owned()) {
                var actor = boss;
                var parry = new MikageNativeCombat.Rule(target, true, true,
                        (p, source) -> !actor.duel().parryPursuit(p, actor.getEyePosition()), (p, source) -> { });
                MikageNativeCombat.run(boss, owned.scope, parry, () -> AttackManager.doSlash(actor, 25, false, false, .52));
                tick(owned.scope, 4);
                check(player.getHealth() < 20, "next fresh native strike cannot reuse consumed input or previous protection");
            }
            boss.duel().clear(); reset(player, outsider); session.participants.remove(player.getUUID());
            try (Owned owned = new Owned()) {
                var actor = boss;
                MikageNativeCombat.run(boss, owned.scope, rule, () -> AttackManager.doSlash(actor, 25, false, false, .52));
                tick(owned.scope, 4); check(player.getHealth() == 20, "participant removal cancels delayed native targeting");
            }
            check(ownedMap().isEmpty(), "every closed cast released all native bindings");
            LogUtils.getLogger().info("MIKAGE_NATIVE_SMOKE_PASS: {} checks; real melee, Drive, sword, cut, immunity, outsider, reflection, parry, cleanup", checks);
        } catch (Throwable failure) {
            LogUtils.getLogger().error("MIKAGE_NATIVE_SMOKE_FAIL", failure);
            throw new IllegalStateException("Mikage native diagnostic failed", failure);
        } finally {
            if (sessions != null) sessions.remove(SESSION);
            if (boss != null) boss.discard();
            if (player != null) player.discard(); if (outsider != null) outsider.discard();
            MikageNativeCombat.clear(); event.getServer().halt(false);
        }
    }
    private static ServerPlayer testPlayer(ServerLevel level, GameProfile profile) {
        // Forge FakePlayer.hurt intentionally returns false. Reuse its no-op connection only.
        ServerPlayer player = new ServerPlayer(level.getServer(), level, profile);
        player.connection = FakePlayerFactory.get(level, profile).connection;
        for (int i = 0; i < 61; i++) player.tick(); // expire normal login/spawn immunity
        return player;
    }
    private static void reset(ServerPlayer player, ServerPlayer outsider) {
        player.setHealth(20); player.setAbsorptionAmount(0); player.invulnerableTime = 0;
        player.setPos(180, 65, -7); player.setYRot(180); player.setXRot(0); player.setDeltaMovement(Vec3.ZERO);
        outsider.setHealth(20); outsider.invulnerableTime = 0; outsider.setPos(180.7, 65, -7);
    }
    @SuppressWarnings("unchecked") private static Map<CastScope, Set<Entity>> ownedMap() throws Exception {
        var field = MikageNativeCombat.class.getDeclaredField("SCOPES"); field.setAccessible(true);
        return (Map<CastScope, Set<Entity>>) field.get(null);
    }
    private static void tick(CastScope scope, int count) throws Exception {
        for (int i = 0; i < count; i++) for (Entity entity : List.copyOf(ownedMap().getOrDefault(scope, Set.of())))
            if (!entity.isRemoved()) entity.tick();
    }
    private static void check(boolean condition, String description) {
        checks++; if (!condition) throw new IllegalStateException(description);
    }
    private static final class Owned implements AutoCloseable {
        final CastScope scope = new CastScope(checks + 1);
        @Override public void close() throws Exception {
            var entities = List.copyOf(ownedMap().getOrDefault(scope, Set.of())); scope.close();
            check(entities.stream().allMatch(Entity::isRemoved), "closed scope discards native entities");
        }
    }
    private MikageNativeSmokeTest() { }
}
