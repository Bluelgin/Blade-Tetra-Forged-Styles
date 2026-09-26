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
        String techniques = read(
                "src/main/java/dev/bladetetra/client/BladeTechniqueVfxClient.java");
        String geometry = read(
                "src/main/java/dev/bladetetra/client/BladeTechniqueVfxGeometry.java");
        assertTrue(geometry.contains("VfxPrimitives"),
                "Legacy technique geometry should delegate common emission to VfxPrimitives");
        assertFalse(techniques.contains("private static void ringHorizontal("));
        assertFalse(techniques.contains("private static void ringVertical("));
        assertFalse(techniques.contains("private static void quad("));
        assertFalse(techniques.contains("private static void vertex("));

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

        String kyouka = read("src/main/java/dev/bladetetra/client/KyoukaVfxClient.java");
        assertTrue(kyouka.contains("rotatedAtlasBillboard("),
                "Kyouka shards should use the shared batched atlas billboard primitive");
        assertTrue(kyouka.contains("horizontalTexturedQuad("),
                "Kyouka pools should use the shared textured quad primitive");
        assertFalse(kyouka.contains("private static void horizontalQuad("));
        assertFalse(kyouka.contains("private static void billboardCell("));
        assertFalse(kyouka.contains("private static void vertex("));

        String boundary = read(
                "src/main/java/dev/bladetetra/client/BoundaryForgingVfxClient.java");
        assertTrue(boundary.contains("bandFacing(buffer"),
                "Boundary camera-facing lines should delegate to VfxPrimitives");
        assertFalse(boundary.contains("private static void quad("));
        assertFalse(boundary.contains("private static void vertex("));
        assertFalse(boundary.contains("private static int color("));
        assertFalse(boundary.contains("private static int withAlpha("));

        String mikageBoundary = read(
                "src/main/java/dev/bladetetra/client/MikageBoundaryClient.java");
        assertTrue(mikageBoundary.contains("ringHorizontal(buffer"),
                "Mikage arena rings should use the shared ring primitive");
        assertFalse(mikageBoundary.contains("private static void drawFloorCircle("));
        assertFalse(mikageBoundary.contains("private static void drawFloorPolyline("));
        assertFalse(mikageBoundary.contains("private static void quad("));
        assertFalse(mikageBoundary.contains("renderCeremonialTextures("),
                "Retired boundary wall/seal renderer should not remain as dead code");
    }

    @Test
    void voidScatteringSharesFallbackGeometryWithoutTouchingShaderPayloads()
            throws IOException {
        String domain = read(
                "src/main/java/dev/bladetetra/client/VoidScatteringVfxClient.java");
        assertTrue(domain.contains("bandFacing(buffer"),
                "Void Scattering fallback lines should use the shared facing band primitive");
        assertTrue(domain.contains("vertex(buffer, matrix, point, color)"),
                "Fallback dome vertices should delegate to VfxPrimitives");
        assertFalse(domain.contains("private static void line("));
        assertFalse(domain.contains("private static void vertex("));
        assertFalse(domain.contains("private static int color("));
        assertTrue(domain.contains("private static void shaderVertex("),
                "Shader payload vertices must remain effect-owned");
        assertTrue(domain.contains(".color(filled, flash, seed, alpha)"),
                "Shader payload channel packing must not be routed through color primitives");

        String counter = read(
                "src/main/java/dev/bladetetra/client/VoidScatteringCounterVfxClient.java");
        assertTrue(counter.contains("quad(buffer, matrix"),
                "Void Scattering counter fallback should use the shared quad primitive");
        assertFalse(counter.contains("private static void fallbackVertex("));
        assertTrue(counter.contains("private static void shaderVertex("),
                "Counter shader payload vertices must remain effect-owned");
        assertTrue(counter.contains(".color(filled, flash, seed, alpha)"),
                "Counter shader payload channel packing must stay intact");
    }

    @Test
    void primitiveLayerSupportsBatchFriendlyTexturedGeometry() throws IOException {
        String primitives = read(
                "src/main/java/dev/bladetetra/client/vfx/render/VfxPrimitives.java");
        assertTrue(primitives.contains("horizontalTexturedQuad(BufferBuilder"));
        assertTrue(primitives.contains("billboard(BufferBuilder"));
        assertTrue(primitives.contains("rotatedAtlasBillboard(BufferBuilder"));
        assertTrue(primitives.contains("texturedPlane(BufferBuilder"));
        assertTrue(primitives.contains("texturedQuad(BufferBuilder"));
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
