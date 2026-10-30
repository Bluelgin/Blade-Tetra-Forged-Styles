package dev.bladetetra.client;

import mods.flammpfeil.slashblade.client.renderer.model.obj.WavefrontObject;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AwakeningBladeGeometryTest {
    @Test void realBladeFacesClipFromTipWhileHiltAndOriginalMeshSurvive() {
        String obj="""
                v -100 0 0
                v -20 0 0
                v -20 4 0
                v -100 4 0
                v 0 0 0
                v 30 0 0
                v 30 4 0
                vt 0.1 0.9
                vt 0.4 0.9
                vt 0.4 0.8
                vt 0.1 0.8
                vt 0.1 0.5
                vt 0.3 0.5
                vt 0.3 0.4
                vn 0 0 1
                g blade
                f 1/1/1 2/2/1 3/3/1
                f 1/1/1 3/3/1 4/4/1
                f 5/5/1 6/6/1 7/7/1
                """;
        var source=new WavefrontObject("test",new ByteArrayInputStream(obj.getBytes(StandardCharsets.UTF_8)));
        assertSame(source,AwakeningBladeGeometry.remaining(source,"blade",1));
        var half=AwakeningBladeGeometry.remaining(source,"blade",.5F);
        assertNotNull(half);
        for(var group:half.groupObjects) for(var face:group.faces) for(var v:face.vertices)
            assertTrue(v.x>=-60.001F,"a long triangle must be clipped, not just selected by its center");
        var hilt=AwakeningBladeGeometry.remaining(source,"blade",0);
        assertNotNull(hilt);
        for(var group:hilt.groupObjects) for(var face:group.faces) for(var v:face.vertices)
            assertTrue(v.x>=0,"hilt is the original geometry, no generic model replacement");
        assertEquals(3,source.groupObjects.get(0).faces.size());
        assertSame(half,AwakeningBladeGeometry.remaining(source,"blade",.5F));
        AwakeningBladeGeometry.clear();
    }
}
