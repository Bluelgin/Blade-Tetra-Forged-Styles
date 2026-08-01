package dev.bladetetra.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import dev.bladetetra.BladeTetra;
import dev.bladetetra.config.ClientVisualConfig;
import dev.bladetetra.easteregg.AkatsukiAwakening;
import dev.bladetetra.easteregg.KyoukaAwakening;
import dev.bladetetra.easteregg.SenbonzakuraAwakening;
import dev.bladetetra.item.ModularSlashBladeItem;
import dev.bladetetra.visual.MaterialAppearance;
import dev.bladetetra.visual.SayaBannerSkin;
import dev.bladetetra.visual.SayaPresetSkin;
import mods.flammpfeil.slashblade.client.renderer.util.BladeRenderState;
import mods.flammpfeil.slashblade.client.renderer.model.BladeModelManager;
import mods.flammpfeil.slashblade.event.client.RenderOverrideEvent;
import mods.flammpfeil.slashblade.init.DefaultResources;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.entity.BannerPattern;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds material-aware SlashBlade texture atlases on demand.
 *
 * <p>SlashBlade renders through a single texture resource location, while the
 * Alpha 9 atlas already assigns every visible component to an isolated region.
 * Recoloring those regions at render time preserves independent Tetra material
 * choices without producing a combinatorial number of PNG resources.</p>
 */
