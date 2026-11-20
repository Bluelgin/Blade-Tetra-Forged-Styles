package dev.bladetetra.client;

import dev.bladetetra.BladeTetra;
import dev.bladetetra.registry.ModEntities;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Internal test build: prove that the Kyouka entity reached the client. */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID, value = Dist.CLIENT)
public final class KyoukaVClientProbe {
    private static final Logger LOG = LoggerFactory.getLogger("BladeTetra-KyoukaVProbe");

    @SubscribeEvent
    public static void join(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide()
                || event.getEntity().getType() != ModEntities.KYOUKA_AFTERIMAGE.get()) return;
        var entity = event.getEntity();
        var renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(entity);
        LOG.info("[KYOUKA-V][CLIENT_JOIN] type=blade_tetra:kyouka_afterimage entityId={} uuid={} renderer={} dimension={} pos={}",
                entity.getId(), entity.getUUID(),
                renderer == null ? "NULL" : renderer.getClass().getName(),
                entity.level().dimension().location(), entity.position());
    }

    private KyoukaVClientProbe() {}
}
