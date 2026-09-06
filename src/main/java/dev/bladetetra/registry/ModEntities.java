package dev.bladetetra.registry;

import dev.bladetetra.BladeTetra;
import dev.bladetetra.challenge.MikageEntity;
import dev.bladetetra.challenge.MikageEchoEntity;
import dev.bladetetra.challenge.MikagePhantomSwordEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, BladeTetra.MOD_ID);

    public static final RegistryObject<EntityType<MikageEntity>> MIKAGE = ENTITIES.register(
            "mikage",
            () -> EntityType.Builder.of(MikageEntity::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.8F)
                    .clientTrackingRange(12)
                    .updateInterval(2)
                    .build(BladeTetra.MOD_ID + ":mikage"));

    public static final RegistryObject<EntityType<MikagePhantomSwordEntity>> MIKAGE_PHANTOM_SWORD =
            ENTITIES.register("mikage_phantom_sword",
                    () -> EntityType.Builder.of(MikagePhantomSwordEntity::new,
                                    MobCategory.MONSTER)
                            .sized(0.55F, 1.55F)
                            .clientTrackingRange(12)
                            .updateInterval(1)
                            .build(BladeTetra.MOD_ID + ":mikage_phantom_sword"));

    public static final RegistryObject<EntityType<MikageEchoEntity>> MIKAGE_ECHO =
            ENTITIES.register("mikage_echo",
                    () -> EntityType.Builder.of(MikageEchoEntity::new, MobCategory.MONSTER)
                            .sized(0.6F, 1.8F)
                            .clientTrackingRange(12)
                            .updateInterval(1)
                            .build(BladeTetra.MOD_ID + ":mikage_echo"));

    private ModEntities() {
    }
}
