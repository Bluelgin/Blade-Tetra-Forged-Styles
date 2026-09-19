package dev.bladetetra.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NamedLegacyPackFailureGuardTest {
    @Test
    void optionalCatalogFailureCannotAbortPackConstruction() throws IOException {
        String pack = Files.readString(Path.of(
                "src/main/java/dev/bladetetra/forging/NamedLegacyPack.java"));

        int discovery = pack.indexOf("NamedLegacyCatalog.values()");
        int fallback = pack.indexOf("discovered = List.of();");
        int genericModules = pack.indexOf("putGenericModule(\"saya\")");

        assertTrue(discovery >= 0 && fallback > discovery,
                "Named-blade discovery must have a fail-open fallback");
        assertTrue(genericModules > fallback,
                "The fallback must still build the generic Tetra modules");
        assertTrue(pack.contains("catch (RuntimeException | LinkageError failure)"),
                "Optional addon linkage failures must not abort pack construction");
        assertFalse(pack.contains("catch (Throwable"),
                "Fatal JVM errors must not be swallowed by the compatibility boundary");
    }
}
