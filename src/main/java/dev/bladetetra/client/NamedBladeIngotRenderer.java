package dev.bladetetra.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.bladetetra.BladeTetra;
import dev.bladetetra.forging.LegacyImprintKind;
import dev.bladetetra.item.NamedBladeIngotItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.InputStream;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Client-only source-textured ingots: iron silhouette and metal edges, with a
 * source icon/backplate artwork under unified metal lighting.
 * No external texture decoding, atlas baking or cache mutation occurs on servers.
 */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.MOD)
public final class NamedBladeIngotRenderer extends BlockEntityWithoutLevelRenderer {
    private static final int SIZE = 32;
    private static final int LIMIT = 96;
    private static final ResourceLocation IRON = ResourceLocation.fromNamespaceAndPath(
            "minecraft", "textures/item/iron_ingot.png");
    private static final Set<String> UNAVAILABLE = new LinkedHashSet<>();
    private static final Map<String, ResourceLocation> CACHE =
            new LinkedHashMap<>(32, .75F, true);

    public NamedBladeIngotRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(),
                Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context,
            PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        ResourceLocation texture = resolve(stack);
        VertexConsumer vertices = buffers.getBuffer(RenderType.entityTranslucent(texture));
        var top = pose.last();
        draw(vertices, top, light, overlay, false);
        draw(vertices, top, light, overlay, true);
    }

    private static void draw(VertexConsumer out, PoseStack.Pose pose,
            int light, int overlay, boolean reverse) {
        float z = reverse ? -0.001F : 0.001F;
        float n = reverse ? -1F : 1F;
        if (reverse) {
            vertex(out, pose, 0, 0, z, 0, 1, light, overlay, n);
            vertex(out, pose, 0, 1, z, 0, 0, light, overlay, n);
            vertex(out, pose, 1, 1, z, 1, 0, light, overlay, n);
            vertex(out, pose, 1, 0, z, 1, 1, light, overlay, n);
        } else {
            vertex(out, pose, 1, 0, z, 1, 1, light, overlay, n);
            vertex(out, pose, 1, 1, z, 1, 0, light, overlay, n);
            vertex(out, pose, 0, 1, z, 0, 0, light, overlay, n);
            vertex(out, pose, 0, 0, z, 0, 1, light, overlay, n);
        }
    }

    private static void vertex(VertexConsumer out, PoseStack.Pose pose,
            float x, float y, float z, float u, float v, int light, int overlay, float normal) {
        out.vertex(pose.pose(), x, y, z).color(255, 255, 255, 255)
                .uv(u, v).overlayCoords(overlay).uv2(light)
                .normal(pose.normal(), 0, 0, normal).endVertex();
    }

    private static synchronized ResourceLocation resolve(ItemStack stack) {
        String id = NamedBladeIngotItem.sourceId(stack);
        LegacyImprintKind kind = NamedBladeIngotItem.source(stack);
        if (id == null || kind == null) return new ResourceLocation("blade_tetra", "textures/item/named_blade_ingot_unknown.png");
        if (UNAVAILABLE.contains(id)) return IRON;
        ResourceLocation cached = CACHE.get(id);
        if (cached != null) return cached;
        Minecraft minecraft = Minecraft.getInstance();
        try (InputStream ingot = minecraft.getResourceManager()
                        .getResourceOrThrow(IRON).open();
                NativeImage iron = NativeImage.read(ingot);
                NativeImage sword = NamedIngotArtwork.source(minecraft.getResourceManager(), kind);
                NativeImage output = new NativeImage(SIZE, SIZE, true)) {
            NamedIngotArtwork.compose(iron, sword, output);
            ResourceLocation registered = minecraft.getTextureManager()
                    .register("named_ingot/" + Integer.toUnsignedString(id.hashCode(), 16)
                            + "_" + id.length(),
                            new DynamicTexture(copy(output)));
            CACHE.put(id, registered);
            while (CACHE.size() > LIMIT) {
                Iterator<ResourceLocation> itr = CACHE.values().iterator();
                ResourceLocation eldest = itr.next();
                itr.remove();
                minecraft.getTextureManager().release(eldest);
            }
            return registered;
        } catch (Exception ignored) {
            // Cache bad resource references until reload rather than decoding every frame.
            UNAVAILABLE.add(id);
            while (UNAVAILABLE.size() > LIMIT) {
                Iterator<String> itr = UNAVAILABLE.iterator();
                itr.next();
                itr.remove();
            }
            return IRON;
        }
    }

    private static NativeImage copy(NativeImage image) {
        NativeImage copy = new NativeImage(image.getWidth(), image.getHeight(), true);
        copy.copyFrom(image);
        return copy;
    }

    static synchronized int cachedSources() { return CACHE.size(); }

    private static synchronized void clear() {
        Minecraft minecraft = Minecraft.getInstance();
        for (ResourceLocation id : CACHE.values()) minecraft.getTextureManager().release(id);
        CACHE.clear();
        UNAVAILABLE.clear();
    }

    @SubscribeEvent
    public static void onReload(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener)
                manager -> clear());
    }
}
