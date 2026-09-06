package dev.bladetetra.client;

import dev.bladetetra.BladeTetra;
import mods.flammpfeil.slashblade.entity.EntityHeavyRainSwords;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Prevents saved orphaned SlashBlade rain swords from entering the client tick list. */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MikageProjectileSafetyClient {
    private static final ResourceLocation MIRROR_REALM =
            new ResourceLocation(BladeTetra.MOD_ID, "mirror_realm");

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide()
                || !event.getLevel().dimension().location().equals(MIRROR_REALM)
                || !(event.getEntity() instanceof EntityHeavyRainSwords sword)) {
            return;
        }
        if (!sword.itFired() && sword.getVehicle() == null && sword.getOwner() == null) {
            event.setCanceled(true);
        }
    }

    private MikageProjectileSafetyClient() {
    }
}
