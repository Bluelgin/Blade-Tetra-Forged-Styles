package dev.bladetetra.challenge;

import dev.bladetetra.BladeTetra;
import dev.bladetetra.registry.ModEntities;
import dev.bladetetra.registry.ModItems;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Dedicated companion AI host, with the same vanilla player silhouette as Mikage. */
public final class MikageDivineCompanionEntity extends PathfinderMob {
    private static final EntityDataAccessor<Integer> ACTION = SynchedEntityData.defineId(
            MikageDivineCompanionEntity.class, EntityDataSerializers.INT);
    private long session;
    private int recover;
    private int actionUntil;

    public MikageDivineCompanionEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        setCanPickUpLoot(false);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(ACTION, 0);
    }

    public void bind(long id) {
        session = id;
        setItemSlot(EquipmentSlot.MAINHAND, ModItems.MODULAR_SLASHBLADE.get().createDefaultStack());
        setDropChance(EquipmentSlot.MAINHAND, 0);
    }

    public void pose(int pose) {
        if (pose == 0 && tickCount < actionUntil) {
            return;
        }
        entityData.set(ACTION, pose);
        if (pose != 0) {
            actionUntil = tickCount + (pose == 2 ? 10 : 20);
        }
    }

    public int pose() {
        return entityData.get(ACTION);
    }

    public boolean recovering() {
        return recover > 0;
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide) {
            if (!DivineDomainManager.ownsCompanion(session, getUUID())) {
                discard();
                return;
            }
            if (tickCount >= actionUntil) {
                pose(0);
            }
            if (recover > 0) {
                getNavigation().stop();
                recover--;
                pose(4);
                if (recover == 0) {
                    setHealth(getMaxHealth());
                    pose(0);
                }
            }
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        var attacker = source.getEntity();
        if (level().isClientSide || recover > 0 || attacker == null) {
            return false;
        }
        var activeSession = DivineDomainManager.activeSession(attacker);
        if (activeSession == null || activeSession.id != session
                || !activeSession.enemies.contains(attacker.getUUID())) {
            return false;
        }
        float hit = Math.min(12, amount);
        if (hit >= getHealth()) {
            setHealth(1);
            recover = 100;
            return true;
        }
        return super.hurt(source, hit);
    }

    @Override
    protected boolean shouldDespawnInPeaceful() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    public static AttributeSupplier.Builder attributes() {
        return createMobAttributes()
                .add(Attributes.MAX_HEALTH, 100)
                .add(Attributes.MOVEMENT_SPEED, .29)
                .add(Attributes.ATTACK_DAMAGE, 3)
                .add(Attributes.ARMOR, 12)
                .add(Attributes.KNOCKBACK_RESISTANCE, .8);
    }

    @Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class Registration {
        @SubscribeEvent
        public static void attributes(EntityAttributeCreationEvent event) {
            event.put(ModEntities.MIKAGE_DIVINE_COMPANION.get(),
                    MikageDivineCompanionEntity.attributes().build());
        }
    }
}
