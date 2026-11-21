package dev.bladetetra.client;

import dev.bladetetra.forging.LegacyImprintKind;

/** Reports source appearance coverage without affecting research or extraction. */
public final class LegacyResearchabilityClient {
    record Result(java.util.Map<LegacySourceMesh.Component, LegacyRenderSources.Support> parts) {}

    static Result inspect(LegacyImprintKind kind) {
        var source = LegacyRenderSources.resolve(kind);
        var parts = new java.util.EnumMap<LegacySourceMesh.Component, LegacyRenderSources.Support>(
                LegacySourceMesh.Component.class);
        for (var part : LegacySourceMesh.Component.values()) {
            parts.put(part, source.support(part));
        }
        return new Result(java.util.Collections.unmodifiableMap(parts));
    }

    private LegacyResearchabilityClient() {}
}
