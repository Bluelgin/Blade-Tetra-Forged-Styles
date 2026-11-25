package dev.bladetetra.export;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Versioned local editor settings. Server-side snapshots and approvals are session data. */
record BladeStudioProject(int schema, String addon, String version, String title, String bladeName,
                          String chineseName, String author, String description, String lore, String chineseLore, String logoPath,
                          CustomBackplateImage.Mode backplateMode, String backplatePath) {
    private static final int MAX_BYTES = 16 * 1024;
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    BladeStudioProject {
        if (schema != 1 || addon == null || !addon.matches("[a-z0-9_]{1,50}")
                || logoPath == null || logoPath.length() > 1024 || backplateMode == null || backplatePath == null || backplatePath.length() > 1024) {
            throw new IllegalArgumentException("Invalid Blade Studio project");
        }
        new BladeExportArchive.Metadata("bt_export_" + addon, version, title, author, description,
                bladeName, chineseName, lore, chineseLore, new byte[0]);
    }

    static Path file(Path gameDirectory, String addon) throws IOException {
        if (!addon.matches("[a-z0-9_]{1,50}")) throw new IllegalArgumentException("Invalid add-on ID");
        Path directory = gameDirectory.resolve("blade-studio-projects");
        Files.createDirectories(directory);
        if (!directory.toRealPath().getParent().equals(gameDirectory.toRealPath())) {
            throw new IOException("Studio project directory points outside the game directory");
        }
        return directory.resolve(addon + ".json");
    }

    static BladeStudioProject load(Path path) throws IOException {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("No Blade Studio project with that ID");
        }
        try (var input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
            byte[] bytes = input.readNBytes(MAX_BYTES + 1);
            if (bytes.length > MAX_BYTES) throw new IOException("Studio project exceeds the size limit");
            var project = JSON.fromJson(new String(bytes, StandardCharsets.UTF_8), BladeStudioProject.class);
            if (project == null || !path.getFileName().toString().equals(project.addon() + ".json")) {
                throw new IOException("Studio project ID does not match its file");
            }
            return project;
        } catch (JsonParseException invalid) {
            throw new IOException("Invalid Blade Studio project", invalid);
        }
    }

    void save(Path path, boolean replace) throws IOException {
        Path temporary = Files.createTempFile(path.getParent(), ".studio-", ".tmp");
        try {
            Files.writeString(temporary, JSON.toJson(this), StandardCharsets.UTF_8);
            if (replace) {
                try {
                    Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException ignored) {
                    Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
                }
            } else {
                // A same-directory move without REPLACE_EXISTING also detects a concurrent create.
                Files.move(temporary, path);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
