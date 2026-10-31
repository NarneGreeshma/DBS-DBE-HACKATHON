package com.smarthire.storage;

import com.smarthire.config.AppProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Saves uploads under {@code smarthire.storage.upload-dir} using a flat, collision-proof
 * name. Paths are stored relative to the upload root so the data directory stays portable.
 */
@Slf4j
@Service
public class FileStorageService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "doc", "docx", "txt", "rtf");

    private final Path root;

    public FileStorageService(AppProperties properties) {
        this.root = Paths.get(properties.getStorage().getUploadDir()).toAbsolutePath().normalize();
    }

    public String store(MultipartFile file, String prefix) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file is empty");
        }
        String extension = extensionOf(file.getOriginalFilename());
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException(
                    "Unsupported file type '.%s'. Allowed: %s".formatted(extension, ALLOWED_EXTENSIONS));
        }

        Files.createDirectories(root);
        String filename = "%s-%s.%s".formatted(
                sanitize(prefix), UUID.randomUUID().toString().substring(0, 8), extension);
        Path target = root.resolve(filename).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("Invalid file path");
        }
        try (var in = file.getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
        log.debug("Stored upload at {}", target);
        return root.relativize(target).toString().replace('\\', '/');
    }

    public void deleteIfExists(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return;
        }
        try {
            Path resolved = root.resolve(relativePath).normalize();
            if (resolved.startsWith(root)) {
                Files.deleteIfExists(resolved);
            }
        } catch (IOException ex) {
            log.warn("Could not delete stored file {}", relativePath, ex);
        }
    }

    private static String extensionOf(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static String sanitize(String value) {
        String cleaned = value == null ? "file" : value.replaceAll("[^a-zA-Z0-9-_]", "");
        return cleaned.isBlank() ? "file" : cleaned.toLowerCase(Locale.ROOT);
    }
}
