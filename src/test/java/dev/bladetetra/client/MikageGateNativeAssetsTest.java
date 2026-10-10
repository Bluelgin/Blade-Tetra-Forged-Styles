package dev.bladetetra.client;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import javax.imageio.ImageIO;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

/** Verify the pinned dependency supplies the actual render assets and the native reflection hook. */
class MikageGateNativeAssetsTest {
    @Test void nativeGateModelsAndTexturesArePresent() throws Exception {
        for (String[] model : new String[][]{{"slashdim", "base", "wind"}, {"ss", "ss"}}) {
            try (InputStream input = resource("assets/slashblade/model/util/" + model[0] + ".obj")) {
                String obj = new String(input.readAllBytes(), StandardCharsets.UTF_8);
                for (int group = 1; group < model.length; group++) {
                    String name = model[group];
                    assertTrue(obj.lines().anyMatch(line -> line.trim().equals("g " + name)), name);
                }
            }
            try (InputStream input = resource("assets/slashblade/model/util/" + model[0] + ".png")) {
                var png = ImageIO.read(input); assertNotNull(png);
                assertTrue(png.getWidth() > 0 && png.getHeight() > 0);
            }
        }
    }
    @Test void originalReflectionAndBurstHooksRemainCompatible() throws Exception {
        var reflector = nativeClass("ability/ArrowReflector");
        var reflect = reflector.methods.stream().filter(m -> m.name.equals("doReflect")
                && m.desc.equals("(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity;)V"))
                .findFirst().orElseThrow();
        assertTrue((reflect.access & Opcodes.ACC_STATIC) != 0, "reflection mixin injects a static method");
        var swords = nativeClass("entity/EntityAbstractSummonedSword");
        var burst = swords.methods.stream().filter(m -> m.name.equals("burst") && m.desc.equals("()V"))
                .findFirst().orElseThrow();
        assertEquals(0, burst.access & (Opcodes.ACC_FINAL | Opcodes.ACC_STATIC), "trial swords suppress native area effects");
    }
    private ClassNode nativeClass(String name) throws Exception {
        try (InputStream input = resource("mods/flammpfeil/slashblade/" + name + ".class")) {
            var node = new ClassNode();
            new ClassReader(input).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return node;
        }
    }
    private InputStream resource(String name) {
        InputStream input = getClass().getResourceAsStream("/" + name); assertNotNull(input, name); return input;
    }
}
