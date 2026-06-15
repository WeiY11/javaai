package com.example.evimind.extractor;

import net.sourceforge.tess4j.Tesseract;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

@Component
@ConditionalOnProperty(name = "custom.extractor.ocr.enabled", havingValue = "true", matchIfMissing = false)
public class ImageTextExtractor implements FileContentExtractor {

    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(
        ".png", ".jpg", ".jpeg", ".tiff", ".tif", ".bmp"
    );

    @Value("${custom.extractor.ocr.tessdata-path:tessdata}")
    private String tessdataPath;

    @Value("${custom.extractor.ocr.language:chi_sim+eng}")
    private String language;

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
            Tesseract tesseract = new Tesseract();
            tesseract.setDatapath(tessdataPath);
            tesseract.setLanguage(language);

            String text = tesseract.doOCR(filePath.toFile());

            if (text == null || text.isBlank()) {
                return ExtractionResult.success(
                    "[图片OCR未识别到文本内容]",
                    "text",
                    Map.of("ocrEngine", "tesseract", "language", language)
                );
            }

            String content = text.trim();
            if (content.length() > maxSize) {
                content = content.substring(0, maxSize) + "\n... [内容已截断，文件过大]";
            }

            return ExtractionResult.success(content, "text",
                Map.of("ocrEngine", "tesseract", "language", language));

        } catch (Exception e) {
            return ExtractionResult.failure("OCR识别失败: " + e.getMessage());
        }
    }
}
