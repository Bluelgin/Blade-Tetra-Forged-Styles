package dev.btsealed.runtime;

import com.mojang.blaze3d.vertex.PoseStack;
import mods.flammpfeil.slashblade.client.renderer.model.BladeModelManager;
import mods.flammpfeil.slashblade.client.renderer.util.BladeRenderState;
import mods.flammpfeil.slashblade.event.client.RenderOverrideEvent;
import mods.flammpfeil.slashblade.init.DefaultResources;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;

/** Renders a per-export durability portrait without Blade Tetra or Tetra on the receiver. */
public final class PortableBackplateClient {
    private static final ThreadLocal<Boolean> INSIDE = ThreadLocal.withInitial(() -> false);
    static void register() { MinecraftForge.EVENT_BUS.addListener(PortableBackplateClient::render); }

    private static void render(RenderOverrideEvent event) {
        if (INSIDE.get() || !DefaultResources.resourceDurabilityTexture.equals(event.getOriginalTexture())) return;
        var state = event.getStack().getCapability(ItemSlashBlade.BLADESTATE).resolve().orElse(null);
        if (state == null) return;
        String key = state.getTranslationKey();
        if (!key.startsWith("item.bt_export_") || !key.endsWith(".sealed_blade")) return;
        String namespace = key.substring(5, key.length() - ".sealed_blade".length());
        if (!namespace.matches("bt_export_[a-z0-9_]{1,50}")) return;
        var texture = ResourceLocation.fromNamespaceAndPath(namespace, "model/sealed_backplate.png");
        var model = ResourceLocation.fromNamespaceAndPath(namespace, "model/sealed_backplate_frame.obj");
        var manager = Minecraft.getInstance().getResourceManager();
        if (manager.getResource(texture).isEmpty() || manager.getResource(model).isEmpty()) return;
        var frame = BladeModelManager.getInstance().getModel(model);
        if (frame == null) return;
        // The original renderer uses the custom frame for base and durability-gauge groups.
        event.setModel(frame);
        event.setGetRenderType(PortableBackplateRenderType::get);
        event.setPackedLightIn(LightTexture.FULL_BRIGHT);
        if (!"base".equals(event.getOriginalTarget())) return;
        event.setCanceled(true);
        INSIDE.set(true);
        PoseStack poses = event.getPoseStack();
        try {
            BladeRenderState.renderOverrided(event.getStack(), frame, "base",
                    DefaultResources.resourceDurabilityTexture, poses, event.getBuffer(),
                    event.getPackedLightIn(), PortableBackplateRenderType::get, false);
            BladeRenderState.resetCol();
            BladeRenderState.renderOverrided(event.getStack(), frame, "portrait", texture,
                    poses, event.getBuffer(), LightTexture.FULL_BRIGHT,
                    PortableBackplateRenderType::get, false);
        } finally {
            INSIDE.set(false);
            BladeRenderState.resetCol();
        }
    }
    private PortableBackplateClient() {}
}
