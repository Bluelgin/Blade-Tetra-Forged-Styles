package dev.bladetetra.aura;

import dev.bladetetra.BladeTetra;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Player-owned mastery; no weapon NBT or Tetra schematic dependency. */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID)
public final class SwordAuraMastery {
    private static final String LEARNED = "blade_tetra_lifeng_learned";

    public static boolean knows(ServerPlayer player) {
        return player.getPersistentData().getBoolean(LEARNED);
    }

    public static boolean learn(ServerPlayer player) {
        if (knows(player)) return false;
        player.getPersistentData().putBoolean(LEARNED, true);
        return true;
    }

    /** A world's dragon defeat unlocks research for every resident, even late joiners. */
    public static boolean worldUnlocked(ServerPlayer player) {
        ServerLevel end = player.server.getLevel(Level.END);
        return end != null && end.getDragonFight() != null
                && end.getDragonFight().hasPreviouslyKilledDragon();
    }

    @SubscribeEvent
    public static void preserveOnDeath(PlayerEvent.Clone event) {
        if (event.isWasDeath() && event.getOriginal().getPersistentData().getBoolean(LEARNED)) {
            event.getEntity().getPersistentData().putBoolean(LEARNED, true);
        }
    }

    private SwordAuraMastery() {}
}
