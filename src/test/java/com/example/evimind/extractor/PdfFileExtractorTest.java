package com.example.evimind.extractor;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PdfFileExtractorTest {

  @TempDir Path tempDir;

  @Test
  void shouldFailScannedPdfWhenOcrFallbackIsUnavailable() throws Exception {
    Path pdf = createBlankPdf("scanned.pdf");
    PdfFileExtractor extractor = new PdfFileExtractor();

    ExtractionResult result = extractor.extract(pdf, 2000);

    assertFalse(result.isSuccess());
    assertTrue(result.getErrorMessage().contains("OCR"));
  }

  @Test
  void shouldUseOcrFallbackWhenPdfHasNoExtractableText() throws Exception {
    Path pdf = createBlankPdf("scanned.pdf");
    PdfOcrTextExtractor fallback =
        (filePath, maxSize) ->
            ExtractionResult.success(
                "UAV-assisted DAG scheduling recognized by OCR",
                "text",
                Map.of("ocrEngine", "test-ocr"));
    PdfFileExtractor extractor = new PdfFileExtractor(List.of(fallback));

    ExtractionResult result = extractor.extract(pdf, 2000);

    assertTrue(result.isSuccess());
    assertEquals("ocr", result.getMetadata().get("extractionMode"));
    assertEquals(true, result.getMetadata().get("ocrUsed"));
    assertEquals(1, result.getMetadata().get("pageCount"));
    assertTrue(result.getContent().contains("DAG scheduling"));
  }

  @Test
  void shouldPreferNativePdfTextWhenExtractedTextIsUsable() throws Exception {
    Path pdf =
        createTextPdf(
            "paper.pdf",
            "Lyapunov PPO safe scheduler for UAV-assisted vehicular edge computing.");
    AtomicBoolean ocrCalled = new AtomicBoolean(false);
    PdfOcrTextExtractor fallback =
        (filePath, maxSize) -> {
          ocrCalled.set(true);
          return ExtractionResult.success("incorrect OCR text", "text", Map.of());
        };
    PdfFileExtractor extractor = new PdfFileExtractor(List.of(fallback));

    ExtractionResult result = extractor.extract(pdf, 2000);

    assertTrue(result.isSuccess());
    assertFalse(ocrCalled.get());
    assertEquals("native", result.getMetadata().get("extractionMode"));
    assertEquals(false, result.getMetadata().get("ocrUsed"));
    assertTrue(result.getContent().contains("safe scheduler"));
  }

  private Path createBlankPdf(String fileName) throws Exception {
    Path path = tempDir.resolve(fileName);
    try (PDDocument document = new PDDocument()) {
      document.addPage(new PDPage());
      document.save(path.toFile());
    }
    return path;
  }

  private Path createTextPdf(String fileName, String text) throws Exception {
    Path path = tempDir.resolve(fileName);
    String escapedText =
        text.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)");
    String contentStream = "BT\n/F1 12 Tf\n72 720 Td\n(" + escapedText + ") Tj\nET\n";
    List<String> objects =
        List.of(
            "1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n",
            "2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n",
            "3 0 obj\n"
                + "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] "
                + "/Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>\n"
                + "endobj\n",
            "4 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>\nendobj\n",
            "5 0 obj\n<< /Length "
                + contentStream.getBytes(StandardCharsets.ISO_8859_1).length
                + " >>\nstream\n"
                + contentStream
                + "endstream\nendobj\n");
    StringBuilder pdf = new StringBuilder("%PDF-1.4\n");
    List<Integer> offsets = new ArrayList<>();
    for (String object : objects) {
      offsets.add(pdf.length());
      pdf.append(object);
    }
    int xrefOffset = pdf.length();
    pdf.append("xref\n0 ").append(objects.size() + 1).append("\n");
    pdf.append("0000000000 65535 f \n");
    for (Integer offset : offsets) {
      pdf.append(String.format("%010d 00000 n \n", offset));
    }
    pdf.append("trailer\n<< /Size ")
        .append(objects.size() + 1)
        .append(" /Root 1 0 R >>\nstartxref\n")
        .append(xrefOffset)
        .append("\n%%EOF\n");
    Files.writeString(path, pdf.toString(), StandardCharsets.ISO_8859_1);
    return path;
  }
}
