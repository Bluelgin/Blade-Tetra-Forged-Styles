package dev.bladetetra.compat;

import dev.bladetetra.BladeTetra;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

import java.util.List;
import java.util.Set;

/** Stable decompression targets for worlds written before the challenge was retired. */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID)
public final class LegacyMikageCompatibility {
    private static final String ORIGIN = "blade_tetra_origin_";
    private static final ResourceLocation DIVINE_DOMAIN = id("divine_domain");
    private static final Set<ResourceLocation> RETIRED_REALMS = Set.of(
            id("mirror_realm"), DIVINE_DOMAIN, id("easter_realm"));
    private static final List<String> PLAYER_KEYS = List.of(
            "blade_tetra_challenge_id", "blade_tetra_easter_entry_ticket",
            "blade_tetra_easter_pending_return", "blade_tetra_gate_lock_until",
            "blade_tetra_divine_challenge", "blade_tetra_divine_origin_x", "blade_tetra_divine_origin_z",
            ORIGIN + "dimension", ORIGIN + "x", ORIGIN + "y", ORIGIN + "z", ORIGIN + "yaw", ORIGIN + "pitch");

    private LegacyMikageCompatibility() {}

    public static List<RegistryObject<EntityType<RetiredEntity>>> registerEntities(
            DeferredRegister<EntityType<?>> registry) {
        return List.of("mikage", "mikage_echo", "mikage_phantom_sword", "mikage_divine_companion",
                "mikage_gate_core", "mikage_gate_sword").stream()
                .map(path -> registry.register(path, () -> EntityType.Builder.of(RetiredEntity::new, MobCategory.MISC)
                        .sized(.25F, .25F).noSummon().build(id(path).toString())))
                .toList();
    }

    @SubscribeEvent
    public static void discardRetiredEntity(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        Entity entity = event.getEntity();
        if (entity instanceof RetiredEntity) {
            event.setCanceled(true);
        } else if (event.loadedFromDisk() && !(entity instanceof ServerPlayer)) {
            var data = entity.getPersistentData();
            if (data.getBoolean("blade_tetra_mikage_attack")
                    || event.getLevel().dimension().location().equals(DIVINE_DOMAIN)
                    && data.contains("blade_tetra_divine_challenge", Tag.TAG_LONG)) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void recoverOnLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) recover(player);
    }

    @SubscribeEvent
    public static void recoverRetiredRealmPlayer(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.player instanceof ServerPlayer player
                && player.tickCount % 20 == 0 && RETIRED_REALMS.contains(player.level().dimension().location())) {
            recover(player);
        }
    }

    static void recover(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        if (!RETIRED_REALMS.contains(player.level().dimension().location())) {
            PLAYER_KEYS.forEach(data::remove);
            return;
        }

        ResourceLocation origin = ResourceLocation.tryParse(data.getString(ORIGIN + "dimension"));
        ServerLevel destination = origin == null || RETIRED_REALMS.contains(origin) ? null
                : player.server.getLevel(ResourceKey.create(Registries.DIMENSION, origin));
        double x = data.getDouble(ORIGIN + "x");
        double y = data.getDouble(ORIGIN + "y");
        double z = data.getDouble(ORIGIN + "z");
        var position = BlockPos.containing(x, y, z);
        boolean coordinates = data.contains(ORIGIN + "x", Tag.TAG_ANY_NUMERIC)
                && data.contains(ORIGIN + "y", Tag.TAG_ANY_NUMERIC)
                && data.contains(ORIGIN + "z", Tag.TAG_ANY_NUMERIC)
                && Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z);
        if (destination == null || !coordinates || destination.isOutsideBuildHeight(position)
                || !destination.getWorldBorder().isWithinBounds(position)) {
            destination = player.server.overworld();
            BlockPos spawn = destination.getSharedSpawnPos();
            x = spawn.getX() + .5;
            y = spawn.getY() + 1;
            z = spawn.getZ() + .5;
        }
        float yaw = data.getFloat(ORIGIN + "yaw");
        float pitch = data.getFloat(ORIGIN + "pitch");
        player.teleportTo(destination, x, y, z, Float.isFinite(yaw) ? yaw : player.getYRot(),
                Float.isFinite(pitch) ? pitch : player.getXRot());
        if (!RETIRED_REALMS.contains(player.level().dimension().location())) {
            PLAYER_KEYS.forEach(data::remove);
        }
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(BladeTetra.MOD_ID, path);
    }

    public static final class RetiredEntity extends Entity {
        private RetiredEntity(EntityType<?> type, Level level) {
            super(type, level);
        }

        @Override protected void defineSynchedData() {}
        @Override protected void readAdditionalSaveData(CompoundTag tag) {}
        @Override protected void addAdditionalSaveData(CompoundTag tag) {}
    }
}
