package dev.bladetetra.visual;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Explicit visual overrides for addon-provided Tetra materials.
 *
 * <p>This registry is intentionally independent from provider mod ids and
 * provider classes. Compatibility code can register a material key and the
 * restrained visual family Blade Tetra should use without adding another
 * provider-specific branch to the texture compositor.</p>
 */
public final class MaterialVisualOverrideRegistry {
    private static final Map<String, TetraMaterialVisualResolver.MaterialVisual> OVERRIDES =
            new LinkedHashMap<>();
    private static long revision = 1L;

    public static synchronized Registration register(
            String materialKey,
            TetraMaterialVisualResolver.MaterialVisual visual) {
        String normalized = MaterialVisualKey.canonical(materialKey);
        Objects.requireNonNull(visual, "visual");
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("materialKey must not be blank");
        }
        if (OVERRIDES.containsKey(normalized)) {
            throw new IllegalStateException(
                    "Material visual override already registered for " + normalized);
        }
        OVERRIDES.put(normalized, visual);
        revision++;
        return new Registration(normalized, visual);
    }

    static synchronized TetraMaterialVisualResolver.MaterialVisual resolve(
            String materialKey) {
        return OVERRIDES.get(MaterialVisualKey.canonical(materialKey));
    }

    static synchronized long revision() {
        return revision;
    }

    static synchronized int registeredCount() {
        return OVERRIDES.size();
    }

    static synchronized void clearForTests() {
        OVERRIDES.clear();
        revision++;
    }

    public static final class Registration implements AutoCloseable {
        private final String materialKey;
        private final TetraMaterialVisualResolver.MaterialVisual visual;
        private boolean closed;

        private Registration(
                String materialKey,
                TetraMaterialVisualResolver.MaterialVisual visual) {
            this.materialKey = materialKey;
            this.visual = visual;
        }

        @Override
        public void close() {
            synchronized (MaterialVisualOverrideRegistry.class) {
                if (closed) {
                    return;
                }
                if (OVERRIDES.remove(materialKey, visual)) {
                    revision++;
                }
                closed = true;
            }
        }
    }

    private MaterialVisualOverrideRegistry() {
    }
}
