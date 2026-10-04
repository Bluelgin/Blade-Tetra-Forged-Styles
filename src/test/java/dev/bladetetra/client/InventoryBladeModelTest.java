package dev.bladetetra.client;

import mods.flammpfeil.slashblade.client.renderer.model.obj.Face;
import mods.flammpfeil.slashblade.client.renderer.model.obj.TextureCoordinate;
import mods.flammpfeil.slashblade.client.renderer.model.obj.Vertex;
import mods.flammpfeil.slashblade.client.renderer.model.obj.WavefrontObject;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class InventoryBladeModelTest {
    @Test
    void diagonalTextureUsesLengthAndWidthInsteadOfScreenAxes() {
        Face original = diagonalBlade();
        Face fixed = InventoryBladeModel.remap(List.of(original)).get(0);
        assertEquals(fixed.textureCoordinates[0].u, fixed.textureCoordinates[3].u, 1e-5);
        assertEquals(fixed.textureCoordinates[1].u, fixed.textureCoordinates[2].u, 1e-5);
        assertEquals(fixed.textureCoordinates[0].v, fixed.textureCoordinates[1].v, 1e-5);
        assertEquals(fixed.textureCoordinates[2].v, fixed.textureCoordinates[3].v, 1e-5);
        assertTrue(Math.abs(fixed.textureCoordinates[1].u - fixed.textureCoordinates[0].u) > .4);
        assertNotSame(original, fixed);
        assertSame(original.vertices, fixed.vertices);
        assertEquals(.20F, original.textureCoordinates[0].u);
    }

    @Test
    void guardsAndDecorationsKeepTheirUvs() {
        Face guard = diagonalBlade();
        for (int i = 0; i < 4; i++) guard.textureCoordinates[i] = new TextureCoordinate(.5F, .55F);
        assertSame(guard, InventoryBladeModel.remap(List.of(guard)).get(0));
    }

    @Test
    void worldTargetsReturnSharedModelWithoutAnyChanges() {
        WavefrontObject source = RuntimeWavefrontViewFactory.create("blade", List.of(diagonalBlade()));
        for (String target : List.of("blade", "blade_damaged", "blade_fragment", "sheath", "base", "color")) {
            assertSame(source, InventoryBladeModel.forTarget(source, target));
        }
    }

    @Test
    void multiGroupFactoryKeepsIndependentIndicesAndCachesIconViews() {
        var groups = new LinkedHashMap<String, List<Face>>();
        groups.put("blade", List.of(diagonalBlade()));
        Face other = diagonalBlade();
        other.vertices = new Vertex[]{new Vertex(100, 0, 0), new Vertex(110, 0, 0),
                new Vertex(110, 2, 0), new Vertex(100, 2, 0)};
        groups.put("item_blade", List.of(other));
        WavefrontObject source = RuntimeWavefrontViewFactory.create(groups);
        assertEquals(100, source.groupObjects.get(1).faces.get(0).vertices[0].x);
        WavefrontObject fixed = InventoryBladeModel.forTarget(source, "item_blade");
        assertSame(fixed, InventoryBladeModel.forTarget(source, "item_damaged"));
        assertNotSame(source, fixed);
        assertEquals(2, fixed.groupObjects.size());
        InventoryBladeModel.clear();
        assertNotSame(fixed, InventoryBladeModel.forTarget(source, "item_blade"));
    }

    @Test
    void guardFlatteningOnlyChangesGuardVerticesAndPreservesUvs() {
        Face guard = diagonalBlade();
        guard.vertices = new Vertex[]{new Vertex(-10, -8, -8), new Vertex(10, -8, -8),
                new Vertex(10, 8, -8), new Vertex(-10, 8, -8)};
        for (int i = 0; i < 4; i++) guard.textureCoordinates[i] = new TextureCoordinate(.5F, .55F);
        Face grip = diagonalBlade();
        grip.vertices = new Vertex[]{new Vertex(30, -1, -8), new Vertex(40, -1, -8),
                new Vertex(40, 1, -8), new Vertex(30, 1, -8)};
        for (int i = 0; i < 4; i++) grip.textureCoordinates[i] = new TextureCoordinate(.1F, .55F);
        Face blade = diagonalBlade();
        var corrected = InventoryGuardGeometry.correct(List.of(guard, grip, blade));
        assertSame(grip, corrected.get(1));
        assertSame(blade, corrected.get(2));
        Face thin = corrected.get(0);
        assertTrue(Math.abs(thin.vertices[0].x) < 1);
        assertEquals(guard.vertices[0].y, thin.vertices[0].y);
        assertEquals(guard.vertices[0].z, thin.vertices[0].z);
        assertSame(guard.textureCoordinates, thin.textureCoordinates);
        assertEquals(-10, guard.vertices[0].x);
        assertNotNull(thin.faceNormal);
    }

    @Test
    void missingGuardOrGripDoesNotChangeGeometry() {
        var faces = List.of(diagonalBlade());
        assertSame(faces, InventoryGuardGeometry.correct(faces));
    }

    @Test
    void allShippedModelsPreserveWorldFacesAndHaveFiniteIconUvs() throws Exception {
        Path models = Path.of("src/main/resources/assets/blade_tetra/model/modular/alpha9");
        int count = 0;
        try (var paths = Files.list(models)) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".obj")).toList()) {
                WavefrontObject source;
                try (var stream = Files.newInputStream(path)) {
                    source = new WavefrontObject(path.getFileName().toString(), stream);
                }
                WavefrontObject fixed = InventoryBladeModel.forTarget(source, "item_blade");
                for (var group : source.groupObjects) {
                    var match = fixed.groupObjects.stream().filter(g -> g.name.equals(group.name)).findFirst().orElseThrow();
                    if (!InventoryBladeModel.isIcon(group.name)) {
                        assertEquals(RuntimeWavefrontViewFactory.serialize(group.name, group.faces),
                                RuntimeWavefrontViewFactory.serialize(match.name, match.faces), path.toString());
                    } else {
                        assertEquals(group.faces.size(), match.faces.size(), path.toString());
                        for (int i = 0; i < group.faces.size(); i++) {
                            Face original = group.faces.get(i), corrected = match.faces.get(i);
                            if (!InventoryGuardGeometry.isGuard(original)) {
                                for (int j = 0; j < original.vertices.length; j++) {
                                    // OBJ serialization normalizes signed zero, not position.
                                    assertEquals(original.vertices[j].x, corrected.vertices[j].x, 0.0F);
                                    assertEquals(original.vertices[j].y, corrected.vertices[j].y, 0.0F);
                                    assertEquals(original.vertices[j].z, corrected.vertices[j].z, 0.0F);
                                }
                            }
                        }
                        for (Face face : match.faces) for (TextureCoordinate uv : face.textureCoordinates) {
                            assertTrue(Float.isFinite(uv.u) && Float.isFinite(uv.v), path.toString());
                            assertTrue(uv.u >= 0 && uv.u <= 1 && uv.v >= 0 && uv.v <= 1, path.toString());
                        }
                    }
                }
                count++;
            }
        }
        assertEquals(108, count);
    }

    private static Face diagonalBlade() {
        Face face = new Face();
        face.vertices = new Vertex[]{new Vertex(0, 0, 0), new Vertex(10, -10, 0),
                new Vertex(12, -8, 0), new Vertex(2, 2, 0)};
        face.textureCoordinates = new TextureCoordinate[]{new TextureCoordinate(.20F, .10F),
                new TextureCoordinate(.40F, .02F), new TextureCoordinate(.44F, .04F),
                new TextureCoordinate(.24F, .12F)};
        face.faceNormal = new Vertex(0, 0, 1);
        return face;
    }
}
