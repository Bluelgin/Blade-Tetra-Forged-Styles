package dev.bladetetra.registry;

import dev.bladetetra.BladeTetra;
import dev.bladetetra.compat.LegacyMikageCompatibility;
import java.util.List;
import dev.bladetetra.aura.SwordAuraDriveEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, BladeTetra.MOD_ID);

    public static final RegistryObject<EntityType<dev.bladetetra.kyouka.KyoukaAfterimageEntity>> KYOUKA_AFTERIMAGE =
            ENTITIES.register("kyouka_afterimage", () -> EntityType.Builder.of(
                    dev.bladetetra.kyouka.KyoukaAfterimageEntity::new, MobCategory.MISC)
                    .sized(.6F, 1.8F).clientTrackingRange(10).updateInterval(1)
                    .build(BladeTetra.MOD_ID + ":kyouka_afterimage"));

    public static final RegistryObject<EntityType<SwordAuraDriveEntity>> SWORD_AURA_DRIVE =
            ENTITIES.register("sword_aura_drive", () -> EntityType.Builder.of(
                    SwordAuraDriveEntity::new, MobCategory.MISC)
                    .sized(0.75F, 0.75F).clientTrackingRange(10).updateInterval(1)
                    .build(BladeTetra.MOD_ID + ":sword_aura_drive"));

    public static final List<RegistryObject<EntityType<LegacyMikageCompatibility.RetiredEntity>>> RETIRED_MIKAGE =
            LegacyMikageCompatibility.registerEntities(ENTITIES);

    private ModEntities() {
    }
}
