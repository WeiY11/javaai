package com.example.javaai.ingestion;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Slf4j
@Component
public class TextCleaner {

    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]");
    private static final Pattern MULTIPLE_SPACES = Pattern.compile(" {3,}");
    private static final Pattern MULTIPLE_NEWLINES = Pattern.compile("\n{3,}");

    public String clean(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }

        String cleaned = CONTROL_CHARS.matcher(text).replaceAll("");
        cleaned = MULTIPLE_SPACES.matcher(cleaned).replaceAll(" ");
        cleaned = MULTIPLE_NEWLINES.matcher(cleaned).replaceAll("\n\n");
        cleaned = cleaned.trim();

        cleaned = removeShortLines(cleaned, 10);

        log.debug("Text cleaned: {} -> {} chars", text.length(), cleaned.length());
        return cleaned;
    }

    private String removeShortLines(String text, int minLineLength) {
        String[] lines = text.split("\n");
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.length() >= minLineLength || trimmed.isEmpty()) {
                sb.append(line).append("\n");
            }
        }
        return sb.toString().trim();
    }
}
