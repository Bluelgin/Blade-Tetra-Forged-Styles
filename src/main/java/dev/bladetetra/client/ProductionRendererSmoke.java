package dev.bladetetra.client;

import com.mojang.logging.LogUtils;
import dev.bladetetra.BladeTetra;
import dev.bladetetra.registry.ModEntities;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

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
        // Forge 47.4.x's AddLayers public getRenderer() is typed only for
        // LivingEntityRenderer and cannot read a generic renderer such as
        // KyoukaAfterimageRenderer or either projectile renderer. Read the
        // Forge-owned completed map directly; its field name is not Mojang-mapped.
        Map<?, ?> renderers = completedRendererMap(event);
        for (EntityType<?> type : required) {
            if (renderers.get(type) == null) {
                throw new IllegalStateException("BLADE_TETRA_PRODUCTION_RENDERERS_FAIL: "
                        + EntityType.getKey(type) + " has no client renderer");
            }
        }
        ready = true;
        LOGGER.info("BLADE_TETRA_PRODUCTION_RENDERERS_PASS: {} actual entity renderers", required.size());
    }

    private static Map<?, ?> completedRendererMap(EntityRenderersEvent.AddLayers event) {
        try {
            Field field = EntityRenderersEvent.AddLayers.class.getDeclaredField("renderers");
            field.setAccessible(true);
            return (Map<?, ?>) field.get(event);
        } catch (ReflectiveOperationException | SecurityException failure) {
            throw new IllegalStateException("BLADE_TETRA_PRODUCTION_RENDERERS_FAIL: "
                    + "could not inspect Forge completed renderer map", failure);
        }
    }

    static boolean renderersReady() {
        return ready;
    }

    private ProductionRendererSmoke() {}
}
