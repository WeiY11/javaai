package com.example.evimind.extractor;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

import org.apache.poi.EncryptedDocumentException;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Component;

@Component
public class ExcelFileExtractor implements FileContentExtractor {

  @Override
  public boolean supports(String fileName) {
    String lower = fileName.toLowerCase();
    return lower.endsWith(".xlsx") || lower.endsWith(".xls");
  }

  @Override
  public ExtractionResult extract(Path filePath, int maxSize) throws IOException {
    try (Workbook workbook = WorkbookFactory.create(filePath.toFile(), (String) null, true)) {
      StringBuilder sb = new StringBuilder();
      int sheetCount = workbook.getNumberOfSheets();
      int totalRows = 0;

      for (int i = 0; i < sheetCount; i++) {
        Sheet sheet = workbook.getSheetAt(i);
        String sheetName = sheet.getSheetName();
        sb.append("## Sheet: ").append(sheetName).append("\n\n");

        Row headerRow = sheet.getRow(0);
        int colCount = headerRow != null ? headerRow.getLastCellNum() : 0;

        // 列头
        if (headerRow != null) {
          sb.append("### 列头:\n");
          for (int c = 0; c < colCount; c++) {
            Cell cell = headerRow.getCell(c);
            sb.append("- ").append(getCellStringValue(cell)).append("\n");
          }
          sb.append("\n");
        }

        // 数据行（最多100行预览）
        sb.append("### 数据:\n");
        int dataRows = 0;
        for (int r = 1; r <= sheet.getLastRowNum() && dataRows < 100; r++) {
          Row row = sheet.getRow(r);
          if (row == null) continue;
          sb.append("行").append(r).append(": ");
          for (int c = 0; c < colCount; c++) {
            if (c > 0) sb.append(" | ");
            Cell cell = row.getCell(c);
            sb.append(getCellStringValue(cell));
          }
          sb.append("\n");
          dataRows++;
          totalRows++;
        }
        sb.append("\n");

        // 数值列统计
        sb.append("### 统计:\n");
        Map<Integer, List<Double>> numericCols = new LinkedHashMap<>();
        for (int r = 1; r <= sheet.getLastRowNum(); r++) {
          Row row = sheet.getRow(r);
          if (row == null) continue;
          for (int c = 0; c < colCount; c++) {
            Cell cell = row.getCell(c);
            if (cell != null && cell.getCellType() == CellType.NUMERIC) {
              numericCols
                  .computeIfAbsent(c, k -> new ArrayList<>())
                  .add(cell.getNumericCellValue());
            }
          }
        }
        for (Map.Entry<Integer, List<Double>> entry : numericCols.entrySet()) {
          int colIdx = entry.getKey();
          List<Double> values = entry.getValue();
          String colName =
              headerRow != null ? getCellStringValue(headerRow.getCell(colIdx)) : ("Col" + colIdx);
          double min = values.stream().mapToDouble(Double::doubleValue).min().orElse(0);
          double max = values.stream().mapToDouble(Double::doubleValue).max().orElse(0);
          double avg = values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
          sb.append("- ")
              .append(colName)
              .append(": min=")
              .append(String.format("%.2f", min))
              .append(", max=")
              .append(String.format("%.2f", max))
              .append(", avg=")
              .append(String.format("%.2f", avg))
              .append(", count=")
              .append(values.size())
              .append("\n");
        }
        sb.append("\n");
      }

      String content = sb.toString();
      if (content.length() > maxSize) {
        content = content.substring(0, maxSize) + "\n... [内容已截断，文件过大]";
      }

      return ExtractionResult.success(
          content, "structured-text", Map.of("sheetCount", sheetCount, "totalRows", totalRows));

    } catch (EncryptedDocumentException e) {
      return ExtractionResult.failure("文件受密码保护，无法解析");
    } catch (IOException e) {
      return ExtractionResult.failure("Excel文件损坏，无法解析: " + e.getMessage());
    }
  }

  private String getCellStringValue(Cell cell) {
    if (cell == null) return "";
    return switch (cell.getCellType()) {
      case STRING -> cell.getStringCellValue();
      case NUMERIC -> {
        double val = cell.getNumericCellValue();
        if (val == Math.floor(val) && !Double.isInfinite(val)) {
          yield String.valueOf((long) val);
        }
        yield String.valueOf(val);
      }
      case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
      case FORMULA -> {
        try {
          yield cell.getStringCellValue();
        } catch (Exception ex) {
          yield String.valueOf(cell.getNumericCellValue());
        }
      }
      default -> "";
    };
  }
}
