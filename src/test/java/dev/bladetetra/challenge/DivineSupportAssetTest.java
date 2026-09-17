package dev.bladetetra.challenge;

import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class DivineSupportAssetTest {
    private static final String ROOT="/assets/blade_tetra/";
    @Test void everyRuntimeModelHasRealTrianglesAndValidUvIndices() throws IOException {
        for(String name:new String[]{"purification_blade","three_sword_anchor","boundary_cut","boundary_shard",
                "guard_barrier","divine_mark","final_binding"}) {
            try(var input=getClass().getResourceAsStream(ROOT+"models/divine/"+name+".obj")) {
                assertNotNull(input,name);
                var lines=new String(input.readAllBytes(),StandardCharsets.UTF_8).lines().toList();
                long vertices=lines.stream().filter(l->l.startsWith("v ")).count();
                long uv=lines.stream().filter(l->l.startsWith("vt ")).count();
                long faces=lines.stream().filter(l->l.startsWith("f ")).count();
                assertTrue(faces>0 && faces<=1000,name+" geometry budget");
                assertTrue(lines.contains("g body"),name+" render group");
                for(String line:lines) if(line.startsWith("f ")) {
                    String[] refs=line.substring(2).split(" "); assertEquals(3,refs.length);
                    for(String ref:refs) {
                        String[] indices=ref.split("/");
                        int v=Integer.parseInt(indices[0]), t=Integer.parseInt(indices[1]);
                        assertTrue(v>0 && v<=vertices,name+" vertex"); assertTrue(t>0 && t<=uv,name+" uv");
                    }
                }
            }
        }
    }
    @Test void paletteAndEveryHudResolutionArePackagedAndTransparent() throws IOException {
        try(var input=getClass().getResourceAsStream(ROOT+"textures/divine/palette.png")) {
            assertNotNull(input); var png=ImageIO.read(input); assertEquals(256,png.getWidth());
            assertEquals(0xEEE6D5,png.getRGB(20,80)&0xFFFFFF);
        }
        for(String name:new String[]{"mikage_support","purification_array","boundary_cut","divine_guard","divine_mark","final_binding"})
            for(int size:new int[]{256,128,64,32}) try(var input=getClass().getResourceAsStream(ROOT+"textures/divine/"+name+"_"+size+".png")) {
                assertNotNull(input,name+size); var png=ImageIO.read(input);
                assertEquals(size,png.getWidth()); assertEquals(size,png.getHeight());
                assertEquals(0,png.getRGB(0,0)>>>24,name+" transparent background");
            }
    }
}
