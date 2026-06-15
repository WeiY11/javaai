package com.example.evimind.extractor;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTable;
import org.apache.poi.xslf.usermodel.XSLFTableCell;
import org.apache.poi.xslf.usermodel.XSLFTableRow;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.springframework.stereotype.Component;

@Component
public class PptExtractor implements FileContentExtractor {

  @Override
  public boolean supports(String fileName) {
    return fileName.toLowerCase().endsWith(".pptx");
  }

  @Override
  public ExtractionResult extract(Path filePath, int maxSize) throws IOException {
    try (XMLSlideShow slideShow = new XMLSlideShow(new FileInputStream(filePath.toFile()))) {
      StringBuilder sb = new StringBuilder();
      List<XSLFSlide> slides = slideShow.getSlides();
      int slideCount = slides.size();
      int shapeCount = 0;

      for (int i = 0; i < slideCount; i++) {
        XSLFSlide slide = slides.get(i);
        sb.append("## Slide ").append(i + 1).append("\n");

        List<XSLFShape> shapes = slide.getShapes();
        shapeCount += shapes.size();

        for (XSLFShape shape : shapes) {
          if (shape instanceof XSLFTextShape textShape) {
            String text = textShape.getText();
            if (text != null && !text.isBlank()) {
              sb.append(text).append("\n\n");
            }
          } else if (shape instanceof XSLFTable table) {
            sb.append("\n");
            List<XSLFTableRow> rows = table.getRows();
            for (int r = 0; r < rows.size(); r++) {
              XSLFTableRow row = rows.get(r);
              sb.append("| ");
              for (XSLFTableCell cell : row.getCells()) {
                sb.append(cell.getText().replace("\n", " ")).append(" | ");
              }
              sb.append("\n");
              if (r == 0) {
                sb.append("| ");
                for (XSLFTableCell ignored : row.getCells()) {
                  sb.append("--- | ");
                }
                sb.append("\n");
              }
            }
            sb.append("\n");
          }
        }
        sb.append("\n");
      }

      String content = sb.toString();
      if (content.length() > maxSize) {
        content = content.substring(0, maxSize) + "\n... [内容已截断，文件过大]";
      }

      return ExtractionResult.success(
          content, "text", Map.of("slideCount", slideCount, "shapeCount", shapeCount));

    } catch (IOException e) {
      return ExtractionResult.failure("PPT文件损坏，无法解析: " + e.getMessage());
    }
  }
}
