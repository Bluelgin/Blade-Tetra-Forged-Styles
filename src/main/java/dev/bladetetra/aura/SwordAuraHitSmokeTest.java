package dev.bladetetra.aura;

import com.mojang.logging.LogUtils;
import dev.bladetetra.BladeTetra;
import dev.bladetetra.registry.ModEntities;
import dev.bladetetra.combat.BladeStyle;
import mods.flammpfeil.slashblade.SlashBlade;
import mods.flammpfeil.slashblade.entity.EntitySlashEffect;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.ArrayList;
import java.util.List;

/** Opt-in real hurt/Forge regression, never active in a normal world. */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID)
public final class SwordAuraHitSmokeTest {
    private static int checks;
    @SubscribeEvent public static void started(ServerStartedEvent event) {
        if (!Boolean.getBoolean("blade_tetra.swordAuraHitSmoke")) return;
        ServerLevel level = event.getServer().overworld();
        var owner = FakePlayerFactory.getMinecraft(level);
        var victim = new Zombie(EntityType.ZOMBIE, level);
        List<Entity> spawned = new ArrayList<>();
        var cancel = new Cancel(victim);
        try {
            // ServerStarted runs before the first tick. Existing combat windows use
            // zero as their unset timestamp, so test contacts after that boundary.
            var clock = (ServerLevelData) level.getLevelData();
            clock.setGameTime(Math.max(100L, clock.getGameTime()));
            level.getChunk(0, 0);
            level.addNewPlayer(owner);
            owner.setPos(2, 80, 0);
            victim.setNoAi(true); victim.setNoGravity(true); victim.setPos(0, 80, 0);
            victim.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);
            victim.getAttribute(Attributes.ARMOR).setBaseValue(0); victim.setHealth(100);
            check(level.addFreshEntity(victim), "victim added"); spawned.add(victim);
            check(victim.hurt(level.damageSources().mobAttack(owner), 6), "ordinary hit establishes real hurt cooldown");
            check(victim.getDeltaMovement().lengthSqr() > 0, "ordinary damage still applies knockback");
            check(victim.invulnerableTime > 10, "ordinary cooldown active");
            var first = shot(level, owner, spawned);
            var source = new SwordAuraDamageSource(first, owner);
            check(source.is(DamageTypes.INDIRECT_MAGIC) && source.is(DamageTypeTags.BYPASSES_COOLDOWN)
                    && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY), "only hurt cooldown is overridden on the native damage type");
            Vec3 movement = new Vec3(0.12D, -0.08D, 0.04D);
            victim.setDeltaMovement(movement);
            float health = victim.getHealth(); first.onHitEntity(new EntityHitResult(victim));
            check(victim.getHealth() == health - 4 && first.isRemoved(), "aura deals full damage during melee cooldown");
            check(victim.getDeltaMovement().equals(movement), "aura adds no knockback and preserves existing movement");
            victim.knockback(0.4D, 1.0D, 0.0D);
            check(!victim.getDeltaMovement().equals(movement), "aura suppression ends immediately after the contact");
            float after = victim.getHealth(); first.onHitEntity(new EntityHitResult(victim));
            check(victim.getHealth() == after, "one aura cannot hit twice");
            var next = shot(level, owner, spawned);
            next.onHitEntity(new EntityHitResult(victim));
            check(victim.getHealth() == after && !next.isRemoved(), "same-tick aura flood is capped without consuming rejected projectile");
            long start = clock.getGameTime(); clock.setGameTime(start + 3);
            next.onHitEntity(new EntityHitResult(victim)); check(victim.getHealth() == after, "three ticks is still inside aura cooldown");
            clock.setGameTime(start + 4); next.onHitEntity(new EntityHitResult(victim));
            check(victim.getHealth() == after - 4, "new aura hit accepted after four ticks while ordinary cooldown is still active");

            clock.setGameTime(start + 8); after = victim.getHealth();
            var blocked = shot(level, owner, spawned); MinecraftForge.EVENT_BUS.register(cancel);
            blocked.onHitEntity(new EntityHitResult(victim));
            check(victim.getHealth() == after && !blocked.isRemoved(), "Forge damage cancellation remains authoritative");
            MinecraftForge.EVENT_BUS.unregister(cancel);
            Vec3 afterCancellation = victim.getDeltaMovement();
            victim.knockback(0.4D, 0.0D, 1.0D);
            check(!victim.getDeltaMovement().equals(afterCancellation), "cancelled aura damage does not leak knockback suppression");
            blocked.onHitEntity(new EntityHitResult(victim));
            check(victim.getHealth() == after - 4, "cancelled contact consumes neither cadence nor one-hit record");

