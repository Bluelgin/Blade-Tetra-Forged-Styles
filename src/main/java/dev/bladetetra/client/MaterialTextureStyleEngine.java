package dev.bladetetra.client;

import dev.bladetetra.forging.FoxLegacyParts;

import static dev.bladetetra.client.MaterialTextureManager.*;

/**
 * Pixel and surface-pattern engine for the procedural blade atlas.
 *
 * <p>Material identity and fallback ordering live in {@link MaterialStyleCatalog};
 * this class owns rendering-facing style types, decoration algorithms and the
 * small compatibility facade used by existing texture composition code.</p>
 */
final class MaterialTextureStyleEngine {
    static MaterialStyle styleFor(String material) {
        return MaterialStyleCatalog.styleFor(material);
    }

    static MaterialStyle foxStyle(MaterialStyle fallback,
            FoxLegacyParts.Color color, boolean blade) {
        return MaterialStyleCatalog.foxStyle(fallback, color, blade);
    }

    static int applyFoxAccent(int base, FoxLegacyParts.Color color,
            float x, float y, int component) {
        return MaterialStyleCatalog.applyFoxAccent(base, color, x, y, component);
    }

    static MaterialStyle componentStyle(String material, int potatoBaseColor) {
        return MaterialStyleCatalog.componentStyle(material, potatoBaseColor);
    }

    static MaterialStyle tetraMaterialStyle(String material) {
        return MaterialStyleCatalog.tetraMaterialStyle(material);
    }

