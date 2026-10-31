package com.smarthire.storage;

import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Turns an uploaded resume into plain text - the single input the Python
 * resume-intelligence service consumes. Uses Apache Tika so PDF, DOCX, RTF and TXT
 * all funnel through the same path.
 */
@Slf4j
@Service
public class ResumeTextExtractor {

    private static final int MAX_CHARS = 40_000;

    private final Tika tika = new Tika();

    public String extract(byte[] bytes, String filename) throws Exception {
        if (bytes == null || bytes.length == 0) {
            return "";
        }
        String name = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        if (name.endsWith(".txt") || name.endsWith(".md")) {
            return truncate(new String(bytes, StandardCharsets.UTF_8));
        }
        String parsed = tika.parseToString(new ByteArrayInputStream(bytes));
        return truncate(parsed);
    }

    private static String truncate(String text) {
        if (text == null) {
            return "";
        }
        String normalized = text.replace('\u0000', ' ').trim();
        if (normalized.length() <= MAX_CHARS) {
            return normalized;
        }
        return normalized.substring(0, MAX_CHARS);
    }
}
