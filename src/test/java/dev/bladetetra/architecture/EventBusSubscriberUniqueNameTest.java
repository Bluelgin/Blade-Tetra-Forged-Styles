package dev.bladetetra.architecture;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the exact generated-name algorithm used by Forge EventBus 6.0.5.
 * Its ModLauncherFactory key uses package + declaring simple name + method +
 * event simple name, not the declaring class's binary name.
 */
class EventBusSubscriberUniqueNameTest {
    private static final String SUBSCRIBER =
            "Lnet/minecraftforge/fml/common/Mod$EventBusSubscriber;";
    private static final String SUBSCRIBE_EVENT =
            "Lnet/minecraftforge/eventbus/api/SubscribeEvent;";

    @Test
    void productionSubscribersHaveUniqueGeneratedListenerNames() throws IOException {
        Path classes = Path.of("build/classes/java/main");
        assertTrue(Files.isDirectory(classes), "main production classes must be compiled before this test");

        Map<String, String> generatedNames = new HashMap<>();
        try (Stream<Path> paths = Files.walk(classes)) {
            paths.filter(path -> path.toString().endsWith(".class"))
                    .forEach(path -> inspect(path, classes, generatedNames));
        }
    }

    private static void inspect(Path path, Path root, Map<String, String> generatedNames) {
        try {
            ClassReader reader = new ClassReader(Files.readAllBytes(path));
            String[] internalName = new String[1];
            boolean[] subscriber = new boolean[1];
            Map<String, String> callbacks = new HashMap<>();

            reader.accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public void visit(int version, int access, String name, String signature,
                        String superName, String[] interfaces) {
                    internalName[0] = name;
                }

                @Override
                public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                    if (SUBSCRIBER.equals(descriptor)) subscriber[0] = true;
                    return null;
                }

                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor,
                        String signature, String[] exceptions) {
                    Type[] arguments = Type.getArgumentTypes(descriptor);
                    if (arguments.length != 1) return null;
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override
                        public AnnotationVisitor visitAnnotation(String annotation, boolean visible) {
                            if (SUBSCRIBE_EVENT.equals(annotation)) {
                                callbacks.put(name, arguments[0].getClassName() + "|" + descriptor);
                            }
                            return null;
                        }
                    };
                }

                @Override
                public void visitEnd() {
                    if (!subscriber[0] || internalName[0] == null) return;
                    String binaryName = internalName[0].replace('/', '.');
                    int packageEnd = binaryName.lastIndexOf('.');
                    String packageName = packageEnd < 0 ? "" : binaryName.substring(0, packageEnd);
                    String className = packageEnd < 0 ? binaryName : binaryName.substring(packageEnd + 1);
                    int nested = className.lastIndexOf('$');
                    String simpleName = nested < 0 ? className : className.substring(nested + 1);

                    callbacks.forEach((method, signature) -> {
                        int separator = signature.indexOf('|');
                        String eventName = signature.substring(0, separator);
                        String descriptor = signature.substring(separator + 1);
                        int eventSeparator = Math.max(eventName.lastIndexOf('.'), eventName.lastIndexOf('$'));
                        String eventSimpleName = eventName.substring(eventSeparator + 1);
                        String generatedName = String.format("%s.__%s_%s_%s",
                                packageName, simpleName, method, eventSimpleName);
                        String callback = binaryName + "#" + method + descriptor;
                        String previous = generatedNames.putIfAbsent(generatedName, callback);
                        assertTrue(previous == null || previous.equals(callback),
                                () -> "Forge EventBus 6.0.5 generated listener-name collision: "
                                        + generatedName + " maps to both " + previous + " and " + callback);
                    });
                }
            }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
