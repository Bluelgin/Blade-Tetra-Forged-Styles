package dev.bladetetra.visual;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class DeadThoughtModelResourceTest {
    @Test void exportedGroupsAreUniqueAndTrianglesReferenceValidVerticesAndUvs() throws Exception {
        Path root = Path.of("src/main/resources/assets/blade_tetra/models/effect/dead_thought");
        int total = 0;
        for (String family : new String[]{"domain","rifts","final_wheel","branch_cage","scars","life_remnant","execution_line"}) {
            Set<String> groups = new HashSet<>();
            int vertices = 0, uvs = 0, triangles = 0;
            for (String line : Files.readAllLines(root.resolve(family + ".obj"))) {
                if (line.startsWith("v ")) vertices++;
                if (line.startsWith("vt ")) {
                    uvs++;
                    for (String value : line.substring(3).split(" ")) {
                        double uv = Double.parseDouble(value);
                        assertTrue(uv >= 0 && uv <= 1, family + " UV outside atlas");
                    }
                }
                if (line.startsWith("g ")) assertTrue(groups.add(line.substring(2)), family + " repeats group");
                if (line.startsWith("f ")) {
                    triangles++;
                    String[] face = line.substring(2).split(" ");
                    assertEquals(3,face.length);
                    for(String vertex : face) {
                        String[] indices = vertex.split("/");
                        assertTrue(Integer.parseInt(indices[0]) > 0 && Integer.parseInt(indices[0]) <= vertices);
                        assertTrue(Integer.parseInt(indices[1]) > 0 && Integer.parseInt(indices[1]) <= uvs);
                    }
                }
            }
            assertTrue(triangles > 0); assertFalse(groups.isEmpty()); total += triangles;
        }
        assertTrue(total < 5000, "Authored geometry budget exceeded: " + total);
    }
}
