package dev.bladetetra.visual;

import se.mickelus.tetra.data.DataManager;
import se.mickelus.tetra.effect.ItemEffect;
import se.mickelus.tetra.module.data.MaterialData;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Resolves visual hints from Tetra's active material registry.
 *
 * <p>This deliberately depends only on Tetra's public material data. Addons
 * such as More Material Tetra therefore work without becoming hard runtime
 * dependencies and without copying their stats, effects, or assets.</p>
 */
public final class TetraMaterialVisualResolver {
    private static volatile Map<String, MaterialVisual> cache;
    private static volatile long revision = 1L;

    static {
        if (DataManager.instance != null
                && DataManager.instance.materialData != null) {
            DataManager.instance.materialData.onReload(
                    TetraMaterialVisualResolver::invalidate);
        }
    }

    public static MaterialVisual resolve(String materialKey) {
        Map<String, MaterialVisual> current = cache;
        if (current == null) {
            synchronized (TetraMaterialVisualResolver.class) {
                current = cache;
                if (current == null) {
                    current = buildIndex();
                    cache = current;
                }
            }
        }
        return current.get(normalize(materialKey));
    }

    public static long revision() {
        return revision;
    }

    public static synchronized void invalidate() {
        cache = null;
        revision++;
    }

    private static Map<String, MaterialVisual> buildIndex() {
        if (DataManager.instance == null
                || DataManager.instance.materialData == null
                || DataManager.instance.materialData.getData() == null) {
            return Map.of();
        }

        Map<String, MaterialVisual> result = new HashMap<>();
        DataManager.instance.materialData.getData().entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.naturalOrder()))
                .map(Map.Entry::getValue)
                .filter(data -> data != null && data.key != null)
                .forEach(data -> add(result, data));
        return Map.copyOf(result);
    }

    private static void add(
            Map<String, MaterialVisual> result,
            MaterialData data) {
        MaterialKind kind = MaterialKind.from(data.category);
        if (kind == null || data.tints == null) {
            return;
        }

        if (data.tints.texture < 0) {
            return;
        }
        int color = data.tints.texture & 0xFFFFFF;

        String key = normalize(data.key);
        if (key.isBlank()) {
            return;
        }
        result.putIfAbsent(
                key,
                new MaterialVisual(
                        color,
                        kind,
                        surface(data.textures),
                        visualTrait(data)));
    }

    private static VisualTrait visualTrait(MaterialData data) {
        StringBuilder hints = new StringBuilder(normalize(data.key));
        if (data.effects != null) {
            for (ItemEffect effect : data.effects.getValues()) {
                if (effect != null && effect.getKey() != null) {
                    hints.append(' ')
                            .append(effect.getKey().toLowerCase(Locale.ROOT));
                }
            }
        }
        return VisualTrait.from(hints.toString());
    }

    private static SurfaceHint surface(String[] textures) {
        if (textures == null) {
            return SurfaceHint.DEFAULT;
        }
        for (String texture : textures) {
            String normalized = texture == null
                    ? ""
                    : texture.toLowerCase(Locale.ROOT);
            if (normalized.contains("polished")
                    || normalized.contains("shiny")) {
                return SurfaceHint.POLISHED;
            }
            if (normalized.contains("crude")
                    || normalized.contains("rough")) {
                return SurfaceHint.CRUDE;
            }
            if (normalized.contains("heavy")) {
                return SurfaceHint.HEAVY;
            }
        }
        return SurfaceHint.DEFAULT;
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        int namespace = normalized.lastIndexOf(':');
        if (namespace >= 0) {
            normalized = normalized.substring(namespace + 1);
        }
        int path = normalized.lastIndexOf('/');
        if (path >= 0) {
            normalized = normalized.substring(path + 1);
        }
        return normalized.replaceAll("[^a-z0-9_\\-.]", "_");
    }

    public record MaterialVisual(
            int color,
            MaterialKind kind,
            SurfaceHint surface,
            VisualTrait trait) {
    }

    public enum MaterialKind {
        METAL,
        GEM,
        BONE,
        WOOD,
        STONE;

        private static MaterialKind from(String category) {
            if (category == null) {
                return null;
            }
            return switch (category.toLowerCase(Locale.ROOT)) {
                case "metal" -> METAL;
                case "gem" -> GEM;
                case "bone" -> BONE;
                case "wood" -> WOOD;
                case "stone" -> STONE;
                default -> null;
            };
        }
    }

    public enum SurfaceHint {
        DEFAULT,
        POLISHED,
        CRUDE,
        HEAVY
    }

    /**
     * Broad visual families inferred from public material keys and effects.
     *
     * <p>The matching intentionally uses common words instead of addon ids so
     * future material packs receive a useful appearance without becoming hard
     * dependencies. Only one restrained primary trait is selected.</p>
     */
    public enum VisualTrait {
        NONE,
        FIRE,
        ICE,
        LIGHTNING,
        SOUL,
        SHADOW,
        CURSED,
        COSMIC,
        ARCANE,
        TOXIC;

        private static VisualTrait from(String hints) {
            String normalized = " "
                    + hints.toLowerCase(Locale.ROOT)
                    .replaceAll("[^a-z0-9]+", " ")
                    .trim()
                    + " ";
            if (containsAny(
                    normalized,
                    "infinity", "cosmic", "stellar", "astral",
                    "galaxy", "space", "celestial", "star_power")) {
                return COSMIC;
            }
            if (containsAny(
                    normalized,
                    "lightning", "thunder", "electric", "electricity",
                    "electro", "shock", "storm", "voltage", "charged")) {
                return LIGHTNING;
            }
            if (containsAny(
                    normalized,
                    "fire", "flame", "blaze", "burn", "burning",
                    "heat", "lava", "magma", "infernal", "phoenix",
                    "fiery")) {
                return FIRE;
            }
            if (containsAny(
                    normalized,
                    "ice", "frost", "frozen", "glacial", "snow",
                    "winter", "cold")) {
                return ICE;
            }
            if (containsAny(
                    normalized,
                    "soul", "spirit", "ghost", "spectral",
                    "phantasm", "phantasmal", "necrotic", "necromancy",
                    "undead")) {
                return SOUL;
            }
            if (containsAny(
                    normalized,
                    "curse", "cursed", "dread", "corruption",
                    "corrupted", "demonic", "blood_magic", "blood_power")) {
                return CURSED;
            }
            if (containsAny(
                    normalized,
                    "shadow", "darkness", "night_power", "void",
                    "eclipse")) {
                return SHADOW;
            }
            if (containsAny(
                    normalized,
                    "toxic", "poison", "venom", "radiation",
                    "radioactive", "uranium", "plague", "acid")) {
                return TOXIC;
            }
            if (containsAny(
                    normalized,
                    "mana", "magic", "spell", "arcane", "rune",
                    "enchant", "enchanted", "enchantment", "occult",
                    "aether", "ender", "enderium")) {
                return ARCANE;
            }
            return NONE;
        }

        private static boolean containsAny(
                String value,
                String... needles) {
            for (String needle : needles) {
                String normalizedNeedle = needle.replace('_', ' ');
                if (value.contains(" " + normalizedNeedle + " ")) {
                    return true;
                }
            }
            return false;
        }
    }

    private TetraMaterialVisualResolver() {
    }
}
