package com.example.evimind.service;

import java.awt.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.example.evimind.config.AnalysisProperties;
import com.example.evimind.model.AnalysisResult;
import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfWriter;

@Service
public class ReportExportService {

  private final AnalysisProperties properties;
  private final String baseDir;

  public ReportExportService(
      AnalysisProperties properties, @Value("${custom.data.base-dir}") String baseDir) {
    this.properties = properties;
    this.baseDir = baseDir;
  }

  public String exportMarkdown(List<AnalysisResult> results, String reportTitle) {
    try {
      Path reportDir = Paths.get(baseDir, properties.getReportDir());
      Files.createDirectories(reportDir);

      StringBuilder sb = new StringBuilder();
      sb.append("# ").append(reportTitle).append("\n\n");
      sb.append("生成时间: ")
          .append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
          .append("\n\n");
      sb.append("---\n\n");

      for (AnalysisResult result : results) {
        sb.append("## ").append(result.getFileName()).append("\n\n");
        sb.append("- **文件路径**: ").append(result.getFilePath()).append("\n");
        sb.append("- **分析时间**: ")
            .append(
                result.getAnalyzedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
            .append("\n");
        sb.append("- **AI模型**: ").append(result.getProvider()).append("\n");
        sb.append("- **文件大小**: ").append(formatSize(result.getFileSize())).append("\n\n");
        sb.append(result.getContent()).append("\n\n");
        sb.append("---\n\n");
      }

      String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
      String sanitizedTitle = reportTitle.replaceAll("[\\\\/:*?\"<>|]", "_");
      String fileName = timestamp + "-" + sanitizedTitle + ".md";
      Path filePath = reportDir.resolve(fileName);

      Files.writeString(filePath, sb.toString(), StandardCharsets.UTF_8);
      return properties.getReportDir() + "/" + fileName;

    } catch (IOException e) {
      throw new RuntimeException("导出Markdown报告失败: " + e.getMessage(), e);
    }
  }

  public String exportPdf(List<AnalysisResult> results, String reportTitle) {
    try {
      Path reportDir = Paths.get(baseDir, properties.getReportDir());
      Files.createDirectories(reportDir);

      String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
      String sanitizedTitle = reportTitle.replaceAll("[\\\\/:*?\"<>|]", "_");
      String fileName = timestamp + "-" + sanitizedTitle + ".pdf";
      Path filePath = reportDir.resolve(fileName);

      Document document = new Document(PageSize.A4, 50, 50, 50, 50);
      PdfWriter.getInstance(document, new FileOutputStream(filePath.toFile()));
      document.open();

      // 中文字体
      BaseFont baseFont = createChineseBaseFont();
      Font titleFont = new Font(baseFont, 20, Font.BOLD);
      Font headingFont = new Font(baseFont, 14, Font.BOLD);
      Font metaFont = new Font(baseFont, 10, Font.NORMAL);
      metaFont.setColor(Color.GRAY);
      Font contentFont = new Font(baseFont, 11, Font.NORMAL);

      // 标题页
      document.add(new Paragraph(reportTitle, titleFont));
      document.add(
          new Paragraph(
              "生成时间: "
                  + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
              metaFont));
      document.add(Chunk.NEWLINE);
      document.add(new Paragraph("————————————————————————————————", metaFont));
      document.add(Chunk.NEWLINE);

      // 各文件分析内容
      for (AnalysisResult result : results) {
        document.add(new Paragraph(result.getFileName(), headingFont));
        document.add(
            new Paragraph(
                "分析时间: "
                    + result
                        .getAnalyzedAt()
                        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                    + "  |  AI模型: "
                    + result.getProvider()
                    + "  |  文件大小: "
                    + formatSize(result.getFileSize()),
                metaFont));
        document.add(Chunk.NEWLINE);

        // 将分析内容按段落拆分
        String content = result.getContent();
        String[] lines = content.split("\n");
        for (String line : lines) {
          if (line.startsWith("# ")) {
            document.add(new Paragraph(line.substring(2), headingFont));
          } else if (line.startsWith("## ")) {
            Font subHeading = new Font(baseFont, 12, Font.BOLD);
            document.add(new Paragraph(line.substring(3), subHeading));
          } else {
            document.add(new Paragraph(line, contentFont));
          }
        }
        document.add(Chunk.NEWLINE);
        document.add(new Paragraph("————————————————————————————————", metaFont));
        document.add(Chunk.NEWLINE);
      }

      document.close();
      return properties.getReportDir() + "/" + fileName;

    } catch (DocumentException | IOException e) {
      throw new RuntimeException("导出PDF报告失败: " + e.getMessage(), e);
    }
  }

  private BaseFont createChineseBaseFont() throws IOException, DocumentException {
    String fontPath = properties.getPdfFontPath();
    if (fontPath != null && !fontPath.isBlank() && Files.exists(Paths.get(fontPath))) {
      return BaseFont.createFont(fontPath, BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
    }
    // 尝试系统字体
    String[] systemFonts = {
      "C:/Windows/Fonts/simhei.ttf",
      "C:/Windows/Fonts/simsun.ttc",
      "/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc",
      "/usr/share/fonts/truetype/noto/NotoSansCJK-Regular.ttc",
      "/System/Library/Fonts/PingFang.ttc"
    };
    for (String sysFont : systemFonts) {
      if (Files.exists(Paths.get(sysFont))) {
        return BaseFont.createFont(sysFont, BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
      }
    }
    // fallback: iText内置中文字体
    return BaseFont.createFont("STSong-Light", "UniGB-UCS2-H", BaseFont.NOT_EMBEDDED);
  }

  private String formatSize(long bytes) {
    if (bytes < 1024) return bytes + " B";
    if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
    return String.format("%.1f MB", bytes / (1024.0 * 1024));
  }
}
