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
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Client-only source-textured ingots: iron silhouette and metal edges, with a
 * blurred/rotated mosaic of the named blade's own declared texture inside.
 * No external texture decoding, atlas baking or cache mutation occurs on servers.
 */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.MOD)
public final class NamedBladeIngotRenderer extends BlockEntityWithoutLevelRenderer {
    private static final int SIZE = 32;
    private static final int LIMIT = 96;
    private static final ResourceLocation IRON = ResourceLocation.fromNamespaceAndPath(
            "minecraft", "textures/item/iron_ingot.png");
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
        if (id == null || kind == null) return IRON;
        ResourceLocation cached = CACHE.get(id);
        if (cached != null) return cached;
        Minecraft minecraft = Minecraft.getInstance();
        try (InputStream ingot = minecraft.getResourceManager()
                        .getResourceOrThrow(IRON).open();
                InputStream source = minecraft.getResourceManager()
                        .getResourceOrThrow(kind.texture()).open();
                NativeImage iron = NativeImage.read(ingot);
                NativeImage sword = NativeImage.read(source);
                NativeImage output = new NativeImage(SIZE, SIZE, true)) {
            compose(iron, sword, output, id.hashCode());
            ResourceLocation registered = minecraft.getTextureManager()
                    .register("named_ingot/" + Integer.toUnsignedString(id.hashCode(), 16),
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
            // Removed addon, invalid PNG or unsupported source: use vanilla iron.
            return IRON;
        }
    }

    private static NativeImage copy(NativeImage image) {
        NativeImage copy = new NativeImage(image.getWidth(), image.getHeight(), true);
        copy.copyFrom(image);
        return copy;
    }

    /**
     * Pixel-art friendly blur: 3x3 neighborhood of a rotated source patch
     * selected by ingot quadrant; unchanged metallic rim keeps the iron shape.
     */
    private static void compose(NativeImage iron, NativeImage source,
            NativeImage dest, int seed) {
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                int bx = x * iron.getWidth() / SIZE;
                int by = y * iron.getHeight() / SIZE;
                int metal = iron.getPixelRGBA(bx, by);
                if (alpha(metal) < 40) {
                    dest.setPixelRGBA(x, y, 0);
                    continue;
                }
                boolean edge = false;
                for (int dy = -1; dy <= 1 && !edge; dy++) for (int dx = -1; dx <= 1; dx++) {
                    int nx = bx + dx, ny = by + dy;
                    if (nx < 0 || ny < 0 || nx >= iron.getWidth()
                            || ny >= iron.getHeight()
                            || alpha(iron.getPixelRGBA(nx, ny)) < 40) {
                        edge = true;
                        break;
                    }
                }
                if (edge) {
                    dest.setPixelRGBA(x, y, metal);
                    continue;
                }
                int quadrant = (x >= SIZE / 2 ? 1 : 0) + (y >= SIZE / 2 ? 2 : 0);
                int rr = 0, gg = 0, bb = 0, count = 0;
                for (int sy = -2; sy <= 2; sy++) for (int sx = -2; sx <= 2; sx++) {
                    // Alternate tile orientation: no whole-sword silhouette in the fill.
                    int u = quadrant % 2 == 0 ? x + sx : SIZE - y + sy;
                    int v = quadrant % 2 == 0 ? y + sy : x + sx;
                    int tx = Math.floorMod(u * source.getWidth() / SIZE + seed + quadrant * 23,
                            source.getWidth());
                    int ty = Math.floorMod(v * source.getHeight() / SIZE
                            + (seed >>> 8) + quadrant * 31, source.getHeight());
                    int pixel = source.getPixelRGBA(tx, ty);
                    if (alpha(pixel) < 40) continue;
                    rr += pixel & 255;
                    gg += (pixel >>> 8) & 255;
                    bb += (pixel >>> 16) & 255;
                    count++;
                }
                if (count == 0) {
                    dest.setPixelRGBA(x, y, metal);
                } else {
                    int r = Math.min(255, (rr / count * 3 + (metal & 255)) / 4);
                    int g = Math.min(255, (gg / count * 3 + ((metal >>> 8) & 255)) / 4);
                    int b = Math.min(255, (bb / count * 3 + ((metal >>> 16) & 255)) / 4);
                    dest.setPixelRGBA(x, y, (alpha(metal) << 24)
                            | (b << 16) | (g << 8) | r);
                }
            }
        }
    }

    private static int alpha(int abgr) {
        return (abgr >>> 24) & 255;
    }

    private static synchronized void clear() {
        Minecraft minecraft = Minecraft.getInstance();
        for (ResourceLocation id : CACHE.values()) minecraft.getTextureManager().release(id);
        CACHE.clear();
    }

    @SubscribeEvent
    public static void onReload(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener)
                manager -> clear());
    }
}
