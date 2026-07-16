package com.example.evimind.extractor;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class PdfFileExtractor implements FileContentExtractor {

  private static final int DEFAULT_MIN_NATIVE_TEXT_CHARS = 24;

  private final List<PdfOcrTextExtractor> ocrFallbacks;

  @Value("${custom.extractor.pdf.min-native-text-chars:24}")
  private int minNativeTextChars = DEFAULT_MIN_NATIVE_TEXT_CHARS;

  public PdfFileExtractor() {
    this(List.of());
  }

  @Autowired
  public PdfFileExtractor(List<PdfOcrTextExtractor> ocrFallbacks) {
    this.ocrFallbacks = ocrFallbacks != null ? List.copyOf(ocrFallbacks) : List.of();
  }

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
      int nativeTextChars = countNonWhitespace(content);
      if (!isNativeTextUsable(nativeTextChars)) {
        ExtractionResult ocrResult =
            extractWithOcrFallback(filePath, maxSize, pageCount, nativeTextChars);
        if (ocrResult != null) {
          return ocrResult;
        }
        return ExtractionResult.failure(
            "PDF has no usable embedded text. Enable OCR and retry ingestion for scanned PDFs.");
      }

      if (content.length() > maxSize) {
        content = content.substring(0, maxSize) + "\n... [内容已截断，文件过大]";
      }

      return ExtractionResult.success(
          content,
          "text",
          Map.of(
              "pageCount",
              pageCount,
              "extractionMode",
              "native",
              "ocrUsed",
              false,
              "nativeTextChars",
              nativeTextChars));
    } catch (IOException e) {
      return ExtractionResult.failure("PDF文件损坏，无法解析: " + e.getMessage());
    }
  }

  private ExtractionResult extractWithOcrFallback(
      Path filePath, int maxSize, int pageCount, int nativeTextChars) throws IOException {
    if (ocrFallbacks.isEmpty()) {
      return null;
    }

    ExtractionResult lastFailure = null;
    for (PdfOcrTextExtractor fallback : ocrFallbacks) {
      ExtractionResult result = fallback.extract(filePath, maxSize);
      if (result.isSuccess() && isNativeTextUsable(countNonWhitespace(result.getContent()))) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.putAll(result.getMetadata());
        metadata.put("pageCount", pageCount);
        metadata.put("extractionMode", "ocr");
        metadata.put("ocrUsed", true);
        metadata.put("nativeTextChars", nativeTextChars);
        return ExtractionResult.success(result.getContent(), result.getContentType(), metadata);
      }
      lastFailure = result;
    }

    String detail =
        lastFailure != null && lastFailure.getErrorMessage() != null
            ? lastFailure.getErrorMessage()
            : "OCR fallback returned no usable text";
    return ExtractionResult.failure(
        "PDF has no usable embedded text. OCR fallback failed: " + detail);
  }

  private boolean isNativeTextUsable(int textChars) {
    return textChars >= Math.max(1, minNativeTextChars);
  }

  private int countNonWhitespace(String text) {
    if (text == null || text.isBlank()) {
      return 0;
    }
    int count = 0;
    for (int i = 0; i < text.length(); i++) {
      if (!Character.isWhitespace(text.charAt(i))) {
        count++;
      }
    }
    return count;
  }
}
