package com.example.evimind.extractor;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import net.sourceforge.tess4j.Tesseract;

@Component
@ConditionalOnProperty(
    name = "custom.extractor.ocr.enabled",
    havingValue = "true",
    matchIfMissing = false)
public class TesseractPdfOcrTextExtractor implements PdfOcrTextExtractor {

  @Value("${custom.extractor.ocr.tessdata-path:tessdata}")
  private String tessdataPath;

  @Value("${custom.extractor.ocr.language:chi_sim+eng}")
  private String language;

  @Value("${custom.extractor.ocr.pdf-dpi:220}")
  private int pdfDpi;

  @Value("${custom.extractor.ocr.pdf-max-pages:20}")
  private int maxPages;

  @Override
  public ExtractionResult extract(Path filePath, int maxSize) throws IOException {
    try (PDDocument document = Loader.loadPDF(filePath.toFile())) {
      int pageCount = document.getNumberOfPages();
      int processedPages = Math.min(pageCount, Math.max(1, maxPages));
      PDFRenderer renderer = new PDFRenderer(document);
      Tesseract tesseract = new Tesseract();
      tesseract.setDatapath(tessdataPath);
      tesseract.setLanguage(language);

      StringBuilder text = new StringBuilder();
      for (int pageIndex = 0; pageIndex < processedPages; pageIndex++) {
        BufferedImage image =
            renderer.renderImageWithDPI(pageIndex, Math.max(72, pdfDpi), ImageType.GRAY);
        String pageText = tesseract.doOCR(image);
        if (pageText == null || pageText.isBlank()) {
          continue;
        }
        if (text.length() > 0) {
          text.append("\n\n");
        }
        text.append("## Page ").append(pageIndex + 1).append(" (OCR)\n");
        text.append(pageText.trim());
        if (text.length() > maxSize) {
          break;
        }
      }

      String content = text.toString().trim();
      if (content.isBlank()) {
        return ExtractionResult.failure("PDF OCR recognized no text");
      }
      if (content.length() > maxSize) {
        content = content.substring(0, maxSize) + "\n... [内容已截断，文件过大]";
      }

      return ExtractionResult.success(
          content,
          "text",
          Map.of(
              "ocrEngine",
              "tesseract",
              "language",
              language,
              "pdfDpi",
              Math.max(72, pdfDpi),
              "processedPages",
              processedPages,
              "pageCount",
              pageCount));
    } catch (Exception e) {
      return ExtractionResult.failure("PDF OCR failed: " + e.getMessage());
    }
  }
}
