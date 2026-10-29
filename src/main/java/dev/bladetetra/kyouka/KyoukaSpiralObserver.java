package dev.bladetetra.kyouka;

import java.util.*;
import dev.bladetetra.config.GameplayConfig;
import mods.flammpfeil.slashblade.entity.EntitySpiralSwords;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;

/** Batch by native owner + world game tick. Only actual accepted additions activate. */
@Mod.EventBusSubscriber(modid = "blade_tetra")
public final class KyoukaSpiralObserver {
    private record Batch(UUID owner, long tick) {}
    private static final Map<Batch, Set<EntitySpiralSwords>> PENDING = new HashMap<>();
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void joined(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || event.loadedFromDisk()
                || event.getEntity().getClass() != EntitySpiralSwords.class
                || !(event.getEntity() instanceof EntitySpiralSwords sword)
                || !(sword.getOwner() instanceof ServerPlayer owner)
                || !KyoukaMirrorSessions.eligible(owner.getMainHandItem())) return;
        if (PENDING.size() >= GameplayConfig.KYOUKA_MAX_SESSIONS.get()) return;
        var batch = new Batch(owner.getUUID(), event.getLevel().getGameTime());
        var swords = PENDING.computeIfAbsent(batch, ignored -> new HashSet<>());
        if (swords.size() < 8) swords.add(sword);
    }
    static void flush() {
        for (var swords : PENDING.values()) {
            swords.removeIf(s -> !s.isAlive() || s.itFired()
                    || s.level().getEntity(s.getId()) != s || s.getVehicle() != s.getOwner());
            if (swords.isEmpty()) continue;
            var sword = swords.iterator().next();
            if (sword.getOwner() instanceof ServerPlayer owner)
                KyoukaMirrorSessions.activate(owner, swords);
        }
        PENDING.clear();
    }
    static void clear() { PENDING.clear(); }
    private KyoukaSpiralObserver() {}
}
