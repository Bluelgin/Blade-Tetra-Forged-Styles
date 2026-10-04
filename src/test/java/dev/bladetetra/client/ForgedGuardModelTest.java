package dev.bladetetra.client;

import dev.bladetetra.visual.MaterialAppearance.TsubaProfile;
import mods.flammpfeil.slashblade.client.renderer.model.obj.*;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ForgedGuardModelTest {
    private static WavefrontObject model() throws Exception {
        Path path=Path.of("src/main/resources/assets/blade_tetra/model/modular/alpha9/katana_basic_simple_wrapped.obj");
        try(var input=Files.newInputStream(path)) {return new WavefrontObject(path.getFileName().toString(),input);}
    }
    @Test void allGuardProfilesHaveDistinctActualGeometryAndMaterialUvs() throws Exception {
        var source=model();
        var round=ForgedGuardModel.forProfile(source,TsubaProfile.MARU);
        var pierced=ForgedGuardModel.forProfile(source,TsubaProfile.MOKKO);
        var octagon=ForgedGuardModel.forProfile(source,TsubaProfile.KAKU);
        String a=RuntimeWavefrontViewFactory.serialize("blade",round.groupObjects.stream().filter(g->g.name.equals("blade")).findFirst().orElseThrow().faces);
        String b=RuntimeWavefrontViewFactory.serialize("blade",pierced.groupObjects.stream().filter(g->g.name.equals("blade")).findFirst().orElseThrow().faces);
        String c=RuntimeWavefrontViewFactory.serialize("blade",octagon.groupObjects.stream().filter(g->g.name.equals("blade")).findFirst().orElseThrow().faces);
        assertNotEquals(a,b);assertNotEquals(b,c);assertNotEquals(a,c);
        assertSame(round,ForgedGuardModel.forProfile(source,TsubaProfile.MARU));
        for(var view:List.of(round,pierced,octagon)) for(var group:view.groupObjects) for(var f:group.faces) {
            for(var v:f.vertices) assertTrue(Float.isFinite(v.x)&&Float.isFinite(v.y)&&Float.isFinite(v.z));
            if(InventoryGuardGeometry.isGuard(f)) {
                assertNotNull(f.faceNormal);
                for(var uv:f.textureCoordinates) assertTrue(uv.u*128>=52 && uv.u*128<76 && uv.v*128>=58 && uv.v*128<82);
            }
        }
    }
    @Test void all108VariantsKeepEveryOtherComponentUnchanged() throws Exception {
        int count=0;
        try(var paths=Files.list(Path.of("src/main/resources/assets/blade_tetra/model/modular/alpha9"))) {
            for(Path path:paths.filter(p->p.toString().endsWith(".obj")).toList()) {
                WavefrontObject source;try(var input=Files.newInputStream(path)){source=new WavefrontObject(path.getFileName().toString(),input);}
                var view=ForgedGuardModel.forProfile(source,TsubaProfile.MARU);
                for(var old:source.groupObjects) {
                    var updated=view.groupObjects.stream().filter(g->g.name.equals(old.name)).findFirst().orElseThrow();
                    var originalOther=old.faces.stream().filter(f->!InventoryGuardGeometry.isGuard(f)).toList();
                    var newOther=updated.faces.stream().filter(f->!InventoryGuardGeometry.isGuard(f)).toList();
                    assertEquals(RuntimeWavefrontViewFactory.serialize(old.name,originalOther),
                            RuntimeWavefrontViewFactory.serialize(old.name,newOther),path+":"+old.name);
                }
                count++;
            }
        }
        assertEquals(108,count);
    }
    @Test void noGuardRemovesGuardFacesButNeverHandle() throws Exception {
        var source=model(); var result=ForgedGuardModel.forProfile(source,TsubaProfile.NONE);
        assertTrue(result.groupObjects.stream().flatMap(g->g.faces.stream()).noneMatch(InventoryGuardGeometry::isGuard));
        assertTrue(result.groupObjects.stream().flatMap(g->g.faces.stream()).anyMatch(f->f.textureCoordinates[0].v*128>59));
    }
    @Test void newGuardFinishHasBevelAndGrooveInEveryPalette() {
        var palette=new MaterialTextureStyleEngine.Palette(0x333333,0x888888,0xEEEEEE);
        for(var profile:List.of(TsubaProfile.MARU,TsubaProfile.MOKKO,TsubaProfile.KAKU)) {
            assertNotEquals(ForgedGuardPainter.color(0x888888,palette,64,81,profile),
                    ForgedGuardPainter.color(0x888888,palette,64,74,profile));
        }
    }
    @Test void moonMarksAreLocalizedAndBladeGlowNeverFillsWholeSurface() {
        assertTrue(AkatsukiArtPainter.crescent(-.6F,0));
        assertFalse(AkatsukiArtPainter.crescent(.5F,0));
        assertFalse(AkatsukiArtPainter.crescent(2,0));
        assertEquals(0,AkatsukiArtPainter.bladeGlow(30,28));
        assertNotEquals(AkatsukiArtPainter.saya(20.5F,45),AkatsukiArtPainter.saya(30,45));
    }
    @Test void guardsHaveAnExplicitInventoryTriangleBudget() throws Exception {
        var source=model();
        for(var profile:List.of(TsubaProfile.MARU,TsubaProfile.MOKKO,TsubaProfile.KAKU)) {
            var result=ForgedGuardModel.forProfile(source,profile);
            var group=result.groupObjects.stream().filter(g->g.name.equals("item_blade")).findFirst().orElseThrow();
            long triangles=group.faces.stream().filter(InventoryGuardGeometry::isGuard).count();
            assertTrue(triangles<=288,"Guard triangle budget exceeded: "+triangles);
        }
        assertTrue(MaterialTextureCache.MAX_CACHE_SIZE>=108,"Both layouts must fit a fully populated inventory");
    }
}
