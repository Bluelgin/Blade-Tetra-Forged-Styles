package dev.bladetetra.visual;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MaterialVisualOverrideRegistryTest {
    @AfterEach
    void clearRegistry() {
        MaterialVisualOverrideRegistry.clearForTests();
    }

    @Test
    void keepsProviderIdentityForNamespacedMaterialKeys() {
        var visual = visual(0x88CCFF);
        var registration = MaterialVisualOverrideRegistry.register(
                "example:materials/Frost-Steel", visual);

        assertEquals(visual, MaterialVisualOverrideRegistry.resolve(
                "example:materials/frost-steel"));
        assertNull(MaterialVisualOverrideRegistry.resolve("frost-steel"));
        assertNull(MaterialVisualOverrideRegistry.resolve(
                "other:path/frost-steel"));
        assertEquals(1, MaterialVisualOverrideRegistry.registeredCount());

        registration.close();
        assertNull(MaterialVisualOverrideRegistry.resolve(
                "example:materials/frost-steel"));
    }

    @Test
    void explicitOverrideParticipatesInResolverRevision() {
        long before = TetraMaterialVisualResolver.revision();
        MaterialVisualOverrideRegistry.register("example:moonsteel", visual(0xC8D6FF));
        long after = TetraMaterialVisualResolver.revision();

        assertTrue(after > before);
        assertEquals(0xC8D6FF,
                TetraMaterialVisualResolver.resolve("example:moonsteel").color());
    }

    @Test
    void duplicateMaterialKeysAreRejected() {
        MaterialVisualOverrideRegistry.register("example:moonsteel", visual(0xC8D6FF));
        assertThrows(IllegalStateException.class,
                () -> MaterialVisualOverrideRegistry.register(
                        "EXAMPLE:MOONSTEEL", visual(0xFFFFFF)));
    }

    @Test
    void differentProvidersMayOverrideTheSameLeafName() {
        var first = visual(0xC8D6FF);
        var second = visual(0xFFAA66);
        MaterialVisualOverrideRegistry.register("first:metal/steel", first);
        MaterialVisualOverrideRegistry.register("second:metal/steel", second);

        assertEquals(first,
                MaterialVisualOverrideRegistry.resolve("first:metal/steel"));
        assertEquals(second,
                MaterialVisualOverrideRegistry.resolve("second:metal/steel"));
    }

    private static TetraMaterialVisualResolver.MaterialVisual visual(int color) {
        return new TetraMaterialVisualResolver.MaterialVisual(
                color,
                TetraMaterialVisualResolver.MaterialKind.METAL,
                TetraMaterialVisualResolver.SurfaceHint.POLISHED,
                TetraMaterialVisualResolver.VisualTrait.ARCANE);
    }
}
