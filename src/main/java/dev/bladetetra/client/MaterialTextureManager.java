package dev.bladetetra.client;

import static dev.bladetetra.client.MaterialTextureStyleEngine.*;

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
    private static int sampleSayaMaterial(
            MaterialStyle material,
            float templateTone,
            float atlasX,
            float atlasY,
            MaterialAppearance.SayaProfile profile) {
        float length = clamp01((atlasX - 1.0F) / 62.0F);
        float width = clamp01((atlasY - 35.0F) / 20.0F);
        float crown = (float) Math.sin(width * Math.PI);
        float endDistance = Math.min(length, 1.0F - length);
        float endShade = 1.0F - clamp01(endDistance / 0.075F);
        float cleanTone = clamp01(
                0.28F
                        + templateTone * 0.34F
                        + crown * 0.12F
                        - endShade * 0.08F);
        int color = material.palette().sample(cleanTone);
        float phase = material.seed() * 0.00037F;
        float detailScale = switch (profile) {
            case SATIN -> 1.0F;
            case QUICKDRAW -> 0.42F;
            case SPIRIT -> 0.72F;
        };

        switch (material.pattern()) {
            case WOOD, IRONWOOD -> {
                double grain = Math.sin(
                        width * Math.PI * 9.0D
                                + Math.sin(length * Math.PI * 3.0D + phase)
                                * 0.72D
                                + phase);
                if (grain > 0.78D) {
                    color = Palette.lerp(
                            color,
                            material.palette().highlight(),
                            0.075F * detailScale);
                } else if (grain < -0.84D) {
                    color = Palette.scale(
                            color,
                            1.0F - 0.065F * detailScale);
                }
            }
            case CRYSTAL, OBSIDIAN, GHOST, ARCANE, ENDER, DRAGON_ICE -> {
                double depth = Math.sin(
                        length * Math.PI * 2.4D
                                + width * Math.PI * 1.6D
                                + phase);
                if (depth > 0.58D) {
                    color = Palette.lerp(
                            color,
                            material.palette().highlight(),
                            0.09F * detailScale);
                } else if (depth < -0.72D) {
                    color = Palette.lerp(
                            color,
                            material.palette().shadow(),
                            0.07F * detailScale);
                }
            }
            case INFINITY -> {
                int cosmic = material.pattern().decorate(
                        color,
                        material.palette(),
                        Math.round(atlasX * 2.0F),
                        Math.round(atlasY * 2.0F),
                        material.seed());
                color = Palette.lerp(color, cosmic, 0.28F * detailScale);
            }
            default -> {
                double brushing = Math.sin(
                        width * Math.PI * 13.0D
                                + length * Math.PI * 0.72D
                                + phase);
                if (brushing > 0.90D) {
                    color = Palette.lerp(
                            color,
                            material.palette().highlight(),
                            0.045F * detailScale);
                } else if (brushing < -0.93D) {
                    color = Palette.scale(
                            color,
                            1.0F - 0.04F * detailScale);
                }
            }
        }
        return color;
    }

    /** Applies the shared urushi depth and the profile-specific clean sheen. */
    private static int applySayaLacquer(
            int color,
            Palette palette,
            MaterialAppearance.SayaProfile profile,
            float atlasX,
            float atlasY) {
        float length = clamp01((atlasX - 1.0F) / 62.0F);
        float width = clamp01((atlasY - 35.0F) / 20.0F);
        float crown = (float) Math.sin(width * Math.PI);
        float endDistance = Math.min(length, 1.0F - length);
        float endFade = clamp01(endDistance / 0.085F);
        color = Palette.scale(color, 0.87F + crown * 0.13F);

        float center;
        float spread;
        float strength;
        switch (profile) {
            case QUICKDRAW -> {
                center = 0.27F;
                spread = 0.060F;
                strength = 0.34F;
            }
            case SPIRIT -> {
                center = 0.34F;
                spread = 0.135F;
                strength = 0.19F;
            }
            default -> {
                center = 0.31F;
                spread = 0.175F;
                strength = 0.15F;
            }
        }

        float distance = (width - center) / spread;
        float sheen = (float) Math.exp(-distance * distance)
                * endFade;
        color = Palette.lerp(
                color,
                palette.highlight(),
                sheen * strength);

        if (profile == MaterialAppearance.SayaProfile.SPIRIT) {
            float pearl = (float) Math.sin(
                    length * Math.PI * 2.5D
                            + width * Math.PI * 1.2D);
            if (pearl > 0.42F) {
                color = Palette.lerp(
                        color,
                        palette.highlight(),
                        (pearl - 0.42F) * 0.085F);
            }
        }

        if (endDistance < 0.028F) {
            color = Palette.lerp(color, palette.shadow(), 0.42F);
        } else if (endDistance < 0.050F) {
            color = Palette.lerp(color, palette.highlight(), 0.10F);
        }
        return color;
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
            MaterialAppearance.TsukaProfile profile,
            DyeColor wrapColor) {
        float centerY = 70.0F;
        float period = switch (profile) {
            case STANDARD -> 7.6F;
            case SWIFT -> 6.2F;
            case STABLE -> 8.8F;
        };
        float halfWidth = switch (profile) {
            case STANDARD -> 2.05F;
            case SWIFT -> 2.0F;
            case STABLE -> 2.35F;
        };
        float halfHeight = switch (profile) {
            case STANDARD -> 5.5F;
            case SWIFT -> 6.2F;
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

            // The pale diamonds are exposed samegawa, not menuki. Actual
            // menuki remain one or two elongated metal ornaments so the
            // denser wrapping still reads as traditional hishigami work.
            int cell = (int) Math.floor((atlasX - 2.4F) / period);
            boolean menukiCell = switch (profile) {
                case STANDARD -> cell == 1 || cell == 4;
                case SWIFT -> cell == 2 || cell == 5;
                case STABLE -> cell == 2;
            };
            if (menukiCell
                    && distanceX < 0.48F
                    && distanceY < 0.12F) {
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
        if (wrapColor != null) {
            wrap = shadeDye(wrapColor, 0.64F + wrapTone * 0.52F);
        }
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
            case NONE -> new TsubaPixel(baseColor, 0);
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

    /**
     * Restores the value hierarchy authored into standard_256 after material
     * tinting. The lines use the material highlight rather than pure white, so
     * dark alloys remain dark while their mune, shinogi, hamon and kissaki are
     * still readable in ordinary daylight.
     */
    private static int applyMasterBladePlanes(
            int baseColor,
            Palette palette,
            float atlasX,
            float atlasY) {
        float progress = clamp01((atlasX - 1.0F) / 62.0F);
        float sweep = (float) Math.sin(progress * Math.PI);
        float tip = clamp01((progress - 0.82F) / 0.18F);
        float shinogi = 10.5F - sweep * 0.38F - tip * 0.60F;
        float hamon = 22.5F
                - sweep * 0.28F
                - tip * 2.5F
                + (float) Math.sin(atlasX * 0.46F) * 0.28F;

        int result = baseColor;
        if (atlasY < 3.4F) {
            result = Palette.lerp(result, palette.shadow(), 0.58F);
        } else if (atlasY < shinogi) {
            result = Palette.lerp(result, palette.shadow(), 0.12F);
        } else if (atlasY < hamon) {
            result = Palette.lerp(result, palette.mid(), 0.10F);
        } else {
            result = Palette.lerp(result, palette.highlight(), 0.24F);
        }

        if (Math.abs(atlasY - 3.0F) < 0.42F) {
            result = Palette.lerp(result, palette.shadow(), 0.72F);
        }
        if (Math.abs(atlasY - shinogi) < 0.48F) {
            result = Palette.lerp(result, palette.highlight(), 0.46F);
        }
        if (Math.abs(atlasY - hamon) < 0.52F) {
            int temperedLight = Palette.lerp(
                    palette.highlight(), 0xF4F7F7, 0.34F);
            result = Palette.lerp(result, temperedLight, 0.40F);
        }
        if (atlasY > 29.3F) {
            int edgeLight = Palette.lerp(
                    palette.highlight(), 0xFFFFFF, 0.48F);
            result = Palette.lerp(result, edgeLight, 0.54F);
        }

        // Diagonal yokote before the kissaki. At 256px this resolves to a
        // deliberate one-to-two pixel break rather than a blurry gradient.
        float yokoteY = 8.5F + (atlasX - 56.0F) * 1.55F;
        if (atlasX >= 55.5F
                && atlasX <= 59.5F
                && atlasY >= 8.0F
                && atlasY <= 28.5F
                && Math.abs(atlasY - yokoteY) < 0.52F) {
            result = Palette.lerp(result, 0xF7FAFA, 0.58F);
        }
        return result;
    }

    /**
     * Keeps the shared model UV-compatible while giving each blade family a
     * different visual rhythm. The modulation is intentionally broad so the
     * material's own surface motif remains readable at normal play distance.
     */
    private static int applyBladeFormComposition(
            int baseColor,
            Palette palette,
            MaterialAppearance.BladeFormProfile profile,
            float atlasX,
            float atlasY) {
        float progress = clamp01((atlasX - 1.0F) / 62.0F);
        return switch (profile) {
            case ORTHODOX -> {
                if (atlasY > 20.5F
                        && Math.sin(progress * Math.PI * 4.0D) > 0.82D) {
                    yield Palette.lerp(baseColor, palette.highlight(), 0.08F);
                }
                yield baseColor;
            }
            case IAIDO -> {
                double drawLine = 16.2D
                        + Math.sin(progress * Math.PI) * 0.34D;
                yield Math.abs(atlasY - drawLine) < 0.30D
                        ? Palette.lerp(baseColor, palette.highlight(), 0.10F)
                        : baseColor;
            }
            case WAKIZASHI -> {
                double pulse = Math.sin(progress * Math.PI * 8.0D);
                if (atlasY > 18.5F && pulse > 0.72D) {
                    yield Palette.lerp(baseColor, palette.highlight(), 0.14F);
                }
                yield baseColor;
            }
            case NODACHI -> {
                double massLine = 8.0D
                        + Math.sin(progress * Math.PI) * 0.50D;
                if (Math.abs(atlasY - massLine) < 0.46D) {
                    yield Palette.scale(baseColor, 0.72F);
                }
                if (atlasY > 24.0F) {
                    yield Palette.lerp(baseColor, palette.highlight(), 0.07F);
                }
                yield baseColor;
            }
        };
    }

    private static int applyForgingProfile(
            int baseColor,
            Palette palette,
            MaterialAppearance.ForgingProfile profile,
            float atlasX,
            float atlasY) {
        if (profile == MaterialAppearance.ForgingProfile.PLAIN
                || atlasX < 4.0F
                || atlasX > 60.0F) {
            return baseColor;
        }
        float progress = clamp01((atlasX - 4.0F) / 56.0F);
        float sweep = (float) Math.sin(progress * Math.PI);
        return switch (profile) {
            case KOBUSE -> {
                float coreBoundary = 18.0F + sweep * 0.75F;
                float distance = Math.abs(atlasY - coreBoundary);
                if (distance < 0.42F) {
                    yield Palette.lerp(baseColor, palette.highlight(), 0.30F);
                }
                if (atlasY < coreBoundary) {
                    yield Palette.scale(baseColor, 0.94F);
                }
                yield baseColor;
            }
            case SANMAI -> {
                float upper = 11.2F + sweep * 0.42F;
                float lower = 21.6F + sweep * 0.58F;
                float nearest = Math.min(
                        Math.abs(atlasY - upper),
                        Math.abs(atlasY - lower));
                if (nearest < 0.36F) {
                    yield Palette.lerp(baseColor, palette.highlight(), 0.27F);
                }
                if (atlasY > upper && atlasY < lower) {
                    yield Palette.scale(baseColor, 0.93F);
                }
                yield baseColor;
            }
            case SHIHOZUME -> {
                float upper = 8.0F + sweep * 0.35F;
                float lower = 23.4F + sweep * 0.50F;
                boolean seam = Math.abs(atlasY - upper) < 0.34F
                        || Math.abs(atlasY - lower) < 0.34F;
                if (seam) {
                    yield Palette.lerp(baseColor, palette.highlight(), 0.32F);
                }
                if (atlasY > upper && atlasY < lower) {
                    yield Palette.lerp(baseColor, palette.mid(), 0.12F);
                }
                yield Palette.scale(baseColor, 0.91F);
            }
            case NORMALIZED -> {
                double grain = Math.sin(
                        atlasX * 0.84D
                                + Math.sin(atlasY * 0.58D) * 0.72D);
                if (grain > 0.86D) {
                    yield Palette.lerp(baseColor, palette.highlight(), 0.09F);
                }
                if (grain < -0.90D) {
                    yield Palette.scale(baseColor, 0.94F);
                }
                yield baseColor;
            }
            case PLAIN -> baseColor;
        };
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
        float width = clamp01((atlasY - 1.0F) / 30.0F);
        float tone;
        if (width < 0.10F) {
            tone = 0.16F + width * 0.72F;
        } else if (width < 0.35F) {
            tone = 0.28F + (width - 0.10F) * 0.48F;
        } else if (width < 0.73F) {
            tone = 0.43F + (width - 0.35F) * 0.40F;
        } else {
            tone = 0.66F + (width - 0.73F) * 0.92F;
        }
        // One very broad reflection keeps the blade alive without reintroducing
        // per-texel grain from the source atlas.
        tone += (float) Math.sin(progress * Math.PI) * 0.018F;
        return clamp01(tone);
    }

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
