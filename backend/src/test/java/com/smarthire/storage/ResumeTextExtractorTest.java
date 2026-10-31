package com.smarthire.storage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResumeTextExtractorTest {

    private final ResumeTextExtractor extractor = new ResumeTextExtractor();

    @Test
    @DisplayName("plain text resumes are decoded as UTF-8")
    void extractsPlainText() throws Exception {
        byte[] bytes = "Alex Rivera\nSkills: Java, Spring Boot, PostgreSQL".getBytes(StandardCharsets.UTF_8);

        String text = extractor.extract(bytes, "resume.txt");

        assertThat(text).contains("Alex Rivera").contains("Spring Boot");
    }

    @Test
    @DisplayName("a missing file yields an empty string rather than an exception")
    void emptyBytesYieldEmptyText() throws Exception {
        assertThat(extractor.extract(new byte[0], "resume.pdf")).isEmpty();
        assertThat(extractor.extract(null, "resume.pdf")).isEmpty();
    }

    @Test
    @DisplayName("oversized documents are truncated to keep payloads bounded")
    void oversizedDocumentIsTruncated() throws Exception {
        String huge = "word ".repeat(20_000);

        String text = extractor.extract(huge.getBytes(StandardCharsets.UTF_8), "resume.txt");

        assertThat(text.length()).isLessThanOrEqualTo(40_000);
    }

    @Test
    @DisplayName("stored file names are sanitized and only known extensions are allowed")
    void storageRejectsUnknownExtensions() throws Exception {
        FileStorageService storage = new FileStorageService(configure("./build/test-uploads"));

        MockMultipartFile good = new MockMultipartFile(
                "file", "My Resume.PDF", "application/pdf", "%PDF-1.4 fake".getBytes(StandardCharsets.UTF_8));
        String stored = storage.store(good, "resume-1");
        assertThat(stored).startsWith("resume-1").endsWith(".pdf");
        assertThat(stored).doesNotContain(" ");

        MockMultipartFile executable = new MockMultipartFile(
                "file", "evil.exe", "application/octet-stream", new byte[]{1, 2, 3});
        assertThatThrownBy(() -> storage.store(executable, "resume-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported file type");
    }

    private static com.smarthire.config.AppProperties configure(String uploadDir) {
        com.smarthire.config.AppProperties properties = new com.smarthire.config.AppProperties();
        properties.getStorage().setUploadDir(uploadDir);
        return properties;
    }
}
