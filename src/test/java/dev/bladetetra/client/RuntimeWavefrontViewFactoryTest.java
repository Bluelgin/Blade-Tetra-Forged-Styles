package dev.bladetetra.client;

import mods.flammpfeil.slashblade.client.renderer.model.obj.Face;
import mods.flammpfeil.slashblade.client.renderer.model.obj.TextureCoordinate;
import mods.flammpfeil.slashblade.client.renderer.model.obj.Vertex;
import mods.flammpfeil.slashblade.client.renderer.model.obj.WavefrontObject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeWavefrontViewFactoryTest {
    @Test
    void constructorReceivesFinalTriangulatedGeometryAndPreservesUvs() {
        Face quad = new Face();
        quad.vertices = new Vertex[]{
                new Vertex(0.0F, 0.0F, 0.0F),
                new Vertex(2.0F, 0.0F, 0.0F),
                new Vertex(2.0F, 1.0F, 0.0F),
                new Vertex(0.0F, 1.0F, 0.0F)
        };
        quad.textureCoordinates = new TextureCoordinate[]{
                new TextureCoordinate(0.1F, 0.2F),
                new TextureCoordinate(0.9F, 0.2F),
                new TextureCoordinate(0.9F, 0.8F),
                new TextureCoordinate(0.1F, 0.8F)
        };
        quad.faceNormal = quad.calculateFaceNormal();

        WavefrontObject view = RuntimeWavefrontViewFactory.create("blade", List.of(quad));

        assertEquals(1, view.groupObjects.size());
        assertEquals("blade", view.groupObjects.get(0).name);
        assertEquals(2, view.groupObjects.get(0).faces.size());
        assertEquals(0.1F, view.groupObjects.get(0).faces.get(0).textureCoordinates[0].u, 0.00001F);
        assertEquals(0.2F, view.groupObjects.get(0).faces.get(0).textureCoordinates[0].v, 0.00001F);
    }

    @Test
    void generatedObjUsesOnlyTrianglesForMixedSourceFaceSizes() {
        Face triangle = face(3);
        Face quad = face(4);

        WavefrontObject view = RuntimeWavefrontViewFactory.create("item_blade", List.of(triangle, quad));

        assertEquals(1, view.groupObjects.size());
        assertEquals(3, view.groupObjects.get(0).faces.size());
        view.groupObjects.get(0).faces.forEach(face -> assertEquals(3, face.vertices.length));
    }

    @Test
    void emptyGroupsAreOmittedAndInvalidNormalsAreReconstructed() {
        var modelGroups = new java.util.LinkedHashMap<String, List<Face>>();
        modelGroups.put("leading_empty", List.of());
        var triangle = face(3);
        triangle.vertexNormals[0] = new Vertex(0, 0, 0);
        triangle.vertexNormals[1] = new Vertex(Float.NaN, 0, 0);
        modelGroups.put("first", List.of(triangle));
        modelGroups.put("middle_empty", List.of(new Face()));
        modelGroups.put("second", List.of(triangle));
        modelGroups.put("trailing_empty", List.of());
        var view = RuntimeWavefrontViewFactory.create(modelGroups);
        assertEquals(List.of("first", "second"), view.groupObjects.stream().map(group -> group.name).toList());
        view.groupObjects.forEach(group -> {
            assertEquals(1, group.faces.size());
            for (var normal : group.faces.get(0).vertexNormals) {
                assertTrue(Float.isFinite(normal.z));
                assertEquals(1, Math.abs(normal.z), .0001F);
            }
        });
    }

    private static Face face(int vertices) {
        Face face = new Face();
        face.vertices = new Vertex[vertices];
        face.textureCoordinates = new TextureCoordinate[vertices];
        face.vertexNormals = new Vertex[vertices];
        for (int i = 0; i < vertices; i++) {
            float angle = (float) (Math.PI * 2.0 * i / vertices);
            face.vertices[i] = new Vertex((float) Math.cos(angle), (float) Math.sin(angle), 0.0F);
            face.textureCoordinates[i] = new TextureCoordinate(i / (float) vertices, i / (float) vertices);
            face.vertexNormals[i] = new Vertex(0.0F, 0.0F, 1.0F);
        }
        face.faceNormal = new Vertex(0.0F, 0.0F, 1.0F);
        return face;
    }
}
