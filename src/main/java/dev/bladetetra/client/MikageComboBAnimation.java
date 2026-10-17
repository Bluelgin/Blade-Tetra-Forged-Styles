package dev.bladetetra.client;

import dev.bladetetra.BladeTetra;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;
import java.util.Map;

/** Native B VMD rotation samples retargeted to the shared arm rig; see bundled animation MIT. */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class MikageComboBAnimation {
    private static final ResourceLocation FILE = new ResourceLocation(BladeTetra.MOD_ID, "animations/mikage_combo_b.json");
    private static MikageComboBTracks clips;
    private static boolean loaded;
    @SubscribeEvent public static void reload(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) manager -> { loaded = false; clips = null; });
    }
    public static Map<String, Vector3f> sample(int stage, float seconds) {
        if (!loaded) load();
        return clips == null ? Map.of() : clips.sample(stage, seconds);
    }
    private static void load() {
        loaded = true;
        try (var reader = Minecraft.getInstance().getResourceManager().getResource(FILE).orElseThrow().openAsReader()) {
            clips = MikageComboBTracks.read(reader);
        } catch (Exception ex) {
            clips = null; org.slf4j.LoggerFactory.getLogger(MikageComboBAnimation.class).warn("Could not load native Combo B poses", ex);
        }
    }
    private MikageComboBAnimation() {}
}
