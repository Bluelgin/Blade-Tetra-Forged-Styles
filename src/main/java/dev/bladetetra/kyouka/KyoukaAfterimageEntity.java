package dev.bladetetra.kyouka;

import dev.bladetetra.BladeTetra;
import dev.bladetetra.registry.ModEntities;
import net.minecraft.network.syncher.*;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/** A stationary, targetable illusion. Never saved, killed for loot, or given AI goals. */
public final class KyoukaAfterimageEntity extends PathfinderMob {
    private static final EntityDataAccessor<Optional<UUID>> OWNER = SynchedEntityData.defineId(
            KyoukaAfterimageEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    ServerPlayer owner;
    ItemStack blade = ItemStack.EMPTY;
    int slot;
    float damage;
    final List<Mob> diverted = new ArrayList<>(4);
    private boolean finished;

    public KyoukaAfterimageEntity(EntityType<? extends PathfinderMob> type, net.minecraft.world.level.Level level) {
        super(type, level);
        setNoGravity(true); noPhysics = true; setNoAi(true); xpReward = 0;
    }
    public Optional<UUID> ownerId() { return entityData.get(OWNER); }
    void configure(ServerPlayer player, ItemStack weapon, Vec3 origin, float yaw, float pitch) {
        owner = player; blade = weapon; slot = player.getInventory().selected;
        damage = (float) Math.min(6, Math.max(0, player.getAttributeValue(Attributes.ATTACK_DAMAGE) * .4));
        entityData.set(OWNER, Optional.of(player.getUUID()));
        moveTo(origin.x, origin.y, origin.z, yaw, pitch);
        yBodyRot = yBodyRotO = yHeadRot = yHeadRotO = yaw;
        setItemSlot(EquipmentSlot.MAINHAND, weapon.copy());
        setDropChance(EquipmentSlot.MAINHAND, 0);
    }
    @Override protected void defineSynchedData() { super.defineSynchedData(); entityData.define(OWNER, Optional.empty()); }
    @Override protected void registerGoals() {}
    @Override public void tick() {
        super.tick(); setDeltaMovement(Vec3.ZERO);
        // Model-independent, bounded Tier-3 visual if all client mesh passes fail.
        if (level().isClientSide && tickCount % 8 == 0) {
            level().addParticle(ParticleTypes.END_ROD,
                    getX(), getY() + 1.0, getZ(), 0, 0.012, 0);
        }
        if (!level().isClientSide && (tickCount >= 60 || !KyoukaAfterimages.valid(this))) discard();
    }
    @Override public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide || finished || amount <= 0 || !KyoukaAfterimages.valid(this)
                || !(source.getEntity() instanceof Mob mob) || !KyoukaAfterimages.hostile(this, mob)) return false;
        if (!net.minecraftforge.common.ForgeHooks.onLivingAttack(this, source, amount)) return false;
        finished = true;
        KyoukaAfterimages.shatter(this);
        discard();
        return true;
    }
    @Override public void remove(RemovalReason reason) {
        if (!level().isClientSide) KyoukaAfterimages.release(this);
        super.remove(reason);
    }
    @Override public boolean isPushable() { return false; }
    @Override public boolean canBeCollidedWith() { return false; }
    @Override public boolean shouldBeSaved() { return false; }
    @Override public boolean removeWhenFarAway(double distance) { return false; }

    @Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        @SubscribeEvent public static void attributes(EntityAttributeCreationEvent event) {
            event.put(ModEntities.KYOUKA_AFTERIMAGE.get(), Mob.createMobAttributes()
                    .add(Attributes.MAX_HEALTH, 1).add(Attributes.MOVEMENT_SPEED, 0)
                    .add(Attributes.KNOCKBACK_RESISTANCE, 1).add(Attributes.FOLLOW_RANGE, 0).build());
        }
    }
}