            clock.setGameTime(start + 12); after = victim.getHealth();
            victim.setInvulnerable(true); var immune = shot(level, owner, spawned);
            immune.onHitEntity(new EntityHitResult(victim));
            check(victim.getHealth() == after, "real invulnerability is not bypassed"); victim.setInvulnerable(false);
            var decoration = shot(level, owner, spawned); decoration.setDecoration(true);
            decoration.onHitEntity(new EntityHitResult(victim));
            check(victim.getHealth() == after, "decorative slash never spends a damage budget");
            check(!victim.hurt(level.damageSources().mobAttack(owner), 1), "normal attacks still obey their own hurt cooldown");
            // A visual-heavy profile must still emit exactly one physical damage projectile.
            for (BladeStyle style : BladeStyle.values()) {
                int before = level.getEntities(ModEntities.SWORD_AURA_DRIVE.get(), aura -> aura.getOwner() == owner).size();
                var slash = new EntitySlashEffect(SlashBlade.RegistryEvents.SlashEffect, level);
                slash.setPos(5, 80, 5);
                SwordAuraSpawner.emit(owner, slash, style);
                var emitted = level.getEntities(ModEntities.SWORD_AURA_DRIVE.get(), aura -> aura.getOwner() == owner);
                check(emitted.size() == before + 1, "one physical aura for " + style);
                // Entity iteration order is not a creation-order contract.
                var latest = emitted.stream().filter(aura -> aura.position().distanceToSqr(5, 80, 5) < 1).findFirst().orElseThrow();
                check(latest.getDamage() > 0, "damage projectile retained for " + style);
                BladeStyle visual = style == BladeStyle.RENGEKI || style == BladeStyle.DANGAKU ? style : BladeStyle.STANDARD;
                check(latest.getVisualStyle() == visual, "launch visual snapshot for " + style);
                var saved = new CompoundTag(); latest.addAdditionalSaveData(saved);
                var restored = new SwordAuraDriveEntity(ModEntities.SWORD_AURA_DRIVE.get(), level);
                var metadata = latest.getEntityData().getNonDefaultValues();
                if (metadata != null) restored.getEntityData().assignValues(metadata);
                check(restored.getVisualStyle() == visual, "spawn metadata carries visual for " + style);
                restored.setVisualStyle(BladeStyle.STANDARD);
                restored.readAdditionalSaveData(saved);
                check(restored.getVisualStyle() == visual, "visual survives save/load for " + style);
                latest.discard();
            }
            LogUtils.getLogger().info("SWORD_AURA_HIT_SMOKE_PASS: {} checks", checks);
        } catch (Throwable failure) {
            LogUtils.getLogger().error("SWORD_AURA_HIT_SMOKE_FAIL", failure);
            throw new IllegalStateException("Sword aura hit diagnostic failed", failure);
        } finally {
            MinecraftForge.EVENT_BUS.unregister(cancel);
            spawned.forEach(Entity::discard); owner.discard(); event.getServer().halt(false);
        }
    }
    private static SwordAuraDriveEntity shot(ServerLevel level, net.minecraft.world.entity.LivingEntity owner, List<Entity> spawned) {
        var aura = new SwordAuraDriveEntity(ModEntities.SWORD_AURA_DRIVE.get(), level);
        aura.setOwner(owner); aura.setPos(0, 80, 0); aura.setDamage(4); aura.setLifetime(20);
        level.addFreshEntity(aura); spawned.add(aura); return aura;
    }
    private static void check(boolean condition, String message) {
        checks++; if (!condition) throw new IllegalStateException(message);
    }
    public static final class Cancel {
        private final Entity victim;
        Cancel(Entity victim) { this.victim = victim; }
        @SubscribeEvent public void damage(LivingDamageEvent event) {
            if (event.getEntity() == victim) event.setCanceled(true);
        }
    }
    private SwordAuraHitSmokeTest() { }
}
