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
import static dev.bladetetra.client.MaterialTextureManager.*;
import static dev.bladetetra.client.MaterialTextureStyleEngine.*;

/**
 * Component-level pixel painters for the generated modular blade atlas.
 *
 * <p>Contains no texture registration or cache lifecycle; callers provide the
 * decoded working image and resolved material styles.</p>
 */
final class MaterialTextureComponentPainter {
    static int sampleSayaMaterial(
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

    static int applySayaLacquer(
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

    static int applySayaSkin(
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

    static int applyPresetSaya(
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

    static int applyTsukaProfile(
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

    static float positiveModulo(float value, float modulus) {
        float result = value % modulus;
        return result < 0.0F ? result + modulus : result;
    }

    static TsubaPixel applyTsubaProfile(
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

    static int shadeDye(DyeColor dyeColor, float tone) {
        float[] channels = dyeColor.getTextureDiffuseColors();
        int color = Math.round(channels[0] * 255.0F) << 16
                | Math.round(channels[1] * 255.0F) << 8
                | Math.round(channels[2] * 255.0F);
        return Palette.scale(color, 0.48F + tone * 0.74F);
    }

    static int applyMasterBladePlanes(
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

    static int applyBladeFormComposition(
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

    static int applyForgingProfile(
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

    static int applyFuller(
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

    static int applyEdgeFinish(
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

    static int wideBoHi(
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

    static int twinRidge(
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

    static float roundedHalfHeight(
            float x,
            float start,
            float end,
            float maximum) {
        float endDistance = Math.min(x - start, end - x);
        return Math.max(0.0F, Math.min(maximum, endDistance));
    }

    static float cleanBladeTone(
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

    private MaterialTextureComponentPainter() {
    }
}
