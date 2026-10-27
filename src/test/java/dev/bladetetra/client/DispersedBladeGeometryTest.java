package dev.bladetetra.client;

import dev.bladetetra.visual.MaterialAppearance.TsubaProfile;
import mods.flammpfeil.slashblade.client.renderer.model.obj.WavefrontObject;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class DispersedBladeGeometryTest {
    @Test void allGuardProfilesRetainActualGripPositionsUvsNormalsWithoutTouchingSource() throws Exception {
        Path file=Path.of("src/main/resources/assets/blade_tetra/model/modular/alpha9/katana_basic_simple_stable.obj");
        try(var in=Files.newInputStream(file)) {
            var model=new WavefrontObject("actual_tetra",in);
            for(var profile:TsubaProfile.values()) {
                var source=ForgedGuardModel.forProfile(model,profile);
                int count=source.groupObjects.stream().mapToInt(g->g.faces.size()).sum();
                var hilt=DispersedBladeGeometry.hilt(source,"blade");
                assertNotNull(hilt);
                assertNotSame(source,hilt,"actual standard model must not fall back to an entire sword");
                Set<String> expected=new HashSet<>(), actual=new HashSet<>();
                for(var group:source.groupObjects) if(group.name.equals("blade")) for(var face:group.faces) {
                    boolean steel=true;
                    for(var uv:face.textureCoordinates) steel&=uv.u>=0&&uv.u<=.5&&uv.v>=0&&uv.v<=.25;
                    if(!steel) for(int i=0;i<face.vertices.length;i++) expected.add(vertex(face,i));
                }
                for(var group:hilt.groupObjects) for(var face:group.faces)
                    for(int i=0;i<face.vertices.length;i++) actual.add(vertex(face,i));
                assertFalse(actual.isEmpty());
                assertEquals(expected,actual,"all original grip/guard vertices, UVs and normals survive: "+profile);
                assertEquals(count,source.groupObjects.stream().mapToInt(g->g.faces.size()).sum());
                assertSame(hilt,DispersedBladeGeometry.hilt(source,"blade"));
            }
        }
        DispersedBladeGeometry.clear();
    }
    private static String vertex(mods.flammpfeil.slashblade.client.renderer.model.obj.Face f,int i) {
        var p=f.vertices[i];var uv=f.textureCoordinates[i];
        var n=f.vertexNormals==null?f.faceNormal:f.vertexNormals[i];
        // Serializer keeps floats exactly; OBJ normal normalization is tolerated at six decimals.
        return String.format(java.util.Locale.ROOT,"%.5f/%.5f/%.5f %.5f/%.5f %.4f/%.4f/%.4f",
                p.x,p.y,p.z,uv.u,uv.v,n.x,n.y,n.z);
    }
    @Test void iconsDroppedObjectsSheathAndRitualRemainUntouched() {
        for(String target:new String[]{"sheath","sheath_luminous","item_blade","item_bladens","item_damaged","blade_fragment"})
            assertFalse(SenbonzakuraDispersedRender.shouldClip(true,true,false,false,target));
        for(String target:new String[]{"blade","blade_damaged","blade_luminous","blade_damaged_luminous"}) {
            assertTrue(SenbonzakuraDispersedRender.shouldClip(true,true,false,false,target));
            assertFalse(SenbonzakuraDispersedRender.shouldClip(true,false,false,false,target));
            assertFalse(SenbonzakuraDispersedRender.shouldClip(false,true,false,false,target));
            assertFalse(SenbonzakuraDispersedRender.shouldClip(true,true,true,false,target));
            assertFalse(SenbonzakuraDispersedRender.shouldClip(true,true,false,true,target));
        }
    }
    @Test void unfamiliarUvProviderSafelyKeepsItsEntireAppearance() {
        String obj="v 0 0 0\nv 1 0 0\nv 1 1 0\nvt .8 .5\nvn 0 0 1\ng blade\nf 1/1/1 2/1/1 3/1/1\n";
        var source=new WavefrontObject("unknown",new java.io.ByteArrayInputStream(obj.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        assertSame(source,DispersedBladeGeometry.hilt(source,"blade"));
    }
}
