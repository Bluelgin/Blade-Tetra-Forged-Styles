package dev.bladetetra.export;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BladeStudioProjectTest {
    @TempDir
    Path gameDirectory;

    @Test
    void roundTripsEditorSettingsAndRequiresExplicitReplacement() throws IOException {
        var path = BladeStudioProject.file(gameDirectory, "moon");
        var original = project("1.0.0");
        original.save(path, false);
        assertEquals(original, BladeStudioProject.load(path));

        var updated = project("1.0.1");
        assertThrows(IOException.class, () -> updated.save(path, false));
        assertEquals(original, BladeStudioProject.load(path));
        updated.save(path, true);
        assertEquals(updated, BladeStudioProject.load(path));
    }

    @Test
    void refusesMalformedProjectsAndPathSegments() throws IOException {
        assertThrows(IllegalArgumentException.class, () -> BladeStudioProject.file(gameDirectory, "../moon"));
        var path = BladeStudioProject.file(gameDirectory, "moon");
        Files.writeString(path, "{\"schema\":1,\"addon\":\"moon\",\"version\":null}");
        assertThrows(IOException.class, () -> BladeStudioProject.load(path));

        project("1.0.0").save(path, true);
        var other = BladeStudioProject.file(gameDirectory, "other");
        Files.move(path, other);
        assertThrows(IOException.class, () -> BladeStudioProject.load(other));
    }

    private static BladeStudioProject project(String version) {
        return new BladeStudioProject(1, "moon", version, "Moon Blades", "New Moon",
                "新月", "Creator", "A blade project", "First snow", "初雪", "logo.png",
                CustomBackplateImage.Mode.OVERLAY, "portrait.png");
    }
}
