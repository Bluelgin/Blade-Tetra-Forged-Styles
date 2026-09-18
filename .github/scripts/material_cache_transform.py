from pathlib import Path


def replace_exact(text: str, old: str, new: str, label: str, expected: int = 1) -> str:
    count = text.count(old)
    if count != expected:
        raise SystemExit(f"{label}: expected {expected} match(es), found {count}")
    return text.replace(old, new)


material = Path("src/main/java/dev/bladetetra/client/MaterialTextureManager.java")
text = material.read_text(encoding="utf-8")

text = replace_exact(
    text,
    """    private static final ThreadLocal<Boolean> RENDERING_INTERNAL_PASS =
            ThreadLocal.withInitial(() -> false);
    private static final Palette RAYSKIN_PALETTE =
""",
    """    private static final ThreadLocal<Boolean> RENDERING_INTERNAL_PASS =
            ThreadLocal.withInitial(() -> false);
    /** Immutable 256px source for per-signature working copies during one resource cycle. */
    private static NativeImage GENERATED_ATLAS_TEMPLATE;
    private static final Palette RAYSKIN_PALETTE =
""",
    "material template field",
)

text = replace_exact(
    text,
    """            Resource resource = minecraft.getResourceManager()
                    .getResourceOrThrow(TEMPLATE);
            NativeImage image = loadGeneratedAtlas(resource);
""",
    """            NativeImage image = copyGeneratedAtlas(
                    minecraft.getResourceManager());
""",
    "direct template loads",
    expected=2,
)

text = replace_exact(
    text,
    """    /**
     * Resamples the authored 512px source atlas into the 256px runtime atlas.
""",
    """    private static synchronized NativeImage copyGeneratedAtlas(
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
""",
    "copyGeneratedAtlas helper",
)

text = replace_exact(
    text,
    """        DURABILITY_BASE_CACHE.clear();
        LegacyModelPartRenderer.clear();
""",
    """        DURABILITY_BASE_CACHE.clear();
        if (GENERATED_ATLAS_TEMPLATE != null) {
            GENERATED_ATLAS_TEMPLATE.close();
            GENERATED_ATLAS_TEMPLATE = null;
        }
        LegacyModelPartRenderer.clear();
""",
    "resource reload template cleanup",
)

material.write_text(text, encoding="utf-8")

guard = Path("src/test/java/dev/bladetetra/architecture/ArchitectureDebtGuardTest.java")
guard_text = guard.read_text(encoding="utf-8")
guard_anchor = """    @Test
    void legacyIntegerTechniquePacketIsFrozenForNewVisualFamilies() throws IOException {
"""
guard_test = """    @Test
    void materialTextureTemplateIsDecodedOncePerResourceCycle() throws IOException {
        String source = Files.readString(Path.of(
                \"src/main/java/dev/bladetetra/client/MaterialTextureManager.java\"));
        assertTrue(source.contains(\"GENERATED_ATLAS_TEMPLATE\"),
                \"The normalized material atlas should be cached for one resource cycle\");
        assertTrue(source.contains(\"copyGeneratedAtlas(\"),
                \"Material and emissive generation should copy the normalized template\");
        assertTrue(source.contains(\"copy.copyFrom(GENERATED_ATLAS_TEMPLATE);\"),
                \"Each generated signature still needs an isolated mutable image\");
        assertTrue(source.contains(\"GENERATED_ATLAS_TEMPLATE.close();\"),
                \"The native template image must be released on resource reload\");
        long directTemplateLoads = source.lines()
                .filter(line -> line.contains(\"getResourceOrThrow(TEMPLATE)\"))
                .count();
        assertTrue(directTemplateLoads <= 1,
                \"Template decode/resample belongs in the resource-cycle cache helper only\");
    }

""" + guard_anchor
guard_text = replace_exact(
    guard_text,
    guard_anchor,
    guard_test,
    "material cache architecture guard anchor",
)
guard.write_text(guard_text, encoding="utf-8")
