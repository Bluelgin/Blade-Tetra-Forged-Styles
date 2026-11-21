package dev.bladetetra.compat.fusion;

import mods.flammpfeil.slashblade.slasharts.SlashArts;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.capabilities.Capability;

import java.util.Set;

/** Read-only checks for the exact optional capabilities used by the audited Recasting arts. */
public final class RecastingFusionCompatibility {
    private static final ResourceLocation TIME_BEYOND = new ResourceLocation("recasting", "time_beyond");
    private static final Set<String> EXTENSION_ARTS = Set.of(
            "void_hole", "void_hole_pitch_black", "void_hole_fishy_red",
            "lightning_chain_1", "lightning_chain_2", "lightning_chain_3", "lightning_chain_3_lambda",
            "soul_sever", "soul_sever_lambda");

    public static boolean canSelect(ResourceLocation id) {
        // Recasting samples real ChargeActionEvents before the cast for this art.
        return !TIME_BEYOND.equals(id);
    }

    public static boolean ready(ResourceLocation id, SlashArts art, ServerPlayer player) {
        if (!"recasting".equals(id.getNamespace()) || !EXTENSION_ARTS.contains(id.getPath())) return true;
        try {
            Class<?> registry = Class.forName("com.til.recasting.handler.CapabilityRegistryHandler",
                    false, art.getClass().getClassLoader());
            var blade = player.getMainHandItem();
            if (!blade.getCapability(capability(registry, "PROPERTIES_DEFINITION_EXTENSION")).isPresent()
                    || !blade.getCapability(capability(registry, "RENDER_DEFINITION_EXTENSION")).isPresent()) {
                return false;
            }
            return !(id.getPath().startsWith("lightning_chain_") || id.getPath().startsWith("soul_sever"))
                    || player.getCapability(capability(registry, "TIME_RUN")).isPresent();
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot verify Recasting capabilities for " + id, exception);
        }
    }

    private static Capability<?> capability(Class<?> registry, String field) throws ReflectiveOperationException {
        return (Capability<?>) registry.getField(field).get(null);
    }

    private RecastingFusionCompatibility() {}
}
