package com.example.evimind.service;

import com.example.evimind.extractor.ExtractionResult;
import com.example.evimind.extractor.FileContentExtractor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

@Service
public class FileExtractorService {

    private static final Set<String> IMAGE_EXTENSIONS = Set.of(".png", ".jpg", ".jpeg", ".gif", ".bmp", ".svg");
    private static final Set<String> BINARY_EXTENSIONS = Set.of(".pt", ".pth");

    private final List<FileContentExtractor> extractors;

    public FileExtractorService(List<FileContentExtractor> extractors) {
        this.extractors = extractors;
    }

    public ExtractionResult extractFile(Path filePath, int maxSize) throws IOException {
        String fileName = filePath.getFileName().toString();
        String lower = fileName.toLowerCase();

        // 图片文件特殊处理：返回 base64
        int dotIdx = lower.lastIndexOf('.');
        if (dotIdx > 0 && IMAGE_EXTENSIONS.contains(lower.substring(dotIdx))) {
            byte[] bytes = Files.readAllBytes(filePath);
            String base64 = Base64.getEncoder().encodeToString(bytes);
            String mime = getMimeType(lower.substring(dotIdx));
            return ExtractionResult.success(
                "data:" + mime + ";base64," + base64,
                "image-base64",
                Map.of("size", bytes.length)
            );
        }

        // 纯二进制文件不提取
        if (dotIdx > 0 && BINARY_EXTENSIONS.contains(lower.substring(dotIdx))) {
            return ExtractionResult.failure("二进制模型文件，无法提取文本内容");
        }

        // 查找匹配的提取器
        for (FileContentExtractor extractor : extractors) {
            if (extractor.supports(fileName)) {
                return extractor.extract(filePath, maxSize);
            }
        }

        return ExtractionResult.failure("不支持的文件格式");
    }

    public boolean isExtractable(String fileName) {
        String lower = fileName.toLowerCase();
        int dotIdx = lower.lastIndexOf('.');
        if (dotIdx > 0) {
            String ext = lower.substring(dotIdx);
            if (IMAGE_EXTENSIONS.contains(ext) || BINARY_EXTENSIONS.contains(ext)) return false;
        }
        for (FileContentExtractor extractor : extractors) {
            if (extractor.supports(fileName)) return true;
        }
        return false;
    }

    private String getMimeType(String ext) {
        return switch (ext) {
            case ".png" -> "image/png";
            case ".jpg", ".jpeg" -> "image/jpeg";
            case ".gif" -> "image/gif";
            case ".bmp" -> "image/bmp";
            case ".svg" -> "image/svg+xml";
            default -> "application/octet-stream";
        };
    }
}
