package dev.bladetetra.visual;

import java.util.Locale;

/** Shared normalization for material-visual lookup keys. */
final class MaterialVisualKey {
    /** Provider-aware identity for explicit overrides. */
    static String canonical(String value) {
        if (value == null) {
            return "";
        }
        return value.trim()
                .toLowerCase(Locale.ROOT)
                .replace('\\', '/')
                .replaceAll("[^a-z0-9_./:\\-]", "_");
    }

    /** Legacy leaf lookup retained for the generic Tetra material index. */
    static String normalize(String value) {
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

    private MaterialVisualKey() {
    }
}
