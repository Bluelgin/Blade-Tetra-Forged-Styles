package dev.bladetetra.client;

import com.mojang.logging.LogUtils;
import dev.bladetetra.BladeTetra;
import dev.bladetetra.registry.ModEntities;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.util.List;

/**
 * Opt-in check on a real Forge client, after the renderer map has been built.
 * This executes from the reobfuscated release JAR rather than the userdev source set.
 * There is no work beyond a boolean check in normal play.
 */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ProductionRendererSmoke {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final boolean ENABLED = Boolean.getBoolean("blade_tetra.productionSmoke");
    private static volatile boolean ready;

    @SubscribeEvent
    public static void verifyRealRendererMap(EntityRenderersEvent.AddLayers event) {
        if (!ENABLED) return;

        List<EntityType<?>> required = List.of(
                ModEntities.KYOUKA_AFTERIMAGE.get(),
                ModEntities.MIKAGE_DIVINE_COMPANION.get(),
                ModEntities.MIKAGE.get(),
                ModEntities.MIKAGE_PHANTOM_SWORD.get(),
                ModEntities.MIKAGE_ECHO.get(),
                ModEntities.MIKAGE_GATE_CORE.get(),
                ModEntities.MIKAGE_GATE_SWORD.get(),
                ModEntities.SWORD_AURA_DRIVE.get()
        );
        for (EntityType<?> type : required) {
            // AddLayers exposes the actual completed renderer map. The typed API is
            // declared for living entities, but the lookup itself supports every type.
            @SuppressWarnings({"rawtypes", "unchecked"})
            EntityRenderer<?> renderer = event.getEntityRenderer((EntityType) type);
            if (renderer == null) {
                throw new IllegalStateException("BLADE_TETRA_PRODUCTION_RENDERERS_FAIL: "
                        + EntityType.getKey(type) + " has no client renderer");
            }
        }
        ready = true;
        LOGGER.info("BLADE_TETRA_PRODUCTION_RENDERERS_PASS: {} actual entity renderers", required.size());
    }

    static boolean renderersReady() {
        return ready;
    }

    private ProductionRendererSmoke() {}
}
