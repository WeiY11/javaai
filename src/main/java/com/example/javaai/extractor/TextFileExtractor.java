package com.example.javaai.extractor;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

@Component
public class TextFileExtractor implements FileContentExtractor {

    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(
        ".json", ".csv", ".md", ".py", ".tex", ".txt", ".log",
        ".yaml", ".yml", ".xml", ".html", ".css", ".js", ".java",
        ".c", ".cpp", ".h", ".sh", ".r", ".sql", ".toml", ".ini", ".cfg"
    );

    @Override
    public boolean supports(String fileName) {
        String lower = fileName.toLowerCase();
        int dotIdx = lower.lastIndexOf('.');
        if (dotIdx < 0) return false;
        return SUPPORTED_EXTENSIONS.contains(lower.substring(dotIdx));
    }

    @Override
    public ExtractionResult extract(Path filePath, int maxSize) throws IOException {
        try {
            String content = Files.readString(filePath);
            if (content.length() > maxSize) {
                content = content.substring(0, maxSize) + "\n... [内容已截断，文件过大]";
            }
            return ExtractionResult.success(content, "text", null);
        } catch (IOException e) {
            return ExtractionResult.failure("无法读取文本文件: " + e.getMessage());
        }
    }
}