@Mod.EventBusSubscriber(
        modid = BladeTetra.MOD_ID,
        value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MaterialTextureManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation TEMPLATE =
            ResourceLocation.fromNamespaceAndPath(
                    BladeTetra.MOD_ID, "model/modular/standard.png");
    private static final ResourceLocation DURABILITY_MODEL =
            ResourceLocation.fromNamespaceAndPath(
                    BladeTetra.MOD_ID, "model/util/durability_filled.obj");
    private static final int LOGICAL_ATLAS_SIZE = 128;
    /**
     * A 512px RGBA atlas consumes roughly 1 MiB of texture memory. Keeping the
     * LRU bounded at 48 preserves material variety without retaining hundreds
     * of high-resolution combinations in long modpack sessions.
     */
    private static final int MAX_CACHE_SIZE = 48;

    private static final Map<String, RegisteredTexture> CACHE =
            new LinkedHashMap<>(32, 0.75F, true);
    private static final Map<String, RegisteredTexture> EMISSIVE_CACHE =
            new LinkedHashMap<>(24, 0.75F, true);
    /**
     * Signatures whose generated emission mask contains no visible pixels.
     * Without this negative cache, an ordinary non-luminous blade would load,
     * recolor, and scan the complete 512px atlas again on every render pass.
     */
    private static final Set<String> NO_EMISSIVE_CACHE =
            new LinkedHashSet<>(24);
    private static final Map<String, RegisteredTexture> DURABILITY_BASE_CACHE =
            new LinkedHashMap<>(16, 0.75F, true);
    private static final ThreadLocal<Boolean> RENDERING_INTERNAL_PASS =
            ThreadLocal.withInitial(() -> false);
    private static final Palette RAYSKIN_PALETTE =
            new Palette(0x6D6552, 0xC4B99A, 0xF1E8CC);

    private MaterialTextureManager() {
    }

    @SubscribeEvent
    public static void onRenderOverride(RenderOverrideEvent event) {
        if (RENDERING_INTERNAL_PASS.get()
                || !(event.getStack().getItem() instanceof ModularSlashBladeItem)) {
            return;
        }

        MaterialAppearance appearance =
                MaterialAppearance.fromStack(event.getStack());
        if (DefaultResources.resourceDurabilityTexture.equals(
                event.getOriginalTexture())) {
            // Resharped's durability "base" group is only a hollow frame.
            // Use a private compatible model whose base also contains a rear
            // face, while retaining the original color/color_r gauge groups.
            event.setModel(BladeModelManager.getInstance()
                    .getModel(DURABILITY_MODEL));
            if ("base".equals(event.getOriginalTarget())) {
                ResourceLocation baseTexture =
                        ensureDurabilityBaseTexture(appearance.blade());
                if (baseTexture != null) {
                    // Resharped normally multiplies this group by a global
                    // gray-to-magenta damage tint intended for its grayscale
                    // frame. Our texture is already material-colored, so draw
                    // it through a guarded pass with a neutral vertex color.
                    // Disabling effect here also keeps an item's foil glint
                    // from masking the material across the filled rear face.
                    event.setCanceled(true);
                    BladeRenderState.resetCol();
                    RENDERING_INTERNAL_PASS.set(true);
                    try {
                        BladeRenderState.renderOverrided(
                                event.getStack(),
                                event.getModel(),
                                event.getTarget(),
                                baseTexture,
                                event.getPoseStack(),
                                event.getBuffer(),
                                event.getPackedLightIn(),
                                event.getGetRenderType(),
                                false);
                    } finally {
                        RENDERING_INTERNAL_PASS.set(false);
                        BladeRenderState.resetCol();
                    }
                }
            }
            // Preserve Resharped's original blue durability layer and red
            // broken-state layer. They share the same source texture but use
            // the "color" and "color_r" targets respectively.
            return;
        }

        ResourceLocation texture = ensureTexture(appearance);
        if (texture == null) {
            return;
        }

        ResourceLocation emissive = ensureEmissiveTexture(
                event.getStack(), appearance);
        if (emissive == null) {
            event.setTexture(texture);
            return;
        }

        // Render the ordinary material texture first, then add a transparent
        // full-bright pass through Resharped's own luminous render type.
        // Cancelling the outer render prevents the base model from being drawn
        // a second time. The thread-local guard keeps both nested passes from
        // recursively generating more overlays.
        event.setCanceled(true);
        RENDERING_INTERNAL_PASS.set(true);
        try {
            BladeRenderState.renderOverrided(
                    event.getStack(),
                    event.getModel(),
                    event.getTarget(),
                    texture,
                    event.getPoseStack(),
                    event.getBuffer(),
                    event.getPackedLightIn(),
                    event.getGetRenderType(),
                    event.isEnableEffect());
            BladeRenderState.renderOverridedLuminous(
                    event.getStack(),
                    event.getModel(),
                    event.getTarget(),
                    emissive,
                    event.getPoseStack(),
                    event.getBuffer(),
                    event.getPackedLightIn());
        } finally {
            RENDERING_INTERNAL_PASS.set(false);
        }
    }

    private static synchronized ResourceLocation ensureEmissiveTexture(
            net.minecraft.world.item.ItemStack stack,
            MaterialAppearance appearance) {
        if (!ClientVisualConfig.ENABLE_EMISSIVE_TEXTURES.get()) {
            return null;
        }

        GlowState glowState = GlowState.fromStack(stack);
        double materialIntensity = ClientVisualConfig.EMISSIVE_INTENSITY.get();
        double soulIntensity = ClientVisualConfig.SOUL_GLOW_INTENSITY.get();
        if (materialIntensity <= 0.0D
                && (glowState.soul() == SoulGlow.NONE || soulIntensity <= 0.0D)) {
            return null;
        }

        String signature = appearance.signature()
                + "|emissive-v1|"
                + glowState.signature()
                + "|" + Math.round(materialIntensity * 100.0D)
                + "|" + Math.round(soulIntensity * 100.0D);
        RegisteredTexture cached = EMISSIVE_CACHE.get(signature);
        if (cached != null) {
            return cached.location();
        }
        if (NO_EMISSIVE_CACHE.contains(signature)) {
            return null;
        }

        Minecraft minecraft = Minecraft.getInstance();
        try {
            Resource resource = minecraft.getResourceManager()
                    .getResourceOrThrow(TEMPLATE);
            NativeImage image;
            try (InputStream stream = resource.open()) {
                image = NativeImage.read(stream);
            }
            recolor(image, appearance, minecraft.getResourceManager());
            boolean visible = applyEmissionMask(
                    image,
                    appearance,
                    glowState,
                    (float) materialIntensity,
                    (float) soulIntensity);
            if (!visible) {
                image.close();
                NO_EMISSIVE_CACHE.add(signature);
                trimNoEmissiveCache();
                return null;
            }

            DynamicTexture dynamicTexture = new DynamicTexture(image);
            ResourceLocation location = ResourceLocation.fromNamespaceAndPath(
                    BladeTetra.MOD_ID,
                    "generated/emissive_"
                            + Integer.toUnsignedString(signature.hashCode(), 16));
            minecraft.getTextureManager().register(location, dynamicTexture);
            EMISSIVE_CACHE.put(
                    signature,
                    new RegisteredTexture(location, dynamicTexture));
            trimEmissiveCache(minecraft);
            return location;
        } catch (IOException | RuntimeException exception) {
            LOGGER.warn(
                    "Unable to generate modular SlashBlade emissive texture for {}",
                    signature,
                    exception);
            return null;
        }
    }

    private static boolean applyEmissionMask(
            NativeImage image,
            MaterialAppearance appearance,
            GlowState glowState,
            float materialIntensity,
            float soulIntensity) {
        boolean visible = false;
        float dormantScale = glowState.broken() ? 0.24F : 1.0F;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int source = image.getPixelRGBA(x, y);
                if (alpha(source) == 0) {
                    continue;
                }

                float atlasX = logicalCoordinate(x, image.getWidth());
                float atlasY = logicalCoordinate(y, image.getHeight());
                MaterialStyle style = emissionStyleAt(
                        appearance, atlasX, atlasY);
                int materialAlpha = style == null
                        ? 0
                        : Math.round(style.emissionAlpha(
                                x, y, atlasX, atlasY)
                                * materialIntensity * dormantScale);

                int soulAlpha = 0;
                int soulColor = 0;
                if (inside(atlasX, atlasY, 1, 1, 63, 31)
                        && glowState.soul() != SoulGlow.NONE) {
                    soulAlpha = Math.round(soulPatternAlpha(
                            glowState.soul(), atlasX, atlasY)
                            * soulIntensity * dormantScale);
                    soulColor = glowState.soul().colorAt(x, y);
                }

                int finalAlpha = Math.min(255, Math.max(materialAlpha, soulAlpha));
                if (finalAlpha <= 0) {
                    image.setPixelRGBA(x, y, 0);
                    continue;
                }

                int baseColor = red(source) << 16
                        | green(source) << 8
                        | blue(source);
                int glowColor = Palette.lerp(baseColor, 0xFFFFFF, 0.28F);
                if (soulAlpha > materialAlpha) {
                    glowColor = Palette.lerp(
                            glowColor, soulColor, 0.78F);
                }
                image.setPixelRGBA(x, y, abgr(finalAlpha, glowColor));
                visible = true;
            }
        }
        return visible;
    }

    private static MaterialStyle emissionStyleAt(
            MaterialAppearance appearance,
            float atlasX,
            float atlasY) {
        if (inside(atlasX, atlasY, 1, 1, 63, 31)) {
            return styleFor(appearance.blade());
        }
        if (inside(atlasX, atlasY, 1, 35, 63, 55)) {
            return styleFor(appearance.saya());
        }
        if (inside(atlasX, atlasY, 1, 59, 47, 81)) {
            return styleFor(appearance.tsuka());
        }
        if (inside(atlasX, atlasY, 52, 58, 76, 82)) {
            return styleFor(appearance.tsuba());
        }
        if (inside(atlasX, atlasY, 80, 58, 96, 82)) {
            return styleFor(appearance.habaki());
        }
        return null;
    }

    private static int soulPatternAlpha(
            SoulGlow soul,
            float atlasX,
            float atlasY) {
        float progress = clamp01((atlasX - 1.0F) / 62.0F);
        return switch (soul) {
            case AKATSUKI -> {
                double veinY = 12.7D
                        + Math.sin(progress * Math.PI) * 1.12D
                        + progress * 0.38D;
                double branchY = veinY + 2.3D
                        - Math.max(0.0D, progress - 0.48D) * 4.2D;
                boolean mainVein = Math.abs(atlasY - veinY) < 0.33D;
                boolean branch = progress > 0.48F
                        && progress < 0.78F
                        && Math.abs(atlasY - branchY) < 0.25D;
                yield mainVein || branch ? 220 : 0;
            }
            case KYOUKA -> {
                double mirrorY = 14.1D
                        + (progress - 0.5D) * 0.48D
                        + Math.sin(progress * Math.PI) * 0.34D;
                yield Math.abs(atlasY - mirrorY) < 0.30D ? 205 : 0;
            }
            case SENBONZAKURA -> {
                double branchY = 14.8D
                        - Math.sin(progress * Math.PI) * 0.82D
                        + progress * 0.28D;
                double twigY = branchY - 2.0D
                        + Math.max(0.0D, progress - 0.56D) * 4.0D;
                boolean branch = Math.abs(atlasY - branchY) < 0.29D;
                boolean twig = progress > 0.56F
                        && progress < 0.84F
                        && Math.abs(atlasY - twigY) < 0.22D;
                yield branch || twig ? 210 : 0;
            }
            case AWAKENED -> {
                double spineY = 15.2D
                        + Math.sin(progress * Math.PI) * 0.42D;
                yield progress > 0.08F
                        && progress < 0.91F
                        && Math.abs(atlasY - spineY) < 0.25D
                        ? 150 : 0;
            }
            case NONE -> 0;
        };
    }

    private static synchronized ResourceLocation ensureDurabilityBaseTexture(
            String bladeMaterial) {
        RegisteredTexture cached = DURABILITY_BASE_CACHE.get(bladeMaterial);
        if (cached != null) {
            return cached.location();
        }

        Minecraft minecraft = Minecraft.getInstance();
        try {
            Resource resource = minecraft.getResourceManager()
                    .getResourceOrThrow(
                            DefaultResources.resourceDurabilityTexture);
            NativeImage image;
            try (InputStream stream = resource.open()) {
                image = NativeImage.read(stream);
            }

            recolorDurabilityBase(image, bladeMaterial);
            DynamicTexture dynamicTexture = new DynamicTexture(image);
            ResourceLocation location =
                    durabilityBaseLocation(bladeMaterial);
            minecraft.getTextureManager().register(location, dynamicTexture);
            DURABILITY_BASE_CACHE.put(
                    bladeMaterial,
                    new RegisteredTexture(location, dynamicTexture));
            trimDurabilityCache(minecraft);
            return location;
        } catch (IOException | RuntimeException exception) {
            LOGGER.warn(
                    "Unable to generate durability base texture for {}",
                    bladeMaterial,
                    exception);
            return null;
        }
    }

    private static void recolorDurabilityBase(
            NativeImage image,
            String bladeMaterial) {
        Palette palette = styleFor(bladeMaterial).palette();
        int visibleHighlight = Palette.lerp(
                palette.highlight(),
                0xFFFFFF,
                0.12F);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int source = image.getPixelRGBA(x, y);
                int sourceAlpha = alpha(source);
                if (sourceAlpha == 0) {
                    continue;
                }
                int luminance = (
                        red(source) * 30
                                + green(source) * 59
                                + blue(source) * 11)
                        / 100;
                float tone = 0.58F + luminance / 255.0F * 0.42F;
                int materialColor = Palette.lerp(
                        palette.mid(),
                        visibleHighlight,
                        tone);
                image.setPixelRGBA(
                        x,
                        y,
                        abgr(sourceAlpha, materialColor));
            }
        }
    }

    private static ResourceLocation durabilityBaseLocation(
            String bladeMaterial) {
        return ResourceLocation.fromNamespaceAndPath(
                BladeTetra.MOD_ID,
                "generated/durability_base_"
                        + Integer.toUnsignedString(
                                bladeMaterial.hashCode(),
                                16));
    }

    private static synchronized ResourceLocation ensureTexture(
            MaterialAppearance appearance) {
        String signature = appearance.signature();
        RegisteredTexture cached = CACHE.get(signature);
        if (cached != null) {
            return cached.location();
        }

        Minecraft minecraft = Minecraft.getInstance();
        boolean firstGeneratedTexture = CACHE.isEmpty();
        try {
            Resource resource = minecraft.getResourceManager()
                    .getResourceOrThrow(TEMPLATE);
            NativeImage image;
            try (InputStream stream = resource.open()) {
                image = NativeImage.read(stream);
            }

            recolor(
                    image,
                    appearance,
                    minecraft.getResourceManager());
            DynamicTexture dynamicTexture = new DynamicTexture(image);
            ResourceLocation location = appearance.textureLocation();
            minecraft.getTextureManager().register(location, dynamicTexture);
            CACHE.put(
                    signature,
                    new RegisteredTexture(location, dynamicTexture));
            trimCache(minecraft);
            if (firstGeneratedTexture) {
                LOGGER.info(
                        "Blade Tetra dynamic material textures are active; "
                                + "first combination: {}",
                        signature);
            }
            LOGGER.debug(
                    "Generated modular SlashBlade material texture {} for {}",
                    location,
                    signature);
            return location;
        } catch (IOException | RuntimeException exception) {
            LOGGER.warn(
                    "Unable to generate modular SlashBlade material texture for {}",
                    signature,
                    exception);
            return null;
        }
    }

    private static void recolor(
            NativeImage image,
            MaterialAppearance appearance,
            ResourceManager resourceManager) {
        MaterialStyle blade = styleFor(appearance.blade());
        MaterialStyle tsuka = styleFor(appearance.tsuka());
        MaterialStyle tsuba = styleFor(appearance.tsuba());
        MaterialStyle saya = styleFor(appearance.saya());
        MaterialStyle habaki = styleFor(appearance.habaki());
        MaterialStyle kashira = styleFor(appearance.kashira());
        MaterialStyle fuller = styleFor(appearance.fuller());
        List<BannerLayer> bannerLayers = loadBannerLayers(
                resourceManager,
                appearance.sayaSkin());
        NativeImage presetSaya = loadPresetSaya(
                resourceManager,
                appearance.sayaPreset());

        try {
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    float atlasX = logicalCoordinate(x, image.getWidth());
                    float atlasY = logicalCoordinate(y, image.getHeight());
                    int source = image.getPixelRGBA(x, y);
                    int alpha = alpha(source);
                    if (alpha == 0) {
                        continue;
                    }

                    int red = red(source);
                    int green = green(source);
                    int blue = blue(source);
                    int luminance = (red * 30 + green * 59 + blue * 11) / 100;
                    MaterialStyle style = null;
                    float tone = 0.5F;

                    if (inside(atlasX, atlasY, 1, 1, 63, 31)) {
                        tone = normalize(luminance, 60, 235);
                        tone = cleanBladeTone(
                                luminance, atlasX, atlasY);
                        int bladeColor = blade.sampleBlade(
                                tone, atlasX, atlasY);
                        int finishedColor = applyFuller(
                                bladeColor,
                                blade,
                                fuller,
                                appearance.fullerProfile(),
                                atlasX,
                                atlasY);
                        finishedColor = applyEdgeFinish(
                                finishedColor,
                                blade.palette(),
                                appearance.edgeFinishProfile(),
                                atlasX,
                                atlasY);
                        image.setPixelRGBA(x, y, abgr(alpha, finishedColor));
                        continue;
                    } else if (inside(atlasX, atlasY, 1, 35, 63, 55)) {
                        boolean fittingBand = red > 90 && green > 70;
                        style = fittingBand ? habaki : saya;
                        tone = fittingBand
                                ? normalize(luminance, 85, 190)
                                : normalize(luminance, 20, 75);
                        int sayaColor = style.sample(tone, x, y);
                        if (!fittingBand && presetSaya != null) {
                            sayaColor = applyPresetSaya(
                                    sayaColor,
                                    tone,
                                    atlasX,
                                    atlasY,
                                    presetSaya);
                        } else if (!fittingBand
                                && appearance.sayaSkin().present()) {
                            sayaColor = applySayaSkin(
                                    sayaColor,
                                    tone,
                                    atlasX,
                                    atlasY,
                                    appearance.sayaSkin(),
                                    bannerLayers);
                        }
                        image.setPixelRGBA(
                                x,
                                y,
                                abgr(alpha, sayaColor));
                        continue;
                    } else if (inside(atlasX, atlasY, 1, 59, 47, 81)) {
                        tone = normalize(luminance, 15, 190);
                        int tsukaColor = applyTsukaProfile(
                                tsuka,
                                kashira,
                                tone,
                                atlasX,
                                atlasY,
                                x,
                                y,
                                appearance.tsukaProfile());
                        image.setPixelRGBA(
                                x,
                                y,
                                abgr(alpha, tsukaColor));
                        continue;
                    } else if (inside(atlasX, atlasY, 52, 58, 76, 82)) {
                        tone = normalize(luminance, 18, 105);
                        TsubaPixel tsubaPixel = applyTsubaProfile(
                                tsuba.sample(tone, x, y),
                                alpha,
                                atlasX,
                                atlasY,
                                appearance.tsubaProfile(),
                                tsuba.palette());
                        image.setPixelRGBA(
                                x,
                                y,
                                abgr(tsubaPixel.alpha(), tsubaPixel.color()));
                        continue;
                    } else if (inside(atlasX, atlasY, 80, 58, 96, 82)) {
                        style = habaki;
                        tone = normalize(luminance, 80, 190);
                    }

                    if (style != null) {
                        image.setPixelRGBA(
                                x,
                                y,
                                abgr(alpha, style.sample(tone, x, y)));
                    }
                }
            }
        } finally {
            bannerLayers.forEach(BannerLayer::close);
            if (presetSaya != null) {
                presetSaya.close();
            }
        }
    }

    private static NativeImage loadPresetSaya(
            ResourceManager resourceManager,
            SayaPresetSkin preset) {
        if (!preset.present()) {
            return null;
        }
        try {
            Resource resource = resourceManager.getResourceOrThrow(
                    preset.textureLocation());
            try (InputStream stream = resource.open()) {
                return NativeImage.read(stream);
            }
        } catch (IOException | RuntimeException exception) {
            LOGGER.warn(
                    "Unable to load authored saya skin {}",
                    preset.textureLocation(),
                    exception);
            return null;
        }
    }

    private static List<BannerLayer> loadBannerLayers(
            ResourceManager resourceManager,
            SayaBannerSkin skin) {
        if (!skin.present() || skin.patternCount() == 0) {
            return List.of();
        }

        List<Pair<Holder<BannerPattern>, DyeColor>> layers =
                skin.layers();
        List<BannerLayer> result = new ArrayList<>(
                Math.max(0, layers.size() - 1));
        for (int index = 1; index < layers.size(); index++) {
            Pair<Holder<BannerPattern>, DyeColor> layer =
                    layers.get(index);
            if (layer.getFirst() == null) {
                continue;
            }
            layer.getFirst().unwrapKey().ifPresent(patternKey -> {
                ResourceLocation pattern = BannerPattern.location(
                        patternKey,
                        true);
                ResourceLocation texture = pattern
                        .withPrefix("textures/")
                        .withSuffix(".png");
                try {
                    Resource resource = resourceManager
                            .getResourceOrThrow(texture);
                    try (InputStream stream = resource.open()) {
                        result.add(new BannerLayer(
                                layer.getSecond(),
                                NativeImage.read(stream)));
                    }
                } catch (IOException | RuntimeException exception) {
                    LOGGER.warn(
                            "Unable to load banner pattern texture {}",
                            texture,
                            exception);
                }
            });
        }
        return result;
    }

    private static int applySayaSkin(
            int baseColor,
            float tone,
            float atlasX,
            float atlasY,
            SayaBannerSkin skin,
            List<BannerLayer> layers) {
        int color = Palette.lerp(
                shadeDye(skin.baseColor(), tone),
                baseColor,
                0.16F);
        for (BannerLayer layer : layers) {
            float coverage = layer.coverage(atlasX, atlasY);
            if (coverage <= 0.0F) {
                continue;
            }
            int layerColor = shadeDye(layer.color(), tone);
            color = Palette.lerp(
                    color,
                    layerColor,
                    coverage * 0.94F);
        }
        return color;
    }

    private static int applyPresetSaya(
            int baseColor,
            float tone,
            float atlasX,
            float atlasY,
            NativeImage preset) {
        float length = clamp01((atlasX - 1.0F) / 62.0F);
        float width = clamp01((atlasY - 35.0F) / 20.0F);
        int sampleX = Math.min(
                preset.getWidth() - 1,
                Math.max(0, Math.round(length * (preset.getWidth() - 1))));
        int sampleY = Math.min(
                preset.getHeight() - 1,
                Math.max(0, Math.round(width * (preset.getHeight() - 1))));
        int pixel = preset.getPixelRGBA(sampleX, sampleY);
        int presetColor = red(pixel) << 16
                | green(pixel) << 8
                | blue(pixel);
        int shaded = Palette.scale(
                presetColor,
                0.72F + tone * 0.56F);
        return Palette.lerp(shaded, baseColor, 0.12F);
    }

    private static int applyTsukaProfile(
            MaterialStyle wrapping,
            MaterialStyle fitting,
            float templateTone,
            float atlasX,
            float atlasY,
            int pixelX,
            int pixelY,
            MaterialAppearance.TsukaProfile profile) {
        float centerY = 70.0F;
        float period = switch (profile) {
            case STANDARD -> 11.5F;
            case SWIFT -> 10.0F;
            case STABLE -> 8.5F;
        };
        float halfWidth = switch (profile) {
            case STANDARD -> 3.05F;
            case SWIFT -> 3.45F;
            case STABLE -> 2.15F;
        };
        float halfHeight = switch (profile) {
            case STANDARD -> 5.7F;
            case SWIFT -> 6.8F;
            case STABLE -> 4.6F;
        };

        // The end collars are the actual kashira/fuchi material. Exposed
        // diamonds are ray skin and no longer incorrectly inherit that metal.
        float endDistance = Math.min(atlasX - 1.0F, 47.0F - atlasX);
        if (endDistance < 2.15F) {
            float collarTone = 0.34F
                    + clamp01(endDistance / 2.15F) * 0.34F;
            int collar = fitting.sample(collarTone, pixelX, pixelY);
            if (endDistance > 1.55F) {
                collar = Palette.lerp(
                        collar, fitting.palette().highlight(), 0.34F);
            }
            return collar;
        }

        float localX = positiveModulo(atlasX - 2.4F, period);
        float distanceX = Math.abs(localX - period * 0.5F) / halfWidth;
        float distanceY = Math.abs(atlasY - centerY) / halfHeight;
        float diamondDistance = distanceX + distanceY;
        float roundedLight = clamp01(
                1.0F - Math.abs(atlasY - centerY) / 11.0F);

        if (diamondDistance <= 1.0F) {
            float rayTone = 0.38F + roundedLight * 0.42F;
            int raySkin = RAYSKIN_PALETTE.sample(rayTone);
            int nodule = Math.floorMod(
                    pixelX * 17 + pixelY * 29, 37);
            if (nodule <= 2) {
                raySkin = Palette.lerp(
                        raySkin, RAYSKIN_PALETTE.highlight(), 0.56F);
            } else if (nodule == 36) {
                raySkin = Palette.scale(raySkin, 0.76F);
            }

            // A restrained menuki appears on alternating central openings.
            int cell = (int) Math.floor((atlasX - 2.4F) / period);
            boolean menukiCell = profile == MaterialAppearance.TsukaProfile.STANDARD
                    ? Math.floorMod(cell, 3) == 1
                    : profile == MaterialAppearance.TsukaProfile.SWIFT
                            && Math.floorMod(cell, 5) == 2;
            if (menukiCell
                    && distanceX < 0.28F
                    && distanceY < 0.22F) {
                return Palette.lerp(
                        fitting.palette().mid(),
                        fitting.palette().highlight(),
                        roundedLight * 0.62F);
            }
            return raySkin;
        }

        float wrapTone = clamp01(
                templateTone * 0.34F + 0.22F + roundedLight * 0.38F);
        int wrap = wrapping.sample(wrapTone, pixelX, pixelY);
        if (diamondDistance < 1.24F) {
            // The dark boundary is the shadow cast by the upper strand over
            // the samegawa and is what makes the wrapping read as layered.
            wrap = Palette.scale(wrap, profile == MaterialAppearance.TsukaProfile.STABLE
                    ? 0.61F
                    : 0.68F);
        }

        int fibre = Math.floorMod(pixelX - pixelY * 2, 19);
        if (fibre <= 1) {
            wrap = Palette.lerp(
                    wrap, wrapping.palette().highlight(), 0.13F);
        } else if (fibre == 18) {
            wrap = Palette.scale(wrap, 0.88F);
        }
        if (profile == MaterialAppearance.TsukaProfile.SWIFT) {
            wrap = Palette.lerp(wrap, wrapping.palette().highlight(), 0.07F);
        } else if (profile == MaterialAppearance.TsukaProfile.STABLE) {
            wrap = Palette.scale(wrap, 0.88F);
        }
        return wrap;
    }

    private static float positiveModulo(float value, float modulus) {
        float result = value % modulus;
        return result < 0.0F ? result + modulus : result;
    }

    private static TsubaPixel applyTsubaProfile(
            int baseColor,
            int baseAlpha,
            float atlasX,
            float atlasY,
            MaterialAppearance.TsubaProfile profile,
            Palette palette) {
        float normalizedX = (atlasX - 64.0F) / 12.0F;
        float normalizedY = (atlasY - 70.0F) / 12.0F;
        float radius = (float) Math.sqrt(
                normalizedX * normalizedX
                        + normalizedY * normalizedY);

        return switch (profile) {
            case MARU -> {
                int color = baseColor;
                if (radius > 0.70F) {
                    color = Palette.lerp(
                            color,
                            palette.highlight(),
                            0.42F);
                } else if (radius < 0.28F) {
                    color = Palette.lerp(
                            color,
                            palette.shadow(),
                            0.56F);
                } else if (Math.abs(radius - 0.42F) < 0.06F) {
                    color = Palette.lerp(
                            color,
                            palette.highlight(),
                            0.20F);
                }
                yield new TsubaPixel(color, baseAlpha);
            }
            case MOKKO -> {
                float holeDistance = Float.MAX_VALUE;
                for (int horizontal : new int[]{-1, 1}) {
                    for (int vertical : new int[]{-1, 1}) {
                        float deltaX = normalizedX - horizontal * 0.38F;
                        float deltaY = normalizedY - vertical * 0.38F;
                        holeDistance = Math.min(
                                holeDistance,
                                deltaX * deltaX + deltaY * deltaY);
                    }
                }
                if (holeDistance < 0.020F) {
                    yield new TsubaPixel(baseColor, 0);
                }
                int color = radius > 0.64F
                        ? Palette.lerp(
                                baseColor,
                                palette.highlight(),
                                0.46F)
                        : Palette.scale(baseColor, 0.88F);
                yield new TsubaPixel(color, baseAlpha);
            }
            case KAKU -> {
                float edge = Math.max(
                        Math.abs(normalizedX),
                        Math.abs(normalizedY));
                int color;
                if (edge > 0.67F) {
                    color = Palette.lerp(
                            baseColor,
                            palette.highlight(),
                            0.52F);
                } else if (edge < 0.48F) {
                    color = Palette.lerp(
                            baseColor,
                            palette.shadow(),
                            0.30F);
                } else {
                    color = Palette.scale(baseColor, 0.91F);
                }
                yield new TsubaPixel(color, baseAlpha);
            }
        };
    }

    private static int shadeDye(DyeColor dyeColor, float tone) {
        float[] channels = dyeColor.getTextureDiffuseColors();
        int color = Math.round(channels[0] * 255.0F) << 16
                | Math.round(channels[1] * 255.0F) << 8
                | Math.round(channels[2] * 255.0F);
        return Palette.scale(color, 0.48F + tone * 0.74F);
    }

    private static int applyFuller(
            int baseColor,
            MaterialStyle blade,
            MaterialStyle fuller,
            MaterialAppearance.FullerProfile profile,
            float atlasX,
            float atlasY) {
        if (profile == MaterialAppearance.FullerProfile.NONE
                || atlasX < 7.0F
                || atlasX > 57.0F) {
            return baseColor;
        }

        return switch (profile) {
            case WIDE_BO_HI -> wideBoHi(
                    baseColor, blade.palette(), fuller.palette(), atlasX, atlasY);
            case TWIN_RIDGE -> twinRidge(
                    baseColor, blade.palette(), fuller.palette(), atlasX, atlasY);
            case NONE -> baseColor;
        };
    }

    private static int applyEdgeFinish(
            int baseColor,
            Palette palette,
            MaterialAppearance.EdgeFinishProfile profile,
            float atlasX,
            float atlasY) {
        if (profile == MaterialAppearance.EdgeFinishProfile.PLAIN
                || atlasX < 3.0F
                || atlasX > 61.0F
                || atlasY < 19.0F) {
            return baseColor;
        }

        float progress = clamp01((atlasX - 3.0F) / 58.0F);
        float curvedBoundary = 22.8F
                + (float) Math.sin(progress * Math.PI) * 0.55F;
        return switch (profile) {
            case HAMAGURI -> {
                float distance = Math.abs(atlasY - 26.0F);
                if (distance < 5.3F) {
                    float blend = 0.10F + (1.0F - distance / 5.3F) * 0.12F;
                    yield Palette.lerp(baseColor, palette.highlight(), blend);
                }
                yield baseColor;
            }
            case HIRA -> {
                float distance = Math.abs(atlasY - curvedBoundary);
                if (distance < 0.48F) {
                    yield Palette.lerp(baseColor, palette.highlight(), 0.34F);
                }
                if (atlasY > curvedBoundary) {
                    yield Palette.scale(baseColor, 1.035F);
                }
                yield baseColor;
            }
            case USUBA -> {
                float distance = Math.abs(atlasY - 28.3F);
                if (distance < 0.72F) {
                    yield Palette.lerp(baseColor, palette.highlight(), 0.48F);
                }
                if (atlasY > 25.6F) {
                    yield Palette.lerp(baseColor, palette.highlight(), 0.16F);
                }
                yield baseColor;
            }
            case PLAIN -> baseColor;
        };
    }

    private static int wideBoHi(
            int baseColor,
            Palette bladePalette,
            Palette fullerPalette,
            float atlasX,
            float atlasY) {
        float halfHeight = roundedHalfHeight(atlasX, 7.0F, 57.0F, 3.0F);
        float distance = Math.abs(atlasY - 10.5F);
        if (distance > halfHeight || halfHeight <= 0.0F) {
            return baseColor;
        }

        float position = distance / halfHeight;
        if (position > 0.80F) {
            int rim = Palette.lerp(baseColor, bladePalette.highlight(), 0.30F);
            return Palette.lerp(rim, fullerPalette.mid(), 0.12F);
        }

        float depth = 0.64F + position * 0.13F;
        return Palette.scale(baseColor, depth);
    }

    private static int twinRidge(
            int baseColor,
            Palette bladePalette,
            Palette fullerPalette,
            float atlasX,
            float atlasY) {
        float halfHeight = roundedHalfHeight(atlasX, 7.0F, 57.0F, 0.95F);
        float first = Math.abs(atlasY - 8.6F);
        float second = Math.abs(atlasY - 12.1F);
        float nearest = Math.min(first, second);
        if (halfHeight > 0.0F && nearest <= halfHeight) {
            float position = nearest / halfHeight;
            if (position > 0.72F) {
                return Palette.lerp(
                        baseColor, bladePalette.highlight(), 0.25F);
            }
            return Palette.scale(baseColor, 0.67F + position * 0.11F);
        }

        if (atlasX >= 8.0F
                && atlasX <= 56.0F
                && Math.abs(atlasY - 10.35F) < 0.34F) {
            int raised = Palette.lerp(
                    baseColor, bladePalette.highlight(), 0.36F);
            return Palette.lerp(raised, fullerPalette.mid(), 0.18F);
        }
        return baseColor;
    }

    private static float roundedHalfHeight(
            float x,
            float start,
            float end,
            float maximum) {
        float endDistance = Math.min(x - start, end - x);
        return Math.max(0.0F, Math.min(maximum, endDistance));
    }

    /**
     * Replaces the template's fine forged noise with a master-smith finish.
     * The broad face is reconstructed from its authored geometry while the
     * lower cutting edge and hamon retain their intentional tonal separation.
     */
    private static float cleanBladeTone(
            int luminance,
            float atlasX,
            float atlasY) {
        float progress = clamp01((atlasX - 1.0F) / 62.0F);
        if (atlasY < 5.5F) {
            return 0.23F + normalize((int) (atlasY * 100), 100, 550) * 0.08F;
        }
        if (atlasY < 22.0F) {
            float faceHeight = clamp01((atlasY - 5.5F) / 16.5F);
            float singleSweep = (float) Math.sin(progress * Math.PI) * 0.035F;
            return clamp01(0.43F + faceHeight * 0.16F + singleSweep);
        }

        // Preserve the authored hamon/cutting-edge boundary without carrying
        // the high-frequency pattern from the upper blade face into it.
        float source = normalize(luminance, 45, 235);
        float edge = clamp01((atlasY - 22.0F) / 9.0F);
        float cleanEdge = 0.58F + edge * 0.34F;
        return clamp01(source * 0.62F + cleanEdge * 0.38F);
    }

    private static float logicalCoordinate(int coordinate, int dimension) {
        return (coordinate + 0.5F)
                * LOGICAL_ATLAS_SIZE
                / (float) dimension;
    }

    private static boolean inside(
            float x,
            float y,
            float minX,
            float minY,
            float maxX,
            float maxY) {
        return x >= minX && x < maxX && y >= minY && y < maxY;
    }

    private static float normalize(int value, int minimum, int maximum) {
        if (value <= minimum) {
            return 0.0F;
        }
        if (value >= maximum) {
            return 1.0F;
        }
        return (value - minimum) / (float) (maximum - minimum);
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    private static MaterialStyle styleFor(String material) {
        return switch (material) {
            case "iron" -> style(
                    new Palette(0x52636D, 0xAEBCC4, 0xEEF4F3),
                    SurfacePattern.FORGED_METAL,
                    material);
            case "copper" -> style(
                    new Palette(0x63382B, 0xB86C46, 0xE6AE7A),
                    SurfacePattern.PATINA_METAL,
                    material);
            case "gold" -> style(
                    new Palette(0x6B501A, 0xD6AD43, 0xFFF09A),
                    SurfacePattern.POLISHED_METAL,
                    material);
            case "netherite" -> style(
                    new Palette(0x211D22, 0x4B4245, 0x89777D),
                    SurfacePattern.NETHERITE,
                    material);
            case "diamond" -> crystal(
                    new Palette(0x1F6569, 0x59CFCB, 0xC6FFF5), material);
            case "emerald" -> crystal(
                    new Palette(0x174D32, 0x3FB66D, 0xB2F2BD), material);
            case "amethyst", "pristine_amethyst" ->
                    crystal(
                            new Palette(0x442E65, 0x9D70CC, 0xE2C5FF),
                            material);
            case "obsidian" -> style(
                    new Palette(0x171323, 0x34264F, 0x756398),
                    SurfacePattern.OBSIDIAN,
                    material);
            case "blackstone" -> stone(
                    new Palette(0x17171B, 0x34343A, 0x68686E), material);
            case "flint" -> stone(
                    new Palette(0x252A30, 0x535B63, 0x9AA3A8), material);
            case "stone" -> stone(
                    new Palette(0x3E4244, 0x777B7E, 0xB7BBBA), material);
            case "andesite" -> stone(
                    new Palette(0x4A4D4E, 0x86898A, 0xC7C9C7), material);
            case "diorite" -> stone(
                    new Palette(0x696B68, 0xC3C3BB, 0xF1F0E8), material);
            case "granite" -> stone(
                    new Palette(0x4E3029, 0x9B6250, 0xD8A083), material);
            case "bone" -> style(
                    new Palette(0x6F6754, 0xC9BC99, 0xF1E9D0),
                    SurfacePattern.BONE,
                    material);
            case "oak", "stick" -> wood(0x8A5A2F);
            case "spruce" -> wood(0x5A3A24);
            case "birch" -> wood(0xC5A86C);
            case "jungle" -> wood(0x9A5A35);
            case "acacia" -> wood(0xA54F2D);
            case "dark_oak" -> wood(0x3F2B20);
            case "mangrove" -> wood(0x6C2F2F);
            case "cherry" -> wood(0xC47F89);
            case "crimson" -> wood(0x762D4E);
            case "warped" -> wood(0x2D706D);
            case "bamboo" -> wood(0xAD9D45);
            case "blaze_rod" -> style(
                    new Palette(0x70230E, 0xD66A1D, 0xFFD05D),
                    SurfacePattern.BLAZE,
                    material);
            case "end_rod" -> style(
                    new Palette(0x69645A, 0xD8D0B1, 0xFFFBE3),
                    SurfacePattern.ARCANE,
                    material);
            case "forged_beam" -> style(
                    new Palette(0x343B40, 0x6B747B, 0xBBC4C7),
                    SurfacePattern.PATTERN_WELDED,
                    material);
            case "dragonsteel_fire" -> style(
                    new Palette(0x2C151B, 0x863B49, 0xF1D5D0),
                    SurfacePattern.DRAGON_FIRE,
                    material);
            case "dragonsteel_ice" -> style(
                    new Palette(0x123E5B, 0x3FA9C7, 0xC8F6FF),
                    SurfacePattern.DRAGON_ICE,
                    material);
            case "dragonsteel_lightning" -> style(
                    new Palette(0x21193D, 0x594DA6, 0xC8C1FF),
                    SurfacePattern.DRAGON_LIGHTNING,
                    material);
            case "ghost_ingot", "phantasmal_ingot" -> style(
                    new Palette(0x1F6D64, 0x78E7CF, 0xE4FFF2),
                    SurfacePattern.GHOST,
                    material);
            case "apocalyptium", "apocalyptium_ingot" -> style(
                    new Palette(0x160C08, 0xB33C13, 0xFFD45E),
                    SurfacePattern.APOCALYPTIUM,
                    material);
            default -> externalMaterialStyle(material);
        };
    }

    private static MaterialStyle style(
            Palette palette,
            SurfacePattern pattern,
            String material) {
        return new MaterialStyle(palette, pattern, material.hashCode());
    }

    private static MaterialStyle wood(int base) {
        return style(
                Palette.fromBase(base, 0.47F, 1.28F),
                SurfacePattern.WOOD,
                Integer.toHexString(base));
    }

    private static MaterialStyle stone(Palette palette, String material) {
        return style(palette, SurfacePattern.STONE, material);
    }

    private static MaterialStyle crystal(Palette palette, String material) {
        return style(palette, SurfacePattern.CRYSTAL, material);
    }

    private static MaterialStyle externalMaterialStyle(String material) {
        if (material.contains("dragonsteel_fire")) {
            return style(
                    new Palette(0x2C151B, 0x863B49, 0xF1D5D0),
                    SurfacePattern.DRAGON_FIRE,
                    material);
        }
        if (material.contains("dragonsteel_ice")) {
            return style(
                    new Palette(0x123E5B, 0x3FA9C7, 0xC8F6FF),
                    SurfacePattern.DRAGON_ICE,
                    material);
        }
        if (material.contains("dragonsteel_lightning")) {
            return style(
                    new Palette(0x21193D, 0x594DA6, 0xC8C1FF),
                    SurfacePattern.DRAGON_LIGHTNING,
                    material);
        }
        if (containsAny(material, "ghost_ingot", "phantasmal")) {
            return style(
                    new Palette(0x1F6D64, 0x78E7CF, 0xE4FFF2),
                    SurfacePattern.GHOST,
                    material);
        }
        if (material.contains("apocalyptium")) {
            return style(
                    new Palette(0x160C08, 0xB33C13, 0xFFD45E),
                    SurfacePattern.APOCALYPTIUM,
                    material);
        }
        if (containsAny(material, "refined_obsidian", "obsidian")) {
            return style(
                    Palette.fromBase(0x432E63, 0.44F, 1.55F),
                    SurfacePattern.OBSIDIAN,
                    material);
        }
        if (containsAny(
                material,
                "diamond",
                "emerald",
                "amethyst",
                "ruby",
                "sapphire",
                "topaz",
                "opal",
                "quartz",
                "crystal",
                "gem")) {
            return crystal(generatedPalette(material), material);
        }
        if (containsAny(material, "ice", "frost", "glacial", "frozen")) {
            return style(
                    Palette.fromBase(0x63BCD0, 0.42F, 1.48F),
                    SurfacePattern.DRAGON_ICE,
                    material);
        }
        if (containsAny(material, "refined_glowstone", "lumium", "glowstone")) {
            return style(
                    Palette.fromBase(0xE0C65B, 0.48F, 1.34F),
                    SurfacePattern.ARCANE,
                    material);
        }
        if (containsAny(material, "blaze", "fiery", "signalum")) {
            return style(
                    Palette.fromBase(0xC95A2A, 0.42F, 1.38F),
                    SurfacePattern.BLAZE,
                    material);
        }
        if (containsAny(material, "enderium", "ender", "azure")) {
            return style(
                    Palette.fromBase(0x397D78, 0.43F, 1.42F),
                    SurfacePattern.ARCANE,
                    material);
        }
        if (containsAny(
                material,
                "mithril",
                "mythril",
                "aether",
                "arcane",
                "celestial",
                "spirit",
                "soul",
                "starmetal",
                "star_metal")) {
            return style(
                    Palette.fromBase(0x79AFC1, 0.42F, 1.46F),
                    SurfacePattern.ARCANE,
                    material);
        }
        if (material.contains("manasteel")) {
            return style(
                    Palette.fromBase(0x5CA9D8, 0.42F, 1.38F),
                    SurfacePattern.ARCANE,
                    material);
        }
        if (material.contains("elementium")) {
            return style(
                    Palette.fromBase(0xD65DA5, 0.44F, 1.34F),
                    SurfacePattern.POLISHED_METAL,
                    material);
        }
        if (material.contains("terrasteel")) {
            return style(
                    Palette.fromBase(0x58A35B, 0.40F, 1.42F),
                    SurfacePattern.ENDER,
                    material);
        }
        if (material.contains("cobalt")) {
            return style(
                    Palette.fromBase(0x3D76BF, 0.42F, 1.40F),
                    SurfacePattern.FORGED_METAL,
                    material);
        }
        if (containsAny(material, "netherite", "dark_steel")) {
            return style(
                    Palette.fromBase(0x4B4245, 0.40F, 1.45F),
                    SurfacePattern.NETHERITE,
                    material);
        }
        if (containsAny(material, "tungsten", "titanium", "iridium", "platinum")) {
            int base = material.contains("tungsten")
                    ? 0x626B70
                    : material.contains("platinum") ? 0xD1D9D5 : 0x9DB4BE;
            return style(
                    Palette.fromBase(base, 0.44F, 1.38F),
                    SurfacePattern.POLISHED_METAL,
                    material);
        }
        if (containsAny(material, "adamant", "orichalcum")) {
            int base = material.contains("adamant") ? 0x426B52 : 0xA69C45;
            return style(
                    Palette.fromBase(base, 0.42F, 1.40F),
                    SurfacePattern.PATTERN_WELDED,
                    material);
        }
        if (containsAny(
                material,
                "damascus",
                "pattern_welded",
                "folded_steel",
                "forged_beam")) {
            return style(
                    externalMetalPalette(material),
                    SurfacePattern.PATTERN_WELDED,
                    material);
        }
        if (containsAny(
                material,
                "bronze",
                "brass",
                "constantan",
                "rose_gold")) {
            int base = material.contains("brass") ? 0xB99B45 : 0xA9693D;
            return style(
                    Palette.fromBase(base, 0.45F, 1.36F),
                    SurfacePattern.PATINA_METAL,
                    material);
        }
        if (containsAny(material, "copper")) {
            return style(
                    Palette.fromBase(0xB86C46, 0.44F, 1.36F),
                    SurfacePattern.PATINA_METAL,
                    material);
        }
        if (containsAny(material, "gold", "electrum")) {
            return style(
                    Palette.fromBase(0xD2B04F, 0.48F, 1.35F),
                    SurfacePattern.POLISHED_METAL,
                    material);
        }
        if (containsAny(material, "uranium")) {
            return style(
                    Palette.fromBase(0x668C43, 0.40F, 1.45F),
                    SurfacePattern.STONE,
                    material);
        }
        if (containsAny(
                material,
                "steel",
                "silver",
                "tin",
                "lead",
                "nickel",
                "invar",
                "aluminum",
                "aluminium",
                "osmium",
                "zinc",
                "iron")) {
            return style(
                    externalMetalPalette(material),
                    SurfacePattern.FORGED_METAL,
                    material);
        }
        return style(
                generatedPalette(material),
                SurfacePattern.FORGED_METAL,
                material);
    }

    private static Palette externalMetalPalette(String material) {
        int base;
        if (material.contains("silver") || material.contains("tin")) {
            base = 0xC2CED0;
        } else if (material.contains("lead")) {
            base = 0x555A72;
        } else if (material.contains("nickel") || material.contains("invar")) {
            base = 0xA6A58A;
        } else if (material.contains("osmium")) {
            base = 0x829CB5;
        } else if (material.contains("zinc")) {
            base = 0xAAB8AE;
        } else if (material.contains("aluminum")
                || material.contains("aluminium")) {
            base = 0xBCC8CE;
        } else {
            base = 0x919EA5;
        }
        return Palette.fromBase(base, 0.46F, 1.34F);
    }

    private static boolean containsAny(String value, String... needles) {
        for (String needle : needles) {
            if (value.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static Palette generatedPalette(String material) {
        int hash = material.hashCode();
        float hue = (hash & 0xffff) / 65535.0F;
        float saturation = 0.28F + ((hash >>> 16) & 0xff) / 255.0F * 0.25F;
        float brightness = 0.56F + ((hash >>> 24) & 0xff) / 255.0F * 0.16F;
        int base = java.awt.Color.HSBtoRGB(hue, saturation, brightness) & 0xffffff;
        return Palette.fromBase(base, 0.48F, 1.32F);
    }

    private static void trimCache(Minecraft minecraft) {
        while (CACHE.size() > MAX_CACHE_SIZE) {
            Iterator<RegisteredTexture> iterator = CACHE.values().iterator();
            RegisteredTexture oldest = iterator.next();
            iterator.remove();
            minecraft.getTextureManager().release(oldest.location());
        }
    }

    private static void trimEmissiveCache(Minecraft minecraft) {
        while (EMISSIVE_CACHE.size() > MAX_CACHE_SIZE) {
            Iterator<RegisteredTexture> iterator =
                    EMISSIVE_CACHE.values().iterator();
            RegisteredTexture oldest = iterator.next();
            iterator.remove();
            minecraft.getTextureManager().release(oldest.location());
        }
    }

    private static void trimNoEmissiveCache() {
        while (NO_EMISSIVE_CACHE.size() > MAX_CACHE_SIZE) {
            Iterator<String> iterator = NO_EMISSIVE_CACHE.iterator();
            iterator.next();
            iterator.remove();
        }
    }

    private static void trimDurabilityCache(Minecraft minecraft) {
        while (DURABILITY_BASE_CACHE.size() > 64) {
            Iterator<RegisteredTexture> iterator =
                    DURABILITY_BASE_CACHE.values().iterator();
            RegisteredTexture oldest = iterator.next();
            iterator.remove();
            minecraft.getTextureManager().release(oldest.location());
        }
    }

    private static synchronized void clearCache() {
        Minecraft minecraft = Minecraft.getInstance();
        for (RegisteredTexture texture : CACHE.values()) {
            minecraft.getTextureManager().release(texture.location());
        }
        CACHE.clear();
        for (RegisteredTexture texture : EMISSIVE_CACHE.values()) {
            minecraft.getTextureManager().release(texture.location());
        }
        EMISSIVE_CACHE.clear();
        NO_EMISSIVE_CACHE.clear();
        for (RegisteredTexture texture : DURABILITY_BASE_CACHE.values()) {
            minecraft.getTextureManager().release(texture.location());
        }
        DURABILITY_BASE_CACHE.clear();
    }

    private static int alpha(int abgr) {
        return abgr >>> 24 & 0xff;
    }

    private static int red(int abgr) {
        return abgr & 0xff;
    }

    private static int green(int abgr) {
        return abgr >>> 8 & 0xff;
    }

    private static int blue(int abgr) {
        return abgr >>> 16 & 0xff;
    }

    private static int abgr(int alpha, int rgb) {
        int red = rgb >>> 16 & 0xff;
        int green = rgb >>> 8 & 0xff;
        int blue = rgb & 0xff;
        return alpha << 24 | blue << 16 | green << 8 | red;
    }

    private record RegisteredTexture(
            ResourceLocation location,
            DynamicTexture texture) {
    }

    private record TsubaPixel(int color, int alpha) {
    }

    private record BannerLayer(
            DyeColor color,
            NativeImage mask) implements AutoCloseable {
        private static final int BANNER_CANVAS_WIDTH = 42;
        private static final int BANNER_CANVAS_HEIGHT = 41;

        private float coverage(float atlasX, float atlasY) {
            float length = Math.max(
                    0.0F,
                    Math.min(1.0F, (atlasX - 1.0F) / 62.0F));
            float width = Math.max(
                    0.0F,
                    Math.min(1.0F, (atlasY - 35.0F) / 20.0F));
            int sampleX = Math.min(
                    Math.min(
                            BANNER_CANVAS_WIDTH,
                            mask.getWidth()) - 1,
                    Math.max(
                            0,
                            Math.round(
                                    width
                                            * (Math.min(
                                                    BANNER_CANVAS_WIDTH,
                                                    mask.getWidth()) - 1))));
            int sampleY = Math.min(
                    Math.min(
                            BANNER_CANVAS_HEIGHT,
                            mask.getHeight()) - 1,
                    Math.max(
                            0,
                            Math.round(
                                    (1.0F - length)
                                            * (Math.min(
                                                    BANNER_CANVAS_HEIGHT,
                                                    mask.getHeight()) - 1))));
            int pixel = mask.getPixelRGBA(sampleX, sampleY);
            float brightness = (red(pixel) + green(pixel) + blue(pixel))
                    / (255.0F * 3.0F);
            return alpha(pixel) / 255.0F * brightness;
        }

        @Override
        public void close() {
            mask.close();
        }
    }

    private record MaterialStyle(
            Palette palette,
            SurfacePattern pattern,
            int seed) {
        private int sample(float tone, int x, int y) {
            return pattern.decorate(
                    palette.sample(tone),
                    palette,
                    x,
                    y,
                    seed);
        }

        private int sampleBlade(
                float tone,
                float atlasX,
                float atlasY) {
            return pattern.decorateBlade(
                    palette.sample(tone),
                    palette,
                    atlasX,
                    atlasY,
                    seed);
        }

        private int emissionAlpha(
                int x,
                int y,
                float atlasX,
                float atlasY) {
            if (inside(atlasX, atlasY, 1, 1, 63, 31)) {
                return pattern.bladeEmissionAlpha(
                        atlasX, atlasY, seed);
            }
            return pattern.emissionAlpha(x, y, seed);
        }
    }

    private record GlowState(SoulGlow soul, boolean broken) {
        private static GlowState fromStack(
                net.minecraft.world.item.ItemStack stack) {
            boolean broken = stack.getCapability(ModularSlashBladeItem.BLADESTATE)
                    .map(state -> state.isBroken())
                    .orElse(false);
            SoulGlow soul = SoulGlow.NONE;
            if (AkatsukiAwakening.isCandidate(stack)
                    && AkatsukiAwakening.isUnlocked(stack)) {
                soul = SoulGlow.AKATSUKI;
            } else if (KyoukaAwakening.hasMirrorInscription(stack)
                    && KyoukaAwakening.isUnlocked(stack)) {
                soul = SoulGlow.KYOUKA;
            } else if (SenbonzakuraAwakening.hasSakuraInscription(stack)
                    && SenbonzakuraAwakening.isUnlocked(stack)) {
                soul = SoulGlow.SENBONZAKURA;
            } else {
                net.minecraft.nbt.CompoundTag tag = stack.getTag();
                if (tag != null
                        && ModularSlashBladeItem.AWAKENED_SOUL_INSCRIPTION_MODULE
                        .equals(tag.getString(
                                ModularSlashBladeItem.INSCRIPTION_SLOT))) {
                    soul = SoulGlow.AWAKENED;
                }
            }
            return new GlowState(soul, broken);
        }

        private String signature() {
            return soul.name().toLowerCase(java.util.Locale.ROOT)
                    + (broken ? "-dormant" : "-active");
        }
    }

    private enum SoulGlow {
        NONE(0xFFFFFF),
        AWAKENED(0xC8A8FF),
        AKATSUKI(0xFF284D),
        KYOUKA(0x84F5FF),
        SENBONZAKURA(0xFFB7D5);

        private final int color;

        SoulGlow(int color) {
            this.color = color;
        }

        private int colorAt(int x, int y) {
            if (this == AWAKENED
                    && Math.floorMod(x + y * 2, 11) <= 3) {
                return 0xE9B95F;
            }
            return color;
        }
    }

    private static int decorateDragonScales(
            int color,
            Palette palette,
            int x,
            int y,
            int seed) {
        int row = Math.floorDiv(y + seed, 8);
        int localX = Math.floorMod(x + (row & 1) * 7 + seed, 14) - 7;
        int localY = Math.floorMod(y + seed, 8) - 1;
        double arc = Math.sqrt(
                localX * (double) localX
                        + localY * localY * 1.65D);
        if (localY >= 0 && Math.abs(arc - 6.5D) < 0.72D) {
            return Palette.lerp(color, palette.highlight(), 0.36F);
        }
        if (localY >= 1 && arc > 6.7D) {
            return Palette.scale(color, 0.76F);
        }
        return color;
    }

    private enum SurfacePattern {
        FORGED_METAL {
            @Override
            int decorate(int color, Palette palette, int x, int y, int seed) {
                double layer = Math.sin(
                        x * 0.19D
                                + Math.sin(y * 0.31D + seed * 0.001D)
                                * 1.75D);
                double crossing = Math.sin(
                        x * 0.071D - y * 0.23D + seed * 0.004D);
                if (layer > 0.90D) {
                    return Palette.lerp(color, palette.highlight(), 0.20F);
                }
                if (layer < -0.92D || crossing < -0.985D) {
                    return Palette.scale(color, 0.82F);
                }
                return color;
            }
        },
        POLISHED_METAL {
            @Override
            int decorate(int color, Palette palette, int x, int y, int seed) {
                double broadShine = Math.sin(
                        x * 0.037D + y * 0.14D + seed * 0.002D);
                if (broadShine > 0.84D) {
                    return Palette.lerp(color, palette.highlight(), 0.31F);
                }
                return broadShine < -0.94D
                        ? Palette.scale(color, 0.88F)
                        : color;
            }
        },
        PATINA_METAL {
            @Override
            int decorate(int color, Palette palette, int x, int y, int seed) {
                double patina = Math.sin(
                        x * 0.083D
                                + Math.sin(y * 0.19D + seed * 0.002D)
                                * 1.35D);
                if (patina > 0.91D) {
                    return Palette.lerp(color, 0x4F8874, 0.22F);
                }
                if (patina < -0.94D) {
                    return Palette.scale(color, 0.88F);
                }
                return FORGED_METAL.decorate(color, palette, x, y, seed);
            }
        },
        NETHERITE {
            @Override
            int decorate(int color, Palette palette, int x, int y, int seed) {
                double lamination = Math.sin(
                        x * 0.12D
                                + Math.sin(y * 0.23D + seed * 0.003D)
                                * 1.1D);
                if (lamination < -0.88D) {
                    return Palette.scale(color, 0.76F);
                }
                if (lamination > 0.93D) {
                    return Palette.lerp(color, 0x715A58, 0.20F);
                }
                return color;
            }
        },
        PATTERN_WELDED {
            @Override
            int decorate(int color, Palette palette, int x, int y, int seed) {
                double wave = Math.sin(
                        x * 0.56D
                                + Math.sin(y * 0.72D + seed * 0.01D) * 2.0D);
                if (wave > 0.72D) {
                    return Palette.lerp(color, palette.highlight(), 0.34F);
                }
                if (wave < -0.76D) {
                    return Palette.scale(color, 0.72F);
                }
                return color;
            }
        },
        DRAGON_FIRE {
            @Override
            int decorate(int color, Palette palette, int x, int y, int seed) {
                int scaled = decorateDragonScales(
                        color, palette, x, y, seed);
                double wave = Math.sin(
                        x * 0.14D
                                + Math.sin(y * 0.21D + seed * 0.01D) * 1.6D);
                if (wave > 0.91D) {
                    return Palette.lerp(scaled, 0xFF9D4A, 0.58F);
                }
                if (wave < -0.94D) {
                    return Palette.scale(scaled, 0.68F);
                }
                return scaled;
            }
        },
        DRAGON_ICE {
            @Override
            int decorate(int color, Palette palette, int x, int y, int seed) {
                int scaled = decorateDragonScales(
                        color, palette, x, y, seed);
                int frost = Math.floorMod(x * 3 - y * 5 + seed, 19);
                if (frost <= 1) {
                    return Palette.lerp(scaled, 0xE8FFFF, 0.68F);
                }
                if (Math.floorMod(x + y + seed, 31) == 0) {
                    return Palette.lerp(
                            scaled, palette.highlight(), 0.46F);
                }
                return CRYSTAL.decorate(scaled, palette, x, y, seed);
            }
        },
        DRAGON_LIGHTNING {
            @Override
            int decorate(int color, Palette palette, int x, int y, int seed) {
                int scaled = decorateDragonScales(
                        color, palette, x, y, seed);
                int bolt = Math.floorMod(
                        x * 5 + y * 7 + y / 4 * 3 + seed, 29);
                boolean branch = Math.floorMod(y + seed, 8) == 0
                        && bolt >= 2
                        && bolt <= 6;
                if (bolt <= 1 || branch) {
                    return Palette.lerp(scaled, 0xEEE9FF, 0.82F);
                }
                return bolt == 28 ? Palette.scale(scaled, 0.68F) : scaled;
            }
        },
        GHOST {
            @Override
            int decorate(int color, Palette palette, int x, int y, int seed) {
                int shimmer = Math.floorMod(x * 2 + y * 5 + seed, 23);
                if (shimmer <= 2) {
                    return Palette.lerp(color, 0xF0FFF8, 0.68F);
                }
                if (Math.floorMod(x - y + seed, 13) == 0) {
                    return Palette.scale(color, 0.72F);
                }
                return color;
            }
        },
        APOCALYPTIUM {
            @Override
            int decorate(int color, Palette palette, int x, int y, int seed) {
                int vein = Math.floorMod(
                        x * 7 + y * 3 + y / 5 * 4 + seed, 31);
                boolean branch = Math.floorMod(x + seed, 9) == 0
                        && vein >= 2
                        && vein <= 7;
                if (vein <= 1 || branch) {
                    return Palette.lerp(color, 0xFFD45E, 0.74F);
                }
                if (vein >= 28) {
                    return Palette.lerp(color, 0x721B0D, 0.52F);
                }
                return Palette.scale(color, 0.78F);
            }
        },
        CRYSTAL {
            @Override
            int decorate(int color, Palette palette, int x, int y, int seed) {
                double longitudinal = Math.sin(
                        (x + seed * 0.03D) * 0.095D
                                + Math.sin((y - seed) * 0.21D) * 0.72D);
                double diagonal = Math.sin(
                        x * 0.052D + y * 0.39D + seed * 0.013D);
                int planeA = Math.floorMod(x + y * 2 + seed, 31);
                int planeB = Math.floorMod(x * 2 - y * 3 + seed, 43);
                int faceted = color;
                if (longitudinal > 0.62D) {
                    faceted = Palette.lerp(
                            color, palette.highlight(), 0.16F);
                } else if (longitudinal < -0.68D) {
                    faceted = Palette.scale(color, 0.90F);
                }

                if (diagonal > 0.985D || planeA <= 1 || planeB == 0) {
                    faceted = Palette.lerp(
                            faceted, palette.highlight(), 0.38F);
                } else if (planeA >= 27 && planeB >= 38) {
                    faceted = Palette.scale(faceted, 0.78F);
                }
                return faceted;
            }
        },
        OBSIDIAN {
            @Override
            int decorate(int color, Palette palette, int x, int y, int seed) {
                int facetA = Math.floorMod(x + y * 3 + seed, 37);
                int facetB = Math.floorMod(x * 2 - y * 5 + seed, 53);
                boolean crack = facetA == 0 || facetB == 0;
                boolean branch = Math.floorMod(y + seed, 23) == 0
                        && facetA <= 8;
                if (crack || branch) {
                    return Palette.lerp(
                            color,
                            palette.highlight(),
                            branch ? 0.48F : 0.82F);
                }
                if (facetA > 29 && facetB > 43) {
                    return Palette.lerp(color, palette.mid(), 0.22F);
                }
                return Palette.scale(color, 0.84F);
            }
        },
        STONE {
            @Override
            int decorate(int color, Palette palette, int x, int y, int seed) {
                int speckle = Math.floorMod(x * 17 + y * 11 + seed, 37);
                if (speckle == 0) {
                    return Palette.scale(color, 0.82F);
                }
                if (speckle == 1) {
                    return Palette.lerp(color, palette.highlight(), 0.14F);
                }
                return color;
            }
        },
        WOOD {
            @Override
            int decorate(int color, Palette palette, int x, int y, int seed) {
                int grain = Math.floorMod(x + y / 3 * 2 + seed, 13);
                if (grain == 0) {
                    return Palette.scale(color, 0.72F);
                }
                if (grain == 1) {
                    return Palette.lerp(color, palette.highlight(), 0.16F);
                }
                return color;
            }
        },
        BONE {
            @Override
            int decorate(int color, Palette palette, int x, int y, int seed) {
                int pore = Math.floorMod(x * 13 + y * 17 + seed, 53);
                return pore <= 1 ? Palette.scale(color, 0.58F) : color;
            }
        },
        BLAZE {
            @Override
            int decorate(int color, Palette palette, int x, int y, int seed) {
                int heat = Math.floorMod(x * 2 + y + seed, 13);
                if (heat <= 2) {
                    return Palette.lerp(color, palette.highlight(), 0.56F);
                }
                return Palette.scale(color, heat == 12 ? 0.72F : 1.0F);
            }
        },
        ARCANE {
            @Override
            int decorate(int color, Palette palette, int x, int y, int seed) {
                int lane = Math.floorMod(y + seed, 18);
                int glyph = Math.floorMod(x * 5 + y * 3 + seed, 41);
                boolean runeStroke = (lane == 8 || lane == 9)
                        && (glyph <= 8 || glyph >= 35);
                if (runeStroke) {
                    return Palette.lerp(
                            color, palette.highlight(), 0.52F);
                }
                double underGlow = Math.sin(
                        x * 0.055D + y * 0.17D + seed * 0.004D);
                if (underGlow > 0.92D) {
                    return Palette.lerp(color, palette.mid(), 0.22F);
                }
                return Palette.scale(color, 0.94F);
            }
        },
        ENDER {
            @Override
            int decorate(int color, Palette palette, int x, int y, int seed) {
                int shimmer = Math.floorMod(x * 3 + y * 5 + seed, 29);
                if (shimmer == 0 || shimmer == 1) {
                    return Palette.lerp(color, palette.highlight(), 0.62F);
                }
                return color;
            }
        };

        /**
         * Blade faces use a deliberately restrained finish. Component
         * textures may retain material grain, but a forged blade receives no
         * random speckles or rapidly repeating waves.
         */
        private int decorateBlade(
                int color,
                Palette palette,
                float atlasX,
                float atlasY,
                int seed) {
            float progress = clamp01((atlasX - 1.0F) / 62.0F);
            int finished = switch (this) {
                case POLISHED_METAL -> broadPolish(
                        color, palette, progress, atlasY);
                case PATINA_METAL -> broadPatina(
                        color, progress, atlasY);
                case NETHERITE -> broadLamination(
                        color, progress, atlasY);
                case CRYSTAL -> largeCrystalFacets(
                        color, palette, progress, atlasY);
                case OBSIDIAN -> continuousObsidianCrack(
                        color, palette, progress, atlasY, seed);
                case DRAGON_FIRE, DRAGON_ICE, DRAGON_LIGHTNING ->
                        decorateDragonScales(
                                color,
                                palette,
                                Math.round(atlasX * 2.0F),
                                Math.round(atlasY * 2.0F),
                                seed);
                default -> color;
            };

            if (this == OBSIDIAN || this == CRYSTAL) {
                return finished;
            }
            return masterFlowLine(
                    finished, palette, progress, atlasY, seed,
                    switch (this) {
                        case DRAGON_FIRE, DRAGON_ICE, DRAGON_LIGHTNING -> 0.18F;
                        case PATTERN_WELDED, NETHERITE -> 0.13F;
                        default -> 0.10F;
                    });
        }

        private int bladeEmissionAlpha(
                float atlasX,
                float atlasY,
                int seed) {
            float progress = clamp01((atlasX - 1.0F) / 62.0F);
            double distance = Math.abs(
                    atlasY - masterFlowY(progress, seed));
            return switch (this) {
                case DRAGON_FIRE -> distance < 0.48D ? 190 : 0;
                case DRAGON_ICE -> distance < 0.42D ? 170 : 0;
                case DRAGON_LIGHTNING -> distance < 0.36D ? 215 : 0;
                case GHOST -> distance < 0.48D ? 145 : 0;
                case APOCALYPTIUM -> distance < 0.42D ? 190 : 0;
                case CRYSTAL -> crystalFacetDistance(progress, atlasY) < 0.30D
                        ? 135 : 0;
                case OBSIDIAN -> obsidianCrackDistance(
                        progress, atlasY, seed) < 0.31D ? 175 : 0;
                case BLAZE -> distance < 0.48D ? 185 : 0;
                case ARCANE -> distance < 0.38D ? 155 : 0;
                case ENDER -> distance < 0.40D ? 160 : 0;
                default -> 0;
            };
        }

        private static int masterFlowLine(
                int color,
                Palette palette,
                float progress,
                float atlasY,
                int seed,
                float strength) {
            double offset = atlasY - masterFlowY(progress, seed);
            double distance = Math.abs(offset);
            if (distance < 0.22D) {
                return Palette.lerp(color, palette.highlight(), strength);
            }
            if (offset >= 0.22D && offset < 0.58D) {
                return Palette.scale(color, 1.0F - strength * 0.42F);
            }
            return color;
        }

        private static double masterFlowY(float progress, int seed) {
            double phase = ((seed & 0xff) / 255.0D - 0.5D) * 0.12D;
            return 14.35D
                    + Math.sin((progress - 0.10D) * Math.PI + phase) * 0.72D
                    + (progress - 0.5D) * 0.18D;
        }

        private static int broadPolish(
                int color,
                Palette palette,
                float progress,
                float atlasY) {
            double center = 9.0D + progress * 6.0D;
            double distance = Math.abs(atlasY - center);
            return distance < 3.4D
                    ? Palette.lerp(color, palette.highlight(),
                            (float) ((3.4D - distance) / 3.4D) * 0.055F)
                    : color;
        }

        private static int broadPatina(
                int color,
                float progress,
                float atlasY) {
            double center = 17.0D
                    - Math.sin(progress * Math.PI) * 0.65D;
            double distance = Math.abs(atlasY - center);
            return distance < 2.8D
                    ? Palette.lerp(color, 0x4F8874,
                            (float) ((2.8D - distance) / 2.8D) * 0.075F)
                    : color;
        }

        private static int broadLamination(
                int color,
                float progress,
                float atlasY) {
            double center = 16.6D
                    + Math.sin(progress * Math.PI) * 0.45D;
            double distance = Math.abs(atlasY - center);
            return distance < 1.4D
                    ? Palette.scale(color,
                            0.94F + (float) (distance / 1.4D) * 0.06F)
                    : color;
        }

        private static int largeCrystalFacets(
                int color,
                Palette palette,
                float progress,
                float atlasY) {
            double first = atlasY - (8.0D + progress * 9.0D);
            double second = atlasY - (25.0D - progress * 7.0D);
            double boundary = Math.min(Math.abs(first), Math.abs(second));
            if (boundary < 0.34D) {
                return Palette.lerp(color, palette.highlight(), 0.28F);
            }
            if (first > 0.0D && second < 0.0D) {
                return Palette.lerp(color, palette.highlight(), 0.07F);
            }
            return Palette.scale(color, 0.96F);
        }

        private static double crystalFacetDistance(
                float progress,
                float atlasY) {
            double first = Math.abs(atlasY - (8.0D + progress * 9.0D));
            double second = Math.abs(atlasY - (25.0D - progress * 7.0D));
            return Math.min(first, second);
        }

        private static int continuousObsidianCrack(
                int color,
                Palette palette,
                float progress,
                float atlasY,
                int seed) {
            double distance = obsidianCrackDistance(
                    progress, atlasY, seed);
            if (distance < 0.24D) {
                return Palette.lerp(color, palette.highlight(), 0.72F);
            }
            if (distance < 0.52D) {
                return Palette.lerp(color, palette.mid(), 0.18F);
            }
            return Palette.scale(color, 0.90F);
        }

        private static double obsidianCrackDistance(
                float progress,
                float atlasY,
                int seed) {
            double phase = ((seed >>> 8) & 0xff) / 255.0D * 0.16D;
            double crackY = 13.1D
                    + Math.sin((progress + phase) * Math.PI) * 1.05D
                    + progress * 0.42D;
            return Math.abs(atlasY - crackY);
        }

        private int emissionAlpha(int x, int y, int seed) {
            return switch (this) {
                case DRAGON_FIRE -> {
                    double wave = Math.sin(
                            x * 0.14D
                                    + Math.sin(y * 0.21D + seed * 0.01D)
                                    * 1.6D);
                    yield wave > 0.91D ? 190 : 0;
                }
                case DRAGON_ICE -> {
                    int frost = Math.floorMod(x * 3 - y * 5 + seed, 19);
                    yield frost <= 1
                            ? 175
                            : Math.floorMod(x + y + seed, 31) == 0 ? 120 : 0;
                }
                case DRAGON_LIGHTNING -> {
                    int bolt = Math.floorMod(
                            x * 5 + y * 7 + y / 4 * 3 + seed, 29);
                    boolean branch = Math.floorMod(y + seed, 8) == 0
                            && bolt >= 2 && bolt <= 6;
                    yield bolt <= 1 || branch ? 235 : 0;
                }
                case GHOST -> Math.floorMod(x * 2 + y * 5 + seed, 23) <= 2
                        ? 155 : 0;
                case APOCALYPTIUM -> {
                    int vein = Math.floorMod(
                            x * 7 + y * 3 + y / 5 * 4 + seed, 31);
                    boolean branch = Math.floorMod(x + seed, 9) == 0
                            && vein >= 2 && vein <= 7;
                    yield vein <= 1 || branch ? 215 : 0;
                }
                case CRYSTAL -> {
                    int planeA = Math.floorMod(x + y * 2 + seed, 31);
                    int planeB = Math.floorMod(x * 2 - y * 3 + seed, 43);
                    double diagonal = Math.sin(
                            x * 0.052D + y * 0.39D + seed * 0.013D);
                    yield diagonal > 0.985D || planeA <= 1 || planeB == 0
                            ? 105 : 0;
                }
                case OBSIDIAN -> {
                    int facetA = Math.floorMod(x + y * 3 + seed, 37);
                    int facetB = Math.floorMod(x * 2 - y * 5 + seed, 53);
                    boolean branch = Math.floorMod(y + seed, 23) == 0
                            && facetA <= 8;
                    yield facetA == 0 || facetB == 0 || branch ? 125 : 0;
                }
                case BLAZE -> Math.floorMod(x * 2 + y + seed, 13) <= 2
                        ? 180 : 0;
                case ARCANE -> {
                    int lane = Math.floorMod(y + seed, 18);
                    int glyph = Math.floorMod(x * 5 + y * 3 + seed, 41);
                    boolean runeStroke = (lane == 8 || lane == 9)
                            && (glyph <= 8 || glyph >= 35);
                    double underGlow = Math.sin(
                            x * 0.055D + y * 0.17D + seed * 0.004D);
                    yield runeStroke || underGlow > 0.92D ? 155 : 0;
                }
                case ENDER -> {
                    int shimmer = Math.floorMod(x * 3 + y * 5 + seed, 29);
                    yield shimmer <= 1 ? 150 : 0;
                }
                default -> 0;
            };
        }

        abstract int decorate(
                int color,
                Palette palette,
                int x,
                int y,
                int seed);
    }

    private record Palette(int shadow, int mid, int highlight) {
        private static Palette fromBase(
                int base,
                float shadowScale,
                float highlightScale) {
            return new Palette(
                    scale(base, shadowScale),
                    base,
                    scale(base, highlightScale));
        }

        private int sample(float tone) {
            if (tone <= 0.5F) {
                return lerp(shadow, mid, tone * 2.0F);
            }
            return lerp(mid, highlight, (tone - 0.5F) * 2.0F);
        }

        private static int lerp(int from, int to, float amount) {
            int red = Math.round(channel(from, 16)
                    + (channel(to, 16) - channel(from, 16)) * amount);
            int green = Math.round(channel(from, 8)
                    + (channel(to, 8) - channel(from, 8)) * amount);
            int blue = Math.round(channel(from, 0)
                    + (channel(to, 0) - channel(from, 0)) * amount);
            return red << 16 | green << 8 | blue;
        }

        private static int scale(int color, float scale) {
            int red = Math.min(255, Math.round(channel(color, 16) * scale));
            int green = Math.min(255, Math.round(channel(color, 8) * scale));
            int blue = Math.min(255, Math.round(channel(color, 0) * scale));
            return red << 16 | green << 8 | blue;
        }

        private static int channel(int color, int shift) {
            return color >>> shift & 0xff;
        }
    }

    @Mod.EventBusSubscriber(
            modid = BladeTetra.MOD_ID,
            value = Dist.CLIENT,
            bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ReloadRegistration {
        private ReloadRegistration() {
        }

        @SubscribeEvent
        public static void onRegisterReloadListeners(
                RegisterClientReloadListenersEvent event) {
            event.registerReloadListener(
                    (ResourceManagerReloadListener) resourceManager -> clearCache());
        }
    }
}