    static int decorateDragonScales(
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

    static int cosmicHash(int x, int y, int seed) {
        int hash = seed ^ x * 0x45d9f3b ^ y * 0x119de1f3;
        hash ^= hash >>> 16;
        hash *= 0x45d9f3b;
        hash ^= hash >>> 16;
        return hash & Integer.MAX_VALUE;
    }

    static int infinityEmissionAlpha(int x, int y, int seed) {
        int star = cosmicHash(x, y, seed);
        if (star % 613 == 0) {
            return 235;
        }
        if (star % 257 == 0) {
            return 165;
        }
        double nebula = Math.sin(
                x * 0.021D
                        + Math.sin(y * 0.037D + seed * 0.0003D) * 1.65D);
        return nebula > 0.965D ? 55 : 0;
    }

    static record MaterialStyle(
            Palette palette,
            SurfacePattern pattern,
            int seed) {
        int sample(float tone, int x, int y) {
            return pattern.decorate(
                    palette.sample(tone),
                    palette,
                    x,
                    y,
                    seed);
        }

        int sampleBlade(
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

        int emissionAlpha(
                int x,
                int y,
                float atlasX,
                float atlasY,
                boolean bladePixel) {
            if (bladePixel) {
                return pattern.bladeEmissionAlpha(
                        atlasX, atlasY, seed);
            }
            return pattern.emissionAlpha(x, y, seed);
        }
    }

    enum SurfacePattern {
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
        HEAVY_METAL {
            @Override
            int decorate(int color, Palette palette, int x, int y, int seed) {
                double band = Math.sin(
                        x * 0.082D
                                + Math.sin(y * 0.145D + seed * 0.002D)
                                * 0.86D);
                int hammer = cosmicHash(x / 3, y / 3, seed);
                if (band < -0.76D) {
                    return Palette.scale(color, 0.72F);
                }
                if (band > 0.90D) {
                    return Palette.lerp(color, palette.highlight(), 0.18F);
                }
                if (hammer % 67 == 0) {
                    return Palette.scale(color, 0.80F);
                }
                return Palette.scale(color, 0.94F);
            }
        },
        CRUDE_METAL {
            @Override
            int decorate(int color, Palette palette, int x, int y, int seed) {
                int pit = cosmicHash(x, y, seed);
                double scale = Math.sin(
                        x * 0.29D
                                + Math.sin(y * 0.41D + seed * 0.006D)
                                * 1.42D);
                if (pit % 43 == 0 || scale < -0.965D) {
                    return Palette.scale(color, 0.66F);
                }
                if (pit % 59 == 0 || scale > 0.94D) {
                    return Palette.lerp(color, palette.highlight(), 0.16F);
                }
                return Palette.scale(color, 0.91F);
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
        CURSED_METAL {
            @Override
            int decorate(int color, Palette palette, int x, int y, int seed) {
                double binding = Math.sin(
                        x * 0.075D + Math.sin(y * 0.13D + seed * 0.002D));
                double counter = Math.sin(
                        x * 0.041D - y * 0.19D + seed * 0.003D);
                int bound = binding > 0.88D && counter > 0.12D
                        ? Palette.lerp(color, palette.highlight(), 0.52F)
                        : binding < -0.91D
                        ? Palette.scale(color, 0.78F)
                        : color;
                return generatedSurface(
                        bound, palette, MaterialPatternMask.SPECTRAL_CURSE,
                        x, y, seed, 0.42F);
            }
        },
        WITHERITE {
            @Override
            int decorate(int color, Palette palette, int x, int y, int seed) {
                int forged = HEAVY_METAL.decorate(color, palette, x, y, seed);
                return generatedCracks(
                        forged, palette, MaterialPatternMask.WITHER_CRACKS,
                        x, y, seed, 0.76F);
            }
        },
        DARK_ALLOY {
            @Override
            int decorate(int color, Palette palette, int x, int y, int seed) {
                double seam = Math.sin(
                        x * 0.048D + y * 0.16D + seed * 0.002D);
                int alloy = seam > 0.93D
                        ? Palette.lerp(color, palette.highlight(), 0.26F)
                        : seam < -0.84D
                        ? Palette.scale(color, 0.68F)
                        : color;
                return generatedSurface(
                        alloy, palette, MaterialPatternMask.VOID_RUNES,
                        x, y, seed, 0.24F);
            }
        },
        IRONWOOD {
            @Override
            int decorate(int color, Palette palette, int x, int y, int seed) {
                double grain = Math.sin(
                        x * 0.11D + Math.sin(y * 0.08D + seed * 0.002D) * 1.3D);
                int layered = grain > 0.86D
                        ? Palette.lerp(color, palette.highlight(), 0.22F)
                        : grain < -0.88D
                        ? Palette.scale(color, 0.80F)
                        : color;
                return generatedSurface(
                        layered, palette, MaterialPatternMask.IRONWOOD_LAYERS,
                        x, y, seed, 0.34F);
            }
        },
        STEELEAF {
            @Override
            int decorate(int color, Palette palette, int x, int y, int seed) {
                double vein = Math.sin(
                        x * 0.055D + Math.sin(y * 0.18D + seed * 0.002D) * 0.8D);
                double branch = Math.sin(x * 0.028D - y * 0.24D + seed * 0.004D);
                int leaf = vein > 0.94D || vein > 0.78D && branch > 0.82D
                        ? Palette.lerp(color, palette.highlight(), 0.38F)
                        : vein < -0.94D
                        ? Palette.scale(color, 0.84F)
                        : color;
                return generatedSurface(
                        leaf, palette, MaterialPatternMask.STEELEAF_VEINS,
                        x, y, seed, 0.38F);
            }
        },
        KNIGHTMETAL {
            @Override
            int decorate(int color, Palette palette, int x, int y, int seed) {
                int plate = Math.floorMod(x + seed, 18);
                double bevel = Math.sin(y * 0.10D + seed * 0.002D);
                if (plate == 0 || plate == 1) {
                    return Palette.scale(color, 0.72F);
                }
                return bevel > 0.90D
                        ? Palette.lerp(color, palette.highlight(), 0.20F)
                        : color;
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
                int spectral = color;
                if (shimmer <= 2) {
                    spectral = Palette.lerp(color, 0xF0FFF8, 0.68F);
                } else if (Math.floorMod(x - y + seed, 13) == 0) {
                    spectral = Palette.scale(color, 0.72F);
                }
                return generatedSurface(
                        spectral, palette, MaterialPatternMask.SPECTRAL_CURSE,
                        x, y, seed, 0.28F);
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
        INFINITY {
            @Override
            int decorate(int color, Palette palette, int x, int y, int seed) {
                double nebula = Math.sin(
                        x * 0.021D
                                + Math.sin(y * 0.037D + seed * 0.0003D)
                                * 1.65D);
                int cosmic = Palette.scale(color, 0.66F);
                if (nebula > 0.38D) {
                    cosmic = Palette.lerp(
                            cosmic,
                            nebula > 0.82D ? 0x6245A8 : 0x31256B,
                            nebula > 0.82D ? 0.34F : 0.18F);
                } else if (nebula < -0.76D) {
                    cosmic = Palette.lerp(cosmic, 0x081A3C, 0.26F);
                }

                int star = cosmicHash(x, y, seed);
                if (star % 613 == 0) {
                    return Palette.lerp(cosmic, 0xFFFFFF, 0.92F);
                }
                if (star % 257 == 0) {
                    return Palette.lerp(cosmic, 0x8EC8FF, 0.66F);
                }
                return cosmic;
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
                return generatedSurface(
                        faceted, palette, MaterialPatternMask.CRYSTAL_FACETS,
                        x, y, seed, 0.34F);
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
                int obsidian = color;
                if (crack || branch) {
                    obsidian = Palette.lerp(
                            color,
                            palette.highlight(),
                            branch ? 0.48F : 0.82F);
                } else if (facetA > 29 && facetB > 43) {
                    obsidian = Palette.lerp(color, palette.mid(), 0.22F);
                } else {
                    obsidian = Palette.scale(color, 0.84F);
                }
                return generatedSurface(
                        obsidian, palette, MaterialPatternMask.VOID_RUNES,
                        x, y, seed, 0.30F);
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
                int heated = heat <= 2
                        ? Palette.lerp(color, palette.highlight(), 0.56F)
                        : Palette.scale(color, heat == 12 ? 0.72F : 1.0F);
                return generatedCracks(
                        heated, palette, MaterialPatternMask.MOLTEN,
                        x, y, seed, 0.82F);
            }
        },
        ARCANE {
            @Override
            int decorate(int color, Palette palette, int x, int y, int seed) {
                int lane = Math.floorMod(y + seed, 18);
                int glyph = Math.floorMod(x * 5 + y * 3 + seed, 41);
                boolean runeStroke = (lane == 8 || lane == 9)
                        && (glyph <= 8 || glyph >= 35);
                int inscribed = color;
                if (runeStroke) {
                    inscribed = Palette.lerp(
                            color, palette.highlight(), 0.52F);
                } else {
                    double underGlow = Math.sin(
                            x * 0.055D + y * 0.17D + seed * 0.004D);
                    if (underGlow > 0.92D) {
                        inscribed = Palette.lerp(color, palette.mid(), 0.22F);
                    } else {
                        inscribed = Palette.scale(color, 0.94F);
                    }
                }
                return generatedCracks(
                        inscribed, palette, MaterialPatternMask.ARCANE_CIRCUIT,
                        x, y, seed, 0.58F);
            }
        },
        ENDER {
            @Override
            int decorate(int color, Palette palette, int x, int y, int seed) {
                int shimmer = Math.floorMod(x * 3 + y * 5 + seed, 29);
                int voidMetal = shimmer == 0 || shimmer == 1
                        ? Palette.lerp(color, palette.highlight(), 0.62F)
                        : color;
                return generatedSurface(
                        voidMetal, palette, MaterialPatternMask.VOID_RUNES,
                        x, y, seed, 0.34F);
            }
        },
        TOXIC {
            @Override
            int decorate(int color, Palette palette, int x, int y, int seed) {
                double vein = Math.sin(
                        x * 0.071D
                                + Math.sin(y * 0.18D + seed * 0.004D)
                                * 1.28D);
                int blister = cosmicHash(x, y, seed);
                if (vein > 0.91D) {
                    return Palette.lerp(color, 0xB9F25B, 0.48F);
                }
                if (blister % 73 == 0) {
                    return Palette.lerp(color, 0xE4FF8A, 0.38F);
                }
                return vein < -0.94D
                        ? Palette.scale(color, 0.74F)
                        : Palette.lerp(color, 0x527A38, 0.08F);
            }
        };

        /**
         * Blade faces use a deliberately restrained finish. Component
         * textures may retain material grain, but a forged blade receives no
         * random speckles or rapidly repeating waves.
         */
        int decorateBlade(
                int color,
                Palette palette,
                float atlasX,
                float atlasY,
                int seed) {
            float progress = clamp01((atlasX - 1.0F) / 62.0F);
            int finished = switch (this) {
                case HEAVY_METAL -> heavyBlade(
                        color, palette, progress, atlasY, seed);
                case CRUDE_METAL -> crudeBlade(
                        color, palette, progress, atlasY, seed);
                case POLISHED_METAL -> broadPolish(
                        color, palette, progress, atlasY);
                case PATINA_METAL -> broadPatina(
                        color, progress, atlasY);
                case NETHERITE -> broadLamination(
                        color, progress, atlasY);
                case CURSED_METAL, WITHERITE, DARK_ALLOY -> broadLamination(
                        color, progress, atlasY);
                case IRONWOOD -> broadLamination(
                        color, progress, atlasY);
                case STEELEAF -> broadPatina(
                        color, progress, atlasY);
                case KNIGHTMETAL -> broadPolish(
                        color, palette, progress, atlasY);
                case CRYSTAL -> largeCrystalFacets(
                        color, palette, progress, atlasY);
                case OBSIDIAN -> continuousObsidianCrack(
                        color, palette, progress, atlasY, seed);
                case INFINITY -> cosmicBlade(
                        color, palette, atlasX, atlasY, seed);
                case DRAGON_FIRE, DRAGON_ICE, DRAGON_LIGHTNING -> dragonBlade(
                        color, palette, progress, atlasY);
                case TOXIC -> toxicBlade(
                        color, palette, progress, atlasY, seed);
                default -> color;
            };

            int maskX = Math.round(atlasX);
            int maskY = Math.round(atlasY * 2.0F);
            int generated = switch (this) {
                case BLAZE, APOCALYPTIUM -> generatedCracks(
                        finished, palette, MaterialPatternMask.MOLTEN,
                        maskX, maskY, seed, 0.68F);
                case CURSED_METAL, GHOST -> generatedSurface(
                        finished, palette, MaterialPatternMask.SPECTRAL_CURSE,
                        maskX, maskY, seed, 0.34F);
                case WITHERITE -> generatedCracks(
                        finished, palette, MaterialPatternMask.WITHER_CRACKS,
                        maskX, maskY, seed, 0.76F);
                case OBSIDIAN, ENDER -> generatedSurface(
                        finished, palette, MaterialPatternMask.VOID_RUNES,
                        maskX, maskY, seed, 0.28F);
                case DARK_ALLOY -> finished;
                case IRONWOOD -> generatedSurface(
                        finished, palette, MaterialPatternMask.IRONWOOD_LAYERS,
                        maskX, maskY, seed, 0.28F);
                case STEELEAF -> generatedSurface(
                        finished, palette, MaterialPatternMask.STEELEAF_VEINS,
                        maskX, maskY, seed, 0.32F);
                case CRYSTAL -> finished;
                case ARCANE -> generatedCracks(
                        finished, palette, MaterialPatternMask.ARCANE_CIRCUIT,
                        maskX, maskY, seed, 0.44F);
                default -> finished;
            };

            if (this == OBSIDIAN || this == CRYSTAL || this == INFINITY) {
                return generated;
            }
            return masterFlowLine(
                    generated, palette, progress, atlasY, seed,
                    switch (this) {
                        case DRAGON_FIRE, DRAGON_ICE, DRAGON_LIGHTNING -> 0.18F;
                        case CURSED_METAL -> 0.24F;
                        case WITHERITE -> 0.17F;
                        case DARK_ALLOY -> 0.08F;
                        case IRONWOOD, STEELEAF, KNIGHTMETAL,
                                PATTERN_WELDED, NETHERITE,
                                HEAVY_METAL -> 0.13F;
                        case CRUDE_METAL -> 0.07F;
                        case TOXIC -> 0.15F;
                        default -> 0.04F;
                    });
        }

        int bladeEmissionAlpha(
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
                case CURSED_METAL -> distance < 0.40D ? 175 : 0;
                case WITHERITE -> generatedEmission(
                        MaterialPatternMask.WITHER_CRACKS,
                        atlasX, atlasY, seed, 202, 145);
                case DARK_ALLOY -> distance < 0.28D ? 105 : 0;
                case INFINITY -> infinityEmissionAlpha(
                        Math.round(atlasX * 4.0F),
                        Math.round(atlasY * 4.0F),
                        seed);
                case CRYSTAL -> crystalFacetDistance(progress, atlasY) < 0.30D
                        ? 135 : 0;
                case OBSIDIAN -> obsidianCrackDistance(
                        progress, atlasY, seed) < 0.31D ? 175 : 0;
                case BLAZE -> Math.max(
                        distance < 0.48D ? 185 : 0,
                        generatedEmission(
                                MaterialPatternMask.MOLTEN,
                                atlasX, atlasY, seed, 208, 180));
                case ARCANE -> Math.max(
                        distance < 0.38D ? 155 : 0,
                        generatedEmission(
                                MaterialPatternMask.ARCANE_CIRCUIT,
                                atlasX, atlasY, seed, 218, 135));
                case ENDER -> Math.max(
                        distance < 0.40D ? 160 : 0,
                        generatedEmission(
                                MaterialPatternMask.VOID_RUNES,
                                atlasX, atlasY, seed, 226, 110));
                case TOXIC -> distance < 0.34D ? 105 : 0;
                default -> 0;
            };
        }

        private int dragonBlade(int color, Palette palette, float progress, float width) {
            if (progress < .10F || progress > .91F || width < 4 || width > 25) return color;
            float line = switch (this) {
                case DRAGON_FIRE -> 15.0F + (float)Math.sin(progress*24)*.85F;
                case DRAGON_ICE -> 14.5F + (float)Math.abs(Math.sin(progress*19))*2.3F;
                default -> 14.8F + (float)(Math.floor(progress*12)%2)*1.25F;
            };
            float distance = Math.abs(width-line);
            if (distance < .32F) return Palette.lerp(color, switch (this) {
                case DRAGON_FIRE -> 0xF3A065;
                case DRAGON_ICE -> 0xD7F7FF;
                default -> 0xE5D6FF;
            }, .52F);
            if (distance < .72F) return Palette.lerp(color, palette.shadow(), .24F);
            return color;
        }

        static int generatedSurface(
                int color,
                Palette palette,
                MaterialPatternMask mask,
                int x,
                int y,
                int seed,
                float strength) {
            int value = mask.sample(x, y, seed);
            if (value > 128) {
                float amount = (value - 128) / 127.0F * strength;
                return Palette.lerp(color, palette.highlight(), amount);
            }
            float shade = (128 - value) / 128.0F * strength * 0.48F;
            return Palette.scale(color, 1.0F - shade);
        }

        static int generatedCracks(
                int color,
                Palette palette,
                MaterialPatternMask mask,
                int x,
                int y,
                int seed,
                float strength) {
            int value = mask.sample(x, y, seed);
            if (value >= 176) {
                float amount = (value - 176) / 79.0F * strength;
                return Palette.lerp(color, palette.highlight(), amount);
            }
            if (value <= 68) {
                float shade = (68 - value) / 68.0F * strength * 0.34F;
                return Palette.scale(color, 1.0F - shade);
            }
            return color;
        }

        static int generatedEmission(
                MaterialPatternMask mask,
                float atlasX,
                float atlasY,
                int seed,
                int threshold,
                int maximum) {
            int value = mask.sample(
                    Math.round(atlasX), Math.round(atlasY * 2.0F), seed);
            if (value <= threshold) {
                return 0;
            }
            return Math.round(
                    (value - threshold) / (float) (255 - threshold) * maximum);
        }

        static int heavyBlade(
                int color,
                Palette palette,
                float progress,
                float atlasY,
                int seed) {
            int layered = broadLamination(color, progress, atlasY);
            double lowerBand = Math.abs(
                    atlasY - (18.3D - Math.sin(progress * Math.PI) * 0.38D));
            if (lowerBand < 0.72D) {
                layered = Palette.scale(layered, 0.91F);
            }
            int hammer = cosmicHash(
                    Math.round(progress * 46.0F),
                    Math.round(atlasY * 0.72F),
                    seed);
            return hammer % 97 == 0
                    ? Palette.scale(layered, 0.86F)
                    : Palette.lerp(layered, palette.mid(), 0.03F);
        }

        static int crudeBlade(
                int color,
                Palette palette,
                float progress,
                float atlasY,
                int seed) {
            int pit = cosmicHash(
                    Math.round(progress * 84.0F),
                    Math.round(atlasY * 1.25F),
                    seed);
            if (pit % 89 == 0) {
                return Palette.scale(color, 0.74F);
            }
            if (pit % 131 == 0) {
                return Palette.lerp(color, palette.highlight(), 0.12F);
            }
            return Palette.scale(color, 0.97F);
        }

        static int toxicBlade(
                int color,
                Palette palette,
                float progress,
                float atlasY,
                int seed) {
            double veinY = masterFlowY(progress, seed)
                    + Math.sin(progress * Math.PI * 4.0D) * 0.26D;
            double distance = Math.abs(atlasY - veinY);
            if (distance < 0.26D) {
                return Palette.lerp(color, 0xC8FF65, 0.42F);
            }
            if (distance < 0.58D) {
                return Palette.lerp(color, 0x527A38, 0.14F);
            }
            return Palette.lerp(color, palette.mid(), 0.03F);
        }

        static int masterFlowLine(
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

        static double masterFlowY(float progress, int seed) {
            double phase = ((seed & 0xff) / 255.0D - 0.5D) * 0.12D;
            return 14.35D
                    + Math.sin((progress - 0.10D) * Math.PI + phase) * 0.72D
                    + (progress - 0.5D) * 0.18D;
        }

        static int broadPolish(
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

        static int broadPatina(
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

        static int broadLamination(
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

        static int largeCrystalFacets(
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

        static int cosmicBlade(
                int color,
                Palette palette,
                float atlasX,
                float atlasY,
                int seed) {
            int x = Math.round(atlasX * 4.0F);
            int y = Math.round(atlasY * 4.0F);
            double nebula = Math.sin(
                    x * 0.021D
                            + Math.sin(y * 0.037D + seed * 0.0003D)
                            * 1.65D);
            int cosmic = Palette.lerp(color, 0x070615, 0.58F);
            if (nebula > 0.32D) {
                cosmic = Palette.lerp(
                        cosmic,
                        nebula > 0.82D ? 0x6B4CB8 : 0x31256B,
                        nebula > 0.82D ? 0.38F : 0.20F);
            } else if (nebula < -0.76D) {
                cosmic = Palette.lerp(cosmic, 0x081A3C, 0.28F);
            }

            int star = cosmicHash(x, y, seed);
            if (star % 613 == 0) {
                return Palette.lerp(cosmic, 0xFFFFFF, 0.94F);
            }
            if (star % 257 == 0) {
                return Palette.lerp(cosmic, 0x8EC8FF, 0.68F);
            }
            return Palette.lerp(cosmic, palette.mid(), 0.06F);
        }

        static double crystalFacetDistance(
                float progress,
                float atlasY) {
            double first = Math.abs(atlasY - (8.0D + progress * 9.0D));
            double second = Math.abs(atlasY - (25.0D - progress * 7.0D));
            return Math.min(first, second);
        }

        static int continuousObsidianCrack(
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

        static double obsidianCrackDistance(
                float progress,
                float atlasY,
                int seed) {
            double phase = ((seed >>> 8) & 0xff) / 255.0D * 0.16D;
            double crackY = 13.1D
                    + Math.sin((progress + phase) * Math.PI) * 1.05D
                    + progress * 0.42D;
            return Math.abs(atlasY - crackY);
        }

        int emissionAlpha(int x, int y, int seed) {
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
                case CURSED_METAL -> {
                    double binding = Math.sin(
                            x * 0.075D + Math.sin(y * 0.13D + seed * 0.002D));
                    double counter = Math.sin(
                            x * 0.041D - y * 0.19D + seed * 0.003D);
                    yield binding > 0.88D && counter > 0.12D ? 185 : 0;
                }
                case DARK_ALLOY -> {
                    double seam = Math.sin(
                            x * 0.048D + y * 0.16D + seed * 0.002D);
                    yield seam > 0.96D ? 95 : 0;
                }
                case INFINITY -> infinityEmissionAlpha(x, y, seed);
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
                case TOXIC -> {
                    double vein = Math.sin(
                            x * 0.071D
                                    + Math.sin(y * 0.18D + seed * 0.004D)
                                    * 1.28D);
                    yield vein > 0.94D ? 115 : 0;
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

    static record Palette(int shadow, int mid, int highlight) {
        static Palette fromBase(
                int base,
                float shadowScale,
                float highlightScale) {
            return new Palette(
                    scale(base, shadowScale),
                    base,
                    scale(base, highlightScale));
        }

        int sample(float tone) {
            if (tone <= 0.5F) {
                return lerp(shadow, mid, tone * 2.0F);
            }
            return lerp(mid, highlight, (tone - 0.5F) * 2.0F);
        }

        static int lerp(int from, int to, float amount) {
            int red = Math.round(channel(from, 16)
                    + (channel(to, 16) - channel(from, 16)) * amount);
            int green = Math.round(channel(from, 8)
                    + (channel(to, 8) - channel(from, 8)) * amount);
            int blue = Math.round(channel(from, 0)
                    + (channel(to, 0) - channel(from, 0)) * amount);
            return red << 16 | green << 8 | blue;
        }

        static int scale(int color, float scale) {
            int red = Math.min(255, Math.round(channel(color, 16) * scale));
            int green = Math.min(255, Math.round(channel(color, 8) * scale));
            int blue = Math.min(255, Math.round(channel(color, 0) * scale));
            return red << 16 | green << 8 | blue;
        }

        static int channel(int color, int shift) {
            return color >>> shift & 0xff;
        }
    }

    private MaterialTextureStyleEngine() {
    }
}
