package dev.bladetetra.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Guardrails for the shared low-level VFX rendering foundation. */
class VfxFoundationGuardTest {
    @Test
    void migratedClientsUseSharedPrimitivesInsteadOfLocalCopies() throws IOException {
        String combat = read("src/main/java/dev/bladetetra/client/BladeCombatVfxClient.java");
        assertTrue(combat.contains("VfxPrimitives"),
                "Blade combat VFX should render common geometry through VfxPrimitives");
        assertFalse(combat.contains("private static void ringHorizontal("));
        assertFalse(combat.contains("private static void ringVertical("));
        assertFalse(combat.contains("private static void band3d("));
        assertFalse(combat.contains("private static void planeBand("));

        String raikiri = read("src/main/java/dev/bladetetra/client/RaikiriVfxClient.java");
        assertTrue(raikiri.contains("VfxPrimitives"),
                "Raikiri VFX should render common geometry through VfxPrimitives");
        assertFalse(raikiri.contains("private static void texturedRibbon("));
        assertFalse(raikiri.contains("private static void billboard("));
    }

    @Test
    void primitiveLayerStaysFreeOfTechniqueAndNetworkSemantics() throws IOException {
        String primitives = read(
                "src/main/java/dev/bladetetra/client/vfx/render/VfxPrimitives.java");
        assertFalse(primitives.contains("dev.bladetetra.network"),
                "Low-level primitives must not depend on packet transport");
        assertFalse(primitives.contains("TechniqueVfx"),
                "Low-level primitives must not own technique lifecycle semantics");
        assertTrue(primitives.contains("ringHorizontal("));
        assertTrue(primitives.contains("texturedRibbon("));
        assertTrue(primitives.contains("billboard("));
    }

    private static String read(String path) throws IOException {
        return Files.readString(Path.of(path));
    }
}
