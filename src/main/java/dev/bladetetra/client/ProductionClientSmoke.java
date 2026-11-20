package dev.bladetetra.client;

import com.mojang.logging.LogUtils;
import dev.bladetetra.BladeTetra;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/**
 * Emits a terminal success marker only after the actual packaged Forge client
 * has loaded the menu and ProductionRendererSmoke has inspected its renderer map.
 * Disabled for normal players unless a CI/diagnostic JVM flag explicitly opts in.
 */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ProductionClientSmoke {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final boolean ENABLED = Boolean.getBoolean("blade_tetra.productionSmoke");
    private static boolean emitted;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (!ENABLED || emitted || event.phase != TickEvent.Phase.END) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getOverlay() != null || minecraft.screen == null || minecraft.level != null) return;
        if (!ProductionRendererSmoke.renderersReady()) {
            throw new IllegalStateException("BLADE_TETRA_PRODUCTION_CLIENT_FAIL: "
                    + "client menu opened without a verified entity renderer map");
        }
        emitted = true;
        LOGGER.info("BLADE_TETRA_PRODUCTION_CLIENT_PASS: release JAR loaded on client; renderer map and menu ready");
    }

    private ProductionClientSmoke() {}
}
