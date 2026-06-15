package com.example.evimind.extractor;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Component;

@Component
public class WordFileExtractor implements FileContentExtractor {

  @Override
  public boolean supports(String fileName) {
    return fileName.toLowerCase().endsWith(".docx");
  }

  @Override
  public ExtractionResult extract(Path filePath, int maxSize) throws IOException {
    try (XWPFDocument document = new XWPFDocument(new FileInputStream(filePath.toFile()))) {
      StringBuilder sb = new StringBuilder();
      int paragraphCount = 0;
      int tableCount = 0;

      // 段落
      for (XWPFParagraph paragraph : document.getParagraphs()) {
        String text = paragraph.getText();
        if (text == null || text.isBlank()) continue;

        String style = paragraph.getStyle();
        if (style != null && style.startsWith("Heading")) {
          try {
            int level = Integer.parseInt(style.substring("Heading".length()));
            sb.append("#".repeat(Math.min(level, 6))).append(" ").append(text).append("\n\n");
          } catch (NumberFormatException e) {
            sb.append(text).append("\n\n");
          }
        } else {
          sb.append(text).append("\n\n");
        }
        paragraphCount++;
      }

      // 表格
      for (XWPFTable table : document.getTables()) {
        sb.append("\n");
        for (int i = 0; i < table.getRows().size(); i++) {
          XWPFTableRow row = table.getRow(i);
          sb.append("| ");
          for (XWPFTableCell cell : row.getTableCells()) {
            sb.append(cell.getText().replace("\n", " ")).append(" | ");
          }
          sb.append("\n");
          if (i == 0) {
            sb.append("| ");
            for (XWPFTableCell cell : row.getTableCells()) {
              sb.append("--- | ");
            }
            sb.append("\n");
          }
        }
        sb.append("\n");
        tableCount++;
      }

      String content = sb.toString();
      if (content.length() > maxSize) {
        content = content.substring(0, maxSize) + "\n... [内容已截断，文件过大]";
      }

      return ExtractionResult.success(
          content, "text", Map.of("paragraphCount", paragraphCount, "tableCount", tableCount));

    } catch (IOException e) {
      return ExtractionResult.failure("Word文件损坏，无法解析: " + e.getMessage());
    }
  }
}
