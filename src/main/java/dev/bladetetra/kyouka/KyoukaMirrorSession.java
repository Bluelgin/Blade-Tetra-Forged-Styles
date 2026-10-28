package dev.bladetetra.kyouka;

import java.util.*;
import mods.flammpfeil.slashblade.entity.EntitySpiralSwords;
import mods.flammpfeil.slashblade.entity.EntitySlashEffect;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.ResourceLocation;

/** One server authority, independent of the awakening ceremony and client render state. */
final class KyoukaMirrorSession {
    final UUID id = UUID.randomUUID(), weapon;
    final ServerPlayer owner;
    final LivingEntity target;
    final ItemStack blade;
    final Set<EntitySpiralSwords> ring;
    final Set<UUID> viewers = new HashSet<>();
    final Map<Long, Budget> budgets = new LinkedHashMap<>();
    final Map<List<net.minecraft.world.entity.Entity>, Shot> shots = new IdentityHashMap<>();
    ResourceLocation combo = new ResourceLocation("slashblade", "none");
    long started, action, nextDrain;
    float yaw;
    KyoukaMirrorSession(ServerPlayer owner, LivingEntity target, UUID weapon,
            Set<EntitySpiralSwords> ring) {
        this.owner = owner; this.target = target; this.weapon = weapon;
        blade = owner.getMainHandItem(); this.ring = new HashSet<>(ring);
        started = owner.level().getGameTime(); nextDrain = started + 20;
    }
    static final class Budget {
        boolean paid;
        int windows;
        final Set<String> keys = new HashSet<>();
    }
    record Shot(EntitySlashEffect entity, long action, Set<Integer> windows) {}
}
