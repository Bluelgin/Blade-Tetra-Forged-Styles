package dev.bladetetra.client;

import static dev.bladetetra.client.MaterialTextureStyleEngine.*;
import static dev.bladetetra.client.MaterialTextureComponentPainter.*;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import dev.bladetetra.BladeTetra;
import dev.bladetetra.config.ClientVisualConfig;
import dev.bladetetra.easteregg.AkatsukiAwakening;
import dev.bladetetra.easteregg.BladeLegacyEasterEggs;
import dev.bladetetra.easteregg.KyoukaAwakening;
import dev.bladetetra.easteregg.NbtSageEasterEgg;
import dev.bladetetra.easteregg.SenbonzakuraAwakening;
import dev.bladetetra.easteregg.SoulLegacyState;
import dev.bladetetra.item.ModularSlashBladeItem;
import dev.bladetetra.visual.MaterialAppearance;
import dev.bladetetra.forging.FoxLegacyParts;
import dev.bladetetra.visual.SayaBannerSkin;
import dev.bladetetra.visual.SayaPresetSkin;
import dev.bladetetra.visual.TetraMaterialVisualResolver;
import dev.bladetetra.visual.TsukaWrapColor;
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
                    BladeTetra.MOD_ID, "model/modular/standard_256.png");
    private static final ResourceLocation DURABILITY_MODEL =
            ResourceLocation.fromNamespaceAndPath(
                    BladeTetra.MOD_ID, "model/util/durability_filled.obj");
    private static final int LOGICAL_ATLAS_SIZE = 128;
    private static final int GENERATED_ATLAS_SIZE = 256;
    private static final String ART_REVISION = "blade-art-2.0-v6-native-item-icons";
    /**
     * A 256px RGBA atlas consumes roughly 256 KiB of texture memory. Keeping
     * the LRU bounded at 48 preserves material variety without retaining
     * hundreds of generated combinations in long modpack sessions.
     */
    private static final int MAX_CACHE_SIZE = 48;

    private static final Map<String, RegisteredTexture> CACHE =
            new LinkedHashMap<>(32, 0.75F, true);
    private static final Map<String, RegisteredTexture> EMISSIVE_CACHE =
            new LinkedHashMap<>(24, 0.75F, true);
    /**
     * Signatures whose generated emission mask contains no visible pixels.
     * Without this negative cache, an ordinary non-luminous blade would load,
     * recolor, and scan the complete generated atlas on every render pass.
     */
    private static final Set<String> NO_EMISSIVE_CACHE =
            new LinkedHashSet<>(24);
    private static final Map<String, RegisteredTexture> DURABILITY_BASE_CACHE =
            new LinkedHashMap<>(16, 0.75F, true);
    private static final ThreadLocal<Boolean> RENDERING_INTERNAL_PASS =
            ThreadLocal.withInitial(() -> false);
    /** Immutable 256px source for per-signature working copies during one resource cycle. */
    private static NativeImage GENERATED_ATLAS_TEMPLATE;
    static final Palette RAYSKIN_PALETTE =
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
        if (appearance.physicalComponentsMatch("potato")
                && "sheath".equals(event.getOriginalTarget())) {
            event.setCanceled(true);
            return;
        }
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

        GlowState glowState = GlowState.fromStack(event.getStack());
        TextureLayout textureLayout = TextureLayout.fromTarget(
                event.getOriginalTarget());
        ResourceLocation texture = ensureTexture(
                appearance, glowState, textureLayout);
        if (texture == null) {
            return;
        }

        ResourceLocation emissive = ensureEmissiveTexture(
                event.getStack(), appearance, textureLayout);
        if (dev.bladetetra.forging.NamedLegacyParts.fromStack(event.getStack()).present()
                && LegacyModelPartRenderer.supports(event.getOriginalTarget())) {
            RENDERING_INTERNAL_PASS.set(true);
            try {
                LegacyModelPartRenderer.render(event, dev.bladetetra.forging.NamedLegacyParts.fromStack(event.getStack()),
                        texture, emissive);
            } finally {
                RENDERING_INTERNAL_PASS.set(false);
                BladeRenderState.resetCol();
            }
            return;
        }
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
            MaterialAppearance appearance,
            TextureLayout textureLayout) {
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
                + "|tetra-material-revision="
                + TetraMaterialVisualResolver.revision()
                + "|" + ART_REVISION
                + "|emissive-v2|"
                + textureLayout.serializedName + "|"
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
            NativeImage image = copyGeneratedAtlas(
                    minecraft.getResourceManager());
            recolor(
                    image,
                    appearance,
                    minecraft.getResourceManager(),
                    textureLayout);
            applyLegacyBaseArt(image, glowState, textureLayout);
            boolean visible = applyEmissionMask(
                    image,
                    appearance,
                    glowState,
                    (float) materialIntensity,
                    (float) soulIntensity,
                    textureLayout);
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
            float soulIntensity,
            TextureLayout textureLayout) {
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
                BladeCoordinateMap.Coordinates bladeCoordinates =
                        bladeCoordinates(
                                textureLayout,
                                x,
                                y,
                                image.getWidth(),
                                image.getHeight(),
                                atlasX,
                                atlasY);
                float effectX = bladeCoordinates.valid()
                        ? bladeCoordinates.bladeX()
                        : atlasX;
                float effectY = bladeCoordinates.valid()
                        ? bladeCoordinates.bladeY()
                        : atlasY;
                MaterialStyle style = emissionStyleAt(
                        appearance, atlasX, atlasY);
                int materialAlpha = style == null
                        ? 0
                        : Math.round(style.emissionAlpha(
                                x, y, effectX, effectY,
                                bladeCoordinates.valid())
                                * materialIntensity * dormantScale);

                int soulAlpha = 0;
                int soulColor = 0;
                if (bladeCoordinates.valid()
                        && glowState.soul() != SoulGlow.NONE) {
                    soulAlpha = Math.round(soulPatternAlpha(
                            glowState.soul(), effectX, effectY)
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
            case NBT_SAGE -> {
                int column = Math.floorMod((int) Math.floor(atlasX * 2.0F), 13);
                int row = Math.floorMod((int) Math.floor(atlasY * 2.0F), 11);
                boolean rune = (column == 0 && row < 5)
                        || (row == 0 && column < 5)
                        || (column == row && column < 4);
                yield rune && atlasY > 7.0F && atlasY < 20.0F ? 178 : 0;
            }
            case RAIKIRI -> {
                double bolt = 14.2D
                        + Math.sin(progress * Math.PI * 5.0D) * 1.15D;
                double fork = bolt + Math.sin(progress * Math.PI * 11.0D) * 0.64D;
                boolean main = Math.abs(atlasY - bolt) < 0.26D;
                boolean branch = progress > 0.30F
                        && progress < 0.82F
                        && Math.abs(atlasY - fork) < 0.20D;
                yield main || branch ? 205 : 0;
            }
            case BANSHO -> {
                double wave = 14.8D
                        + Math.sin(progress * Math.PI * 3.0D) * 0.72D;
                yield Math.abs(atlasY - wave) < 0.27D ? 165 : 0;
            }
            case BAIREN -> {
                double foldA = 12.3D
                        + Math.sin(progress * Math.PI * 6.0D) * 0.42D;
                double foldB = 17.1D
                        - Math.sin(progress * Math.PI * 6.0D) * 0.42D;
                yield Math.min(
                        Math.abs(atlasY - foldA),
                        Math.abs(atlasY - foldB)) < 0.22D ? 142 : 0;
            }
            case SHOSHIN -> {
                double origin = 15.6D
                        + Math.sin(progress * Math.PI) * 0.22D;
                yield progress > 0.10F
                        && progress < 0.88F
                        && Math.abs(atlasY - origin) < 0.22D
                        ? 125 : 0;
            }
            case NONE -> 0;
        };
    }

    private static synchronized ResourceLocation ensureDurabilityBaseTexture(
            String bladeMaterial) {
        long materialRevision = TetraMaterialVisualResolver.revision();
        String cacheKey = bladeMaterial
                + "|tetra-material-revision="
                + materialRevision;
        RegisteredTexture cached = DURABILITY_BASE_CACHE.get(cacheKey);
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
                    durabilityBaseLocation(bladeMaterial, materialRevision);
            minecraft.getTextureManager().register(location, dynamicTexture);
            DURABILITY_BASE_CACHE.put(
                    cacheKey,
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
            String bladeMaterial,
            long materialRevision) {
        return ResourceLocation.fromNamespaceAndPath(
                BladeTetra.MOD_ID,
                "generated/durability_base_"
                        + Integer.toUnsignedString(
                                (bladeMaterial
                                        + "|"
                                        + materialRevision).hashCode(),
                                16));
    }

    private static synchronized ResourceLocation ensureTexture(
            MaterialAppearance appearance,
            GlowState glowState,
            TextureLayout textureLayout) {
        long materialRevision = TetraMaterialVisualResolver.revision();
        String signature = appearance.signature()
                + "|tetra-material-revision="
                + materialRevision
                + "|" + ART_REVISION
                + "|" + textureLayout.serializedName
                + "|" + glowState.signature();
        RegisteredTexture cached = CACHE.get(signature);
        if (cached != null) {
            return cached.location();
        }

        Minecraft minecraft = Minecraft.getInstance();
        boolean firstGeneratedTexture = CACHE.isEmpty();
        try {
            NativeImage image = copyGeneratedAtlas(
                    minecraft.getResourceManager());

            recolor(
                    image,
                    appearance,
                    minecraft.getResourceManager(),
                    textureLayout);
            applyLegacyBaseArt(image, glowState, textureLayout);
            DynamicTexture dynamicTexture = new DynamicTexture(image);
            ResourceLocation location = ResourceLocation.fromNamespaceAndPath(
                    BladeTetra.MOD_ID,
                    "generated/material_"
                            + stableTextureHash(signature));
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

    private static synchronized NativeImage copyGeneratedAtlas(
            ResourceManager resourceManager) throws IOException {
        if (GENERATED_ATLAS_TEMPLATE == null) {
            Resource resource = resourceManager.getResourceOrThrow(TEMPLATE);
            GENERATED_ATLAS_TEMPLATE = loadGeneratedAtlas(resource);
        }
        NativeImage copy = new NativeImage(
                GENERATED_ATLAS_SIZE,
                GENERATED_ATLAS_SIZE,
                true);
        copy.copyFrom(GENERATED_ATLAS_TEMPLATE);
        return copy;
    }

    /**
     * Resamples the authored 512px source atlas into the 256px runtime atlas.
     * UVs remain unchanged because all models use normalized texture
     * coordinates; the smaller runtime image therefore changes memory cost,
     * not model compatibility.
     */
    private static NativeImage loadGeneratedAtlas(Resource resource)
            throws IOException {
        NativeImage source;
        try (InputStream stream = resource.open()) {
            source = NativeImage.read(stream);
        }
        if (source.getWidth() == GENERATED_ATLAS_SIZE
                && source.getHeight() == GENERATED_ATLAS_SIZE) {
            return source;
        }

        NativeImage target = new NativeImage(
                GENERATED_ATLAS_SIZE,
                GENERATED_ATLAS_SIZE,
                true);
        try {
            for (int y = 0; y < GENERATED_ATLAS_SIZE; y++) {
                int sourceY = Math.min(
                        source.getHeight() - 1,
                        Math.round((y + 0.5F)
                                * source.getHeight()
                                / GENERATED_ATLAS_SIZE
                                - 0.5F));
                for (int x = 0; x < GENERATED_ATLAS_SIZE; x++) {
                    int sourceX = Math.min(
                            source.getWidth() - 1,
                            Math.round((x + 0.5F)
                                    * source.getWidth()
                                    / GENERATED_ATLAS_SIZE
                                    - 0.5F));
                    target.setPixelRGBA(
                            x,
                            y,
                            source.getPixelRGBA(sourceX, sourceY));
                }
            }
        } finally {
            source.close();
        }
        return target;
    }

    private static String stableTextureHash(String signature) {
        long hash = 0xcbf29ce484222325L;
        for (int index = 0; index < signature.length(); index++) {
            hash ^= signature.charAt(index);
            hash *= 0x100000001b3L;
        }
        return Long.toUnsignedString(hash, 16);
    }

    private static void applyLegacyBaseArt(
            NativeImage image,
            GlowState glowState,
            TextureLayout textureLayout) {
        if (glowState.soul() == SoulGlow.NONE) {
            return;
        }
        float dormantScale = glowState.broken() ? 0.38F : 1.0F;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                BladeCoordinateMap.Coordinates bladeCoordinates =
                        bladeCoordinates(
                                textureLayout,
                                x,
                                y,
                                image.getWidth(),
                                image.getHeight(),
                                logicalCoordinate(x, image.getWidth()),
                                logicalCoordinate(y, image.getHeight()));
                if (!bladeCoordinates.valid()) {
                    continue;
                }
                float bladeX = bladeCoordinates.bladeX();
                float bladeY = bladeCoordinates.bladeY();
                int source = image.getPixelRGBA(x, y);
                int sourceAlpha = alpha(source);
                if (sourceAlpha == 0) {
                    continue;
                }
                int sourceColor = red(source) << 16
                        | green(source) << 8
                        | blue(source);
                int result = sourceColor;

                Palette awakenedPalette = glowState.soul().awakenedPalette();
                if (awakenedPalette != null) {
                    int luminance = (red(source) * 30
                            + green(source) * 59
                            + blue(source) * 11) / 100;
                    int awakened = awakenedPalette.sample(
                            normalize(luminance, 18, 242));
                    float coverage = glowState.broken() ? 0.38F : 0.84F;
                    result = Palette.lerp(sourceColor, awakened, coverage);
                }

                int accentAlpha = soulPatternAlpha(
                        glowState.soul(), bladeX, bladeY);
                if (accentAlpha > 0) {
                    float accent = accentAlpha / 255.0F
                            * 0.34F * dormantScale;
                    result = Palette.lerp(
                            result,
                            glowState.soul().colorAt(x, y),
                            accent);
                }
                image.setPixelRGBA(x, y, abgr(sourceAlpha, result));
            }
        }
    }

    private static void recolor(
            NativeImage image,
            MaterialAppearance appearance,
            ResourceManager resourceManager,
            TextureLayout textureLayout) {
        boolean completePotato = appearance.physicalComponentsMatch("potato");
        int potatoYellow = 0xD9AD4F;
        MaterialStyle blade = componentStyle(appearance.blade(), potatoYellow);
        MaterialStyle tsuka = componentStyle(
                appearance.tsuka(), completePotato ? potatoYellow : 0x956033);
        MaterialStyle tsuba = componentStyle(
                appearance.tsuba(), completePotato ? potatoYellow : 0xB17A31);
        MaterialStyle saya = componentStyle(appearance.saya(), 0x9D6635);
        MaterialStyle habaki = componentStyle(
                appearance.habaki(), completePotato ? potatoYellow : 0x704222);
        MaterialStyle kashira = componentStyle(
                appearance.kashira(), completePotato ? potatoYellow : 0x744525);
        MaterialStyle fuller = styleFor(appearance.fuller());
        FoxLegacyParts fox = appearance.foxLegacy();
        blade = foxStyle(blade, fox.blade(), true);
        saya = foxStyle(saya, fox.saya(), false);
        tsuba = foxStyle(tsuba, fox.tsuba(), false);
        tsuka = foxStyle(tsuka, fox.tsuka(), false);
        DyeColor tsukaWrapColor = TsukaWrapColor.dye(
                appearance.tsukaWrapColor());
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

                    BladeCoordinateMap.Coordinates bladeCoordinates =
                            bladeCoordinates(
                                    textureLayout,
                                    x,
                                    y,
                                    image.getWidth(),
                                    image.getHeight(),
                                    atlasX,
                                    atlasY);
                    if (bladeCoordinates.valid()) {
                        float bladeX = bladeCoordinates.bladeX();
                        float bladeY = bladeCoordinates.bladeY();
                        tone = normalize(luminance, 60, 235);
                        tone = cleanBladeTone(
                                luminance, bladeX, bladeY);
                        int bladeColor = blade.sampleBlade(
                                tone, bladeX, bladeY);
                        int structuredColor = applyMasterBladePlanes(
                                bladeColor,
                                blade.palette(),
                                bladeX,
                                bladeY);
                        structuredColor = applyBladeFormComposition(
                                structuredColor,
                                blade.palette(),
                                appearance.bladeFormProfile(),
                                bladeX,
                                bladeY);
                        structuredColor = applyForgingProfile(
                                structuredColor,
                                blade.palette(),
                                appearance.forgingProfile(),
                                bladeX,
                                bladeY);
                        int finishedColor = applyFuller(
                                structuredColor,
                                blade,
                                fuller,
                                appearance.fullerProfile(),
                                bladeX,
                                bladeY);
                        finishedColor = applyEdgeFinish(
                                finishedColor,
                                blade.palette(),
                                appearance.edgeFinishProfile(),
                                bladeX,
                                bladeY);
                        finishedColor = applyFoxAccent(
                                finishedColor, fox.blade(), bladeX, bladeY, 0);
                        image.setPixelRGBA(x, y, abgr(alpha, finishedColor));
                        continue;
                    } else if (inside(atlasX, atlasY, 1, 35, 63, 55)) {
                        boolean fittingBand = red > 90 && green > 70;
                        if (completePotato && !fittingBand) {
                            image.setPixelRGBA(x, y, abgr(0, 0));
                            continue;
                        }
                        style = fittingBand ? habaki : saya;
                        tone = fittingBand
                                ? normalize(luminance, 85, 190)
                                : normalize(luminance, 20, 75);
                        int sayaColor = fittingBand
                                ? habaki.sample(tone, x, y)
                                : sampleSayaMaterial(
                                        saya,
                                        tone,
                                        atlasX,
                                        atlasY,
                                        appearance.sayaProfile());
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
                        if (!fittingBand) {
                            sayaColor = applySayaLacquer(
                                    sayaColor,
                                    saya.palette(),
                                    appearance.sayaProfile(),
                                    atlasX,
                                    atlasY);
                            sayaColor = applyFoxAccent(
                                    sayaColor, fox.saya(), atlasX, atlasY, 1);
                        }
                        image.setPixelRGBA(
                                x,
                                y,
                                abgr(alpha, sayaColor));
                        continue;
                    } else if (inside(atlasX, atlasY, 1, 59, 47, 81)) {
                        tone = normalize(luminance, 15, 190);
                        int tsukaColor = completePotato
                                ? tsuka.sample(tone, x, y)
                                : applyTsukaProfile(
                                        tsuka,
                                        kashira,
                                        tone,
                                        atlasX,
                                        atlasY,
                                        x,
                                        y,
                                        appearance.tsukaProfile(),
                                        tsukaWrapColor);
                        tsukaColor = applyFoxAccent(
                                tsukaColor, fox.tsuka(), atlasX, atlasY, 2);
                        image.setPixelRGBA(
                                x,
                                y,
                                abgr(alpha, tsukaColor));
                        continue;
                    } else if (inside(atlasX, atlasY, 52, 58, 76, 82)) {
                        if (completePotato) {
                            image.setPixelRGBA(x, y, abgr(0, 0));
                            continue;
                        }
                        tone = normalize(luminance, 18, 105);
                        TsubaPixel tsubaPixel = applyTsubaProfile(
                                tsuba.sample(tone, x, y),
                                alpha,
                                atlasX,
                                atlasY,
                                appearance.tsubaProfile(),
                                tsuba.palette());
                        int foxTsubaColor = applyFoxAccent(
                                tsubaPixel.color(), fox.tsuba(), atlasX, atlasY, 3);
                        image.setPixelRGBA(
                                x,
                                y,
                                abgr(tsubaPixel.alpha(), foxTsubaColor));
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

    /**
     * Saya are finished objects rather than exposed chunks of their source
     * material. Rebuild their base from the material palette and keep only a
     * restrained, lengthwise trace of the underlying material character.
     */


    /** Applies the shared urushi depth and the profile-specific clean sheen. */














    /**
     * Restores the value hierarchy authored into standard_256 after material
     * tinting. The lines use the material highlight rather than pure white, so
     * dark alloys remain dark while their mune, shinogi, hamon and kissaki are
     * still readable in ordinary daylight.
     */


    /**
     * Keeps the shared model UV-compatible while giving each blade family a
     * different visual rhythm. The modulation is intentionally broad so the
     * material's own surface motif remains readable at normal play distance.
     */














    /**
     * Replaces the template's fine forged noise with a master-smith finish.
     * The broad face is reconstructed from its authored geometry while the
     * lower cutting edge and hamon retain their intentional tonal separation.
     */


    static float logicalCoordinate(int coordinate, int dimension) {
        return (coordinate + 0.5F)
                * LOGICAL_ATLAS_SIZE
                / (float) dimension;
    }

    /**
     * The in-world blade and Resharped's native inventory blade use different
     * UV projections of the same atlas region. The world projection needs the
     * authored coordinate map, while the inventory projection is deliberately
     * laid out linearly by the Alpha 9 model builder. Keeping both layouts on
     * separate generated textures preserves the native icon transforms and
     * its per-style silhouette without contaminating the in-world blade art.
     */
    private static BladeCoordinateMap.Coordinates bladeCoordinates(
            TextureLayout textureLayout,
            int x,
            int y,
            int imageWidth,
            int imageHeight,
            float atlasX,
            float atlasY) {
        if (textureLayout == TextureLayout.ITEM
                && inside(atlasX, atlasY, 1, 1, 63, 31)) {
            return new BladeCoordinateMap.Coordinates(
                    clamp01((atlasX - 1.0F) / 62.0F),
                    clamp01((atlasY - 1.0F) / 30.0F),
                    true);
        }
        return BladeCoordinateMap.sample(x, y, imageWidth, imageHeight);
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

    static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }



















    /**
     * Hand-tuned profiles for recognizable adventure-pack progression
     * materials. Matching normalized Tetra material keys keeps these profiles
     * optional: the source mods are never linked or required at runtime.
     */










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
        if (GENERATED_ATLAS_TEMPLATE != null) {
            GENERATED_ATLAS_TEMPLATE.close();
            GENERATED_ATLAS_TEMPLATE = null;
        }
        LegacyModelPartRenderer.clear();
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

    static record TsubaPixel(int color, int alpha) {
    }

    static record BannerLayer(
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



    private record GlowState(SoulGlow soul, boolean broken) {
        private static GlowState fromStack(
                net.minecraft.world.item.ItemStack stack) {
            boolean broken = stack.getCapability(ModularSlashBladeItem.BLADESTATE)
                    .map(state -> state.isBroken())
                    .orElse(false);
            SoulGlow soul = SoulGlow.NONE;
            if (SoulLegacyState.isActive(stack, SoulLegacyState.Legacy.AKATSUKI)) {
                soul = SoulGlow.AKATSUKI;
            } else if (SoulLegacyState.isActive(stack, SoulLegacyState.Legacy.KYOUKA)) {
                soul = SoulGlow.KYOUKA;
            } else if (SoulLegacyState.isActive(
                    stack, SoulLegacyState.Legacy.SENBONZAKURA)) {
                soul = SoulGlow.SENBONZAKURA;
            } else if (SoulLegacyState.isActive(stack, SoulLegacyState.Legacy.RAIKIRI)) {
                soul = SoulGlow.RAIKIRI;
            } else if (NbtSageEasterEgg.isUnlocked(stack)) {
                soul = SoulGlow.NBT_SAGE;
            } else if (BladeLegacyEasterEggs.isBanshoUnlocked(stack)) {
                soul = SoulGlow.BANSHO;
            } else if (BladeLegacyEasterEggs.isBairenUnlocked(stack)) {
                soul = SoulGlow.BAIREN;
            } else if (BladeLegacyEasterEggs.isShoshinUnlocked(stack)) {
                soul = SoulGlow.SHOSHIN;
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
        SENBONZAKURA(0xFFB7D5),
        NBT_SAGE(0xC69BFF),
        RAIKIRI(0xA99BFF),
        BANSHO(0xD8D4FF),
        BAIREN(0xF4E4B6),
        SHOSHIN(0xE1B875);

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

        private Palette awakenedPalette() {
            return switch (this) {
                case AKATSUKI -> new Palette(0x25090F, 0x98172D, 0xFF6975);
                case KYOUKA -> new Palette(0x152B35, 0x62C9D5, 0xE0FFFF);
                case SENBONZAKURA -> new Palette(0x4A243B, 0xD88CAA, 0xFFF0F7);
                default -> null;
            };
        }
    }











    private enum TextureLayout {
        WORLD("world"),
        ITEM("item");

        private final String serializedName;

        TextureLayout(String serializedName) {
            this.serializedName = serializedName;
        }

        private static TextureLayout fromTarget(String target) {
            return "item_blade".equals(target)
                            || "item_bladens".equals(target)
                            || "item_damaged".equals(target)
                    ? ITEM
                    : WORLD;
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
