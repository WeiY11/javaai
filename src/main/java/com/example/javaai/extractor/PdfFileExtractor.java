package com.example.javaai.extractor;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

@Component
public class PdfFileExtractor implements FileContentExtractor {

    @Override
    public boolean supports(String fileName) {
        return fileName.toLowerCase().endsWith(".pdf");
    }

    @Override
    public ExtractionResult extract(Path filePath, int maxSize) throws IOException {
        try (PDDocument document = Loader.loadPDF(filePath.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            StringBuilder text = new StringBuilder();
            int pageCount = document.getNumberOfPages();

            for (int i = 1; i <= pageCount; i++) {
                stripper.setStartPage(i);
                stripper.setEndPage(i);
                String pageText = stripper.getText(document);
                text.append(pageText);
            }

            String content = text.toString().trim();
            if (content.isEmpty()) {
                return ExtractionResult.success(
                    "[扫描件PDF，无法提取文本内容]",
                    "text",
                    Map.of("pageCount", pageCount)
                );
            }

            if (content.length() > maxSize) {
                content = content.substring(0, maxSize) + "\n... [内容已截断，文件过大]";
            }

            return ExtractionResult.success(content, "text", Map.of("pageCount", pageCount));
        } catch (IOException e) {
            return ExtractionResult.failure("PDF文件损坏，无法解析: " + e.getMessage());
        }
    }
}
